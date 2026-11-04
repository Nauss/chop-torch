#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise real signing tools with a DISPOSABLE test key, never a release key.
Only password source is substituted in memory; keytool/zipalign/apksigner are real.
Also probes a new unique Windows vault entry and deletes it. No existing credentials read.
"""
import hashlib
import os
from pathlib import Path
import secrets
import tempfile
from unittest.mock import patch
import sign_release as signing

class MemoryVault:
    def __init__(self): self.entries={}
    def get_password(self,service,account): return self.entries.get((service,account))
    def set_password(self,service,account,password): self.entries[service,account]=password
    def delete_password(self,service,account): del self.entries[service,account]

def main():
    vault=signing.vault()
    account='probe-'+secrets.token_hex(12); password=secrets.token_urlsafe(32)
    vault.set_password('ChopTorch.signing-self-test',account,password)
    try: assert vault.get_password('ChopTorch.signing-self-test',account)==password
    finally: vault.delete_password('ChopTorch.signing-self-test',account)
    assert vault.get_password('ChopTorch.signing-self-test',account) is None
    print('PASS Windows Credential Manager: unique probe stored/read/deleted; no existing account inspected')
    keytool=Path(os.environ['JAVA_HOME'])/'bin/keytool.exe'
    with tempfile.TemporaryDirectory(prefix='choptorch-disposable-sign-',dir=os.environ['TMPDIR']) as temp:
        folder=Path(temp); key=folder/'disposable.p12'; output=folder/'disposable-signed.apk'
        memory=MemoryVault()
        with patch.object(signing,'vault',return_value=memory):
            signing.initialize(key,'apk',keytool)
            before=hashlib.sha256(key.read_bytes()).hexdigest()
            try: signing.initialize(key,'apk',keytool)
            except FileExistsError: pass
            else: raise AssertionError('Overwrote key')
            assert hashlib.sha256(key.read_bytes()).hexdigest()==before
            env=dict(os.environ,**{signing.PASSWORD_ENV:memory.get_password(signing.SERVICE,signing.ALIASES['apk'])})
            der=signing.run([keytool,'-exportcert','-keystore',key,'-alias',signing.ALIASES['apk'],'-storepass:env',signing.PASSWORD_ENV],env)
            source=signing.ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk'
            signing.sign(key,source,output,hashlib.sha256(der).hexdigest(),os.environ['ANDROID_HOME'],keytool)
            try: signing.sign(key,source,output,hashlib.sha256(der).hexdigest(),os.environ['ANDROID_HOME'],keytool)
            except FileExistsError: pass
            else: raise AssertionError('Overwrote signed APK')
            try: signing.sign(key,source,folder/'bad.apk','0'*64,os.environ['ANDROID_HOME'],keytool)
            except ValueError: pass
            else: raise AssertionError('Ignored certificate pin')
    assert not folder.exists()
    print('PASS actual keytool + zipalign + apksigner verify, key/output overwrite refusal, certificate pin; disposable key/APK deleted. NOT a public signing operation.')

if __name__=='__main__': main()
