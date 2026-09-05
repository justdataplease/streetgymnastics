# Street Basketball

A separate offline basketball tab adds 48 ordered court sessions across six levels. Calisthenics retains its existing 600-workout sequence and original progress storage.

| Level | Emphasis |
| --- | --- |
| First Touch | Ball control, legal stops and close shooting |
| Both Sides | Passing, movement and both-hand layups |
| Create Space | Pace changes, catch-and-shoot and closeouts |
| Win Your Matchup | Live reads, finishing and containing a ball handler |
| Play Together | Spacing, screens, help defense and small-sided play |
| Confident Pickup | Repeatable skills, fewer turnovers and game review |

Each level has eight sessions, including an easy fourth session and a checkpoint eighth session. Each session includes preparation, explicitly prescribed drills, rest and a cooldown. The 39 drill definitions include coaching cues, equipment and easier or solo options. The static half-court diagrams illustrate spacing, not biomechanical technique.

## Practice and progression

Begin with two court sessions per week, adding a third when recovery permits. Count pickup games toward training and leave easy days between demanding sessions. On calisthenics days, use short technical court practice and reduce leg loading when tired. Effort targets are 3-6 out of 10. Attempts are capped regardless of how many shots are made.

The next uncompleted session is assigned automatically. Completed basketball sessions can be repeated without advancing the sequence twice. Future sessions can be inspected. Eighteen persistent skill checks use repeatable, measurable targets; these are original practice goals, not validated proficiency standards. Check a target after meeting it on two different days. Live-player checks require opponents or teammates; solo alternatives do not establish live proficiency. Completion of the sessions does not certify mastery.

Basketball and calisthenics have separate completion sets, active workouts and histories. Both use the existing rest-timer and effort-rating flow. Switching sports never transfers checked sets or completion into the other track. Checkpoints are stored separately and can be unchecked.

## Sources

The plan and prescriptions are originally authored, with the basic skill categories informed by [USA Basketball's coaching skill levels](https://www.usab.com/coaching/coaching-resources/skills-drills-coaching) and its [foundational ball-handling resources](https://www.usab.com/play/player-resources/skills-drills-players-foundational-ball-handling). The practice targets are not USA Basketball standards.

The in-app 3x3 summary follows [FIBA's overview](https://about.fiba.basketball/en/our-sport/3x3-basketball), consulted September 5, 2026. Local pickup scoring and restart customs vary, so players agree on rules before playing. This is a brief overview, not a substitute for competition regulations.

## Files and verification

- `app/src/main/assets/basketball_curriculum.json`: authored session order, prescriptions and drill library.
- `BasketballSkills.kt`: eighteen self-assessed targets.
- `BasketballCourtView.kt`: offline schematic court illustrations.
- `tools/validate_basketball.ps1`: content validation, also run during distribution packaging.
- `BasketballInstrumentation.kt`: framework-only Android checks for parsed content, progress isolation, resuming, Activity restoration, repeated sessions, future-session locks and checkpoint persistence. Tests restore the emulator's original progress in a finally block.

Build the app and test APK with `gradlew.bat assembleDebug assembleDebugAndroidTest lintDebug --offline`, then run on a development emulator:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w co.streetgymnastic.streetgymnastic.revival.debug.test/co.streetgymnastic.streetgymnastic.revival.BasketballInstrumentation
```

The instrumentation result must contain `PASS: Basketball instrumented checks completed.`
