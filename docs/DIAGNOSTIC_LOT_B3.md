# B3 — investigation hors ligne, sans nouvel APK

## Résultat et frontière de preuve

Le propriétaire confirme que le rythme normal fonctionne mieux en0.3, mais que le
rythme rapide nécessite des répétitions. Les deux sorties de chaque référence ne
constituent **pas** un résultat2/2 ; intentions individuelles non annotées. On ne
redemande pas si les mouvements ont été répétés : cette question est résolue.

**Aucun correctif applicatif retenu, aucune0.4, aucune installation.** Un défaut de
plage temporelle synthétique est isolé ; son élargissement ne récupère aucun épisode
réel démontré dans les captures fournies. Le publier comme correction du problème
physique serait non fondé. La version, le détecteur, ses gardes historiques et le
cooldown restent strictement ceux de0.3/cc4e492.

## Travaux exécutés

- Reconstruction locale du vrai détecteur Kotlin et reproduction des baselines.
- Cinq captures privées rejouées : normal, rapid, confort, fortev2, premièrev1.
  Admission enregistrée **et** recalculée pour lesv2 ; detector-only pour lav1,
  qui ne contient pas les temps de réception. Origines/timestamps et ordre conservés.
- Références normal/rapid : mêmes sorties **et mêmes instants** aux réglages0/50/100
  dans les deux admissions. Zéro admission refusée et zéro désaccord fraîcheur.
  Les autres références retrouvent leurs sorties historiques. Cela ne mesure pas
  la réussite des intentions, ni la latence depuis le début d'un geste physique.
- Inspection avant/après par échelle : certains soutiens courts sont refusés sur
  une échelle pendant qu'une autre poursuit un candidat reconnu. Le refus d'une
  paire courte coexiste avec une direction non alignée et une reconnaissance sur
  une autre échelle. D'autres épisodes expirent en freinage malgré soutien et
  intervalle suffisants. Aucun de ces compteurs n'est un nombre d'essais physiques.
- L'orientation fixée à l'armement et le filtre gravité restent des hypothèses pour
  les épisodes prolongés, **pas** une cause physique prouvée. Le filtre du replay
  démarre à froid ; l'historique précédent la capture est absent.

Les temps/directions/détails individuels sont conservés **uniquement** dans le
rapport privé durable, pas dans ce dépôt public.

## Boucle précise et expérience rejetée

Paire **SYNTHÉTIQUE** : deux cycles sinusoïdaux continus accélération/freinage,
amplitude6m/s², sensibilité100, axeX, cadence8ms. À période280ms : une sortie.
À période140ms : aucune, avec `PAIR_INTERVAL` uniquement. Même amplitude, même
réglage, aucune force accrue, aucune transformation d'une capture personnelle.

```sh
./gradlew --offline --console=plain :app:compileDebugKotlin
python tools/speed_sweep.py --require-pair 280  # GREEN sur0.3
python tools/speed_sweep.py --require-pair 140  # RED attendu sur0.3, code non nul
python tools/speed_sweep.py                   # balayage, pas test d'acceptation
```

Une expérience à une seule variable (minimum de paire150→120ms) a rendu le cas140
GREEN, sans régression des46testsJVM. Pourtant, replays normal/rapid : mêmes
sorties aux mêmes instants. Le rejet de direction demeure là où le rejet
`PAIR_INTERVAL` disparaît. **Expérience annulée**, recompile propre et vérifications
finales exécutées. Aucun des planchers de soutien/inversion ni seuils d'amplitude
n'a été abaissé en cascade. Aucun contre-exemple historique supprimé.

Balayage déterministe de2016combinaisons : périodes100/120/140/160/200/280/400ms,
amplitudes6/12/24, sensibilités0/50/100, quatre axes/signes, quatre cadences dont
irrégulières, deux courbures transverses0/0.15. Il caractérise la segmentation,
**pas** des essais physiques ou un taux de réussite. Les amplitudes de cette
matrice ne sont pas une consigne d'augmenter la force.

## Outillage ajouté

`replay_capture.py --state` active, hors application uniquement, les événements
existants et un état avant/après chaque mesure : filtre, chauffe, cooldown et
phase/horloges/soutien/pics/directions des trois échelles. Sans option, aucune
capture d'état. Rejets d'admission explicités, aucune réception synthétisée,
aucun tri/rééchantillonnage. Les sorties restent privées et peuvent être volumineuses.

```sh
# Chemin privé et hash original explicites ; rediriger HORS Git.
python tools/replay_capture.py CHEMIN_PRIVE --expected-sha256 HASH_ORIGINAL --mode recomputed-freshness --state
python -m unittest tools.test_replay tools.test_speed_sweep -v
./gradlew --offline --console=plain clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

La réflexion est confinée au programme Java de développement, pas à l'APK.
Elle échoue explicitement si l'ancienne architecture ne possède pas les échelles.
Les horloges et pics peuvent subsister après annulation : lire `phase` et
`firstDirection`, pas les champs périmés comme un candidat actif. Les événements
restent agrégés ; un snapshot ne crée aucune étiquette d'intention.

Le SHA du seul `ChopDetector.class` n'identifie pas toute la logique Kotlin :
la garde modifiée expérimentalement se trouve dans `ChopDetector$StrokeScale.class`.
Le replay final imprime donc aussi les empreintes de toutes les classes internes.

## Vérification finale et stockage

-46testsJVM, zéro échec/erreur/saut ; négatifs historiques conservés.
-8tests outillage réussis, dont équivalence replay avec/sans probe, admission
  refusée et couverture sans doublons de la matrice synthétique.
- `clean`, lint et assemblage debug réussis ; aucun changement applicatif.
- Originaux vérifiés avant/après, jamais modifiés ni committés.
- Preuves privées durables : `artifacts/lot-b3/investigation/` à la racine du
  dépôt principal, hors worktree élagable : manifest, logs, matrice, rapport privé.
  Ce dossier n'est **pas** un gel de livraison APK0.4. Ne pas le joindre publiquement.
- Outils/diagnostic seuls sur branche de travail ; pas d'intégration main/remote
  et pas de revue Roger prétendue. Toute correction ultérieure devra passer la
  revue indépendante avant intégration et gel/livraison.

## Précision minimale nécessaire

Il manque une seule intention rapide complète ratée identifiable. Si elle peut
être repérée dans la référence existante, sa fenêtre approximative suffit.
Sinon : **une seule capture**, un seul double chop rapide naturel vers2s après
le début, **sans répétition**, puis résultat oui/non du premier essai. Aucun
nouveau test normal demandé ; la référence normale est conservée. Aucun geste
plus fort, aucune série jusqu'au déclenchement. Cette précision sert à choisir
le segment à reproduire, non à prouver d'avance le futur correctif.

La carte globale reste ouverte ; veille, énergie, reprise, faux positifs réels et
F-Droid ne sont ni couverts ni validés par cette investigation.
