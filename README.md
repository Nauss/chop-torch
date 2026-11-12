# ChopTorch

Toggle your flashlight with a double chop.

A native app for **Android 17 (API 37) only**, with adjustable sensitivity,
proximity protection, haptic feedback and an English/French interface.

## Status and downloads

This source tree corresponds to **0.13-beta.1 (version code 16)**. Its Google Play
release is being prepared; it is not yet publicly available on Google Play.

- [Earlier public APK: 0.6-beta.1](https://github.com/Nauss/chop-torch/releases/tag/v0.6-beta.1) — the older recognizer and interface, with signed APK and `SHA256SUMS`.
- [Personal F-Droid repository](https://nauss.github.io/chop-torch/) — the earlier public beta, add-repository link and verification fingerprint. **Not the official F-Droid catalogue.**

Verify the APK checksum or repository fingerprint before installing. Allow
installation from your chosen source only if Android requires it.

## Use

1. Open ChopTorch. Detection is **off** on first launch.
2. Enable **Detect gestures** and grant notification permission when prompted.
3. Hold the phone securely and make two quick chopping motions, mainly in the plane of the screen, to turn the light on or off.
4. Adjust **Sensitivity** if needed; higher values detect lighter movements. Stop detection in the app or its notification.

Detection requires compatible linear-acceleration and proximity sensors. Keep the
top of the phone uncovered; a hand or cover pauses detection. Motions mainly toward
or away from the screen are rejected. There is no personal calibration or learned
user profile. Saved sensitivity numbers are retained; this is an engineering scale,
not a measurement of human effort.

Background gestures depend on your device's battery management and sensor behaviour
during deep sleep; continuous availability is not guaranteed.

No measured battery overhead, deep-sleep reliability or population-wide false-trigger
rate is claimed. Detection does not use a permanent CPU wake lock.

## Privacy

Offline, with no ads, tracking or accounts. Motion data is processed in memory;
settings stay on your device. No Internet or camera permission is required.
See the [privacy policy](https://nauss.github.io/chop-torch/privacy.html).

## Build

See [build and signing](docs/SIGNING.md). Release builds are unsigned until a local
signing step; no keystore or signing password belongs in this repository.

## License

[GPL-3.0-or-later](LICENSE), without warranty.
