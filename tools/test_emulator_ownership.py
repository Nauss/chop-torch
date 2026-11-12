# SPDX-License-Identifier: GPL-3.0-or-later
"""Ownership regressions: real entry points, fake processes/ADB, no devices."""
import contextlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

import psutil

TOOLS = Path(__file__).resolve().parent
AVD = 'ChopTorch_P1_API37'
SERIAL = 'emulator-5580'


class MutationReached(RuntimeError):
    pass


def load_script(name, optimize):
    module = types.ModuleType(name)
    module.__file__ = str(TOOLS / (name + '.py'))
    exec(compile(Path(module.__file__).read_text(), module.__file__, 'exec', optimize=optimize), module.__dict__)
    return module


class EntryPointSafety(unittest.TestCase):
    def probe(self, name, serial=SERIAL, avd_response=AVD):
        for optimize in (0, 1):
            with self.subTest(script=name, optimize=optimize), tempfile.TemporaryDirectory() as directory:
                out = Path(directory)
                owner = {'serial': serial, 'avd': AVD, 'processes': [
                    {'pid': 12345, 'create_time': 10.0, 'name': 'qemu-system-x86_64.exe'}]}
                (out / 'emulator-owner.json').write_text(json.dumps(owner))
                mutations = []

                def run(command, **kwargs):
                    words = command[3:]
                    if command[1:] == ['devices', '-l']:
                        return subprocess.CompletedProcess(command, 0, f'{serial} device transport_id:7\n'.encode(), b'')
                    if words == ['shell', 'getprop', 'ro.hardware']:
                        data = b'ranchu\n'
                    elif words == ['emu', 'avd', 'name']:
                        data = (avd_response + '\nOK\n').encode()
                    else:
                        mutations.append(command)
                        raise MutationReached(str(command))
                    return subprocess.CompletedProcess(command, 0, data, b'')

                argv = [name, '--evidence', directory, '--serial', serial] if name.endswith('_ui') else [name, directory, '--serial', serial]
                with contextlib.ExitStack() as stack:
                    stack.enter_context(patch.dict(os.environ, {'ANDROID_HOME': directory}))
                    stack.enter_context(patch.object(sys, 'argv', argv))
                    stack.enter_context(patch('subprocess.run', side_effect=run))
                    stack.enter_context(patch('subprocess.check_output', side_effect=lambda command, **kw: run(command, **kw).stdout))
                    stack.enter_context(patch('subprocess.Popen', side_effect=AssertionError('Unexpected process launch')))
                    stack.enter_context(patch('psutil.Process', side_effect=psutil.NoSuchProcess(12345)))
                    module = load_script(name, optimize)
                    # Restore shared verify_native_ui globals after each invocation.
                    if name.endswith('_back'):
                        stack.enter_context(patch.dict(module.ui.__dict__, module.ui.__dict__.copy()))
                    try:
                        module.main()
                    except (RuntimeError, AssertionError, ValueError, psutil.Error):
                        pass
                self.assertEqual([], mutations, 'Refusal must occur BEFORE any mutating command')

    def test_matrix_physical_target_never_mutates(self):
        self.probe('verify_affiche_ui', serial='PHYSICAL-TEST-SERIAL')

    def test_back_physical_target_never_mutates(self):
        self.probe('verify_affiche_back', serial='PHYSICAL-TEST-SERIAL')

    def test_matrix_stale_manifest_never_mutates(self):
        self.probe('verify_affiche_ui')

    def test_back_stale_manifest_never_mutates(self):
        self.probe('verify_affiche_back')

    def test_matrix_avd_substring_never_mutates(self):
        self.probe('verify_affiche_ui', avd_response=AVD + '_outsider')

    def test_back_avd_substring_never_mutates(self):
        self.probe('verify_affiche_back', avd_response=AVD + '_outsider')

    def test_session_failed_boot_never_kills_reused_serial(self):
        for optimize in (0, 1):
            with self.subTest(optimize=optimize), tempfile.TemporaryDirectory() as directory:
                mutations = []
                launcher = types.SimpleNamespace(pid=12345, poll=lambda: 1)

                def run(command, **kwargs):
                    if command[1:] in (['devices'], ['devices', '-l']):
                        return subprocess.CompletedProcess(command, 0, b'List of devices attached\n', b'')
                    if command[-2:] == ['emu', 'kill']:
                        mutations.append(command)
                    return subprocess.CompletedProcess(command, 0, b'', b'')

                with contextlib.ExitStack() as stack:
                    stack.enter_context(patch.object(sys, 'argv', ['session', '--evidence', str(Path(directory) / 'new')]))
                    stack.enter_context(patch('subprocess.Popen', return_value=launcher))
                    stack.enter_context(patch('subprocess.run', side_effect=run))
                    stack.enter_context(patch('subprocess.check_output', side_effect=lambda command, **kw: run(command, **kw).stdout))
                    stack.enter_context(patch('psutil.process_iter', return_value=[]))
                    stack.enter_context(patch('psutil.Process', side_effect=psutil.NoSuchProcess(12345)))
                    stack.enter_context(patch('psutil.net_connections', return_value=[]))
                    stack.enter_context(patch('socket.socket'))
                    stack.enter_context(patch('time.sleep'))
                    stack.enter_context(patch('time.monotonic', side_effect=[0, 1000, 1000, 1000]))
                    module = load_script('ui_emulator_session', optimize)
                    module.OUT = Path(directory)
                    try:
                        module.main()
                    except (RuntimeError, ValueError, psutil.Error):
                        pass
                self.assertEqual([], mutations, 'Failed boot cleanup must not address a replacement serial')


if __name__ == '__main__':
    unittest.main()
