# SPDX-License-Identifier: GPL-3.0-or-later
"""Portable fake adapter tests; no SDK, process signals or device connections."""
import contextlib
import copy
import json
import os
from pathlib import Path
import subprocess
import tempfile
import types
import unittest
from unittest.mock import patch

import psutil

from test_emulator_ownership import AVD, SERIAL, load_script


class FakeProcess:
    def __init__(self, pid, created, command=None):
        self.pid, self.created = pid, created
        self.command = command or ['qemu-system-x86_64', '-avd', AVD, '-port', '5580']
        self.alive = True
        self.denied = False
        self.stopped = False
        self.on_terminate = None

    def create_time(self):
        if self.denied:
            raise psutil.AccessDenied(self.pid)
        if not self.alive:
            raise psutil.NoSuchProcess(self.pid)
        return self.created

    def is_running(self):
        return self.alive

    def cmdline(self):
        self.create_time()
        return self.command

    def terminate(self):
        if self.on_terminate:
            self.on_terminate()
        self.create_time()
        self.stopped, self.alive = True, False

    def wait(self, timeout):
        if self.alive:
            raise psutil.TimeoutExpired(timeout, self.pid)
        return 0


def connection(port, pid):
    return types.SimpleNamespace(status=psutil.CONN_LISTEN, laddr=types.SimpleNamespace(ip='127.0.0.1', port=port), pid=pid)


class FakeHost:
    def __init__(self):
        self.processes = {10: FakeProcess(10, 100), 20: FakeProcess(20, 101), 30: FakeProcess(30, 90, ['adb', 'server'])}
        self.ports = [connection(5580, 20), connection(5581, 20), connection(5037, 30)]
        self.hardware = 'ranchu'
        self.avd = AVD
        self.transport = '7'
        self.calls, self.mutations = [], []
        self.after_read = None
        self.owner = {'schema': 2, 'serial': SERIAL, 'avd': AVD, 'created_at': 110,
                      'expires_at': 500, 'supervisor': {'pid': 10, 'create_time': 100},
                      'adb_server': {'pid': 30, 'create_time': 90}, 'transport_id': '7',
                      'processes': [{'pid': 20, 'create_time': 101}]}

    def process(self, pid):
        if pid not in self.processes:
            raise psutil.NoSuchProcess(pid)
        return self.processes[pid]

    def run(self, command, **kwargs):
        self.calls.append(command)
        if command[1:] == ['devices', '-l']:
            data = f'List of devices attached\n{SERIAL} device transport_id:{self.transport}\n'
        elif command[3:] == ['shell', 'getprop', 'ro.hardware']:
            data = self.hardware + '\n'
        elif command[3:] == ['emu', 'avd', 'name']:
            data = self.avd + '\nOK\n'
        elif command[3:] == ['shell', 'getprop', 'sys.boot_completed']:
            data = '1\n'
        else:
            self.mutations.append(command)
            data = ''
        if self.after_read and command[3:] == ['emu', 'avd', 'name']:
            self.after_read()
        return subprocess.CompletedProcess(command, 0, data.encode(), b'')

    @contextlib.contextmanager
    def installed(self):
        with patch.dict(os.environ, {'ADB_SERVER_SOCKET': '', 'ANDROID_ADB_SERVER_ADDRESS': '', 'ANDROID_ADB_SERVER_PORT': ''}), patch('psutil.Process', side_effect=self.process), patch('psutil.net_connections', side_effect=lambda **kw: self.ports), patch('time.time', return_value=120), patch('subprocess.run', side_effect=self.run), patch('subprocess.Popen', side_effect=AssertionError('No real launches')):
            yield


