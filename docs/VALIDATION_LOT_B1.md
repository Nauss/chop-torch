# Lot B1 — reconnaissance continue, diagnostic 0.2

## Portée et cause

Correctif logiciel expérimental, **pas encore une validation du nouveau build sur téléphone**.
Le premier essai réel a confirmé permissions, activation et bouton torche dans les deux sens,
mais aucune reconnaissance. Appareil constaté : **Pixel 9a**, Android 17/API 37.
Le capteur disponible est non-wake-up : le fonctionnement en suspension demeure un problème
produit séparé, pas la cause démontrée de l'échec écran allumé.

Le moteur initial exigeait en phase accélération de passer directement d'une projection
positive à un freinage suffisamment négatif : traverser progressivement la zone entre les
deux annulait le geste. Les anciens tests à créneaux ne traversaient pas cette zone.
En outre, le repos vectoriel absolu de 40 ms entre coups excluait un retour continu et
confondait une composante transversale résiduelle avec une absence de relâchement axial.

## Contrat du candidat et garde-fous

Trois phases : attente → accélération → freinage → relâchement axial.
Deux coups complets et orientés dans le même sens sont requis, pas deux pics.

- Filtre gravité (constante 0,6 s), chauffe 500 ms et seuil 18−0,1×sensibilité inchangés.
- Accélération : attendre le passage à −0,65×seuil au plus 160 ms après le départ.
  La traversée de zéro ne détruit plus le candidat.
- Accélération soutenue : cumuler au moins 40 ms d'intervalles dont **les deux extrémités**
  dépassent +0,35×seuil projeté. Le temps passé dans la zone de transition ne compte pas.
- Freinage : au moins 40 ms observés de la même façon sous −0,30×seuil, dans les 180 ms
  suivant l'entrée en freinage. Un freinage bref, absent ou trop long annule le candidat.
- Fin du coup : projection remontée au-dessus de −0,30×seuil après ce freinage.
  **Il n'est plus exigé de pause vectorielle de 40 ms.** Un échantillon de retour déjà
  au-dessus du seuil axial peut commencer le coup suivant sans être perdu.
- Alignement des départs ≥0,80, intervalle départ-à-départ 150–600 ms et cooldown 900 ms
  inchangés. L'expiration d'un premier coup au repos ne détruit plus un second coup déjà
  commencé dans sa fenêtre ; l'intervalle est toujours contrôlé à la reconnaissance.
- Trou >120 ms, temps non croissant, valeur invalide ou annulation explicite : abandon.
  Aucun changement de SampleFreshness, de capteur, de permissions ni de stratégie d'éveil.

Le choix du relâchement axial remplace un critère de repos structurellement incompatible
avec deux coups continus ; ce n'est pas un abaissement global des seuils. Son risque est
clair : d'autres mouvements possédant deux cycles accélération/freinage soutenus et alignés
peuvent aussi être reconnus. Une validation sur contre-exemples quotidiens est encore requise.

## Boucle RED/GREEN et expérience par étape

Fixtures JVM génériques **SYNTHÉTIQUES**, jamais dérivées de la capture personnelle :
transitions sinusoïdales, retour sans pause, relâchement multiaxe, X/Y/Z et signes/diagonale,
cadences 7 ms, environ 8,4 ms irrégulières, trous à 17 ms et 20 ms, annulation,
timestamps dupliqués/inversés/trou >120 ms, fenêtre du couple, cooldown et sensibilité.
Les tests négatifs antérieurs ne sont ni supprimés ni affaiblis.

Régressions vues RED avant correction : transition continue ; relâchement transversal
(test minimal à 7 ms) ; retour continu sans pause ; impulsion accélératrice isolée avec
freinage retardé ; freinage impulsionnel ; second coup finissant après expiration du premier.
Les deux impulsions étaient encore reconnues sans les compteurs de durée : elles deviennent
négatives avec les gardes de soutien. Le nouveau schéma de diagnostic a également son test
RED (type manquant), puis GREEN sur les valeurs et leur ordre.
Les autres scénarios étendent la caractérisation ; ils ne sont pas tous de nouveaux défauts.

Replay **du vrai moteur Kotlin compilé**, sur export privé original inchangé :

