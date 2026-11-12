# SPDX-License-Identifier: GPL-3.0-or-later
"""Fail-closed ownership for the disposable UI session, never device auto-selection.

A manifest is only a claim: check its live supervisor, process creation identities,
console/ADB listeners, ADB server identity, pinned transport and exact AVD on EVERY
call. Only read-only identity probes bypass run(); never call a UI wrapper from
validation. Shutdown uses creation-checked process objects, never `adb emu kill`.
"""
from contextlib import contextmanager
import copy
import json
import math
import os
from pathlib import Path
import re
import subprocess
import time

import psutil


class OwnershipError(RuntimeError):
    """No mutation is authorized when ownership cannot be established."""


def identity(process):
    return {'pid': process.pid, 'create_time': process.create_time()}


def matching_process(record):
    if (not isinstance(record, dict) or type(record.get('pid')) is not int
            or record['pid'] <= 0 or type(record.get('create_time')) not in (int, float)
            or not math.isfinite(record['create_time']) or record['create_time'] <= 0):
        raise OwnershipError('Invalid process identity')
    process = psutil.Process(record['pid'])
    if process.create_time() != record['create_time'] or not process.is_running():
        raise OwnershipError('Process disappeared or PID was reused')
    return process


def listener_pids(connections, port):
    rows = [c for c in connections if c.status == psutil.CONN_LISTEN and c.laddr.port == port]
    if not rows or any(c.pid is None or c.laddr.ip not in ('127.0.0.1', '::1', '0.0.0.0', '::') for c in rows):
        raise OwnershipError(f'Unverifiable listener on port {port}')
    return {c.pid for c in rows}


def transport_id(adb, serial):
    result = subprocess.run([adb, 'devices', '-l'], capture_output=True, timeout=15, check=True)
    rows = [line.split() for line in result.stdout.decode().splitlines() if line.split()[:1] == [serial]]
    if len(rows) != 1 or rows[0][1:2] != ['device']:
        raise OwnershipError('Explicit serial is missing, ambiguous or offline')
    ids = [word.split(':', 1)[1] for word in rows[0][2:] if word.startswith('transport_id:')]
    if len(ids) != 1 or not re.fullmatch(r'[1-9][0-9]*', ids[0]):
        raise OwnershipError('Missing unique ADB transport identity')
    return ids[0]


def local_adb_environment():
    # Never let an environment variable redirect checks/commands to a remote server.
    for key in ('ADB_SERVER_SOCKET', 'ANDROID_ADB_SERVER_ADDRESS', 'ANDROID_ADB_SERVER_PORT'):
        if os.environ.get(key):
            raise OwnershipError('Non-default ADB server environment is not allowed: ' + key)