class GuardContract(unittest.TestCase):
    def setUp(self):
        import owned_emulator_guard
        self.guard_module = owned_emulator_guard
        self.host = FakeHost()
        self.stack = contextlib.ExitStack()
        self.addCleanup(self.stack.close)
        self.stack.enter_context(self.host.installed())

    def guard(self, serial=SERIAL):
        return self.guard_module.OwnedEmulator(self.host.owner, serial, 'adb', AVD)

    def refused(self):
        for words in [('shell', 'pm', 'clear', 'pkg'), ('emu', 'kill')]:
            with self.subTest(command=words), self.assertRaises(self.guard_module.OwnershipError):
                self.guard().run(*words)
        self.assertEqual([], self.host.mutations)

    def test_valid_session_dispatches_on_pinned_transport(self):
        self.guard().run('shell', 'pm', 'clear', 'pkg')
        self.assertEqual([['adb', '-t', '7', 'shell', 'pm', 'clear', 'pkg']], self.host.mutations)

    def test_console_identity_read_has_explicit_serial(self):
        self.guard().run('emu', 'avd', 'name')
        console_calls = [command for command in self.host.calls if command[3:4] == ['emu']]
        self.assertTrue(console_calls)
        self.assertTrue(all(command[1:3] == ['-s', SERIAL] for command in console_calls))

    def test_windows_console_blank_line_framing(self):
        self.host.avd = AVD + '\r\n'
        self.guard().run('shell', 'pm', 'clear', 'pkg')
        self.assertEqual(1, len(self.host.mutations))

    def test_console_extra_name_or_nonexact_whitespace_refused(self):
        for avd in (AVD + '\nOTHER', ' ' + AVD, AVD + ' '):
            with self.subTest(avd=avd):
                self.host.avd = avd
                self.refused()

    def test_physical_serial_refused(self):
        self.host.owner['serial'] = 'PHYSICAL'
        with self.assertRaises(self.guard_module.OwnershipError):
            self.guard('PHYSICAL').run('shell', 'pm', 'clear', 'pkg')
        self.assertEqual([], self.host.calls)

    def test_mismatched_serial(self):
        self.host.owner['serial'] = 'emulator-5582'
        self.refused()

    def test_exact_avd_not_substring(self):
        self.host.avd += '_outsider'
        self.refused()

    def test_wrong_manifest_avd(self):
        self.host.owner['avd'] += '_outsider'
        self.refused()

    def test_non_emulator_hardware(self):
        self.host.hardware = 'physical'
        self.refused()

    def test_missing_processes(self):
        self.host.owner['processes'] = []
        self.refused()

    def test_missing_pid(self):
        del self.host.processes[20]
        self.refused()

    def test_dead_pid(self):
        self.host.processes[20].alive = False
        self.refused()

    def test_reused_pid(self):
        self.host.processes[20].created = 102
        self.refused()

    def test_dead_supervisor(self):
        self.host.processes[10].alive = False
        self.refused()

    def test_reused_console_port(self):
        self.host.ports[0] = connection(5580, 999)
        self.refused()

    def test_reused_adb_port(self):
        self.host.ports[1] = connection(5581, 999)
        self.refused()

    def test_missing_port(self):
        self.host.ports.pop(0)
        self.refused()

    def test_unknown_port_owner(self):
        self.host.ports[0] = connection(5580, None)
        self.refused()

    def test_reused_adb_server(self):
        self.host.processes[30].created += 1
        self.refused()

    def test_reused_transport(self):
        self.host.transport = '8'
        self.refused()

    def test_expired_manifest(self):
        self.host.owner['expires_at'] = 119
        self.refused()

    def test_legacy_manifest(self):
        del self.host.owner['schema']
        self.refused()

    def test_access_denied(self):
        self.host.processes[20].denied = True
        self.refused()

    def test_port_access_denied(self):
        with patch('psutil.net_connections', side_effect=psutil.AccessDenied()):
            self.refused()

    def test_remote_adb_environment_refused(self):
        with patch.dict(os.environ, {'ADB_SERVER_SOCKET': 'tcp:outsider:5037'}):
            self.refused()
        self.assertEqual([], self.host.calls)

    def test_exact_process_avd(self):
        self.host.processes[20].command[2] += '_outsider'
        self.refused()

    def test_disappearance_after_identity_reads(self):
        self.host.after_read = lambda: setattr(self.host.processes[20], 'alive', False)
        self.refused()

    def test_revalidation_on_every_call(self):
        guard = self.guard()
        guard.run('shell', 'getprop', 'sys.boot_completed')
        self.host.ports[0] = connection(5580, 999)
        with self.assertRaises(self.guard_module.OwnershipError):
            guard.run('shell', 'pm', 'clear', 'pkg')
        self.assertEqual([], self.host.mutations)

    def test_no_global_commands_or_selector_injection(self):
        for words in [('kill-server',), ('-s', 'PHYSICAL', 'shell', 'pm', 'clear', 'pkg'), ('emu', 'kill'), ('connect', 'localhost')]:
            with self.subTest(words=words), self.assertRaises(self.guard_module.OwnershipError):
                self.guard().run(*words)
        self.assertEqual([], self.host.mutations)

    def test_native_helpers_use_guard_without_recursion(self):
        with patch.dict(os.environ, {'ANDROID_HOME': 'unused'}):
            ui = load_script('verify_native_ui', 0)
        guard = self.guard()
        with self.guard_module.bind_native_ui(ui, guard):
            ui.shell('pm', 'clear', 'pkg')
            self.host.processes[20].alive = False
            with self.assertRaises(self.guard_module.OwnershipError):
                ui.adb('exec-out', 'screencap', '-p', binary=True)
        self.assertEqual(1, len(self.host.mutations))

    def test_cleanup_stops_only_still_matching_processes(self):
        report = self.guard_module.stop_owned_processes(self.host.owner['processes'])
        self.assertTrue(self.host.processes[20].stopped)
        self.assertFalse(self.host.processes[10].stopped)
        self.assertFalse(self.host.processes[30].stopped)
        self.assertEqual([], report['unresolved'])
        self.assertEqual([], self.host.calls)

    def test_cleanup_reused_pid_not_stopped(self):
        self.host.processes[20].created += 1
        report = self.guard_module.stop_owned_processes(self.host.owner['processes'])
        self.assertFalse(self.host.processes[20].stopped)
        self.assertEqual([], self.host.calls)
        self.assertIn(20, report['gone_or_reused'])

    def test_cleanup_denied_is_unresolved(self):
        self.host.processes[20].denied = True
        report = self.guard_module.stop_owned_processes(self.host.owner['processes'])
        self.assertFalse(self.host.processes[20].stopped)
        self.assertTrue(report['unresolved'])

    def test_cleanup_disappearance_before_terminate(self):
        self.host.processes[20].on_terminate = lambda: setattr(self.host.processes[20], 'alive', False)
        report = self.guard_module.stop_owned_processes(self.host.owner['processes'])
        self.assertFalse(self.host.processes[20].stopped)
        self.assertEqual([], report['unresolved'])
        self.assertEqual([], self.host.calls)


