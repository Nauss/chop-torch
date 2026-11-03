# B3 — candidat diagnostic 0.4, rythme normal et rapide

## Périmètre de preuve

Le propriétaire a confirmé que la série rapide précédente contenait des
**répétitions**. Ses deux sorties ne sont donc pas deux premiers essais réussis.
Une nouvelle capture d'un seul essai rapide naturel, demandé isolé, a été fournie
avec le retour « J'ai refait, non déclenché ». Ce n'est plus un blocage de mesure.
Aucun début/fin physique exact ni label par échantillon n'est inventé.

**Résultat logiciel :** l'échec est reproduit sur le vrai moteur0.3, puis corrigé
hors ligne sur ce nouvel export. Les références normale et rapide sont conservées
au même réglage. **Aucune validation physique de l'APK0.4**, aucune installation,
aucun service ou LED activé par ce lot. Revue indépendante Roger obligatoire avant
intégration main/remote et remise par le superviseur.

## Cause isolée, pas abaissement des délais

Le nouveau cas échoue à tous les réglages comparés, sans admission rejetée ni
écart de fraîcheur. Les deux coups candidats aboutissent mais la comparaison
`PAIR_DIRECTION` utilise **une seule mesure à l'armement**. Un retour transversal
au début du deuxième coup déplace cette direction ; l'accélération soutenue reste
cohérente. Les fenêtres de soutien et d'intervalle passent déjà. Le cooldown n'est
pas en cause avant la première sortie, et aucun callback torche n'est attendu
quand le moteur ne reconnaît rien.

La preuve privée par échelle compare les directions d'armement aux intégrales
soutenues ; l'alignement soutenu franchit le même critère0.80 là où l'alignement
initial échoue. Les timestamps, directions, originaux et scripts d'analyse restent
hors Git. C'est un diagnostic de ce signal, pas une preuve d'orientation absolue
ou de réussite générale sur téléphone.

## Correctif borné

Chaque échelle garde son axe initial pour **toute la segmentation**, les
projections signées, les pics d'amplitude et les durées. Elle accumule aussi le
vecteur d'accélération pondéré par le temps **uniquement sur les intervalles déjà
admis comme soutien positif consécutif**. Son intégrale non nulle est normalisée
à la fin du coup ; pas d'intégration du freinage ou des retours non soutenus.

Une paire complète sur une même échelle doit avoir soit deux directions
**d'armement** alignées, soit deux directions **soutenues** alignées, au même
critère0.80. Jamais armement d'un coup contre intégrale de l'autre, jamais des
moitiés de différentes échelles. Les deux témoins sont indépendants ; il ne
s'agit ni de nouveaux modes utilisateur ni d'un seuil sélectionné selon la vitesse.

La variante « remplacer entièrement l'armement par l'intégrale » a récupéré le
cas rapide mais perdu une sortie de la première référencev1. Elle a été rejetée.
Le témoin initial est donc conservé : les coups à départ cohérent et courbures
modérées opposées ne doivent pas régresser. Cette décision est couverte par un
contre-exemple synthétique et le replayv1, pas cachée derrière le total global.

**Inchangés :** filtre gravité, trois échelles, seuils d'entrée, curseur et
amplitude, chauffe500ms, soutien40ms par demi-coup, inversion minimale40ms,
240ms par phase,440ms par coup, paire150–600ms, remise à zéro sur trou>120ms ou
temps non croissant, fraîcheur250ms et cooldown global900ms. L'expérience antérieure
150→120ms n'a pas été réintroduite : elle n'expliquait pas l'échec fourni.
Pas de capteur, permission, réseau, son, wake lock ou traitement permanent ajouté.

## RED → GREEN et contre-exemples

- Test SYNTHÉTIQUE d'une paire courbe, amplitude6m/s²/sensibilité100/cadence8ms :
  témoin rectiligne reconnu, variante à départ courbe rejetée sur0.3 à période200ms.
  Échec d'assertion réellement observé, puis GREEN avec le correctif. Le témoin
  normal280ms et le rapide200ms passent au **même réglage et à même amplitude**.
- Matrice de108 cas positifs dans `CurvedOnsetTest` : deux rythmes, trois cadences,
  trois axes, avec/sans courbure, trois couples amplitude/réglage. Les amplitudes
  de test ne sont pas une consigne de bouger plus fort.
- Témoins négatifs : trajets perpendiculaires et faux appariement croisé des deux
  types de direction. Les négatifs historiques restent intacts : coup unique,
  freinage manquant/bref, impulsions, vibrations rapides, marche synthétique,
  lenteur excessive, gaps, timestamps inversés/dupliqués, annulation et cooldown.
