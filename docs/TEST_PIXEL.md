# Protocole Pixel 9a — diagnostics A et B1

**Historique : le test physique0.2 a depuis été négatif. Pour0.3 utiliser
[TEST_B2.md](TEST_B2.md), qui prévaut. Ne pas refaire la capture confortable déjà reçue.**

## Reprise B1 (0.2) — avant tout essai de veille

Le premier essai 0.1 a déjà confirmé les permissions, l'activation et le bouton torche,
mais aucun double chop. La capture existante a été exploitée : ne pas la refaire pour
répondre à cette même investigation. Le capteur vérifié est non-wake-up.

1. Après revue indépendante Roger, **default** recontrôle APK, manifeste, hash et même
   certificat debug. Si le téléphone autorisé est branché, revalider modèle Pixel 9a,
   build `CP3A.261005.005` et package `fr.nauss.choptorch.diagnostic` avant l'update.
2. Mettre à jour avec `adb install -r` uniquement (0.2-diagnostic/versionCode 2).
   Ne jamais désinstaller, effacer les données ni signer avec une autre clé. Vérifier
   la version installée. Ouvrir l'interface est autorisé ; ne pas activer le service,
   la LED ou accorder les permissions automatiquement. Aucun audio.
3. Le propriétaire réactive volontairement le service et fait quelques doubles chops
   courts, normalement tenus, **écran allumé**, espacés d'au moins deux secondes.
   Sensibilité 50 puis 100 si nécessaire ; noter séparément allumage/extinction et
   éventuelles doubles commutations. Ne pas amplifier dangereusement les mouvements.
4. Si le résultat reste mauvais, une nouvelle capture **volontaire** v2 pourra alors
   distinguer l'admission des événements (timestamps de réception exportés) du moteur.
   Cela ne valide pas la veille : pas de longue veille avant ce petit essai réussi.

L'ancien envoi APK Telegram a été refusé par la politique de livraison : pas de contournement
par archive, extension ou autre API. Utiliser le relais default/ADB autorisé, ou signaler
que le branchement manque. Cette procédure ne prétend pas qu'une installation a eu lieu.

Cible fournie par le propriétaire : **Android 17, build `CP3A.261005.005`**. Ne pas modifier cette information ; l’appli cible l’API 37 et ne requiert pas de déterminer un canal stable/bêta.

## 1. Installer et autoriser le minimum

1. Recevoir **le fichier APK diagnostic**, pas un lien inventé. Le superviseur vérifie sa taille et son SHA-256 contre le manifeste de livraison avant l’envoi.
2. Ouvrir l’APK sur le Pixel. Si Android le demande, autoriser provisoirement cette source d’installation (ex. Telegram ou gestionnaire de fichiers), puis retirer cette autorisation après installation. Ne pas désactiver globalement les protections du téléphone.
3. Ouvrir **ChopTorch diagnostic**. Il ne doit demander ni micro, ni caméra, ni stockage, ni localisation, ni accessibilité. Lors de l’activation, accepter la notification silencieuse après lecture du consentement. Si elle est refusée, le diagnostic n’active pas son service.
4. Vérifier l’état réel « actif », le nom du capteur et `Wake-up`. La puissance déclarée n’est pas une mesure de batterie. Si `Wake-up=false`, relever ce fait : la veille fiable n’est pas assurée.
5. L’exemption d’optimisation batterie est facultative. Si vous la testez, utiliser le bouton puis modifier **seulement ChopTorch diagnostic** dans les réglages. Revenir et vérifier que l’état affiché est réellement « accordée ». Ne pas changer l’économie globale.

## 2. Premier essai écran allumé

1. Tenir fermement le téléphone, dans un endroit dégagé, loin du visage/des yeux. Aucun lancer, geste violent ou vidéo à réaliser en risquant de le lâcher.
2. Tester d’abord le bouton **Tester la torche**, une fois pour allumer, une fois pour éteindre. Vérifier la LED et le texte `callback` ; si la caméra est occupée, relever le message au lieu d’insister.
3. Attendre une seconde, effectuer deux mouvements courts de hachage consécutifs, puis laisser au moins deux secondes entre essais. Commencer sensibilité 50. Ne pas regarder directement la LED.
4. Répéter pour éteindre. Noter gestes reconnus / essayés, main utilisée, délai approximatif et toute double commutation. Un geste unique ne devrait pas suffire, sans garantie universelle à ce stade.
5. Pour calibrer : **Capturer 15 secondes**, consentir, attendre une seconde puis faire quelques doubles gestes espacés. La capture remplace la précédente, cesse à 15 s / 1000 mesures et reste seulement en mémoire. Exporter avant de fermer/arrêter le processus.

## 3. Premier geste après verrouillage — essai décisif

1. Sans capture brute active, laisser le service activé et la torche éteinte. Verrouiller l’écran et poser le téléphone en sécurité. Débrancher le chargeur pour une veille plus représentative ; pas besoin de câble ADB pour cet essai.
2. Attendre d’abord 2 minutes, puis faire le **premier double chop sans appuyer sur Marche ni déverrouiller avant**. Noter si la LED s’allume immédiatement ou seulement au réveil de l’écran. Répéter après 15–30 minutes et, ultérieurement, après une nuit naturelle. Ces durées ne prouvent pas à elles seules Doze profond.
3. Refaire le geste pour éteindre, puis déverrouiller et exporter le diagnostic. Noter durée d’attente, état écran, résultat du premier geste, capteur wake-up ou non, exemption oui/non, économie batterie active ou non. Ne pas conclure « veille validée » à partir du seul compteur de mesures.
4. Si le premier geste échoue après veille : le signaler simplement, ne pas multiplier les gestes violents. Aucune promesse de solution par réveil permanent. L’analyse du JSON et des capteurs déterminera la stratégie suivante.

## 4. Export volontaire et arrêt

- **Exporter inventaire et diagnostic JSON** ouvre le sélecteur Android. Choisir un emplacement, lire le fichier avant de le partager. Il peut décrire les mouvements et capteurs du téléphone. Aucun export automatique ; aucun fichier public imposé.
- **Effacer les diagnostics en mémoire** efface capture et journal. Une exportation déjà sauvegardée doit être effacée dans le fournisseur de documents choisi.
- Désactiver l’interrupteur ou utiliser **Arrêter** sur la notification. Vérifier que la notification disparaît, que le service affiche arrêté et que la LED est éteinte. Si un autre composant garde la LED allumée, l’éteindre avec les réglages rapides Android.
- Après force-stop, arrêt système, redémarrage, mise à jour ou mort du processus : rouvrir et réactiver **manuellement**. Pas d’autorestart dans le lot A.

## Retour minimal demandé

Résultat du test bouton torche ; capteur sélectionné / wake-up ; résultat écran allumé ; résultat du **premier** geste écran éteint après attente ; JSON volontaire si possible. Ne pas envoyer de capture des réglages réseau ou d’information personnelle inutile. Aucun test d’endurance 72 h demandé avant ce premier diagnostic.

**Bilan actuel : premier test 0.1 exploité ; test physique du nouveau build B1 EN ATTENTE.**
Le budget batterie, la fiabilité du geste, les faux positifs en poche, la tenue 72 h et la
distribution F-Droid ne sont pas validés par compilation.
