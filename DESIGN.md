---
version: alpha
name: ChopTorch
description: Utilitaire Android natif discret, lisible, graphite et lumière chaude.
colors:
  primary: "#665219"
  surface: "#FAF9F6"
  surface-container: "#EEEDE8"
  on-surface: "#20211F"
  on-surface-variant: "#585B54"
  control-normal: "#73766D"
  dark-primary: "#E5CA7B"
  dark-surface: "#171918"
  dark-surface-container: "#242724"
  dark-on-surface: "#F0F1EB"
  dark-on-surface-variant: "#BFC3B9"
  dark-control-normal: "#A2A79B"
typography:
  headline:
    fontFamily: sans-serif
    fontWeight: 500
  title:
    fontFamily: sans-serif
    fontWeight: 500
  body:
    fontFamily: sans-serif
    fontWeight: 400
components:
  screen:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
  detection-panel:
    backgroundColor: "{colors.surface-container}"
    textColor: "{colors.on-surface}"
  secondary-copy:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface-variant}"
  text-action:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
  screen-dark:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-on-surface}"
  detection-panel-dark:
    backgroundColor: "{colors.dark-surface-container}"
    textColor: "{colors.dark-on-surface}"
  secondary-copy-dark:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-on-surface-variant}"
  text-action-dark:
    backgroundColor: "{colors.dark-surface}"
    textColor: "{colors.dark-primary}"
native:
  platform: android-views
  mode: Operate
  headline: 28sp
  title: 20sp
  body: 16sp
  label: 14sp
  body-line-extra: 4sp
  screen-padding: 24dp
  group-gap: 32dp
  panel-padding: 20dp
  panel-radius: 20dp
  maximum-content-width: 600dp
  minimum-touch-target: 48dp
---

## Overview

Finition de l’écran existant, documentée après son implémentation P1 du 4 novembre 2026. Ce fichier n’est pas une autorité préexistante ni un remplacement du produit. Mode **Operate** : activer la détection, lire son état, ajuster sa sensibilité, puis quitter l’écran. Le geste 0.4 est accepté par le propriétaire ; le détecteur et ses paramètres restent gelés.

Kotlin et Android Views/XML uniquement. Le système visuel suit le thème Android ; aucune préférence de thème supplémentaire. Pas de panneau laboratoire, faux onglets, statistiques ou décoration de tableau de bord.

## Colors

Les valeurs normatives viennent de `app/src/main/res/values/colors.xml` et `values-night/colors.xml`. Un seul accent chaud : brun doré lisible en clair, lumière dorée en sombre. Il désigne l’interaction et la sélection ; pas une promesse de fiabilité.

| Rôle | Clair | Sombre |
|---|---|---|
| Surface | #FAF9F6 | #171918 |
| Conteneur détection | #EEEDE8 | #242724 |
| Texte principal | #20211F | #F0F1EB |
| Texte secondaire | #585B54 | #BFC3B9 |
| Action / sélection | #665219 | #E5CA7B |
| Contrôle neutre | #73766D | #A2A79B |

`tools/test_finish.py` calcule le contraste WCAG : texte principal, secondaire et accent ≥4.5:1 sur les deux surfaces de chaque thème ; contrôle neutre ≥3:1. Le switch garde sa géométrie, ses états et sa sémantique Android ; son curseur utilise le rôle texte secondaire au repos, primaire en sélection. Le framework module la piste ; ne pas confondre le contraste du token avec celui d’un pixel de piste semi-transparent.

## Typography

Police système `sans` / `sans-serif-medium`. Rôles partagés, pas de tailles propres à chaque vue :

- Headline : 28sp, medium, identité ChopTorch.
- Title : 20sp, medium, intitulés et interrupteur.
- Body : 16sp, regular ; +4sp d’interligne. Emphasis : même taille, medium.
- Label : 14sp pour bornes et version uniquement.

Les tailles restent **sp**, les distances **dp**. Les extensions `native` conservent ces unités ; aucun équivalent CSS ou Tailwind n’est revendiqué. L’export DTCG porte surtout les couleurs, pas une fausse implémentation web. Pas de police téléchargée ni de glyphes emoji substitués aux icônes.

