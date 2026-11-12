#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""One fresh bounded emulator session; never reuse archived evidence as authority."""
import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import time
import psutil
from owned_emulator_guard import (OwnedEmulator, OwnershipError, identity,
    listener_pids, local_adb_environment, matching_process, stop_owned_processes,
    transport_id)

ROOT = Path(__file__).resolve().parents[1]

SDK = Path('C:/Users/Nauss/AppData/Local/Android/Sdk')
ADB = str(SDK / 'platform-tools/adb.exe')
AVD = 'ChopTorch_P1_API37'
SERIAL = 'emulator-5580'

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--evidence', type=Path, required=True,
        help='New directory only; an existing STOP/queue/owner is never reused')
    parser.add_argument('--smoke', action='store_true', help='One guarded boot read, then cleanup')
    args = parser.parse_args()
    out = args.evidence
    out.mkdir(parents=True, exist_ok=False)
    queue = out / 'queue'
    queue.mkdir(exist_ok=True)
    local_adb_environment()
    for port in (5580, 5581):
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', port))
    # Require an already running local server; do not start/kill a global server.
    servers = listener_pids(psutil.net_connections(kind='tcp'), 5037)
    if len(servers) != 1:
        raise OwnershipError('Ambiguous local ADB server')
    server = identity(psutil.Process(next(iter(servers))))
    devices = subprocess.check_output([ADB, 'devices', '-l']).decode()
    if any(line.split()[:1] == [SERIAL] for line in devices.splitlines()):
        raise OwnershipError('Requested serial already exists')
    owned = []
    proc = None
    owner = None
    log = (out / 'emulator.log').open('wb')
    try:
        proc = subprocess.Popen([str(SDK / 'emulator/emulator.exe'), '-avd', AVD,
            '-port', '5580', '-read-only', '-no-snapshot', '-no-window', '-no-audio',
            '-gpu', 'swiftshader', '-memory', '2048', '-cores', '2'], stdout=log, stderr=log)
        launcher = psutil.Process(proc.pid)
        launcher_record = identity(launcher)
        # Popen retains the original Windows process handle. A PID lookup alone
        # could adopt a replacement if the launcher already exited before capture.
        if proc.poll() is not None:
            raise OwnershipError('Launcher exited before its identity was bound')
        owned.append(launcher_record)
        started = time.time()
        guard = None
        deadline = time.monotonic() + (90 if args.smoke else 240)
        while time.monotonic() < deadline:
            # Only descendants of our exact launcher, never a name/AVD search.
            # Keep identities already discovered even if the launcher later exits.
            for record in list(owned):
                try:
                    parent = matching_process(record)
                    children = parent.children(recursive=True)
                    matching_process(record)
                    for child in children:
                        child_record = identity(child)
                        if child_record not in owned:
                            owned.append(child_record)
                except psutil.NoSuchProcess:
                    continue
            if guard is None:
                try:
                    connections = psutil.net_connections(kind='tcp')
                    engine_pids = listener_pids(connections, 5580) | listener_pids(connections, 5581)
                    engines = [record for record in owned if record['pid'] in engine_pids]
                    if {record['pid'] for record in engines} != engine_pids:
                        raise OwnershipError('Boot ports are not owned descendants')
                    owner = {'schema': 2, 'serial': SERIAL, 'avd': AVD,
                        'created_at': started, 'expires_at': started + 5700,
                        'supervisor': identity(psutil.Process(os.getpid())),
                        'adb_server': server, 'processes': engines,
                        'transport_id': transport_id(ADB, SERIAL)}
                    candidate = OwnedEmulator(owner, SERIAL, ADB, AVD)
                    candidate.validate()
                    guard = candidate
                except (OwnershipError, psutil.Error, subprocess.SubprocessError) as error:
                    (out / 'boot-pending.txt').write_text(str(error))
                    time.sleep(2)
                    continue
            if guard.text('shell', 'getprop', 'sys.boot_completed', timeout=15).strip() == '1':
                break
            time.sleep(2)
        else:
            raise RuntimeError('Emulator boot timeout')
        (out / 'emulator-owner.json').write_text(json.dumps(owner, indent=2))
        if args.smoke:
            (out / 'guarded-read.json').write_text(json.dumps({'serial': SERIAL,
                'boot_completed': guard.text('shell', 'getprop', 'sys.boot_completed').strip()}, indent=2))
            return
        guard.run('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
        guard.run('shell', 'input', 'keyevent', '82')
        (out / 'emulator-ready.txt').write_text(SERIAL)
        deadline = time.monotonic() + 5400
        while time.monotonic() < deadline:
            if (out / 'STOP').exists():
                break
            for request in sorted(queue.glob('*.json')):
                result = request.with_suffix('.result')
                if result.exists():
                    continue
                spec = json.loads(request.read_text())
                completed = guard.run(*spec['args'], timeout=spec.get('timeout', 180))
                if 'binary' in spec:
                    destination = (out / spec['binary']).resolve()
                    if not destination.is_relative_to(out.resolve()):
                        raise OwnershipError('Binary output must stay in session evidence')
                    destination.write_bytes(completed.stdout)
                    text = 'binary saved'
                else:
                    text = completed.stdout.decode(errors='replace')
                result.write_text(json.dumps({'code': completed.returncode, 'stdout': text, 'stderr': completed.stderr.decode(errors='replace')}, indent=2))
            time.sleep(0.3)
    finally:
        # Even after identity loss, never send `emu kill` to the old serial.
        # Each signal is independently creation-checked; outsiders are untouched.
        try:
            report = stop_owned_processes(owned)
            if proc is not None and not owned:
                report['unresolved'].append({'pid': proc.pid, 'error': 'Launcher identity unavailable'})
            report['owned_processes'] = owned
            report['launcher_exited'] = proc is None or proc.poll() is not None
            (out / 'emulator-cleanup.json').write_text(json.dumps(report, indent=2))
            if report['unresolved']:
                raise OwnershipError('Unresolved owned-process cleanup; see emulator-cleanup.json')
        finally:
            log.close()

if __name__ == '__main__':
    main()
