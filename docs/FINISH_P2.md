# Finition P2 — reprise logicielle et préparation publique

## Périmètre et garde

P1 approuvé par Roger sur **41234531e69a28bb50d21db48d4b40ae317c990f**, intégré exclusivement en fast-forward dans la racine propre et poussé sur main. `git ls-remote` et API GitHub ont confirmé ce SHA avant les edits P2. Le worktree P2 part de cette base.

**Candidat à revue, pas auto-approuvé.** Aucun tag/release, dépôt Pages/F-Droid ou clé de publication créé en P2. P3 attend le verdict indépendant exact et le landing main/remote. La carte globale reste ouverte pour ses objectifs matériels et distribution.

Version **0.6-beta.1**, versionCode **6**, public `fr.nauss.choptorch`, futur tag **v0.6-beta.1** (pas de tag existant observé lors de la préparation). Preview debug `.diagnostic` séparée ; aucune donnée du téléphone personnel touchée. La gestuelle 0.4 acceptée n’est pas retunée.

## Implémentation

- `Activation` sépare intention persistée et état `LiveState` observé. Écriture synchrone avant départ/arrêt. Une nouvelle installation reste désactivée ; aucun accès aux préférences d’un autre package.
- Service foreground `specialUse` silencieux, sticky uniquement pour détection autorisée. Null intent de reprise système passe les mêmes gardes. Échec/refus terminal jusqu’à nouvelle activation explicite, pas de boucle, polling, alarme/job ni wakelock de maintien.
- `RecoveryReceiver` privé, deux actions protégées boot/update, seule nouvelle permission RECEIVE_BOOT_COMPLETED. Préférences credential-protected : **premier déverrouillage requis**, pas de Direct Boot.
- Arrêt notification et UI écrivent l’intention false avant arrêt. Applications actives/force-stop n’offrent pas de callback : historique système de cette app, REASON_USER_REQUESTED postérieur au dernier consentement, désarme la reprise au prochain point d’entrée. Force-stop respecté aussi par Android.
- Notifications globales/canal bloqué, restriction d’arrière-plan, absence/refus capteur ou flash : état fidèle, service arrêté/refusé. Une indisponibilité temporaire de torche reste observée via callback, sans ouverture de caméra ni permission CAMERA.
- Drapeau stopping visible, commandes et callbacks sérialisés ; les publications d’un ancien contrôleur torche sont ignorées après arrêt. Démarrages répétés ne doublent pas les listeners. Commande de sensibilité seule/obsolète ne crée pas de FGS idle sticky ; les réglages ne passent qu’au détecteur existant.
- Lien batterie conservé vers fiche de cette app, action utilisateur seulement. Aucune exemption adb ni changement global sur téléphone.

Sources primaires et limites légales : [DECISIONS.md](DECISIONS.md). La protection/reprise logicielle n’assure **pas** la réception d’un accéléromètre non-wake-up pendant suspension.

## Vérifications réellement exécutées

Preuves durables **hors worktree** : `C:/root/AI/chop-torch/artifacts/finish/readiness/`.

### TDD, JVM et build