- Assertion privée RED sur le nouvel export réel : exactement une sortie attendue
  pour l'essai isolé, zéro sur0.3. GREEN sur le candidat, aux réglages0/50/100,
  admission enregistrée **et** recalculée. Aucun changement des temps de réception,
  de l'ordre ou des échantillons ; aucune trace personnelle ajoutée aux tests publics.
- Probe hors application enrichie (`--state`) avec intégrale courante et premier
  témoin soutenu. Test outil RED puis GREEN ; le replay sans probe conserve son résultat.

## Replays comparés, pas taux de réussite

Reconstruction du détecteur courant avant chaque série ; empreintes de la classe
externe **et** des classes internes conservées. Originaux vérifiés avant/après.

| Référence privée |0.3|Candidat0.4|Interprétation bornée|
|---|---:|---:|---|
|Essai rapide isolé non déclenché|0|1|Échec récupéré hors ligne, première sortie dans la fenêtre de mouvement observée|
|Rythme normal|2|2|Deux sorties conservées : instants identiques à0 ; première plus tôt à50/100, seconde identique|
|Série rapid avec répétitions|2|2|Première identique, seconde plus tôt ; aucun2/2 ni taux d'intention déduit|
|Référence confortablev2|2|2|Mêmes instants aux trois réglages|
|Ancienne escalade fortev2|2|2|Première identique ; une nouvelle sortie anticipe la seconde et masque l'ancienne dans le cooldown|
|Première référencev1|3|3|Mêmes instants aux trois réglages ; detector-only exclusivement|

Pour les cinqv2 : admission enregistrée et recalculée aux trois réglages ; zéro
admission rejetée et zéro désaccord. La nouvelle sortie de la référence forte est
explicitement **non annotée** : ne pas l'assimiler à la même intention, ni à une
amélioration de latence. Les comparaisons détaillées sont privées. Une sortie
avancée à l'intérieur du cooldown peut modifier les événements ultérieurs sans
augmenter le nombre total. Les chiffres de cette table comptent des sorties,
**pas des gestes physiques**, sauf le contexte utilisateur de l'essai isolé.

Balayage `speed_sweep.py` :2016combinaisons synthétiques durée/amplitude/réglage/
axe/cadence/courbure. Caractérisation seulement : les périodes140ms et inférieures
ne sont pas garanties et les gardes antivibrations restent actives. Aucun quatrième
abaissement arbitraire des seuils pour élargir artificiellement ce tableau.

## Vérifications et gel candidat

- `clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` exécutés avec le
  wrapper épinglé, hors ligne :51testsJVM, zéro échec/erreur/saut ; lint zéro constat.
-10tests outils Python réussis (exports synthétiques seulement), dont les gardes
  de destination du gel et la probe enrichie.
- Package `fr.nauss.choptorch.diagnostic`, versionCode4, `0.4-diagnostic`, API37.
  Certificat debug attendu identique à0.3 ; permissions inchangées vérifiées au gel.

Depuis le worktree **propre et commité** :

```sh
# TMPDIR/TEMP/TMP doivent viser le scratch Hermes, pas le Temp système.
python tools/freeze_lot_b3.py --output CHEMIN_DURABLE/artifacts/lot-b3/delivery-v04 --previous-apk CHEMIN_DURABLE/artifacts/lot-b2/ChopTorch-0.3-diagnostic.apk
```

Le script refuse toute destination existante, reconstruit, vérifie tests/lint,
package/version/API/permissions/service/signature, et fige APK, hash, manifest,
rapports, empreintes source et protocole. Le manifest est **PENDING review** ; le
verdict indépendant sur le commit exact est porté par Kanban, pas fabriqué dans
le manifest. Ne pas envoyer l'APK avant ce verdict.

Preuves privées nouvelles : `artifacts/lot-b3/single-miss-investigation/` au dépôt
principal, distinct de l'investigation précédente, jamais joint publiquement.
Gel livrable public local : `artifacts/lot-b3/delivery-v04/`, hors worktree élagable.
Le superviseur revalide le gel revu et l'appareil avant une mise à jourADB-r sans
reset des préférences. Ce lot n'effectue ni installation ni publication distante.

## Suite de recette

Après revue et remise uniquement : suivre [TEST_B3.md](TEST_B3.md), un premier
essai normal puis un premier essai rapide, sans répétitions jusqu'au déclenchement.
La carte produit globale reste ouverte : veille profonde, énergie, reprise,
faux positifs quotidiens, endurance et F-Droid sont des validations distinctes.