class LiveEntryPoints(unittest.TestCase):
    def probe(self, name, valid):
        from test_emulator_ownership import MutationReached
        for optimize in (0, 1):
            with self.subTest(script=name, optimize=optimize), tempfile.TemporaryDirectory() as directory:
                host = FakeHost()
                (Path(directory) / 'emulator-owner.json').write_text(json.dumps(host.owner))
                if not valid:
                    host.processes[20].created += 1

                def run(command, **kwargs):
                    result = host.run(command, **kwargs)
                    if host.mutations:
                        raise MutationReached('Stop before simulated matrix/navigation')
                    return result

                argv = [name, '--evidence', directory, '--serial', SERIAL] if name.endswith('_ui') else [name, directory, '--serial', SERIAL]
                with host.installed(), patch.dict(os.environ, {'ANDROID_HOME': directory}), patch('sys.argv', argv), patch('subprocess.run', side_effect=run):
                    module = load_script(name, optimize)
                    with contextlib.ExitStack() as stack:
                        if name.endswith('_back'):
                            stack.enter_context(patch.dict(module.ui.__dict__, module.ui.__dict__.copy()))
                        with self.assertRaises(RuntimeError):
                            module.main()
                if valid:
                    self.assertEqual(1, len(host.mutations))
                    self.assertEqual(['-t', '7', 'shell', 'pm', 'clear'], host.mutations[0][1:6])
                else:
                    self.assertEqual([], host.mutations)

    def test_matrix_live_session_reaches_guarded_clear(self):
        self.probe('verify_affiche_ui', True)

    def test_back_live_session_reaches_guarded_clear(self):
        self.probe('verify_affiche_back', True)

    def test_matrix_replacement_current_schema_refused(self):
        self.probe('verify_affiche_ui', False)

    def test_back_replacement_current_schema_refused(self):
        self.probe('verify_affiche_back', False)