- Politique : RED observé (fonction absente puis refus du cas activé) → GREEN. Première intégration service : RED Android « Fresh/disabled app started detection » → GREEN. Receiver ajouté après RED compilation manquante. Canal notification bloqué et commande de réglage orpheline ont chacun leur RED Android puis correctif/vert.
- **56 tests JVM** : cinq nouveaux contrats de reprise plus moteur existant. **22 tests outils Python** verts. Aucun résultat matériel déduit des tests JVM.
- `clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest` offline : BUILD SUCCESSFUL. Lint debug/release sans issue. Rebuild clean exécuté également.
- Wrapper JAR et distribution Gradle9.5.0 revérifiés contre HTTPS officiel, graphe runtime release et buildEnvironment conservés. API37/JDK21/BuildTools37.0.0/AGP9.3.3/Kotlin2.2.10 épinglés. Aucune dépendance propriétaire applicative.
- Release vraiment unsigned (apksigner verify échoue faute de signature), non debuggable, package/version/code/API vérifiés sur manifeste compilé. Permissions exactes : FGS, FGS_SPECIAL_USE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED. Receiver/service non exportés. Aucun INTERNET, CAMERA, stockage, micro, accessibility ou root.
- DEX release : diagnostics, échantillons de capture, commandes capture/export/test et instrumentation absents. Clés et keystores absents de l’APK/graphe build.
- Sources détecteur/SampleFreshness et **cinq classes compilées, classes internes incluses**, identiques à la référence5a77634. **11 sorties de replay privées** (v1 detector-only et cinq références v2 en recorded/recomputed freshness) identiques octet pour octet à B3 accepté après normalisation CRLF ; entrées originales inchangées. Aucun taux de réussite physique revendiqué.
- Deux assemblages clean unsigned locaux identiques : **d147b0cbb1c4402d54eb8b18ebbc42d287088d9259bb0d857c2f8583375317fc**. Cela ne démontre pas la reproductibilité intermachines ni le build officiel F-Droid.

### Android natif, pas seulement mocks

AVD explicitement jetables, Android API37. Aucun téléphone sélectionné ou redémarré.

`43-stop-instrumentation.log` : vrai framework/service et préférences, app fraîche/désactivée, intention conservée mais service absent annulable depuis la vraie Activity, commande sensibilité orpheline, départ valide, starts répétés, réglage/commande obsolète sur service existant, arrêt par le PendingIntent réel de notification, boot désactivé, reprise update via receiver de production, dix start/stop rapides puis attente6 s et état arrêté, canal notification bloqué refusé. L’appel direct du receiver dans cette instrumentation est distingué de la livraison système ci-dessous.

`44-lifecycle-final.log` / `native-final/checks.json` : **12 scénarios** via installation et UI native :

1. Installation fraîche désactivée.
2. Refus de notification, pas de service.
3. Activation explicite, capteur et callback torche réels de l’émulateur.
4. SIGKILL du processus non force-stop : nouveau PID vivant et état UI actif après vraie reprise sticky système.
5. Remplacement réel d’APK : livraison système, service activé repris.
6. Vrai reboot de l’émulateur activé : reprise boot après déverrouillage.
7. Arrêt explicite suivi d’un remplacement : reste arrêté.
8. Vrai reboot de l’émulateur désactivé : reste arrêté.
9. Stop Applications actives (`cmd activity stop-app`) suivi de remplacement : intention désarmée.
10. Force-stop et réouverture manuelle : reste arrêté.
11. Révocation notification, remplacement, réautorisation puis remplacement : pas de consentement inventé.
12. Restriction réelle d’arrière-plan sur AVD : activation refusée et UI fidèle ; réglage AVD restauré.

`54-missing-torch-final.log` et `51-missing-sensor-final.log` : second AVD, successivement sans caméra/flash puis sans accéléromètre, échec d’activation fidèle et consentement effacé, sans boucle. Ce sont des **absences configurées réelles**, pas des débranchements à chaud d’un capteur statique ou des essais d’usure physique.

`46-ui-final.log` / `47-insets-final.log` : navigation native, annulation, refus/accord, arrêt, sensibilité au clavier et persistance, FR/EN, clair/sombre, aide, 130/200%, rotation/tablette ; aide/source atteignables et viewport hors barres sur six offsets EN100 et FR200. Captures store inspectées visuellement : propres, sans PII ni diagnostics. Quatre PNG natifs copiés exactement dans fastlane ; seul `icon.png` est dessiné depuis la géométrie vectorielle du projet. P1 esthétique conservée, pas de faux screenshot web.

### Signature sûre exercée, pas signature publique

`49-signing-final.log` : environnement Python isolé, coffre Windows Credential Manager réellement testé avec **une nouvelle entrée de test unique**, vérifiée puis supprimée, sans consulter de credential existant. Clé jetable via vrai keytool, zipalign avant apksigner, vérification certificat/alignement ; refus d’écrasement de clé/sortie et de fingerprint incorrect ; clé/APK temporaires supprimés. Le mot de passe de cette clé de test reste uniquement en mémoire ; aucune clé de publication créée.

