# SPDX-License-Identifier: GPL-3.0-or-later
import importlib.util
from pathlib import Path
import tempfile
import unittest

class SigningSafetyTest(unittest.TestCase):
    def test_public_tree_is_never_a_keystore_location(self):
        spec=importlib.util.spec_from_file_location('sign_release',Path(__file__).with_name('sign_release.py'))
        module=importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for folder in [module.ROOT, module.ROOT/'.ops',module.ROOT/'artifacts',module.ROOT/'.worktrees']:
            with self.assertRaises(ValueError): module.validate_keystore(folder/'key.p12')
    def test_password_arguments_are_indirections(self):
        spec=importlib.util.spec_from_file_location('sign_release',Path(__file__).with_name('sign_release.py'))
        module=importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
        command=module.keytool_command(Path('keytool'),Path('private/key.p12'),'apk')
        self.assertIn('-storepass:env',command)
        self.assertNotIn('-storepass',command)
        self.assertEqual('choptorch-apk',command[command.index('-alias')+1])
        self.assertNotEqual(module.ALIASES['apk'],module.ALIASES['index'])

if __name__=='__main__': unittest.main()