class SessionLifecycle(unittest.TestCase):
    def test_launcher_pid_reuse_before_initial_capture_is_not_adopted(self):
        host = FakeHost()
        outsider = FakeProcess(40, 105)
        host.processes[40] = outsider
        outsider.children = lambda **kw: []
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory) / 'fresh'
            with host.installed(), patch('sys.argv', ['session', '--evidence', str(out), '--smoke']), patch('socket.socket'), patch('subprocess.check_output', return_value=b'List of devices attached\n'), patch('subprocess.Popen', return_value=types.SimpleNamespace(pid=40, poll=lambda: 1)), patch('time.monotonic', side_effect=[0, 1000]):
                session = load_script('ui_emulator_session', 1)
                with self.assertRaises(RuntimeError):
                    session.main()
            self.assertFalse(outsider.stopped)
            self.assertEqual([], host.mutations)

    def session(self, scenario):
        for optimize in (0, 1):
            with self.subTest(scenario=scenario, optimize=optimize), tempfile.TemporaryDirectory() as directory:
                host = FakeHost()
                launcher = FakeProcess(40, 105, ['emulator', '-avd', AVD, '-port', '5580'])
                host.processes[40] = launcher
                launcher.children = lambda **kw: [host.processes[20]]
                host.processes[20].children = lambda **kw: []
                launched = []
                out = Path(directory) / 'fresh'
                ticks = iter([0, 1, 1000])
                popen = types.SimpleNamespace(pid=40, poll=lambda: None if launcher.alive else 0)

                def run(command, **kwargs):
                    if not launched:
                        return subprocess.CompletedProcess(command, 0, b'List of devices attached\n', b'')
                    if scenario == 'boot-timeout' and command[3:] == ['shell', 'getprop', 'sys.boot_completed']:
                        return subprocess.CompletedProcess(command, 0, b'0\n', b'')
                    if scenario == 'replacement' and command[3:] == ['shell', 'getprop', 'sys.boot_completed']:
                        # The original engine dies after its guarded boot read.
                        # The replacement's PID/serial/ports are reused before finally.
                        host.processes[20].created += 1
                    return host.run(command, **kwargs)

                with host.installed(), patch.dict(os.environ, {'ANDROID_HOME': directory}), patch('sys.argv', ['session', '--evidence', str(out), '--smoke']), patch('socket.socket'), patch('os.getpid', return_value=10), patch('subprocess.Popen', side_effect=lambda *a, **kw: (launched.append(True) or popen)), patch('subprocess.run', side_effect=run), patch('subprocess.check_output', side_effect=lambda command, **kw: run(command, **kw).stdout), patch('time.sleep'), patch('time.monotonic', side_effect=lambda: next(ticks)):
                    session = load_script('ui_emulator_session', optimize)
                    if scenario == 'valid':
                        session.main()
                    else:
                        with self.assertRaises(RuntimeError):
                            session.main()
                self.assertEqual([True], launched)
                self.assertTrue(launcher.stopped)
                self.assertFalse(host.processes[30].stopped)
                self.assertEqual([], host.mutations)
                report = json.loads((out / 'emulator-cleanup.json').read_text())
                self.assertEqual([], report['unresolved'])
                if scenario == 'replacement':
                    self.assertFalse(host.processes[20].stopped)
                    self.assertIn(20, report['gone_or_reused'])
                else:
                    self.assertTrue(host.processes[20].stopped)
                if scenario == 'valid':
                    self.assertEqual('1', json.loads((out / 'guarded-read.json').read_text())['boot_completed'])

    def test_valid_short_session_and_cleanup(self):
        self.session('valid')

    def test_boot_timeout_cleans_owned_descendants(self):
        self.session('boot-timeout')

    def test_replacement_between_read_and_finally_is_not_killed(self):
        self.session('replacement')

    def test_old_evidence_stop_and_queue_are_refused(self):
        with tempfile.TemporaryDirectory() as directory, patch('sys.argv', ['session', '--evidence', directory]), patch('subprocess.Popen') as launch:
            (Path(directory) / 'STOP').touch()
            session = load_script('ui_emulator_session', 1)
            with self.assertRaises(FileExistsError):
                session.main()
            launch.assert_not_called()


if __name__ == '__main__':
    unittest.main()
