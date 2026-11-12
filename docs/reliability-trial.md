# Directional/proximity trial (0.12-beta.1, versionCode 14)

Historical design notes, superseded by the linear-acceleration screen-plane detector
in 0.13. References below to production, raw acceleration or pending installation
describe that earlier trial, not the current release. They are retained for comparator
and regression context; see the current README and changelog for supported behavior.

Unpublished candidate, not a field reliability result. Production uses the public pure
Kotlin `DirectionalChopDetector`; `ChopDetector` is unchanged and retained only as a
legacy comparator. Historical calibration tests and `tools/replay_capture.py` validate
that comparator, not the new production model. No private waveform or learned template
is embedded in the application or public tests.

## Mechanism and fixed bounds

- Raw accelerometer only, at the existing requested 20 ms cadence. No gyro, fused stream,
  wake lock, polling timer, extra production thread or raw-data persistence.
- A 384-sample primitive ring. Evaluate at most once per 40 ms on sample arrival, with
  windows of 0.70, 0.90, 1.15 and 1.45 seconds. Too little history is rejected, not grown.
- Leading 60 ms mean baseline; centered 3-axis covariance; bounded 32-rotation symmetric
  Jacobi eigensolver initialized with all three axes. A single largest-diagonal power
  seed is not used because it can miss an orthogonal principal direction.
- Require total covariance above 1e-9 and dominant-axis fraction at least 0.60. Projection
  peak at least 0.25; threshold is max(0.10, 0.18 * peak).
- Exactly four alternating acceleration/braking lobes. No deletion of small extra lobes.
  Same-sign grouping gap at most 120 ms, interpolated threshold-crossing lobe duration
  at least 15 ms, quiet edges at least 60 ms, inter-lobe gap at most 250 ms.
- Minimum/maximum lobe peak ratio 0.18; complete span 0.24–1.20 seconds; two-stroke energy
  balance at least 0.20. Each two-lobe stroke needs its own raw-norm variation. The
  energy denominator has the prototype's 1e-9 floor; singleton lobes use triangular
  crossing-duration energy. All prototype guard inequalities are retained.
- Sensitivity is clamped to 0–100. Its evidence floor is
  `max(0.08, hypot(9.81, 14.4 - 13.2*u + 1.2*u*u) - 9.81)`, where
  `u = (s - 50) / 50`. New 50 preserves the accepted 0.11 minimum (old 0);
  new 100 preserves the previous midpoint (old 50). The lower half now provides
  firmer settings. The saved integer is NOT migrated; adjust it manually to 50
  to retain old-0 behavior. This is an engineering scale, not measured human effort.
  Ordinary-use false toggles have been reported, not reproduced or claimed fixed.
- Accepted history is consumed and debounce lasts 900 ms. Cancellation clears history
  but preserves evaluation cadence and debounce. Nonfinite or component magnitudes
  above 160, nonpositive/nonincreasing timestamps, and gaps over 120 ms reject and
  clear history. The service retains `SampleFreshness` admission (250 ms).
- Positive `Long` nanoseconds are checked before subtraction. Cadence/debounce use
  elapsed differences, avoiding overflow of future deadlines. Window interpolation
  uses local relative seconds, avoiding large absolute-clock precision loss. A clock
  reversal cannot bypass a previously established cadence/debounce deadline.

These bounds port the selected directional-window hypothesis; they are not another
threshold-only recalibration. Nanosecond boundary decisions need not reproduce binary
floating-point rounding of absolute-second timestamps in a Python reference replay.

## Proximity is required, not optional protection

The core alone accepts too many pocket-like variants. The selected Python prototype
reported 255 derived controls: zero combined-context failures but 75 core-only failures.
Those are correlated transformations of three recordings inspected during design, not
255 independent field trials, and are not a Kotlin acceptance result. Independent
production replay is a separate review gate.

`ProximityGate` starts unknown. A valid on-change state persists without expiry. Near,
far and unknown transitions consume gesture history; every covered/unknown acceleration
sample is blocked. Invalid, nonfinite, negative, out-of-range or out-of-order proximity
readings fail closed until a strictly newer valid event. Restart resets to unknown.
The near threshold is `min(5 cm, sensor.maximumRange)` with strict less-than meaning
near, accommodating binary sensors whose far value is their maximum range. The gate
rejects invalid/nonpositive ranges. This threshold is a portability convention, not
proof that every device or fabric is classified correctly.

One wake-up proximity sensor is preferred, with a non-wake-up sensor fallback only
when that is the available hardware. There is no fallback without a registered sensor.
Registration and callbacks use the existing sensor worker. Missing/failed registration
uses the localized terminal failure path and unregisters all listeners. Non-wake-up
hardware does not establish suspend-time reliability.

The running service exposes checking/covered pause text without changing the poster
or separate actual torch state. Keep the top of the phone uncovered: a hand can block
an intended gesture. The gate cannot establish user intent, and pockets/fabric can be
misread. A missing sensor prevents activation rather than silently enabling the weak
core alone.

## Policy and feedback

Steady sensor callbacks refresh notification/background policy at most once per second
using a monotonic sample-driven gate. Saved consent is read every callback. Explicit
start/settings commands retain immediate checks. Every candidate must pass another
forced fresh policy and consent check immediately before `torch.toggle()`. A stale
cached permission can delay the visible failure by at most a second of callbacks but
cannot authorize an unchecked torch command. No timer runs during sleep.

Existing haptics remain actual-state acknowledgements, not claims about command
provenance: CameraCallback cannot identify the client that changed the light. The
250 ms feedback window continues sampling/cadence but clears the directional history
before and after suppressed samples. The historical overload remains for comparator
regressions. Synthetic feedback tests do not validate a physical motor.

## Verification boundaries

JVM tests directly exercise the production detector, proximity and policy gates using
public analytic signals. Historical calibration regressions remain separately named.
Python tests check offline tools and source wiring, not Android execution.

The instrumentation runner accepts `suite=reliability` in addition to the existing
UI/default lifecycle suites. Only owned disposable emulators may run it; the hardware
safety guard remains. It exercises real framework proximity registration and observed
`LiveState`/detail text, restart, persistent on-change context, and isolated synthetic
detector/gate checks. It does not inject synthetic gestures into the live service or
request torch/haptic output. No hidden-API service acquisition is used. It requires
normal notification/foreground prerequisites and a stable emulator sensor context.
The suite must be run separately with near and far emulator sensor contexts by the
reviewer; unavailable hardware takes the terminal error branch. Registration-exception
injection is not provided. Compilation alone is not a native test pass.

No measured comfort, false toggles/hour, battery drain, sensor power, deep-sleep
reliability or physical motor result is claimed. The public package identifier is
unchanged; release remains unsigned in this implementation workflow. Integration,
certificate signing and installation belong to the parent review, not this build.
