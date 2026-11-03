# ChopTorch — diagnostic natif Android 17

**Premier APK de faisabilité, pas une application finale validée.** Kotlin + Android Views, minSdk / targetSdk / compileSdk **37**. Détection expérimentale de deux mouvements de hachage consécutifs pour commuter la torche. Aucun lien avec Motorola, aucune reprise d’algorithme propriétaire.

## Diagnostic 0.4 — candidat lot B3

Le rythme normal fonctionne mieux sur0.3 mais l'utilisateur rapporte des échecs
rapides après répétitions. Une nouvelle capture d'un seul essai rapide non déclenché
a permis d'isoler une direction faussée au départ du coup :0.4 conserve la direction
initiale et ajoute un témoin directionnel soutenu, sans changer les gardes temporelles,
l'amplitude ou le cooldown. Le cas réel est récupéré **hors ligne**, avec références
normale et rapide préservées au même réglage. **Pas encore de validation physique
ni d'installation de0.4.** Revue indépendante obligatoire avant remise.
Voir [rapport B3](docs/VALIDATION_LOT_B3.md) et [protocole B3](docs/TEST_B3.md).
Veille, énergie, reprise et F-Droid restent séparés.

## Historique diagnostic 0.3 — lot B2

Le test physique0.2 était **négatif à sensibilité100**. La0.3 corrige une régression
du curseur démontrée, sépare segmentation fixe et preuve d'amplitude réglable et
étend la plage faible testée. La référence confortable réelle reçue a été rejouée
avant gel ; cela reste un ajustement hors ligne, **pas une validation physique de
l'APK0.3**. Voir [rapport B2](docs/VALIDATION_LOT_B2.md) et
[protocole confortable B2](docs/TEST_B2.md), qui prévaut sur le protocole historique.
Ne pas secouer plus fort. Veille, batterie, reprise et F-Droid restent non validés.

## Historique diagnostic 0.2 — lot B1

Le premier essai réel sur **Pixel 9a** a confirmé le bouton torche dans les deux sens,
mais aucune reconnaissance. Le candidat 0.2 corrige les transitions continues et le retour
entre coups ; tests synthétiques, replay privé non annoté et limites sont décrits dans
[VALIDATION_LOT_B1.md](docs/VALIDATION_LOT_B1.md). Ce replay n'est ni un taux de réussite,
ni une validation physique du nouvel APK. La veille, l'autonomie et les faux positifs
quotidiens restent non validés. Le capteur réel est non-wake-up.

## État logiciel historique du lot A

- Détecteur JVM, tests **synthétiques**, build APK et lint exécutés ; voir [rapport logiciel](docs/VALIDATION_LOT_A.md).
- Service foreground `specialUse` activé volontairement ; accéléromètre wake-up préféré, à défaut variante non-wake-up signalée. Cadence demandée : 50 Hz, livraison immédiate, sans wake lock.
- Torche via `CameraManager.setTorchMode`, état reçu par `TorchCallback`, **aucun flux caméra**.
- Interface française native : activation, état observé du service, sensibilité bornée 0–100 ; zone diagnostic **provisoire** avec test torche, capture manuelle 15 s / 1000 mesures et export JSON volontaire via le sélecteur Android.
- Aucun réseau, analytics, publicité, compte, Play Services, micro, accessibilité ou administration.
- Le bilan initial du lot A ne comprenait pas d'installation ; le premier essai a depuis été exploité dans le lot B1 ci-dessus. Réception écran éteint non validée, pas de mesure d’autonomie ni d’endurance 72 h. Un capteur wake-up ne constitue pas à lui seul une recette réussie. Sans wake-up, aucune réception fiable en suspension n’est assurée.

L’identifiant diagnostic `fr.nauss.choptorch.diagnostic` est distinct du futur identifiant final réservé `fr.nauss.choptorch`. La signature est celle de développement Android, pas une clé pérenne de publication. Ne pas diffuser comme version de production ; une migration distincte sera nécessaire.

## Construire

Prérequis : JDK 21, Android SDK Platform **37.0** (révision 2 utilisée), Build Tools **37.0.0** ; `ANDROID_HOME` vers votre SDK. Accepter vous-même les licences SDK si nécessaire. Versions épinglées : **AGP 9.3.3**, **Gradle 9.5.0**, Kotlin intégré à AGP **2.2.10**, JUnit **4.13.2**. Aucune version dynamique. Les dépendances téléchargées au build ne donnent pas d’accès réseau à l’application.

```sh
./gradlew --console=plain clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# Windows sans bash : gradlew.bat avec les mêmes arguments
```

APK : `app/build/outputs/apk/debug/app-debug.apk`. Le keystore debug Android normal demeure hors dépôt, dans le profil utilisateur. **Ne commitez aucune clé.** Le checksum officiel de la distribution Gradle est fixé dans le wrapper ; son JAR a été comparé au checksum publié par Gradle. Lint traite les avertissements en erreurs, sauf la simple suggestion de mise à jour des versions (`AndroidGradlePluginVersion`) : la compatibilité de cette chaîne est documentée et ses mises à jour doivent être revues, pas appliquées automatiquement.

