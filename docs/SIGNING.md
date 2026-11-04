# Build and signing

## Build

Use JDK 21, Android SDK Platform 37.0 and Build Tools 37.0.0. Set `ANDROID_HOME`
and accept the SDK licences. The committed Gradle wrapper pins the build tools.

```sh
./gradlew --console=plain clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
python -m unittest discover -s tools -p 'test_*.py'
```

On Windows, use `gradlew.bat`. Release output is
`app/build/outputs/apk/release/app-release-unsigned.apk`; building requires no keys
or credentials. Debug uses the separate `.diagnostic` package, not the public app.

## Signing and updates

Use `tools/sign_release.py --help` and its subcommand help. Install the isolated
signing dependencies from `tools/signing-requirements.txt`; Gradle does not need them.
The tool uses Windows Credential Manager (service `ChopTorch.release`) with no
plaintext fallback. Keep the existing, distinct identities:

| Purpose | Private location | Key alias and vault account |
|---|---|---|
| APK updates | `%LOCALAPPDATA%/ChopTorch/signing/apk.p12` | `choptorch-apk` |
| Personal repository indexes | `%LOCALAPPDATA%/ChopTorch/signing/fdroid-index.p12` | `choptorch-fdroid-index` |

Never regenerate or overwrite a published identity. Protect the private directory
with owner-only Windows ACLs before key creation; keep it outside source trees,
artifacts and public sync folders. Back up both keystores and their vault entries
offline, encrypted, and test restoration separately without overwriting originals.
A lost APK key prevents compatible updates; a lost index key breaks repository trust.

Sign only reviewed source. Export the public certificate using `keytool -exportcert`
with a vault password passed only through subprocess environment, then pin the DER
SHA-256 explicitly with `sign --certificate-sha256`. Never put passwords in arguments,
logs, files or chat. The tool aligns before signing and verifies alignment and
certificate identity. Use official fdroidserver for index generation/signing with
the separate index key; keep its private configuration out of the deployment tree.

Use a new, increasing version code and unique tag for each real release; never
replace a published APK. Verify hashes and signatures again after downloading from
each distribution channel. A personal repository is not official F-Droid inclusion.

References: [apksigner](https://developer.android.com/tools/apksigner),
[zipalign](https://developer.android.com/tools/zipalign),
[F-Droid signing](https://f-droid.org/en/docs/Signing_Process/).
