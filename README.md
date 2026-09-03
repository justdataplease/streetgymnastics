# Street Gymnastic

An offline Kotlin/Android training app with one guided 600-workout calisthenics sequence.

The app is English-only and contains exactly 600 assigned workouts: six levels with 100 workouts each. The Today screen advances through one fixed sequence. Future workouts can be inspected, but only the next assigned workout can be started.

## Curriculum

- One balanced progression rather than independent goal routes.
- Six levels with 100 workouts each, including the available detailed source workouts and newly written sessions that complete the sequence.
- Permanent fundamentals: pull-ups or regressions, rows, push-ups, dips/support, squats/lunges, posterior-chain work, and trunk control.
- Daily movement preparation, post-session stretching, and scheduled low-RPE recovery.
- Parallel development of muscle-up, front lever, back lever, dragon flag, and planche/elevated-plank control from Level 3 onward.
- A consistent workout presentation throughout the app, without source classifications or route selection.

The unavailable paid catalog was not bundled in the supplied app, so its missing names, descriptions, exercises, sets, and videos could not be recovered. Sessions needed to complete this sequence are individually written as static JSON using coaching judgment; no Python or procedural workout generator selects movements or doses. A separate technical export records the supplied data exactly for audit purposes.

## Six levels

1. Foundations — 100 workouts
2. Bar Strength — 100 workouts
3. Skill Transition — 100 workouts
4. Skill Foundations — 100 workouts
5. Lever Strength — 100 workouts
6. Mastery Consolidation — 100 workouts

Validation is rolling rather than limited to stored week boundaries. Within each level's authored portion, every consecutive seven authored sessions include a basic pull-up or row, a basic push-up, a dip or support, legs, core, and at least one recovery-like session at RPE 4 or below. From Level 3 onward, every such authored window also exposes all five goals in parallel. Existing fixed prescriptions retain their source doses and use the same conservative scaling guidance as the rest of the app.

Every authored skill or advanced movement includes a listed regression for the same assigned work when the exact variation is not clean. Explicit authored regression labels are validated never to point to a harder descendant. Labels with an unknown target or a self-reference, and cyclic regression chains, are rejected. For an end-of-chain basic with no lower rung, reduce range, support, or dose. Readiness checks are submaximal and considered together; the app does not ask the athlete to select a goal path.

## Project data

- `street_gymnastic_programs_levels.json` — immutable bilingual forensic export from the supplied APK.
- `curriculum/*.json` — English-only authored sessions, movement definitions, and coaching overlays.
- `generated/curriculum_v2.json` — mechanically assembled English-only 600-workout app catalog.
- `tools/assemble_curriculum.ps1` — combines source data and authored batches; it does not invent workouts.
- `tools/validate_curriculum.ps1` — verifies all 619 authored source records and the 600 app workouts: counts, IDs, provenance, prescriptions, explicit per-side doses, movement references, regression direction and prior rehearsal, uniqueness, rolling seven-authored-session fundamentals, Level 3+ five-goal exposure, low-RPE recovery, workload limits, and English-only output.
- `tools/validate_animations.ps1` — verifies the offline exercise-animation asset: schema, figure proportions, pose inheritance and pins, keyframe timing, complete coverage of all 115 movement IDs and all 34 recovered exercise names, and a forward-kinematics pass that keeps every keyframe inside the drawing canvas.
- `tools/preview_animations.py` — optional development aid (Python 3 + Pillow) that renders contact sheets of every animation so pose edits can be reviewed without building the app.
- `tools/animation_authoring/` — optional source scripts for the animation asset; `build.py` regenerates `exercise_animations.json` from compact angle specs with small inverse-kinematics helpers so hands and feet land exactly on bars, boxes, and the floor.

## Exercise animations

Each exercise card shows a small animated line figure. The figure is an articulated side-view rig with fixed bone lengths: a pose stores absolute joint angles (0 = up, 90 = right, 180 = down) for the torso, head, both arms, both legs and feet, plus one pinned joint such as the hands on a bar or a foot on the floor. Because the renderer interpolates angles rather than points, limbs never stretch, contact points stay put, and the drawing keeps its proportions on every screen size. Templates chain several keyframes, so multi-phase movements (muscle-up, pullover, jump to hang, negatives with a slow lowering phase) are shown as sequences rather than a single back-and-forth.

Poses can inherit from a `base` pose and override only what changes. Two small shoulder parameters, `shrug` (along the torso) and `protract` (toward the front), let scapular drills and support shrugs read correctly. Every catalog movement ID maps to a template, every recovered exercise name has an alias, and descriptor heuristics resolve compound exercises from their set names.

The technical export remains bilingual because it records what was present in the supplied package. Polish is absent from the app UI and bundled training catalog.

See the [APK extraction report](docs/EXTRACTION_REPORT.md) for exact forensic findings and the [curriculum method](docs/CURRICULUM_METHOD.md) for programming rationale, research sources, progression policy, and safety boundaries.

## Build and package

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.1.0, Gradle 8.10.2, Java 17, compile/target SDK 35, and min SDK 23.

On Windows with Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
powershell -ExecutionPolicy Bypass -File .\tools\package_dist.ps1
```

The command assembles and validates the catalog and offline exercise-animation data, builds and lints the debug app offline, and creates `dist/` with the APK, JSON deliverables, documentation, and SHA-256 checksums.

## App behavior

- One assigned Today workout and a browse-only six-level catalog for future sessions
- Workout overviews with warm-up, main work, cooldown, doses, rests, safety notes, and regressions
- Offline articulated line-figure movement guides for every exercise, drawn from `app/src/main/assets/exercise_animations.json` (see [Exercise animations](#exercise-animations))
- Persistent active workout and checked sets
- Between-set rest timer, sound cue, finish/effort flow, progress, and history
- Local-only progress in Android `SharedPreferences`

The app contains no advertising, analytics, account login, cloud sync, broad storage permission, video playback, or outbound video links. Unavailable videos are replaced by lightweight offline movement illustrations; historic IDs exist only in the separate technical export.

## Permission and safety

The requester states they have written permission from the owner to modify and recreate the app and reuse its visual identity. Inclusion of the name and artwork here does not grant third parties a license to them.

New sessions are capped at RPE 8, recovery sessions at RPE 4, and no more than three hard days may appear consecutively. The same in-app scaling guidance appears on every workout.

This is general exercise programming, not medical care. Use sound equipment and appropriate spotting. Perform the listed regression whenever control is lost; for a movement with no lower rung, reduce range, support, assistance, repetitions, or sets conservatively. Stop for sharp pain, instability, numbness, dizziness, or unusual symptoms. Seek individualized advice when returning from injury or managing a medical condition.
