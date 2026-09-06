# Street Gymnastic debug distribution

This folder is a scripted, rebuildable debug distribution of Street Gymnastic, including the separate Street Basketball tab.

Basketball adds 48 sessions across six levels, 45 drills, court diagrams, repeatable practice and 24 saved skill checks. Its progress is independent of calisthenics. The basketball asset is included as `street-basketball-curriculum.json`, with program notes in `BASKETBALL_PROGRAM.md`; both are covered by the checksums.

## Contents

- `street-gymnastic-debug.apk` — installable English-only Android debug package.
- `street-gymnastic-curriculum-v2.json` — complete English-only application catalog: six levels, 100 workouts each.
- `street-gymnastic-exercise-animations.json` — editable offline line-figure poses (joint angles on a fixed-proportion rig), animation templates, and exercise mappings used by the app.
- `street-gymnastic-forensic-export.json` — untouched bilingual extraction from the supplied Base APK.
- `EXTRACTION_REPORT.md` — source hashes, exact extraction counts, and missing-Pro findings.
- `CURRICULUM_METHOD.md` — provenance, fixed-sequence design, research, and safety boundaries.
- `SHA256SUMS.txt` — SHA-256 integrity values for every payload file listed above.

## Install

With Android Debug Bridge and a connected device:

```text
adb install -r street-gymnastic-debug.apk
```

The debug application ID is `co.streetgymnastic.streetgymnastic.revival.debug`. Minimum Android version is API 23.

## Training sequence

The calisthenics track contains exactly 600 scheduled workouts: 100 in each of six levels. The basketball track contains 48 additional court sessions.

Available detailed workouts and newly written sessions are combined into one consistent sequence in the app. Source classifications exist only in the catalog and technical reports for auditing; they are not displayed as product labels. The supplied package did not contain the unavailable paid workout details, and the new sessions do not claim to recover them.

The Today screen assigns one fixed next workout. Future cards can be inspected but not started early. Validation is rolling rather than limited to stored week boundaries: within each level's authored portion, every consecutive seven authored sessions retain basic pulling, push-ups, dips or support, legs, core, and at least one recovery-like session at RPE 4 or below. From Level 3 onward, every such window also exposes muscle-up, front lever, back lever, dragon flag, and planche/elevated-plank goals in parallel. Daily preparation and stretching are validated separately.

Every authored skill or advanced movement has an exact prescription and listed regression. Explicit authored regression labels are validated never to point to a harder descendant. Labels with an unknown target or a self-reference, and cyclic regression chains, are rejected. End-of-chain basics use range, support, or dose scaling instead. Existing fixed prescriptions use the same conservative scaling guidance shown throughout the app.

All new workouts are static, individually authored JSON—not runtime-random or Python-generated sessions.

The app UI and bundled catalog are English-only. The separate technical export retains Polish alongside English only to record the supplied source data accurately.

Exercise demonstrations are drawn locally by an articulated line-figure rig from angle-based keyframes in JSON. The app contains no video playback or outbound video links; unavailable videos are not used.

## Safety

New sessions are capped at RPE 8, with workload and hard-day limits checked during packaging. The same scaling warning is shown on every workout.

This is general educational programming, not medical advice. Use sound equipment and appropriate spotting. Follow the listed regression for skill work; scale movements without a lower rung conservatively when control is unavailable. Stop for sharp pain, instability, numbness, dizziness, or unusual symptoms.

Settings includes optional Firebase email/password login, account creation, password reset and sign-out. Training stays available offline. Workout progress remains on the device, shared by accounts using this installation; cloud sync is not included. The basketball plan emphasizes one-on-one handles, pace, counters and short court runs while retaining shooting, finishing, defense and team-play foundations.