## Layout

Une colonne défilable, 24dp de marge, largeur plafonnée à600dp et centrée sur fenêtre large. L’identité est suivie d’une phrase courte, d’un seul groupe tonal pour détection/état, de la sensibilité, puis du bouton d’aide. Groupes espacés de32dp ; contenus liés de4/8/12/16dp. Pas de hauteur fixe de texte.

Insets `systemBars | displayCutout` appliqués au parent fixe `safe_area` : le viewport du ScrollView lui-même est mesuré hors des barres, avec clipping explicite du parent et du ScrollView. Le padding seul de l’ancienne implémentation ne garantissait pas cette borne ; la revue Roger a révélé le chevauchement après défilement. La régression native `tools/verify_scroll_insets.py` compare le viewport et ses descendants visibles aux cadres réels des barres système, après plusieurs défilements de l’aide à100% EN et200% FR. Rotation/recréation Android normales ; sensibilité persistée, aide et refus de permission sauvegardés. Les captures couvrent téléphone portrait/paysage, polices1.0/1.3/2.0 et fenêtre tablette émulée.

## Elevation & Depth

Aucune ombre décorative ou dégradé. Un conteneur tonal distingue le contrôle principal. Retour tactile visuel, focus, dialogs et contrôle slider fournis par Android. Pas d’animation ajoutée, son ou haptique ; les animations des widgets suivent les réglages système.

## Shapes

Conteneur à coins20dp. Icône vectorielle torche à silhouette simple, graphite et or, sans marque ni motif martial. Icône de notification monochrome séparée. Aucune image distante.

## Components

- **Détection** : `Switch` Android, cible d’au moins56dp, label lisible, vrai état observé. Consentement avant démarrage, demande notification au moment utile. Le switch n’affiche jamais une préférence enregistrée comme preuve de service actif.
- **État** : texte explicite actif/désactivé/activation et état torche issu de `TorchCallback`. Erreur utile sans nom d’exception ou compteur. Mise à jour `polite`, uniquement sur changement ; aucun ticker ni publication à chaque mesure.
- **Sensibilité** : `SeekBar` Android0–100, cible48dp, nom accessible, valeur `stateDescription` et bornes textuelles. Valeur persistée, réglage effectif envoyé au service existant. Valeur élevée = geste plus léger, sans modification des gardes du moteur.
- **Aide** : expansion inline, pas une navigation supplémentaire. Geste sûr, limites de veille/batterie, fiche Android de l’application, confidentialité, version/licence/source. Le bouton de réglage notification n’apparaît qu’après un refus utile à récupérer.
- **Dépendances** : zéro ajout. La surface minuscule conserve `Activity`, `Switch`, `SeekBar`, `AlertDialog`, thèmes `Theme.Material` clair/sombre natifs. Les rôles/cibles/hiérarchie suivent la discipline Material ; ce n’est **pas** une prétention de widgets Material3. Ajouter AppCompat/Material Components uniquement pour ces contrôles aurait élargi inutilement le graphe FOSS de cet utilitaire. Une migration esthétique est hors P1.

Documentation officielle consultée avant implémentation : [Dark theme](https://developer.android.com/develop/ui/views/theming/darktheme), [edge-to-edge Views](https://developer.android.com/develop/ui/views/layout/edge-to-edge), [WindowInsets](https://developer.android.com/reference/android/view/WindowInsets), [Switch](https://developer.android.com/reference/android/widget/Switch).

## Do’s and Don’ts

Conserver l’interface sobre, les ressources FR/EN, le retour système, le focus clavier et les contrôles standards. Mesurer un vrai rendu natif ; un scan web n’est pas un audit Android. Les attributs de titres, labels, état et régions dynamiques sont présents ; TalkBack avec voix n’a pas été exercé sur un appareil physique dans P1.

Ne pas ajouter capture, export, test de torche, journal, compteurs, réseau ou acquisition supplémentaire. Ne pas qualifier « actif » de preuve de veille/autonomie. Ne pas retoucher le détecteur validé au prétexte du design. Ne pas publier les screenshots de preview comme captures finales F-Droid : leur package/version restent ceux de la prévisualisation.