| Étape | Sensibilité 0 | 50 | 100 |
|---|---:|---:|---:|
| Initial | 0 | 0 | 0 |
| Traversée continue phase accélération seule | 0 | 0 | 0 |
| Relâchement multiaxe, repos absolu encore requis | 0 | 0 | 0 |
| Relâchement axial sans garde supplémentaire de soutien | 4 | 3 | 2 |
| Soutien accélération **et** freinage ≥40 ms | 1 | 1 | 2 |
| Ablation : garde accélération seule désactivée | 1 | 1 | 2 |
| Ablation : garde freinage seule désactivée | 3 | 3 | 2 |

Chaque ablation a recompilé le vrai moteur ; les deux gardes ont été rétablies ensuite.
L'absence d'effet de la garde accélération sur ces totaux n'autorise pas sa suppression :
le contre-exemple synthétique de l'impulsion retardée montre son rôle indépendant.

Ce sont des **sorties du moteur**, pas un nombre de gestes volontaires confirmés.
Aucun taux de réussite, faux positif ou dénominateur n'est inféré. Ne pas optimiser les
seuils pour maximiser ce tableau : le nombre de tentatives et leurs instants sont inconnus.
La capture initiale n'a pas les timestamps de réception : les éventuels rejets en amont
ne sont pas reconstituables. Les résultats détaillés, instants et journaux RED/GREEN restent
privés dans le dossier ignoré `artifacts/lot-b1/`, jamais dans Git.

## Outil local et confidentialité

Après compilation, depuis un checkout :

```sh
./gradlew --console=plain :app:compileDebugKotlin
python tools/replay_capture.py CHEMIN_EXPLICITE_EXPORT_PRIVE.json
# Options : --expected-sha256 EMPREINTE_LOCALE ; --detail
```

Le script lit le JSON sans l'écrire, dérive temporairement les quatre premières colonnes
sans réordonnancement, appelle `tools/ReplayCapture.java` avec la classe **de production**
et le stdlib Kotlin épinglé, puis supprime le CSV temporaire. Aucun réseau. Son stdout peut
contenir des timings personnels : rediriger exclusivement vers un emplacement privé ignoré.
L'outil applique seulement le détecteur, pas l'admission SampleFreshness, même sur un export v2.
Les tests JVM/CI n'ont aucune dépendance à une capture locale.

Export v2 volontaire seulement : version/applicationId, mêmes inventaire capteur et état de
cadence, puis colonnes historiques inchangées en tête, `received_relative_ms`,
`freshness_accepted` (booléen réellement utilisé par le service) et sensibilité par mesure.
Les deux timestamps sont relatifs au même début de capture, dans la même horloge monotone.
Les événements reçus pendant une capture, même anciens/non ordonnés, ne sont plus masqués
par le filtre du tampon. Les limites 15 s/1000 mesures, mémoire seule et export manuel restent.
Aucun compteur brut permanent, audio, wake lock ou gyroscope continu ajouté.

## Build et remise

```sh
./gradlew --console=plain clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
# Après commit propre, destination durable EXPLICITE, distincte du lot A :
python tools/freeze_lot_b1.py --output CHEMIN_DEPOT/artifacts/lot-b1 --previous-apk CHEMIN_APK_0_1
```

Le nouveau gel refuse l'écrasement, vérifie package/API/version 0.2/versionCode 2,
permissions exactes, service privé specialUse, tests/lint et identité du certificat avec
l'APK précédent. `tools/freeze_lot_a.py` et la livraison A ne sont pas modifiés.
Le manifeste et SHA256SUMS de livraison portent le hash APK et le commit exact ; le statut
de revue au moment du gel est explicitement PENDING. La décision Roger sur le commit exact
fait foi, pas ce marqueur historique. Aucun APK ne doit être installé avant ce contrôle.

La suite autorisée est décrite dans [TEST_PIXEL.md](TEST_PIXEL.md) : recontrôle par default,
update sans désinstallation, puis **court essai écran allumé par le propriétaire**, avant
une longue veille. Ne pas prétendre « corrigé sur Pixel » avant ce résultat. Batterie,
faux positifs quotidiens, suspension, endurance et expérience finale restent non validés.
