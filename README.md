# Street Gymnastic Revival

An offline Kotlin/Android recreation of the discontinued Street Gymnastic app, built from an owner-authorized Base APK and a clearly labeled continuation curriculum.

The revived app is English-only and contains exactly 600 assigned workouts: six levels with 100 workouts each. The Today screen advances through one fixed sequence. Future workouts can be inspected, but only the next assigned workout can be started.

## What is preserved

- The original Street Gymnastic logo, launcher icon, blue/red palette, numbered workout rows, overview flow, and active-workout style.
- All 90 workouts whose exercise details are present in the supplied Base APK.
- Every original APK slot, ID, order value, prescription, and source hash in the immutable forensic export.

## What is newly authored

- 180 distinct app sessions for empty Pro slots within the balanced 100-workout Level 1-3 sequence.
- 30 continuation sessions that extend Level 3 from its original 70 slots to 100.
- 300 sessions across Levels 4-6.
- Permanent fundamentals: pull-ups or regressions, rows, push-ups, dips/support, squats/lunges, posterior-chain work, and trunk control.
- Daily movement preparation, post-session stretching, and scheduled low-RPE recovery.
- Parallel development of muscle-up, front lever, back lever, dragon flag, and planche/elevated-plank control across the authored portion of Level 3 and all later levels.

The original APK contains another 109 empty Pro placeholders above the balanced Level 1-2 limit. They remain preserved in the forensic export, and their authored research drafts remain in `curriculum/`, but they are not part of the 600-workout app sequence.

No new session is represented as recovered Street Gymnastic PRO content. The supplied Base APK does not contain the missing Pro names, descriptions, exercises, sets, or videos. Every continuation session is individually written as static JSON using language-model coaching judgment; no Python or procedural workout generator selects movements or doses.

## Six levels

1. Foundations — 100 workouts
2. Bar Strength — 100 workouts
3. Skill Transition — 100 workouts
4. Skill Foundations — 100 workouts
5. Lever Strength — 100 workouts
6. Mastery Consolidation — 100 workouts

Validation is rolling rather than limited to stored week boundaries. Within each level's authored portion, every consecutive seven authored sessions include a basic pull-up or row, a basic push-up, a dip or support, legs, core, and at least one recovery-like session at RPE 4 or below. From Level 3 onward, every such authored window also exposes all five goals in parallel. These rolling guarantees apply to newly authored content; authentic APK workouts retain their source prescriptions and carry separate scaling guidance.

Every authored skill or advanced movement includes a listed regression for the same assigned work when the exact variation is not clean. Explicit authored regression labels are validated never to point to a harder descendant. Labels with an unknown target or a self-reference, and cyclic regression chains, are rejected. For an end-of-chain basic with no lower rung, reduce range, support, or dose. Readiness checks are submaximal and considered together; the app does not ask the athlete to select a goal path.

## Data and provenance

- `street_gymnastic_programs_levels.json` — immutable bilingual forensic export from the supplied APK.
- `curriculum/*.json` — English-only authored sessions, movement definitions, and coaching overlays.
- `generated/curriculum_v2.json` — mechanically assembled English-only 600-workout app catalog.
- `tools/assemble_curriculum.ps1` — combines source data and authored batches; it does not invent workouts.
- `tools/validate_curriculum.ps1` — verifies all 619 authored source records and the 600 app workouts: counts, IDs, provenance, prescriptions, explicit per-side doses, movement references, regression direction and prior rehearsal, uniqueness, rolling seven-authored-session fundamentals, Level 3+ five-goal exposure, low-RPE recovery, workload limits, and English-only output.

The forensic export remains bilingual because it is evidence of what was actually in the original APK. Polish is absent from the revived UI and bundled application catalog.

See the [APK extraction report](docs/EXTRACTION_REPORT.md) for exact forensic findings and the [curriculum method](docs/CURRICULUM_METHOD.md) for programming rationale, research sources, progression policy, and safety boundaries.

## Build and package

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.1.0, Gradle 8.10.2, Java 17, compile/target SDK 35, and min SDK 23.

On Windows with Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
powershell -ExecutionPolicy Bypass -File .\tools\package_dist.ps1
```

The command assembles and validates the catalog, builds and lints the debug app offline, and creates `dist/` with the APK, both JSON deliverables, documentation, and SHA-256 checksums.

## App behavior

- One assigned Today workout and a browse-only six-level catalog for future sessions
- Workout overviews with warm-up, main work, cooldown, doses, rests, safety notes, and regressions
- Persistent active workout and checked sets
- Between-set rest timer, sound cue, finish/effort flow, progress, and history
- Clear badges separating APK-authentic, reconstructed, extended, and new-level content
- Local-only progress in Android `SharedPreferences`

The app contains no advertising, analytics, account login, cloud sync, broad storage permission, or extracted private video credential. Valid historic video IDs open only as public Vimeo links.

## Permission and safety

The requester states they have written permission from the owner to modify and recreate the app and reuse its visual identity. Inclusion of the name and artwork here does not grant third parties a license to them.

New sessions are capped at RPE 8, recovery sessions at RPE 4, and no more than three hard days may appear consecutively. Authentic prescriptions remain unchanged for provenance and receive a prominent in-app scaling warning.

This is general exercise programming, not medical care. Use sound equipment and appropriate spotting. On newly authored skill work, perform the listed regression whenever control is lost; for a base movement with no lower rung or authentic legacy work, reduce range, support, assistance, repetitions, or sets conservatively. Stop for sharp pain, instability, numbness, dizziness, or unusual symptoms. Seek individualized advice when returning from injury or managing a medical condition.
