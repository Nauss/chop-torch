# Lot B2 — diagnostic 0.3, reconnaissance et sensibilité

## Verdict borné

**Candidat logiciel, validation physique du nouvel APK EN ATTENTE.** Le test réel
0.2 était négatif : demander des secousses plus fortes n'est pas une solution.
Une nouvelle référence déclarée confortable par le propriétaire a été reçue et
exploitée **avant le gel** : deux doubles chops non forcés, espacés. Elle n'est pas
une recette indépendante du réglage ni un essai de l'APK 0.3.

Veille, batterie, reprise automatique, faux positifs quotidiens et F-Droid restent
hors validation de ce lot. Aucun ajout de permission, réseau embarqué, audio,
gyroscope ou wake lock. Package, préférences et certificat debug conservés.

## Bugs et investigation reproductible

1. **Régression démontrée du curseur.** Sur le moteur 0.2 compilé, deux sinusoïdes
   bipolaires mathématiques d'amplitude16 m/s² et durée400 ms par coup, repos80 ms,
   cadence8,4 ms donnent une sortie à50, zéro à100. Test JVM RED exécuté avant
   modification. Ce motif ne provient pas d'un téléphone.
2. Le seuil plus bas démarre plus tôt et modifie la direction et les horloges.
   Une garde accélération160 ms était trop étroite. La porter seule à240 ms ne
   suffit pas : le probe synthétique montre aussi184,8 ms de freinage face à
   l'ancienne garde180 ms. Deux gardes240 ms et une borne de coup440 ms corrigent
   le test, sans autoriser un mouvement arbitrairement lent.
3. **Un seul armement très bas n'est pas une solution robuste.** Ce premier candidat
   passait les motifs simples mais perdait les sorties sur la référence confortable.
   Il a été rejeté. Un contre-exemple public indépendant (préambule transversal3,5
   m/s² pendant140 ms avant une paire axiale) échoue avec ce candidat mono-échelle
   et passe avec le candidat final. Ce motif n'est pas dérivé d'une capture privée.

## Contrat final du détecteur

Un filtre gravité unique (constante0,6 s), trois petites machines à états à échelles
**fixes2,5 / 8 / 13 m/s²**, évaluées sur les mêmes événements. Chaque paire doit
être complète sur une seule échelle ; aucune combinaison d'un demi-coup avec un
autre axe/une autre échelle. Un seul cooldown global900 ms annule toutes les
machines après la première sortie, évitant les doubles commutations.

- Chaque coup : accélération puis freinage opposé, chacun soutenu au moins40 ms
  mesurés entre échantillons consécutifs effectivement soutenus ; traversée du zéro
  autorisée ; relâchement axial sans pause 3D artificielle.
- Pour une échelle d'armement `a`, soutien accélération `max(2, 0,35a)`, entrée
  freinage `max(2,5, 0,65a)`, soutien freinage `max(2, 0,30a)` (m/s²).
- Limites240 ms par phase et440 ms par coup ; départs des coups espacés de150–600 ms ;
  produit scalaire des directions≥0,80. Un retour opposé n'est pas un deuxième chop.
- Le curseur ne change **plus la segmentation, les directions ni les garde-temps**.
  Il valide l'amplitude des coups terminés : seuil `18−0,10s` jusqu'à50, puis
  `13−0,17(s−50)` jusqu'à100. Donc18 / 13 / 4,5 m/s² aux réglages0 / 50 / 100.
  Pic d'accélération projeté≥seuil, pic de freinage≥moitié du seuil. Le début de
  candidat n'est pas une validation de coup. Les préférences existantes ne sont
  pas réinitialisées ; le défaut reste50.
- Chauffe500 ms ; annulation sur temps non croissant/trou>120 ms, entrée invalide
  ou non finie ; événements reçus âgés de plus de250 ms non admis par le service.

La monotonie testée porte sur **une paire isolée contrôlée**, pas sur les totaux
arbitraires d'un flux avec faux positifs, annulations et cooldown.

## Plage faible proposée et tests synthétiques

Hypothèse de travail mesurable, **pas une calibration physiologique** : à100,
paires sinusoïdales d'amplitude6 et8 m/s² et durées200/280/400 ms reconnues une
fois ; paires triangulaires indépendantes et courbes modestes testées ; axes3D,
cadence7/8,4/20 ms et cadence irrégulière testés. Cela remplace l'ancien raccourci
qui appelait « faible » une amplitude12. La référence confortable réelle n'est
pas remplacée par ces courbes mathématiques.

La grille de non-régression croise8 amplitudes,8 durées,3 cadences et21 réglages
par pas de5. Aucun test négatif historique supprimé : impulsion, freinage bref,
pics non freinés, geste unique, vibration rapide, marche elliptique synthétique,
retour/direction incohérente, lenteur, trou/temps invalide et cooldown. Les
contre-exemples additionnels de `SensitivitySafetyTest` couvrent0/50/75/100.
Le passage de ces tests n'est pas une mesure des faux positifs quotidiens.

