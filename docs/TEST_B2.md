# Recette B2 — diagnostic 0.3, geste confortable uniquement

**La référence confortable0.2 a déjà été reçue et exploitée. Ne pas la redemander.**
L'ajustement hors ligne ne valide pas physiquement le nouvel APK. Le test écran
allumé0.2 était négatif et n'est pas transformé en succès par les tests JVM.
Aucun essai de veille, batterie ou F-Droid avant la reconnaissance naturelle.

## Porte de livraison — opérateur default

1. Attendre le verdict indépendant Roger sur le commit exact. Recontrôler le
   manifeste, le SHA-256 et la taille du fichier gelé dans `artifacts/lot-b2`,
   et la correspondance de la source avec main et le remote vérifiés.
2. Vérifier le même package `fr.nauss.choptorch.diagnostic`,
   **versionCode3 / 0.3-diagnostic**, min/target/compile SDK37 et le même certificat
   SHA-256 `530f4619a4caaacb3a1c1fcce20e8e01d85bc78482b915f90e176c58134ad2ae`.
3. Seulement si le téléphone autorisé est branché : revalider Pixel9a,
   Android17/API37, build `CP3A.261005.005`, puis update par **`adb install -r`**.
   Vérifier la version effectivement installée. Ne pas désinstaller, effacer les
   données, changer de clé, octroyer automatiquement une permission, activer le
   service ou commuter la LED. Ouvrir l'UI est autorisé. Les préférences restent.
4. Pas de contournement de l'interdiction d'envoi APK Telegram (archive, renommage,
   API alternative). Si le câble manque, signaler que l'update n'est pas faite.
   Un relais/wake n'est pas une preuve de livraison.

## Essai du propriétaire — écran allumé, sans forcer

- Réactiver volontairement le service après la mise à jour. Garder le réglage100
  déjà utilisé pour comparer ; ne pas demander d'augmenter la force.
- Tenir fermement le téléphone dans un endroit dégagé, loin du visage et des yeux.
  Pas de lancer, pas de vidéo risquée, arrêter au moindre inconfort.
- Faire **un double chop confortable**, puis attendre quelques secondes et en
  faire **un second** tout aussi confortable. Ce sont deux essais de double chop,
  pas deux coups isolés. Observer l'allumage puis l'extinction et toute double
  commutation. Si l'un échoue, le signaler ; ne pas escalader en secousses fortes.
- Retour utile : version0.3, réglage effectif, deux résultats séparés, état service
  et éventuelle double commutation. Ne pas annoncer95% à partir de deux essais.

Une nouvelle capture n'est **pas un préalable** : celle de référence a été reçue.
Si le propriétaire souhaite documenter le nouvel essai0.3, il peut consentir à une
capture manuelle puis exporter volontairement. La capture s'arrête à la première
limite **15 secondes OU1000 mesures**, environ8 secondes sur ce Pixel observé :
faire le premier essai après environ une seconde, le second quelques secondes
plus tard, avant l'arrêt affiché. Le réglage, la réception et la fraîcheur sont
exportés. Aucun enregistrement permanent. Ne pas attribuer les anciens événements
du journal à cette capture, ni confondre le compteur cumulatif avec son résultat.

L'export contient des mouvements privés : ne pas le commiter, ne pas publier
ses instants ni le partager automatiquement. Ne pas collecter réglages réseau,
clés, identifiants ou captures d'écran sans rapport. Rien à effacer dans Downloads.

**Validation physique0.3 : EN ATTENTE d'un retour réel.** Veille profonde, premier
geste sans réveil, reprise, batterie/endurance et faux positifs quotidiens restent
des validations ultérieures distinctes sur la carte globale.
