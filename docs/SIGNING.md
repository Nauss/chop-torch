# Signature locale — garde P3

P2 ne crée **aucune clé de publication**. Le test `verify_signing_native.py` utilise une clé jetable en dossier temporaire, un coffre mémoire pour cette clé, puis supprime clé et APK. Son empreinte n’est pas une identité publique.

## Convention stable, séparée des sources

Après revue indépendante du commit exact et vérification de son landing, P3 crée une fois deux fichiers privés :

| Rôle | Fichier privé conseillé | Alias / compte coffre |
|---|---|---|
| APK | `%LOCALAPPDATA%/ChopTorch/signing/apk.p12` | `choptorch-apk` |
| Index F-Droid | `%LOCALAPPDATA%/ChopTorch/signing/fdroid-index.p12` | `choptorch-fdroid-index` |

Coffre **Windows Credential Manager**, service `ChopTorch.release`. Deux mots de passe aléatoires indépendants ; PKCS12, RSA4096, sujet public minimal `CN=ChopTorch`, validité10000 jours. Ne jamais réutiliser Android Debug, une clé d’un autre projet, ou une clé APK pour l’index. Pas de secret en Git, `.ops`, artifacts, Pages, YAML public ou chat. Les mots de passe ne passent jamais en argument brut ni dans les logs : `keytool -storepass:env` / `apksigner --ks-pass env:…`, environnement limité au subprocessus. Les erreurs du subprocessus secret sont volontairement masquées.

Le wrapper refuse les chemins de keystore dans le projet, les worktrees et dossiers publics habituels, les fichiers existants et les identités de certificat différentes de l’empreinte explicitement fournie. L’opérateur doit choisir un dossier **réellement privé**, hors synchronisation publique ; aucun outil ne peut reconnaître tous les dossiers publics possibles.

Créer un dossier dédié neuf, vérifier ses ACL Windows et limiter son accès au propriétaire et, si voulu, SYSTEM/administrateurs. **Ne pas lancer init avant cette vérification.** Ne pas modifier les ACL d’un dossier partagé existant. Protéger le volume par le mécanisme système approprié et préparer une sauvegarde hors ligne chiffrée des deux keystores et des secrets du coffre, test de restauration compris. Le wrapper ne multiplie pas les copies automatiquement. Perdre la clé APK empêche les mises à jour de cette identité ; perdre celle d’index brise la confiance du dépôt. Ne pas remplacer une clé existante si son mot de passe manque : restaurer depuis sauvegarde.

## Environnement isolé

```sh
uv venv C:/chemin/prive/tooling/signing-venv
uv pip install --python C:/chemin/prive/tooling/signing-venv/Scripts/python.exe -r tools/signing-requirements.txt
```

Aucun besoin de keyring pour Gradle ou F-Droid source. Le wrapper importe directement `WinVaultKeyring`, pas un backend de fichier de secours. Si le coffre n’est pas disponible : arrêter la signature, pas écrire du clair. Ne lire aucun `.env` ni credentials d’une autre application.

## Commandes APRÈS revue (pas exécutées pour publication en P2)

Depuis bash Windows, fournir les chemins natifs `C:/…`. Remplacer PY par le Python isolé et KEYTOOL par `$JAVA_HOME/bin/keytool.exe`.

```sh
PY tools/sign_release.py init --role apk --keystore C:/chemin/prive/signing/apk.p12 --keytool KEYTOOL
PY tools/sign_release.py init --role index --keystore C:/chemin/prive/signing/fdroid-index.p12 --keytool KEYTOOL
```

Pour relever l’empreinte publique avant signature, exporter le certificat via `keytool -exportcert` avec le mot de passe récupéré **dans le même processus Python** depuis l’entrée exacte du coffre et transmis seulement par environnement ; utiliser `sign_release.run` comme dans `verify_signing_native.py`. Ne jamais imprimer ce mot de passe. Conserver et relire le SHA256 du DER public dans le manifeste de release, puis passer cet identifiant explicitement :

```sh
PY tools/sign_release.py sign --keystore C:/chemin/prive/signing/apk.p12 \
  --input app/build/outputs/apk/release/app-release-unsigned.apk \
  --output C:/chemin/release/ChopTorch-0.6-beta.1.apk \
  --certificate-sha256 EMPREINTE_PUBLIQUE_EXACTE --sdk ANDROID_SDK --keytool KEYTOOL
```

Le wrapper vérifie package public/non debuggable, aligne avant signature (`zipalign -P16 4`), signe, vérifie avec `apksigner`, contrôle l’alignement et l’empreinte, puis crée la sortie exclusivement. Ne pas retoucher l’APK ensuite. Relire hash et signature **après téléchargement depuis GitHub/Pages** en P3. Le tag unique prévu est `v0.6-beta.1`, code6 ; toute modification après revue exige une nouvelle revue.

La signature d’index sera réalisée avec **fdroidserver officiel**, pas un index fabriqué à la main. P3 adaptera son mécanisme privé au support environnement/config du fdroidserver réellement installé ; aucune config privée n’est créée ici. L’alias index et le compte coffre sont déjà fixés ci-dessus. Un dépôt personnel signé n’est pas une acceptation au catalogue officiel.

## Vérification locale sans clé pérenne

`PY tools/verify_signing_native.py` : nouvelle entrée de test unique dans le coffre Windows, lecture/vérification puis suppression ; génération d’une clé jetable, vrai alignement/signature/vérification, refus de remplacement et de certificat incorrect, suppression du dossier temporaire. N’inspecte aucune entrée existante. Ne pas distribuer son APK de test.

Sources primaires : [apksigner](https://developer.android.com/tools/apksigner), [zipalign](https://developer.android.com/tools/zipalign), [keytool JDK21](https://docs.oracle.com/en/java/javase/21/docs/specs/man/keytool.html), [F-Droid Signing Process](https://f-droid.org/en/docs/Signing_Process/).
