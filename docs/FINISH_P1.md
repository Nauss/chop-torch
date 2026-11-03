# Finition native P1 — 4 novembre 2026

## Périmètre et verdict auteur

Implémentation réelle Kotlin + Android Views, pas une maquette. Le propriétaire a accepté le geste sur APK0.4 ; P1 ne modifie ni `ChopDetector.kt`, ni `SampleFreshness.kt`. Leur source et **les cinq classes compilées, dont toutes les classes internes**, sont identiques à `5a77634ec17762509ab529fd3bd0d8bd58bfc027`.

L’auteur remet le lot pour **revue indépendante Roger**, sans s’auto-approuver. L’intégration main/remote doit porter uniquement sur le commit approuvé. P2 reste dépendant de cette approbation ; si le reviewer termine la carte avant intégration, default/P2 doit effectuer et vérifier le landing avant tout nouvel edit. Aucune release ou publication F-Droid n’est faite dans P1.

## Changements

- Écran unique XML : identité légère, explication, interrupteur, état service/torche observé, curseur0–100, aide progressive. Contrôles standards Android, cibles48dp minimum, texte en sp, ressources FR/EN et langues déclarées au système.
- Thème système clair/graphite, accent chaud retenu, icône torche vectorielle et icône de notification monochrome. Pas d’ajout de bibliothèque, de réseau, de police distante ou d’animation.
- Consentement avant activation et récupération notification refusée. Le libellé « actif » ne dépend pas de la préférence sauvegardée. Aucun compteur, âge de mesure ou polling UI ; observation seulement quand l’activité est visible et sur changements d’état.
- Suppression complète de `Diagnostics.kt`, des commandes service capture/test torche, de l’export document, du buffer brut, du journal et de l’inventaire. `DiagnosticSample.kt` déplacé **sans changement** vers les sources test pour préserver les régressions du schéma historique. Tests automatisés, replayers hors APK et preuves privées préexistantes conservés.
- Les structures internes `ChopDetector.Observer/Event` restent inchangées pour préserver le moteur ; le service n’installe aucun observer de collecte.
- README actualisé : geste0.4 accepté, sections diagnostic explicitement historiques, confidentialité et limites corrigées. Le PLAN local ignoré Git est mis à jour dans la racine sur demande ; il n’est pas ajouté au dépôt.

## Vérifications réellement exécutées

```sh
./gradlew --offline clean :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease --console=plain
python -m unittest discover -s tools -p 'test_*.py'
python tools/verify_finish.py
python tools/verify_native_ui.py
```

- **51 tests JVM**, zéro échec/erreur ; **15 tests outils Python**, réussis (dont cinq nouveaux contrats de finition).
- Lint debug et release : zéro problème ; build debug signé développement et release **unsigned** réussis. Aucune clé de publication créée.
- Manifest release décodé avec `apkanalyzer` : `debuggable=false`, uniquement `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`. Aucun `INTERNET`, `CAMERA`, stockage, micro ou permission d’exemption imposée. Service non exporté.
- Inspection source et DEX release : absence de `Diagnostics`, `DiagnosticSample`, `TEST_TORCH`, `CAPTURE`, `ACTION_CREATE_DOCUMENT`, `openOutputStream`. Ressources FR et EN complètes, aucune chaîne littérale de rendu XML ; thèmes vérifiés dans l’APK par `aapt dump resources`.
- Comparaison de la source exacte et des hashes de `ChopDetector.class`, `$Event`, `$Observer`, `$StrokeScale` et `SampleFreshness.class` avec compilation de la base dans ce worktree avant toute édition : identiques. Cette preuve remplace un nouveau replay de traces privées ; aucun original privé n’est modifié.
- Contraste WCAG calculé par tests pour les tokens de texte, accent et contrôle dans les deux thèmes, sur surface et conteneur. Ce calcul ne prétend pas mesurer chaque pixel d’un widget semi-transparent.

Le script `verify_finish.py --baseline` s’exécute **uniquement à la base avant edits** et refuse l’écrasement. Le mode normal requiert ce baseline durable. Il copie les rapports et produit `verification.json` avec les hashes d’APK et le commit réellement vérifié. Les outils APK du poste viennent des cmdline-tools23.0 ; les tests outils génériques ne nécessitent pas ces outils Android.

## Impeccable et documentation du système

Skill global lu directement en lecture seule, absent du profil tim. `context --target app/src/main/kotlin/fr/nauss/choptorch/MainActivity.kt` exécuté une seule fois dans le worktree. Références polish, distill, operate, android, harden et craft-floor lues ; pas de faux entretien greenfield ni de modification des profils/plugins.

Une seule passe `impeccable detect --json` sur les fichiers Kotlin/XML/thèmes rend `[]`. **Ces cibles natives ne sont pas analysées par le détecteur web : ce résultat n’est pas un audit Android ni un certificat de qualité.** La vérification Android repose sur lint, les tests, les ressources/APK et les captures natives regardées.

