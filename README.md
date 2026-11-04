# ChopTorch — torche par double chop

Application native Kotlin + Android Views pour Android 17 (API **37**). Deux mouvements de hachage consécutifs allument ou éteignent la torche. Aucun lien avec Motorola, aucune reprise d’algorithme propriétaire.

## État au 4 novembre 2026 — préparation P2 / bêta initiale

**0.6-beta.1 / code6**, futur tag unique `v0.6-beta.1`. Paquet public **fr.nauss.choptorch**, release non debuggable, sans diagnostics et sans signature locale requise au build. Preview debug distincte **fr.nauss.choptorch.diagnostic**. Le public s’installe à côté et démarre désactivé : **arrêter l’ancien détecteur avant d’activer le nouveau**, sans désinstallation ni migration silencieuse des préférences privées. Les étapes suivantes décrivant P1/anciens lots sont historiques.

P2 ajoute consentement persistant séparé de l’état observé, reprise système sticky et receiver privé boot/update après premier déverrouillage, dans les limites Android. Stops, force-stop et restrictions respectés. Tests Android réels sur AVD jetables, pas une recette physique de veille/autonomie. Voir [rapport P2](docs/FINISH_P2.md) et [signature sûre](docs/SIGNING.md).

Le propriétaire a installé et essayé la **0.4**, puis accepté le geste : « C’est plutôt bien ! […] on est bon ». Cette acceptation ne mesure ni la réception en veille profonde, ni l’autonomie, ni l’endurance. Le moteur validé reste inchangé dans ce lot.

La **0.5-ui-preview / code 5** apporte un seul écran natif FR/EN, les thèmes clair et graphite suivant le système, un interrupteur de détection, l’état observé du service et de la torche, le curseur 0–100 et une aide progressive. Capture, export, inventaire, journal, compteurs et commande de test torche sont **retirés de l’APK**, pas seulement masqués. Les tests et outils de replay restent hors APK. Voir [DESIGN.md](DESIGN.md) et [rapport de finition](docs/FINISH_P1.md).

La preview P1 historique n’est pas la version publique finale. **Signature pérenne, release GitHub et dépôt F-Droid personnel restent réservés à P3**, après revue indépendante du commit exact et intégration vérifiée. Aucune acceptation au catalogue officiel F-Droid n’est annoncée. Métadonnées FR/EN dans `fastlane/metadata/android`.

## Historique diagnostic 0.4 — lot B3, avant le retour utilisateur

Le rythme normal fonctionne mieux sur0.3 mais l'utilisateur rapporte des échecs
rapides après répétitions. Une nouvelle capture d'un seul essai rapide non déclenché
a permis d'isoler une direction faussée au départ du coup :0.4 conserve la direction
initiale et ajoute un témoin directionnel soutenu, sans changer les gardes temporelles,
l'amplitude ou le cooldown. Le cas réel est récupéré **hors ligne**, avec références
normale et rapide préservées au même réglage. À la rédaction de ce rapport historique,
l’installation et la validation physique de 0.4 n’avaient pas encore eu lieu ; elles ont depuis été suivies du retour positif ci-dessus.
Voir [rapport B3](docs/VALIDATION_LOT_B3.md) et [protocole B3](docs/TEST_B3.md).
Veille, énergie, reprise et F-Droid restent séparés.

## Historique diagnostic 0.3 — lot B2, avant la finition du 4 novembre 2026

Le test physique0.2 était **négatif à sensibilité100**. La0.3 corrige une régression
du curseur démontrée, sépare segmentation fixe et preuve d'amplitude réglable et
étend la plage faible testée. La référence confortable réelle reçue a été rejouée
avant gel ; cela reste un ajustement hors ligne, **pas une validation physique de
l'APK0.3**. Voir [rapport B2](docs/VALIDATION_LOT_B2.md) et
[protocole confortable B2](docs/TEST_B2.md), qui prévaut sur le protocole historique.
Ne pas secouer plus fort. Veille, batterie, reprise et F-Droid restent non validés.

## Historique diagnostic 0.2 — lot B1, avant la finition du 4 novembre 2026

Le premier essai réel sur **Pixel 9a** a confirmé le bouton torche dans les deux sens,
mais aucune reconnaissance. Le candidat 0.2 corrige les transitions continues et le retour
entre coups ; tests synthétiques, replay privé non annoté et limites sont décrits dans
[VALIDATION_LOT_B1.md](docs/VALIDATION_LOT_B1.md). Ce replay n'est ni un taux de réussite,
ni une validation physique du nouvel APK. La veille, l'autonomie et les faux positifs
quotidiens restent non validés. Le capteur réel est non-wake-up.