```sh
# Après commit, sur arbre propre : figer APK, preuves et source exactes
python tools/freeze_lot_a.py
```

Ce script historique est réservé au lot A **0.1** ; ne pas le réutiliser pour la 0.2.
Pour B1 utiliser `tools/freeze_lot_b1.py` ; pour B2 **0.3/versionCode3**, utiliser
`tools/freeze_lot_b2.py` depuis le worktree propre, avec destination durable et APK0.2
explicites (voir rapport B2). Chaque gel refuse l'écrasement. L’empreinte APK varie avec la clé debug
locale ; aucune reproductibilité binaire intermachines n’est revendiquée. `artifacts/`,
traces personnelles et `.ops/` sont exclus de Git.

## Essai et limites

Suivre [le protocole B3 en français](docs/TEST_B3.md) après revue/remise du candidat0.4. Activation uniquement depuis l’écran visible et consentement explicite. La seule permission d’exécution demandée est la **notification**, requise par ce diagnostic pour rendre le service visible. Aucune permission `CAMERA` n’est déclarée : l’API torche et le chemin AOSP consulté ne la requièrent pas pour une caméra publique normale ; un refus propre au système reste intercepté et affiché, pas contourné. Voir [décisions et sources officielles](docs/DECISIONS.md).

Le réglage d’optimisation batterie est ouvert volontairement ; son état réel est affiché et exporté. Aucune exemption n’est imposée ni économie globale désactivée.

**Lot A : réactivation manuelle** après arrêt Android, force-stop, mort du processus, redémarrage ou mise à jour ; pas de receiver boot ni de reprise automatique. Retirer l’activité des récents ne demande pas l’arrêt du service (`stopWithTask=false`), sans garantie physique encore. La préférence de sensibilité et la dernière activation sont enregistrées ; l’état « actif » n’est jamais déduit de cette préférence. La désactivation et le bouton Arrêter de la notification libèrent le capteur et demandent l’extinction de la torche si ChopTorch l’a allumée. La confirmation finale d’extinction après arrêt n’est pas garantie si la ressource est retirée ; Android arrête aussi la torche à la mort du client.

Le détecteur utilise un filtre gravité unique, trois échelles de segmentation fixes,
une chauffe500 ms, deux coups accélération/freinage soutenus au moins40 ms chacun,
limités à240 ms par phase/440 ms par coup et relâchement axial sans pause artificielle.
Le curseur valide l'amplitude, sans déplacer les horloges des candidats. Il conserve
une direction cohérente d'armement ou de soutien intégré (sans croiser les témoins),
une fenêtre départ-à-départ150–600 ms, une remise à zéro sur trou
>120 ms / timestamp non croissant et un cooldown 900 ms. Les événements vieux de plus de
250 ms ne commandent pas la torche (évite une commutation tardive après livraison FIFO au
réveil). Seuils et fenêtres **expérimentaux**, à ajuster sur traces réelles séparées de la
validation. Les tests ne mesurent pas les faux positifs dans le monde réel.

## Confidentialité

Pas de stockage brut permanent ni accès au stockage partagé. Une seule capture volontaire en mémoire, remplacée à la suivante et perdue à la mort du processus ; journal local circulaire de 64 événements au maximum. Effacement disponible. L’export contient noms/fournisseurs/caractéristiques déclarées des capteurs, données de mouvement capturées, délais, état service/torche et exemption batterie. Vérifiez-le avant partage : il peut révéler des mouvements. L’application n’exporte rien automatiquement. Le fournisseur de documents choisi par l’utilisateur peut être distant ; ce n’est pas un accès réseau de ChopTorch.

## Suite du produit (non livrée dans ce lot)

Calibration des deux mains et contre-exemples ; premier geste après veille profonde ; étude alternative basse consommation (significant-motion puis fenêtre précise, sans supposer le premier chop reçu) ; persistance/reboot/mise à jour et révocations ; comparaison énergie sur 24 h et endurance 72 h ; finition sans diagnostics ; signature pérenne, canal F-Droid personnel testé puis candidature officielle distincte. Ni la très faible consommation, ni le fonctionnement permanent, ni l’acceptation F-Droid ne sont promis par ce build.

## Licence

Copyright (C) 2026 Nauss et contributeurs. **GPL-3.0-or-later** : redistribution/modification sous GNU GPL version 3 ou ultérieure, sans garantie. Texte officiel complet : [LICENSE](LICENSE). Le wrapper Gradle provient de Gradle et porte ses propres en-têtes Apache-2.0 ; Kotlin et JUnit conservent leurs licences respectives. Aucun code ni marque Motorola incorporé.

## English summary

Native Kotlin / Android Views diagnostic for Android 17 (API 37 only). Explicitly enabled foreground accelerometer service, experimental double-chop detector, callback-observed flashlight, bounded opt-in capture and Storage Access Framework JSON export. No network, audio, ads, analytics or Play Services. Debug-only diagnostic application ID and development signature. JVM tests, lint and APK build are software checks, **not device, screen-off, battery or 72-hour validation**. Manual restart is required after process death, reboot, update or system stop in this first version. Build command and pinned tool versions are listed above. GPL-3.0-or-later; not affiliated with Motorola. F-Droid publication remains future work.
