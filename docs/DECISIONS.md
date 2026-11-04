# Décisions techniques et références primaires

Consultation pendant le lot A ; les builds réellement exécutés priment sur les exemples de versions des pages.

## Outils / SDK

- [Compatibilité minimale par API](https://developer.android.com/build/releases/about-agp) : API 37.0 nécessite au moins AGP 9.1.1.
- [AGP 9.3 et correctifs 9.3.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes) : API maximale 37, Gradle minimum 9.5.0, JDK minimum 17. Ce lot exécute JDK 21, SDK 37.0 révision 2 et Build Tools 37.0.0.
- [Kotlin intégré](https://developer.android.com/build/migrate-to-built-in-kotlin) : pas de second plugin kotlin-android ; résolution effective Kotlin compiler/plugin et stdlib **2.2.10** dans le graphe AGP 9.3.3 utilisé. Ne pas confondre ce Kotlin applicatif avec le Kotlin embarqué de Gradle pour ses scripts.
- [Compatibilité Gradle 9.5](https://docs.gradle.org/9.5.0/userguide/compatibility.html), [vérification du wrapper](https://docs.gradle.org/9.5.0/userguide/gradle_wrapper.html#sec:verification) et [checksums officiels](https://gradle.org/release-checksums/).
- Premier bootstrap avec AGP 9.1.1 / Gradle 9.3.1 : test détecteur RED puis GREEN réellement exécuté. D8 émettait « API level 37 is not supported » : remplacement par AGP 9.3.3 / Gradle 9.5.0, build propre sans cet avertissement. **Aucun abaissement** de min/target/compile SDK.

## Service et énergie

[Types foreground officiels](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use) : `specialUse` couvre les usages valides non couverts par les autres types ; permission `FOREGROUND_SERVICE_SPECIAL_USE`, aucune condition runtime spécifique et propriété de sous-type à justifier dans le manifeste. Usage ici : écoute de gestes accéléromètre hors ligne explicitement activée pour la torche écran éteint, pas capture vidéo/audio, donc ni type caméra ni lecture média. Ce choix documenté n’est ni une approbation Play ni une recette appareil.

[Capteurs](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview), [suspension et wake-up](https://source.android.com/docs/core/interaction/sensors/suspend-mode) : un service vivant ne suffit pas à recevoir les événements d’un capteur non-wake-up en suspension. On préfère l’accéléromètre wake-up s’il existe, période demandée 20000 µs / latence de lot 0 ; fallback explicitement non garanti, sans wake lock. Le coût de ces réveils reste **à mesurer**, pas assimilé à de la basse consommation acquise.

[Doze](https://developer.android.com/training/monitoring-device-state/doze-standby) : ouvrir les réglages publics à la demande plutôt que réclamer directement une exemption ou une permission additionnelle. État lu avec `isIgnoringBatteryOptimizations`.

[Arrêt utilisateur](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping) et [restrictions de démarrage](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) : démarrer depuis l’activité visible, arrêt volontaire respecté. `START_NOT_STICKY` dans ce lot ; boot/reprise à traiter et tester séparément, pas de relance cachée.

## Torche et CAMERA

[CameraManager public](https://developer.android.com/reference/android/hardware/camera2/CameraManager#setTorchMode(java.lang.String,%20boolean)) : `setTorchMode` sans ouvrir une caméra ; état observé par `TorchCallback`, disponibilité partagée avec d’autres applications. Ne jamais annoncer une commutation réussie sur le seul retour de la commande.

Vérification complémentaire du chemin [AOSP CameraService::setTorchMode](https://android.googlesource.com/platform/frameworks/av/+/refs/heads/main/services/camera/libcameraservice/CameraService.cpp) (source consultée pendant le lot) : vérifications caméra publique/système, identifiant, flash, ressources, mais pas d’exigence CAMERA pour ce chemin public normal. Il n’est donc **pas demandé CAMERA**, ni `FOREGROUND_SERVICE_CAMERA`. Absence de flash et exceptions CameraAccess/Security/IllegalArgument interceptées. Si un système spécifique refuse, signaler ce refus, ne pas demander au hasard des permissions. L’absence de permission caméra à révoquer est explicite ; les scénarios de révocation applicables sont la notification et les restrictions/arrêts système. La recette exacte de ce comportement sur le Pixel reste en attente.

## Capture / vie privée / licence

[Créer un document public par intention utilisateur](https://developer.android.com/training/data-storage/shared/documents-files#create-file) : `ACTION_CREATE_DOCUMENT`, pas de permission de stockage ou MANAGE_EXTERNAL_STORAGE. Un seul tampon mémoire 15 s / 1000 mesures, journal 64 événements, effacement volontaire, aucune capture automatique après reprise.

[GNU GPL v3 — texte officiel](https://www.gnu.org/licenses/gpl-3.0.txt) téléchargé intégralement dans LICENSE ; choix **GPL-3.0-or-later** explicitement appliqué par README. Wrapper généré par la distribution officielle Gradle avec son en-tête propre.

## P2 — reprise et paquet public (remplace la politique manuelle du lot A/P1)

Sources Android relues pendant P2 : [restrictions de démarrage FGS](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [specialUse](https://developer.android.com/develop/background-work/services/fgs/service-types#special-use), [restrictions boot depuis Android15](https://developer.android.com/about/versions/15/behavior-changes-15#boot-completed-fgs), [START_STICKY](https://developer.android.com/reference/android/app/Service#START_STICKY), [arrêt utilisateur](https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping), [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo), [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby).

- `specialUse` reste le type réel, sans prérequis while-in-use caméra/micro/localisation. Boot et MY_PACKAGE_REPLACED sont des exceptions de démarrage arrière-plan ; les interdictions boot d’autres types ne justifient aucun déguisement. Les exceptions légales ne garantissent pas que chaque constructeur lancera le service : échec intercepté, consentement désarmé, réactivation manuelle, sans retry.
- Intention `enabled` persistée par commit synchrone avant départ/arrêt ; état observé process-local séparé. `START_STICKY` permet au système de recréer par null intent, sans garantie de délai. Ne pas utiliser START_REDELIVER_INTENT : une commande d’arrêt ou de réglage n’a pas à être rejouée.
- Receiver non exporté, actions protégées BOOT_COMPLETED et MY_PACKAGE_REPLACED uniquement ; seule nouvelle permission RECEIVE_BOOT_COMPLETED. Pas de LOCKED_BOOT_COMPLETED/DirectBoot : préférences credential-protected, reprise après premier déverrouillage. Aucun job, alarme ou wakelock de maintien.
- Refus notifications global ou canal gesture bloqué, app restreinte, flash/capteur manquant : refus/arrêt, pas de boucle. L’historique ApplicationExitInfo de cette app est comparé à la date locale de consentement : un REASON_USER_REQUESTED récent désarme la reprise après Stop système/force-stop. Il ne constitue pas un diagnostic exportable. Le force-stop empêche aussi la livraison système jusqu’à déblocage Android. Une nouvelle activation explicite remplace cette intention.
- Notification basse importance silencieuse, arrêt écrit avant libération. Commandes et callbacks capteur sérialisés ; drapeau stopping visible, publication torche de l’ancien contrôleur ignorée après arrêt. Aucun changement du détecteur/filtre/SampleFreshness, confirmé sur sources et classes internes compilées.
- Fiche app publique comme lien batterie ciblé sans nouvelle permission d’exemption. Aucun changement automatique ni exemption adb sur téléphone. Un service sticky vivant ne résout pas la suspension d’un capteur non-wake-up : veille, coût et72h restent non mesurés.
- `release` public `fr.nauss.choptorch`, non-debug et unsigned sans coffre ; seul `debug` ajoute `.diagnostic`, conservant la signature de preview. Aucun accès aux préférences d’un autre package. Version0.6-beta.1/code6/tag prévu v0.6-beta.1 cohérents ; pas de publication P2.

Métadonnées selon [format Fastlane F-Droid](https://f-droid.org/en/docs/All_About_Descriptions_Graphics_and_Screenshots/), FR/EN, captures Android natives sans traces personnelles. Outillage de signature séparé : [SIGNING.md](SIGNING.md). Ces préparatifs ne valent pas acceptation F-Droid ni reproductibilité intermachines démontrée.