## État logiciel historique du lot A — avant la finition du 4 novembre 2026

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
./gradlew --console=plain clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
# Windows sans bash : gradlew.bat avec les mêmes arguments
```

APK preview : `app/build/outputs/apk/debug/app-debug.apk`. Release publique **non signée** : `app/build/outputs/apk/release/app-release-unsigned.apk`. `assembleRelease` fonctionne sans coffre, keyring, keystore ni configuration de signature privée, pour F-Droid source. Aucun APK unsigned/debug ne doit être diffusé comme public. Le keystore debug reste hors dépôt. **Ne commitez aucune clé.** Wrapper JAR et distribution contrôlés contre les checksums officiels. Lint traite les avertissements en erreurs, sauf suggestion de mise à jour AGP à revoir explicitement. Aucune date aléatoire injectée en ressources ; égalité de builds locaux n’est pas preuve de reproductibilité intermachines/F-Droid.

Tests outils : `python -m unittest discover -s tools -p 'test_*.py'`. Après publication seulement, installer l’APK public signé depuis les [releases](https://github.com/Nauss/chop-torch/releases) et vérifier hash/certificat annoncés. Le lien et l’empreinte du dépôt F-Droid personnel seront communiqués après déploiement vérifié, pas inventés ici.

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

Le geste 0.4 est accepté ; aucune nouvelle capture n’est demandée pour P1. Les [protocoles B3](docs/TEST_B3.md) sont historiques, pas un parcours de l’interface actuelle. Activation depuis l’écran visible avec consentement explicite. La seule permission d’exécution demandée est la **notification**, nécessaire pour garder le service visible. Aucune permission `CAMERA` n’est déclarée : le contrôleur utilise seulement les API torche, sans flux caméra. Un refus système reste affiché, pas contourné. Voir [décisions et sources officielles](docs/DECISIONS.md).

L’aide affiche l’état observé de l’exemption batterie et ouvre volontairement la fiche de cette application. Aucune exemption n’est imposée, aucune économie globale désactivée, aucun export proposé.

**P2 reprend uniquement une intention activée valide** : `START_STICKY` laisse Android recréer le service après mort du processus ; un receiver privé accepte boot/remplacement du paquet. Boot après premier déverrouillage, sans Direct Boot ni délai garanti. Désactivation, bouton Arrêter, arrêt via Applications actives et force-stop sont respectés. Notifications refusées/canal bloqué, restrictions d’arrière-plan et matériel manquant empêchent le démarrage ; nouvelle activation requise. Aucun polling, alarme, job de maintien ni wake lock permanent. Retirer l’activité des récents ne demande pas l’arrêt (`stopWithTask=false`). État actif jamais déduit de la préférence. Arrêter libère capteur/callback et demande l’extinction si l’app a allumé la torche ; confirmation impossible si la ressource est retirée. Android arrête aussi la torche à la mort du client.

Le détecteur utilise un filtre gravité unique, trois échelles de segmentation fixes,
une chauffe500 ms, deux coups accélération/freinage soutenus au moins40 ms chacun,
limités à240 ms par phase/440 ms par coup et relâchement axial sans pause artificielle.
Le curseur valide l'amplitude, sans déplacer les horloges des candidats. Il conserve
une direction cohérente d'armement ou de soutien intégré (sans croiser les témoins),
une fenêtre départ-à-départ150–600 ms, une remise à zéro sur trou
>120 ms / timestamp non croissant et un cooldown 900 ms. Les événements vieux de plus de
250 ms ne commandent pas la torche (évite une commutation tardive après livraison FIFO au
réveil). Ce moteur accepté par le propriétaire est gelé pour la finition ; aucune
modification de seuil n’appartient à P1. Les tests ne mesurent pas les faux positifs dans le monde réel.

## Confidentialité

Les événements sont traités en mémoire, jamais enregistrés ni exportés. Aucun tampon de capture, inventaire exportable, journal ou stockage partagé. Seules sensibilité, intention d’activation et date locale de son changement sont conservées, sans sauvegarde Android. L’historique système des sorties de ce processus est consulté localement pour respecter un arrêt utilisateur, jamais exporté. Aucun réseau, analytics, publicité, compte, son ou haptique. Le lien source ouvre le navigateur externe sur action utilisateur ; `INTERNET` absent. Anciennes captures privées locales, ignorées Git, immuables, réservées aux outils hors APK.

## Suite du produit (non livrée dans ce lot)

P2 prépare lifecycle et paquet public ; P3 réalisera signature pérenne, GitHub release, canal F-Droid personnel et candidature officielle distincte. Premier geste après veille profonde, comparaison énergie24 h et endurance72 h restent à mesurer. Capteur Pixel non-wake-up : réception fiable en suspension non assurée. Ni très faible consommation, ni fonctionnement permanent, ni acceptation F-Droid promis. Les tests AVD (processus, reboot, update, permissions/arrêts et matériel absent) ne remplacent pas ces mesures ; aucun réglage/redémarrage du téléphone personnel n’est automatisé.

## Licence

Copyright (C) 2026 Nauss et contributeurs. **GPL-3.0-or-later** : redistribution/modification sous GNU GPL version 3 ou ultérieure, sans garantie. Texte officiel complet : [LICENSE](LICENSE). Le wrapper Gradle provient de Gradle et porte ses propres en-têtes Apache-2.0 ; Kotlin et JUnit conservent leurs licences respectives. Aucun code ni marque Motorola incorporé.

## English summary

Native Kotlin / Android Views flashlight for **API37 only**. Accepted 0.4 detector unchanged. Native FR/EN UI, system light/dark themes, observed states, sensitivity/help. No capture/export/test commands, network, audio, haptics, ads, analytics or Play Services. **0.6-beta.1/code6** prepares initial public `fr.nauss.choptorch`, non-debug release; `.diagnostic` debug remains separate. They install side by side without preference migration: stop the older detector first; fresh public installs off. Saved consent permits Android sticky process recovery and private boot/update recovery after first unlock, within system limits. Stops, Task Manager Stop, force-stop, notification/channel denial and background restrictions respected. No retry loops/keepalive jobs/permanent wake locks. Service alive is not sensor delivery during suspend. Sleep reliability, energy and 72-hour endurance unmeasured. Unsigned release builds need no credentials; signing and GitHub/F-Droid publication await independent exact-commit review/landing in P3, no official acceptance claimed. GPL-3.0-or-later; no Motorola affiliation.
