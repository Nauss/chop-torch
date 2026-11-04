#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Local signing only. Unsigned Gradle builds never import keyring or this module.
Never prints passwords or accepts them as CLI arguments. See docs/SIGNING.md.
"""
import argparse
import hashlib
import os
from pathlib import Path
import secrets
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PROJECT = ROOT.parents[1] if ROOT.parent.name == '.worktrees' else ROOT
SERVICE = 'ChopTorch.release'
ALIASES = {'apk':'choptorch-apk', 'index':'choptorch-fdroid-index'}
PASSWORD_ENV = 'CHOPTORCH_STORE_PASSWORD'

def validate_keystore(path):
    path=Path(path).resolve()
    if path.is_relative_to(PROJECT) or any(part.lower() in ('.ops','artifacts','.git','gh-pages') for part in path.parts):
        raise ValueError('Keystore must be outside repository, worktrees, artifacts and public folders')
    if path.name.lower() == 'debug.keystore':
        raise ValueError('Android Debug keys are forbidden')
    return path

def keytool_command(keytool, path, role):
    return [str(keytool),'-genkeypair','-keystore',str(path),'-storetype','PKCS12',
        '-alias',ALIASES[role],'-keyalg','RSA','-keysize','4096','-validity','10000',
        '-dname','CN=ChopTorch','-storepass:env',PASSWORD_ENV,'-keypass:env',PASSWORD_ENV]

def vault():
    if os.name != 'nt':
        raise RuntimeError('Windows Credential Manager required; no plaintext fallback')
    from keyring.backends.Windows import WinVaultKeyring
    return WinVaultKeyring()

def run(command, env=None):
    # Tool errors are deliberately not echoed: signing subprocesses inherit a secret.
    result=subprocess.run([str(c) for c in command],env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    if result.returncode:
        raise RuntimeError('Signing tool failed: '+Path(str(command[0])).name+' (output suppressed)')
    return result.stdout

def initialize(path, role, keytool):
    path=validate_keystore(path)
    if path.exists(): raise FileExistsError('Existing keystore will never be replaced')
    if not path.parent.is_dir(): raise ValueError('Create and protect the dedicated private parent directory first')
    store=vault(); account=ALIASES[role]
    if store.get_password(SERVICE,account) is not None:
        raise RuntimeError('Vault entry exists; recover the existing key, do not replace its password')
    # Exclusive lock prevents concurrent initializations; never overwrite another key.
    lock=path.with_suffix(path.suffix+'.lock')
    lock.open('x').close()
    try:
        if path.exists(): raise FileExistsError('Existing keystore')
        password=secrets.token_urlsafe(48)
        store.set_password(SERVICE,account,password)
        env=dict(os.environ,**{PASSWORD_ENV:password})
        try:
            run(keytool_command(keytool,path,role),env)
        except Exception:
            path.unlink(missing_ok=True)
            store.delete_password(SERVICE,account)
            raise
    finally:
        lock.unlink(missing_ok=True)
    print('Created private key and Windows vault entry for '+role+'; make a protected offline backup.')

def sign(path, source, output, expected, sdk, keytool):
    path=validate_keystore(path)
    source=Path(source).resolve(); output=Path(output).resolve()
    if output.exists(): raise FileExistsError('Output exists; no replacement allowed')
    if not path.is_file() or path == source or path == output: raise ValueError('Invalid private key path')
    password=vault().get_password(SERVICE,ALIASES['apk'])
    if not password: raise RuntimeError('APK password missing in Windows vault; no fallback')
    env=dict(os.environ,**{PASSWORD_ENV:password})
    der=run([keytool,'-exportcert','-keystore',path,'-alias',ALIASES['apk'],'-storepass:env',PASSWORD_ENV],env)
    actual=hashlib.sha256(der).hexdigest()
    if actual != expected.lower().replace(':',''): raise ValueError('Certificate fingerprint mismatch')
    build=Path(sdk)/'build-tools/37.0.0'
    analyzer=Path(sdk)/'cmdline-tools/23.0/bin/apkanalyzer.bat'
    if run([analyzer,'manifest','application-id',source]).decode().strip() != 'fr.nauss.choptorch':
        raise ValueError('Only the public package can be signed')
    if run([analyzer,'manifest','debuggable',source]).decode().strip() != 'false':
        raise ValueError('Debug APK forbidden')
    signer=['java','-jar',str(build/'lib/apksigner.jar')]
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='choptorch-sign-',dir=output.parent) as temp:
        aligned=Path(temp)/'aligned.apk'; signed=Path(temp)/'signed.apk'
        run([build/'zipalign.exe','-P','16','4',source,aligned])
        run(signer+['sign','--ks',str(path),'--ks-key-alias',ALIASES['apk'],
            '--ks-pass','env:'+PASSWORD_ENV,'--key-pass','env:'+PASSWORD_ENV,'--out',str(signed),str(aligned)],env)
        verification=run(signer+['verify','--verbose','--print-certs',str(signed)]).decode()
        if actual not in verification.lower(): raise ValueError('Signed APK certificate mismatch')
        run([build/'zipalign.exe','-c','-P','16','4',signed])
        # Exclusive creation, even if another process creates output after initial check.
        with output.open('xb') as destination: destination.write(signed.read_bytes())
    print('Signed APK SHA256='+hashlib.sha256(output.read_bytes()).hexdigest())
    print('Certificate SHA256='+actual)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    sub=parser.add_subparsers(dest='command',required=True)
    init=sub.add_parser('init'); init.add_argument('--role',choices=ALIASES,required=True)
    init.add_argument('--keystore',type=Path,required=True); init.add_argument('--keytool',type=Path,required=True)
    apk=sub.add_parser('sign'); apk.add_argument('--keystore',type=Path,required=True)
    apk.add_argument('--input',type=Path,required=True); apk.add_argument('--output',type=Path,required=True)
    apk.add_argument('--certificate-sha256',required=True); apk.add_argument('--sdk',type=Path,required=True)
    apk.add_argument('--keytool',type=Path,required=True)
    args=parser.parse_args()
    if args.command=='init': initialize(args.keystore,args.role,args.keytool)
    else: sign(args.keystore,args.input,args.output,args.certificate_sha256,args.sdk,args.keytool)

if __name__=='__main__': main()
