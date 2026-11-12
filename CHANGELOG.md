# Changelog

## 0.13-beta.1 — linear-acceleration screen-plane detection

- Use Android's linear-acceleration stream with the existing proximity guard.
- Preserve lateral and up/down chops in the plane of the screen; reject pairs dominated by motion normal to it.
- Keep sensitivity 0–100, saved preferences, actual-state haptics and the existing foreground-service policy. No personal calibration or stored motion templates.
- Retain bounded processing and no permanent CPU wake lock. Battery overhead and deep-sleep delivery remain unmeasured.

Google Play publication is in preparation. Earlier private-trial notes below are historical, not descriptions of the current detector.

## 0.12-beta.1 — sensitivity recentering and status clarity

- Map sensitivity 50 to the previous minimum (0), with firmer settings below 50. Keep saved integers unchanged; choose 50 manually to retain old-0 behavior.
- Preserve the directional recognizer, proximity protection, haptics and power policy. Incidental-use false toggles remain under investigation.
- Show actual flashlight state once, preserving distinct context and recovery messages.

## 0.11-beta.1 — unpublished directional/proximity trial (versionCode 13)

- Replace the production scalar segmenter with bounded directional PCA windows and four complete alternating lobes. Preserve the old detector unchanged as an offline comparator.
- Require a registered proximity sensor, block unknown/covered context, clear gesture history on transitions, and expose localized pause/error text. No proximity-free fallback.
- Retain saved integer sensitivity and consent. Sensitivity now sets per-stroke raw-norm variation evidence, not the previous amplitude mapping.
- Refresh system policy at most once per second during steady callbacks, with uncached consent and a forced fresh policy/consent check before each torch command.
- Preserve actual-state-confirmed haptic feedback and silent notification; feed and clear the new detector during the feedback guard.

This candidate is a hypothesis, not proven field reliability. Core-only pocket rejection is insufficient. No installation, publication, motor, battery or deep-sleep validation is implied.

## 0.10-beta.1 — amplitude recalibration

- Recenter sensitivity using `18 / (1 + 0.06 * level)^2.5`: level 0 remains 18, level 50 is 0.5625, and level 100 is approximately 0.138844 m/s².
- Supersede the previous unchanged 0–50 mapping. Keep saved integer settings and the default of 50 without migration.
- Preserve the existing segmenters and bounded 0.6 profile; add fixed proportional 0.3, 0.15 and 0.075 profiles behind monotone evidence-ceiling gates.
- Preserve two strokes, active braking, signed-support durations, direction, timing, transverse-energy and cooldown guards. The native interface, positive poster and service behavior are unchanged.

These are software thresholds, not measurements of comfortable human effort. The extreme deliberately admits much lighter motion, including potentially unintended coherent motion; pocket-trigger and battery performance are not established.

## 0.9-beta.1 — sensitivity extension

- Keep the positive poster tagline separate from the live flashlight status below the detection details.
- Preserve sensitivity levels 0–50 exactly and smoothly extend 51–100 to lighter synthetic motion.
- Keep the four existing segmentation scales, timing and direction guards; add a bounded low-amplitude scale only above 50.

The maximum completed-amplitude threshold is now 1.15 m/s² instead of 2.3 m/s².
This engineering threshold does not imply half the human effort or a measured everyday false-positive rate.

## 0.6-beta.1 — initial public beta

- Toggle the flashlight with a double chop and adjustable sensitivity.
- English/French interface with system light and dark themes.
- Explicit activation and a silent notification with a stop action.
- Saved activation preference allows Android to restore detection after process loss,
  reboot after first unlock, or an app update, subject to system restrictions.
- Offline operation with local settings and no ads or tracking.

Background gesture availability can vary with device battery management and deep sleep.