`DESIGN.md` est extrait des choix effectivement codés, avec tokens clair/sombre, mesures dp/sp, composants et limites. Export DTCG `docs/design-tokens.json`. Lint du DESIGN : zéro erreur ni problème de contraste ; avertissements documentaires sur les deux tokens de contrôle non référencés comme composants textuels et sur l’extension native ignorée par l’export. Les dimensions dp/sp restent explicites dans DESIGN et XML, pas converties artificiellement en CSS.

## Recette native et preuves visuelles

Aucun appareil physique n’était connecté à ADB au début de P1. Aucun verrouillage contourné, permission accordée, thème/police/orientation changés ou screenshot pris sur le téléphone du propriétaire.

Émulateur jetable **ChopTorch_P1_API37**, Android17/API37, image AOSP `system-images;android-37.0;default;x86_64` révision6, profil Pixel7, écran1080×2400, densité420. Fingerprint réel dans chaque fichier de configuration. Pas de validation matérielle du geste, de LED physique, de veille ou de batterie déduite de l’émulation.

Les anciens cmdline-tools19rc créaient une cible AVD incorrecte, entraînant l’assertion graphique `hasReadColorBufferDma` lors du screencap. Mise à jour isolée des outils vers23.0 et recréation de l’AVD : captures réussies, sans patch de l’app ni contournement de screencap. Voir [ticket Android primaire](https://issuetracker.google.com/issues/546200928). Les essais GPU initiaux échoués sont conservés uniquement dans les logs locaux ; ils ne sont pas des preuves visuelles.

Recette `adb input` + UI Automator réellement exécutée sur cet AVD :

- inactif, activation annulée, notification refusée avec action de récupération ;
- notification accordée **sur l’AVD seulement**, service actif observé, état torche callback ;
- slider atteint par Tab, trois flèches →53/100, valeur conservée après mort/recréation du processus ; désactivation respectée ;
- aide ouverte et défilée, thèmes clair/sombre, langues EN/FR, police1.3 en portrait/paysage et2.0 en FR ;
- fenêtre tablette1600×2560/densité240 pour la mesure centrée600dp ; pas un test de tablette physique.

Chaque capture est prise par **`adb exec-out screencap -p`** après vérification du focus de notre fenêtre et absence de lockscreen. L’outil refuse toute sortie sans signature PNG (les premiers screencap Android échoués ne sont donc pas traités comme images). Les PNG, arbres UI et fichiers `*-config.txt` sont durables hors worktree :

`C:/root/AI/chop-torch/artifacts/finish/design/`

| Preuve | État effectif |
|---|---|
| `01-light-en-off.png` | EN, clair, police1.0, désactivé |
| `02-permission-denied.png` | EN, refus notification, récupération visible |
| `03-light-en-active.png` | EN, clair, service actif, torche éteinte observée |
| `04-dark-en-off.png` | EN, sombre, police1.0 |
| `05-dark-en-help.png`, `06-dark-en-help-lower.png` | aide ouverte/défilée, sombre |
| `07-dark-en-font130.png`, `08-landscape-font130.png` | EN, sombre, police1.3, portrait/paysage |
| `09-light-fr-off.png`, `10-dark-fr-off.png` | FR, police1.0, clair/sombre |
| `11-dark-fr-font200.png`, `12-dark-fr-font200-lower.png` | FR, sombre, police2.0, début/suite défilée |
| `13-tablet-fr-dark.png` | FR, sombre, police1.0, fenêtre tablette émulée |

Revue visuelle auteur effectuée sur les rendus natifs : hiérarchie nette, texte secondaire lisible, absence de troncature du titre et du switch même à200%, aide atteignable par défilement, colonne centrée sur fenêtre large. Deux défauts ont été corrigés puis recapturés : texte défilant sous la barre de statut (`clipToPadding`) et pouce de switch trop sombre. Le label à200% passe sur deux lignes sans chevaucher le switch. Les états ne reposent pas uniquement sur une couleur.

Aucun contenu privé vu dans ces images AOSP. Elles restent des **preuves de preview**, pas des screenshots publics F-Droid finaux : package diagnostic et version0.5-ui-preview. P2/P3 devront recapturer la variante publique après vérification. TalkBack parlé, LED et gestes physiques non exercés dans P1 ; attributs d’accessibilité et navigation clavier vérifiés, sans revendiquer une recette de lecteur d’écran complète.

## Limites et suite

Preview : `fr.nauss.choptorch.diagnostic`, versionCode5, `0.5-ui-preview`, min/target/compile37. Le futur package public reste réservé `fr.nauss.choptorch`. Ne pas remettre cette prévisualisation comme release pérenne ; l’APK release P1 est volontairement non signé.

P1 conserve `START_NOT_STICKY` et la réactivation manuelle. Reprise lifecycle à implémenter/tester en P2 sans retuner le geste. Les objectifs initiaux veille profonde, faible consommation et endurance72h ne sont pas clos par l’acceptation du geste ou par ces tests. La carte globale reste ouverte. Revue Roger, puis intégration main/remote vérifiée par default/P2 avant la suite.
