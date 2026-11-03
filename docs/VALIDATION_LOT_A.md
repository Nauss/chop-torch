# Rapport logiciel anonymisé — lot A

## Périmètre et verdict

**APK diagnostic natif construit, pas de verdict matériel.** Sources Kotlin / Views Android, identifiant `fr.nauss.choptorch.diagnostic`, API minimale/cible/compilation 37. Le produit final, ses objectifs de veille, énergie et 72 h restent ouverts. Revue indépendante à effectuer avant livraison définitive ; cette note n’est pas une approbation indépendante.

## Vérifications réellement exécutées

| Contrôle | Résultat logiciel |
|---|---|
| Premier test JVM détecteur | RED réel : `syntheticDoubleChopTriggersOnce`, 1 test échoué par assertion, puis GREEN après implémentation |
| Garde de fraîcheur | RED réel : événement retardé accepté par le seed, puis GREEN après garde 250 ms |
| Suite JVM complète | 15 tests synthétiques, 0 échec / erreur / ignoré |
| Android Lint | 0 constat ; avertissements traités en erreurs, hors suggestion de mise à jour de toolchain volontairement épinglée |
| Build | `clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, succès réel |
| SDK | compileSdk / minSdk / targetSdk 37, plateforme Android 17 révision 2, Build Tools 37.0.0 |
| Outils retenus | JDK 21 ; Gradle 9.5.0 ; AGP 9.3.3 ; Kotlin intégré/stdlib 2.2.10 ; JUnit 4.13.2 |
| Inspection APK | `aapt dump badging`, `permissions`, `xmltree` ; application diagnostic debuggable, service privé `specialUse` avec justification |
| Permissions APK | Seulement FOREGROUND_SERVICE, FOREGROUND_SERVICE_SPECIAL_USE, POST_NOTIFICATIONS |
| Signature | `apksigner verify --verbose --print-certs` : vérifiée v2, Android Debug ; aucune clé pérenne de production créée |
| Gradle wrapper | JAR SHA-256 `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`, égal au checksum officiel Gradle 9.5.0 ; distribution également vérifiée |
| ADB / installation | Aucun appareil observé ; **aucune installation revendiquée** |

Les logs complets réels RED, GREEN, build, tests XML, lint, manifeste binaire et signature sont conservés localement dans le dossier ignoré de livraison, sans publication des chemins personnels, du hostname ou de traces utilisateur. `tools/freeze_lot_a.py` produit un **nouveau build propre depuis un commit exact**, vérifie les invariants APK et gèle APK + SHA-256 + taille + versions + commande + commit dans `artifacts/lot-a/manifest.json`. Ce manifeste, et non une empreinte recopiée avant build, identifie le binaire livré.

### Comportements couverts par les tests synthétiques

Deux strokes complets cohérents ; mouvement seul ; deux pics sans freinage ; directions perpendiculaires ; annulation ; timestamps inversés/dupliqués ; trou de données ; cooldown et nouvelle détection ultérieure ; mouvements lents et trop éloignés ; vibration alternée et marche sinusoïdale ; bornage et effet de la sensibilité ; valeur invalide ; gravité au repos ; fraîcheur d’un événement et rejet du futur / retard. Ce sont des modèles de test, **pas des enregistrements de gestes Pixel ni une mesure de précision**.

## Défauts corrigés pendant l’auto-revue

- Notification native : canal IMPORTANCE_LOW, aucun son/vibration, `setOnlyAlertOnce` ; pas d’API AndroidX empruntée par erreur à l’API native.
- Toolchain minimale initiale : avertissement D8 sur API 37 éliminé en passant à AGP 9.3.3 / Gradle 9.5.0, sans abaisser le SDK.
- Règles d’extraction : exclusions explicites des domaines sauvegarde/transfert, en complément de `allowBackup=false`.
- Refus foreground : aucune inscription capteur ultérieure après un échec de démarrage.
- Sensibilité accessible : les changements clavier/accessibilité sont persistés, pas seulement la fin d’un drag tactile ; un changement annule le geste partiel.
- Vieille livraison FIFO : ne pas déclencher la LED tardivement après réveil à partir de données >250 ms.

L’auto-revue n’a pas exécuté de tests instrumentés Android. Les parcours lifecycle, accès caméra/flash, refus/révocations système, consentements, SAF et affichage réel doivent encore être exercés sur appareil. La vérification statique et les tests JVM ne sont pas présentés comme équivalents.

## Hors validation logicielle de ce lot

Premier double chop après verrouillage/veille profonde, disponibilité effective des capteurs Pixel, coût des réveils à 50 Hz, faux positifs réels, choix gyroscope ou significant-motion, reprise au boot/mise à jour et avant premier déverrouillage, économie batterie, révocations/ressource occupée sur le Pixel, endurance 72 h et distribution F-Droid. Le protocole `TEST_PIXEL.md` décrit le retour matériel minimal ; aucun arbitrage matériel n’est exigé avant de fournir cet APK.