class OwnedEmulator:
    def __init__(self, owner, serial, adb, expected_avd):
        self.owner = copy.deepcopy(owner)
        self.serial, self.adb, self.avd = serial, str(adb), expected_avd
        self._scope()

    @classmethod
    def from_file(cls, path, serial, adb, expected_avd):
        try:
            owner = json.loads(Path(path).read_text())
        except (OSError, ValueError) as error:
            raise OwnershipError('Unreadable owner manifest') from error
        return cls(owner, serial, adb, expected_avd)

    def _scope(self):
        o = self.owner
        match = re.fullmatch(r'emulator-([0-9]+)', self.serial or '')
        if not isinstance(o, dict) or o.get('schema') != 2 or not match:
            raise OwnershipError('A current owned emulator session is required')
        self.port = int(match[1])
        if not 5554 <= self.port <= 5682 or self.port % 2 or self.serial != f'emulator-{self.port}':
            raise OwnershipError('Invalid explicit emulator console port')
        if o.get('serial') != self.serial or o.get('avd') != self.avd:
            raise OwnershipError('Serial/AVD scope mismatch')
        if not isinstance(o.get('processes'), list) or not o['processes']:
            raise OwnershipError('No owned emulator processes')
        if not isinstance(o.get('transport_id'), str) or not re.fullmatch(r'[1-9][0-9]*', o['transport_id']):
            raise OwnershipError('Missing pinned transport')
        start, end = o.get('created_at'), o.get('expires_at')
        if (type(start) not in (int, float) or type(end) not in (int, float)
                or not math.isfinite(start) or not math.isfinite(end)
                or not start <= time.time() < end or not 0 < end - start <= 6000):
            raise OwnershipError('Expired or invalid session lifetime')
        local_adb_environment()

    def _local(self):
        self._scope()
        matching_process(self.owner.get('supervisor'))
        server = matching_process(self.owner.get('adb_server'))
        pids = set()
        for record in self.owner['processes']:
            process = matching_process(record)
            command = process.cmdline()
            # Tokens, not substrings: a sibling AVD must never count as ours.
            for option, value in (('-avd', self.avd), ('-port', str(self.port))):
                indices = [i for i, word in enumerate(command) if word == option]
                if len(indices) != 1 or command[indices[0] + 1:indices[0] + 2] != [value]:
                    raise OwnershipError('Emulator process arguments do not match session')
            pids.add(process.pid)
        connections = psutil.net_connections(kind='tcp')
        for port in (self.port, self.port + 1):
            if not listener_pids(connections, port) <= pids:
                raise OwnershipError('Emulator port belongs to an unowned process')
        if listener_pids(connections, 5037) != {server.pid}:
            raise OwnershipError('ADB server identity changed')

    def validate(self):
        try:
            self._local()
            if transport_id(self.adb, self.serial) != self.owner['transport_id']:
                raise OwnershipError('ADB transport was replaced')
            def read(*words):
                # Emulator console commands use a serial/console port, not an
                # ADB transport. Only this read-only console command is allowed.
                selector = ['-s', self.serial] if words[0] == 'emu' else ['-t', self.owner['transport_id']]
                base = [self.adb, *selector]
                return subprocess.run(base + list(words), capture_output=True, timeout=15, check=True).stdout.decode()
            if read('shell', 'getprop', 'ro.hardware').strip() not in ('ranchu', 'goldfish'):
                raise OwnershipError('Target is not an emulator')
            # Windows console output may contain doubled CR/LF framing. Ignore
            # empty framing lines, but never trim/substring-match an AVD name.
            avd_lines = [line for line in read('emu', 'avd', 'name').splitlines() if line]
            if avd_lines != [self.avd, 'OK']:
                raise OwnershipError('Exact AVD identity mismatch: ' + repr(avd_lines))
            # Reads are not authorization for a later call: close this phase with
            # another process/port check and pin dispatch to this transport ID.
            self._local()
        except (psutil.Error, OSError, ValueError, subprocess.SubprocessError) as error:
            raise OwnershipError('Unable to establish live emulator ownership') from error

    def run(self, *words, timeout=180):
        if (not words or not all(isinstance(w, str) for w in words)
                or words[0] not in ('shell', 'exec-out', 'install', 'pull', 'emu')
                or (words[0] == 'emu' and words != ('emu', 'avd', 'name'))):
            raise OwnershipError('Command outside session scope; shutdown uses owned processes only')
        self.validate()
        selector = ['-s', self.serial] if words[0] == 'emu' else ['-t', self.owner['transport_id']]
        return subprocess.run([self.adb, *selector, *words],
                              capture_output=True, timeout=timeout, check=True)

    def text(self, *words, binary=False, timeout=180):
        data = self.run(*words, timeout=timeout).stdout
        return data if binary else data.decode('utf-8', errors='replace')


@contextmanager
def bind_native_ui(ui, guard):
    """Cover shell/launch/tree/capture transitively; restore globals even on error."""
    old_adb, old_serial = ui.adb, ui.SERIAL
    ui.adb, ui.SERIAL = guard.text, guard.serial
    try:
        yield
    finally:
        ui.adb, ui.SERIAL = old_adb, old_serial


def stop_owned_processes(records):
    """Fallback also works after boot failure/port loss. No serial-based shutdown.

    The SAME psutil.Process object is identity-checked and terminated; psutil's
    terminate rechecks PID reuse. Never signal a newly looked-up replacement.
    Access denial/timeouts are reported, never escalated to blanket kills.
    """
    report = {'stopped': [], 'gone_or_reused': [], 'unresolved': []}
    for record in reversed(records):
        pid = record.get('pid')
        try:
            process = matching_process(record)
            process.terminate()
            process.wait(timeout=10)
            report['stopped'].append(pid)
        except (psutil.NoSuchProcess, OwnershipError):
            report['gone_or_reused'].append(pid)
        except (psutil.Error, OSError) as error:
            report['unresolved'].append({'pid': pid, 'error': type(error).__name__})
    return report