## Replays privés, admission et limites

Les trois originaux ont été lus sans réécriture et leur SHA-256 contrôlé avant et
après replay. Aucun brut, instant individuel, capture d'écran ni fixture dérivée
n'est inclus dans Git. Outil : `tools/replay_capture.py`, qui appelle la **classe
Kotlin réellement compilée**, et affiche son empreinte. Pas de traduction Python
du détecteur. Le filtre démarre froid ; l'historique avant capture manque.

| Référence | Mode | Sorties0.2 aux sensibilités0/50/100 | Sorties0.3 aux sensibilités0/50/100 |
|---|---|---|---|
| Première capture, v1 | detector-only | 1 / 1 / 2 | 3 / 3 / 3 |
| Escalade de secousses, v2 | recorded-freshness et recomputed-freshness | 2 / 1 / 1 | 2 / 2 / 2 |
| Deux doubles chops déclarés confortables, v2 | mêmes deux modes | 2 / 2 / 0 | 2 / 2 / 2 |

V2 : booléens d'admission enregistrés réellement appliqués ; deuxième passe avec
`SampleFreshness` de production et timestamps de réception. Aucun rejet de
fraîcheur ni désaccord entre les deux modes sur ces deux captures v2. Les valeurs
de sensibilité par ligne sont validées puis **explicitement remplacées** par les
réglages fixes0/50/100 pour la comparaison. V1 ne contient pas de réception :
l'outil refuse d'en inventer une et indique `detector-only`.

Les journaux détaillés privés contiennent également les causes, pas seulement les
sorties. Exemples à100 :

| Cause de transition | 0.2 secousses | 0.3 secousses | 0.2 confort | 0.3 confort |
|---|---:|---:|---:|---:|
| Timeout accélération | 15 | 24 | 4 | 10 |
| Soutien accélération insuffisant | 4 | 14 | 0 | 1 |
| Inversion trop précoce | 4 | 6 | 0 | 0 |
| Soutien freinage insuffisant | 4 | 12 | 0 | 0 |
| Rejet de direction de paire | 1 | 4 | 1 | 1 |
| Sorties | 1 | 2 | 0 | 2 |

**Ces colonnes ne sont pas des taux comparables** : les causes0.2 sont inférées des
gardes exactes par réflexion, les causes0.3 sont émises par le moteur et cumulées
sur ses **trois** échelles. Plusieurs échelles peuvent rejeter un même mouvement.
Les interruptions par cooldown changent aussi les candidats évalués. Les événements
`FIRST_STROKE` sont des premiers coups complets, pas un compteur d'intentions
isolées ; une annulation peut ensuite les effacer sans `FIRST_EXPIRED`.

Les deux sorties sur la référence confortable sont compatibles avec le protocole
déclaré, mais les instants d'intention ne sont pas annotés. Elles ne démontrent ni
un taux de réussite, ni un succès sur un essai indépendant, ni que la LED du nouvel
APK réagira correctement. Les secousses fortes ne définissent pas le confort et
aucun paramètre n'a été choisi pour maximiser leur nombre de sorties.

## Diagnostics, construction et gel

Capture toujours volontaire, bornée, en mémoire, sans enregistrement permanent.
L'interface indique maintenant **première limite :15 s OU1000 mesures** ; elle peut
finir vers8 s sur le Pixel observé. L'export v2 conserve réception/fraîcheur/réglage
et ajoute l'origine exacte `elapsedRealtimeNanos` de la capture et ses limites.
Les raisons sont observables dans le replay local, sans journal brut continu dans
le service Android.

```sh
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  --offline --console=plain clean :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
python -m unittest discover -s tools -p 'test_*.py' -v
# Depuis le worktree propre et commité, après vérification de la destination durable :
python tools/freeze_lot_b2.py --output <repo-racine>/artifacts/lot-b2 \
  --previous-apk <repo-racine>/artifacts/lot-b1/ChopTorch-0.2-diagnostic.apk
```

Le manifeste gelé donne le commit exact, la taille, le hash APK, le certificat,
les totaux des tests JVM réellement exécutés, zéro constat lint et les permissions
vérifiées par `aapt`. Le gel refuse d'écraser un APK/manifeste existant et ne touche
ni aux lots A/B1 ni aux captures. Les tests Python exercent les trois modes avec
des exports **synthétiques** temporaires et les refus de schéma/hash/admission.
Le verdict Roger est une étape indépendante sur le commit gelé, pas une déduction
du build. Aucun APK n'est envoyé via un contournement de la politique Telegram.

Le [protocole B2](TEST_B2.md) prévaut désormais sur l'ancien protocole B1.