`tools/sign_release.py` / [SIGNING.md](SIGNING.md) fixent deux identités distinctes APK/index, coffre central Windows, chemins privés hors repo/artifacts, secrets en environnement subprocessus et jamais argv brut/log. `assembleRelease` ne dépend pas du coffre/keyring. P3 doit protéger le dossier privé par ACL et sauvegarder de manière chiffrée avant de créer une identité pérenne.

## Incidents de vérification conservés, pas cachés

- Un premier test instrumentation utilisait un mauvais garde modèle émulateur ; corrigé pour HARDWARE ranchu/goldfish, puis vrai RED fonctionnel obtenu.
- Le RED canal notification levait une assertion sur le main thread de l’instrumentation, donc « process crashed » dans ce **test**. Crash buffer conservé19, attribution explicite à l’assertion de test ; test ensuite remonté depuis thread runner et vert. Pas présenté comme crash métier corrigé par hasard.
- Une seconde passe du harness SIGKILL a lu trop tôt le ServiceRecord Android encore marqué foreground, sans PID vivant. Le harness exige désormais un **nouveau PID vivant + état UI actif** ; passe finale complète verte. Aucun délai garanti déduit de cet essai.
- La vérification finale a révélé un vrai défaut de start/stop rapides : annuler un startForegroundService encore en file par stopService peut laisser un délai de promotion système puis provoquer ForegroundServiceDidNotStartInTimeException. Les traces40/42 sont conservées. Promouvoir seulement à nouveau ne suffisait pas. L’arrêt persiste désormais false et laisse le démarrage en file se promouvoir puis se refuser ; un service observé actif reçoit une commande d’arrêt sérialisée, ignorant un ancien arrêt UI si un nouveau consentement l’a suivi. Régression43 verte après dix cycles et attente6 s, suivie d’une passe lifecycle44 complète. L’intention en attente reste annulable dans l’UI, sans affichage actif inventé ; la sauvegarde automatique de l’interrupteur est désactivée pour ne pas rejouer des callbacks de consentement à la recréation. Le commit intermédiaire f112f6f n’est donc **pas** le candidat final à publier/revoir.

## Remise P3 / revue Roger

- Commit exact : dans le verdict Kanban et `verification.json` généré après commit ; ne pas prendre le SHA P1 d’une ancienne sortie intermédiaire.
- APK unsigned durable : `C:/root/AI/chop-torch/artifacts/finish/readiness/ChopTorch-0.6-beta.1-unsigned.apk`, SHA256 **d147b0cbb1c4402d54eb8b18ebbc42d287088d9259bb0d857c2f8583375317fc**.
- Preview durable : même dossier, `ChopTorch-0.6-beta.1-preview.apk`, package `.diagnostic`, ne pas publier.
- Public : `fr.nauss.choptorch`, version0.6-beta.1/code6/tag prévu v0.6-beta.1, prerelease/bêta honnête. Métadonnées/changelog6 FR/EN + icône + deux captures par langue.
- Garde : revue Roger sur le commit exact avant toute intégration P2/publication. Après approbation seulement, fast-forward exclusif dans racine propre, push puis relire SHA distant, puis mettre le worktree P3 à cette base. Aucun reset/écrasement. Pas de seconde revue parallèle du même artefact.
- **Ne pas publier le dossier readiness** : il contient replays/logs privés. Seuls les fichiers explicitement sélectionnés dans fastlane et futur APK signé/checksums sont publics.

Non établis : recette physique du nouveau paquet, premier geste après longue veille naturelle, capteur pendant suspension, faux positifs quotidiens, coût batterie comparatif24 h, endurance72 h, build F-Droid intermachines, signature pérenne, distribution/client F-Droid et acceptation catalogue. Les opérations du téléphone personnel (activation/exemption/reboot) restent au propriétaire. Pas de nouvelle capture gestuelle nécessaire pour cette remise ; essai ponctuel d’installation publique et arrêt de l’ancien détecteur à demander au moment P3 uniquement. La carte globale n’est pas close.
