# Street Basketball

A separate offline basketball tab adds 48 ordered court sessions across six levels. Calisthenics retains its existing 600-workout sequence and original progress storage.

| Level | Emphasis |
| --- | --- |
| First Touch | Ball control, legal stops and close shooting |
| Both Sides | Running ball control, first-step attacks, passing and both-hand layups |
| Create Space | Pace changes, catch-and-shoot and closeouts |
| Win Your Matchup | Live reads, finishing and containing a ball handler |
| Play Together | One-on-one counters and pace, transferred into small-sided play |
| Confident Pickup | Live matchups, running control, repeatable shots and pickup decisions |

Each level has eight sessions, including an easy fourth session and a checkpoint eighth session. Each session includes preparation, explicitly prescribed drills, rest and a cooldown. The 45 drill definitions include coaching cues, equipment and easier or solo options. The static half-court diagrams illustrate spacing, not biomechanical technique.

## Practice and progression

Begin with two court sessions per week, adding a third when recovery permits. Count pickup games toward training and leave easy days between demanding sessions. On calisthenics days, use short technical court practice and reduce leg loading when tired. Effort targets are 3-6 out of 10. Attempts are capped regardless of how many shots are made.

The next uncompleted session is assigned automatically. Completed basketball sessions can be repeated without advancing the sequence twice. Future sessions can be inspected. Twenty-four persistent skill checks use repeatable, measurable targets; these are original practice goals, not validated proficiency standards. Check a target after meeting it on two different days. Live-player checks require opponents or teammates; solo alternatives do not establish live proficiency. Completion of the sessions does not certify mastery.

Basketball and calisthenics have separate completion sets, active workouts and histories. Both use the existing rest-timer and effort-rating flow. Switching sports never transfers checked sets or completion into the other track. Checkpoints are stored separately and can be unchecked.

## Sources

The plan and prescriptions are originally authored, with the basic skill categories informed by [USA Basketball's coaching skill levels](https://www.usab.com/coaching/coaching-resources/skills-drills-coaching) and its [foundational ball-handling resources](https://www.usab.com/play/player-resources/skills-drills-players-foundational-ball-handling). The practice targets are not USA Basketball standards.

The in-app 3x3 summary follows [FIBA's overview](https://about.fiba.basketball/en/our-sport/3x3-basketball), consulted September 5, 2026. Local pickup scoring and restart customs vary, so players agree on rules before playing. This is a brief overview, not a substitute for competition regulations.

## Files and verification

- `app/src/main/assets/basketball_curriculum.json`: authored session order, prescriptions and drill library.
- `BasketballSkills.kt`: twenty-four self-assessed targets.
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

## One-on-one emphasis (1.3)

Six added drills cover controlled court runs and stops, running dribbles with either hand, hesitation bursts, first-step rehearsals, one-counter attacks and three-dribble duels. Selected repeated blocks are replaced, retaining all 39 original movements across the plan, shooting, finishing, passing, defense, easy sessions and cooldowns. Six extra checkpoint blocks rehearse six new measurable targets. The original eighteen targets retain their saved keys.

Early levels use walk/jog and cooperative reads. Level 4 adds live possessions from a small offensive advantage; Levels 5 and 6 progress to even face-to-face starts and different defenders once control is consistent. Alternate offense and defense, count shot quality as well as makes, and keep possession and dribble limits. Court runs include a gradual stop and walk-back recovery, with speed and distance reduced when control drops. These are authored practice goals, not a guarantee of winning difficult games.

The final level retains team-play transfer while emphasizing one-on-one readiness. Calisthenics remains the app's full 600-workout track. The basketball navigation icon now depicts a hoop, and the home court diagram shows an attacker creating space against one defender.

The instrumentation also checks upgrade migration and saved settings. To exercise real Firebase signup, invalid-password handling, login, session restoration and sign-out on a signed-out development emulator, add `-e liveAuth true` to the instrumentation command. It creates a unique `example.invalid` account and deletes it in cleanup; it never sends email.
