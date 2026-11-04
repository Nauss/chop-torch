# Changelog

## 0.6-beta.1 — initial public beta (versionCode 6)

FR : paquet public `fr.nauss.choptorch`, distinct des anciennes previews diagnostic. Interface native FR/EN, thèmes système, activation volontaire, sensibilité, état observé et aide. Consentement persistant ; reprise système après mort de processus, boot après premier déverrouillage et remplacement du paquet dans les limites Android. Arrêts et refus de permissions respectés. Aucun diagnostic, export ou commande de test dans l’APK. Moteur gestuel 0.4 accepté inchangé.

EN: initial public package, separate from diagnostic previews. Native FR/EN UI, system themes, explicit activation, sensitivity, observed state and help. Saved consent with system process/boot/package-update recovery within Android limits; stops and denials respected. No capture, export or test commands in the APK. Accepted 0.4 gesture engine unchanged.

**Non mesuré / not measured:** deep-sleep first gesture, battery cost24h and endurance72h. **Non démontré / not established:** cross-machine reproducibility, update between two public versions, official F-Droid catalogue acceptance. Personal signed F-Droid repository and GitHub prerelease are distinct from the official catalogue.

Exact source: `75319a44ab4f268239ab7dd3c8170a49e1f70c37`, tag `v0.6-beta.1`.

Earlier implementation reports: [P1](docs/FINISH_P1.md), [P2](docs/FINISH_P2.md), [gesture0.4](docs/VALIDATION_LOT_B3.md). These reports are historical, not current distribution status.
