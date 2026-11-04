# P3 — publication de la bêta 0.6-beta.1

## Distribution vérifiée

- Source APK : `75319a44ab4f268239ab7dd3c8170a49e1f70c37`, approuvée indépendamment en P2, intégrée et relue sur `main` avant signature.
- Tag annoté `v0.6-beta.1` : cible dépliée exacte `75319a44ab4f268239ab7dd3c8170a49e1f70c37` (objet tag `4027edbfb7e07e0d04962b7a45f7c4959667c027`).
- Documentation de distribution : commit `f2bd690c5af41d665a06fd66505d68f71fe36537`, sans modification applicative.
- [GitHub prerelease](https://github.com/Nauss/chop-torch/releases/tag/v0.6-beta.1), ID `433401121`, publique, `draft=false`, `prerelease=true`. APK asset ID `707754069`, 2164453 octets ; `SHA256SUMS` asset ID `707754070`.
- APK SHA256 : `212e977932c39edd2e6c9d9f15d46996ea968fe5bb6c35de3994095dffd9cd20`.
- Certificat APK SHA256 : `6E1EDEEB4E4A610A0430C9287CC274AF9DD7388F9D6AB7B891B5C61568FE2D3F`, sujet minimal `CN=ChopTorch`, RSA4096, distinct du debug.
- Package `fr.nauss.choptorch`, code6 / `0.6-beta.1`, min/target API37, non debuggable. Permissions : foreground service, specialUse, notifications, boot ; pas Internet/caméra. Diagnostics/test/capture/export absents du DEX contrôlé.
- [Landing](https://nauss.github.io/chop-torch/), [dépôt personnel](https://nauss.github.io/chop-torch/fdroid/repo), branche `gh-pages` commit `7b2998fdaebaaef44e92e278323e1d23a465d4f1`. Pages `built`, HTTPS forcé, HTTP200 observé.
- Certificat dépôt SHA256 : `C1AC9D8BEAB479AE4D677D6CA131BFDCA6AC0D165350440AC0E046F401B1CC9F`, distinct de la clé APK. C’est un **dépôt personnel bêta**, pas le catalogue officiel.

L’APK GitHub a été retéléchargé en nouveau dossier puis comparé au gel et vérifié avec `apksigner`. Les 20 fichiers publics du dépôt Pages ont été retéléchargés et leurs hashes comparés ; index v1 et `entry.jar` vérifiés par fdroidserver officiel contre l’empreinte épinglée, chaîne SHA256 vers index v2 et APK contrôlée. L’index v1 utilise le vérificateur historique prévu par fdroidserver ; `entry.jar` utilise le vérificateur strict moderne.

## Exécution réelle

Build Windows propre/offline du commit app : tests JVM, lint debug/release, APK debug/release réussis ; 22 tests outils Python réussis. APK unsigned SHA256 `d147b0cbb1c4402d54eb8b18ebbc42d287088d9259bb0d857c2f8583375317fc` concordant avec P2.

fdroidserver officiel **2.4.5**, installé réellement dans un conteneur Linux isolé (Python3.12/Debian, Java21). Image locale épinglée par ID, pas de prétention de reconstruction reproductible de ses dépendances transitives. `fdroid lint` réussit pour le dépôt personnel ; l’avertissement de permissions YAML provient du bind Windows, le YAML ne contient que des références `{env: ...}`, pas les mots de passe.

Signature d’index hors réseau dans conteneur éphémère : racine read-only, capacités retirées, no-new-privileges, limites mémoire/PID, keystore index seul monté RO ; mot de passe envoyé sur stdin puis utilisé en environnement dans le conteneur, jamais Docker env/argv/config plaintext. Aucun keystore envoyé au serveur, à GitHub ou à la CI. Les contenus publics ont fait l’objet d’une revue indépendante avant publication ; les rapports/logs privés de readiness et les fichiers status de fdroidserver n’ont pas été publiés.

### Vrai client F-Droid

Client officiel **2.0.2**, certificat vérifié contre la documentation F-Droid (`43238d512c1e5eb2d6569f4a3afbf5523418b82e0a3ed1552770abb9a9c9ccab`), installé sur AVD jetable API37 `ChopTorch_P1_API37`.

Le dépôt a été ajouté avec son empreinte ; aperçu et fiche de l’unique application affichés. Installation demandée depuis F-Droid et confirmée dans l’installateur Android (autorisation de cette seule source sur cet émulateur). `installerPackageName=org.fdroid.fdroid`, code6/version attendus. APK installé extrait et revérifié : hash et certificat égaux au gel. Premier lancement : **Detection off**, sensibilité50/100, aucune activation automatique.

L’intent `fdroidrepos://nauss.github.io/chop-torch/fdroid/repo?fingerprint=C1AC9D8BEAB479AE4D677D6CA131BFDCA6AC0D165350440AC0E046F401B1CC9F` fonctionne avec ce client. L’intent HTTPS direct lancé par adb n’était pas résolu ; l’URL HTTPS reste saisissable depuis les paramètres du client. Aucun réglage ni installation sur téléphone personnel.

**Mise à jour non exercée** : une seule vraie version publique existe. Aucun faux code/version ni remplacement du code6 n’a été créé pour simuler un update.

## Catalogue officiel : candidature non envoyée

La recette `fdroid/metadata/fr.nauss.choptorch.yml` est un **brouillon**, pas une promesse de build reproductible ni une MR acceptée. Fastlane FR/EN, icônes, captures natives et changelog6 sont publics. GPL-3.0-or-later, dépendances et build source sans coffre.

Essai réel `fdroid build --stop --latest --test --scan-binary` sur Linux, SDK/API37 et Gradle9.5 : compilation source réussie, scan source et scan DEX/binary sans signalement non libre, mais comparaison à la signature upstream **échouée**. Avec la recette de base, AGP inclut un `META-INF/version-control-info.textproto` différent : révision Git du clone contre `NO_VALID_GIT_FOUND` du worktree Windows utilisé pour la release.

Expériences locales non publiées : absence de `.git` donne `NO_SUPPORTED_VCS_FOUND` ; une disposition Git séparée valide produit un unsigned **strictement identique** au gel Windows (SHA256 `d147b0cb…` complet ci-dessus). Pourtant la copie de signature fdroidserver échoue encore (`CHUNKED_SHA512 digest mismatch`, sans différence de contenu décompressé). Cela n’établit **pas** la reproductibilité de l’APK signé pour inclusion officielle. Ne pas modifier/remplacer le code6 publié ; résoudre proprement sur une recette revue ou une future vraie version, conserver les observations négatives.

Premier lancement de fdroid build : défaut interne `SOURCE_DATE_EPOCH=None` avant clonage ; précloner le dépôt exact permet le parcours réel. Un exit0 fdroid avec «1 build failed» n’est pas un succès ; seuls les logs détaillés et `--stop` ont été retenus.

Aucune session GitLab authentifiée obtenue : navigateur local redirigé vers `/users/sign_in`, interstitiel Cloudflare, aucun login sauvegardé disponible ; `glab` absent. Aucun compte créé, aucun consentement accepté, aucune MR/RFP envoyée. Une connexion GitLab du propriétaire est nécessaire avant soumission. L’acceptation finale appartient aux mainteneurs.

## CI et limites restantes

L’authentification gh actuelle a les scopes repo/gist/read:org, **pas workflow**. Aucun workflow n’a été ajouté/poussé, aucune CI déclarée verte et aucune signature privée en CI. Pour autoriser l’ajout ultérieur d’une CI unsigned : le propriétaire peut exécuter `gh auth refresh -h github.com -s workflow` dans son terminal et confirmer l’OAuth, sans partager de jeton en chat.

Veille profonde/premier geste, énergie comparative24h, endurance72h et faux positifs quotidiens restent à mesurer physiquement. L’émulateur et cette publication ne clôturent pas ces critères du projet global.

## Mise à jour et sauvegarde

Conserver **les deux identités de clé**, sans régénérer ni écraser : clé APK pour toutes les futures versions publiques et clé index pour le canal personnel. Préparer une prochaine vraie version/code croissant, source revue/tag unique, tests/lint, alignement/signature locale avec le certificat épinglé ; régénérer les index officiels, vérifier puis déployer seulement le public. Garder les versions précédentes disponibles et tester la vraie mise à jour via F-Droid sans désinstallation. Ne pas retoucher un APK/versionCode déjà publié.

Clés et mots de passe : emplacement central privé et convention dans [SIGNING.md](SIGNING.md), ACL propriétaire vérifiée, Windows Credential Manager ; aucune copie automatique supplémentaire. Le propriétaire doit préparer une sauvegarde **hors ligne chiffrée** des deux keystores et une exportation protégée des deux entrées de coffre exactes, puis tester une restauration isolée sans écraser les originaux ni afficher les secrets. La sauvegarde/restauration n’a **pas** été exécutée dans P3. Perdre la clé APK empêche les updates ; perdre la clé d’index casse la confiance du canal.

## English status

Signed initial beta published on GitHub and a personal signed F-Droid-compatible GitHub Pages repository. Downloads, hashes and repository signatures verified; a real F-Droid2.0.2 client installed the exact APK on a disposable API37 emulator, with the correct installer identity and default-off launch. No two-release update or owner-phone test. Official-catalogue submission has not been sent: GitLab authentication missing and upstream signature-copy reproducibility still fails despite a local cross-OS byte-identical unsigned experiment. CI workflow OAuth scope missing. Physical sleep, energy and72h endurance remain unmeasured. Back up both private keys and vault entries encrypted offline; no backup or secret upload performed.
