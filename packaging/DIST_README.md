# Street Gymnastic Revival debug distribution

This folder is a scripted, rebuildable debug distribution of the owner-authorized Street Gymnastic revival.

## Contents

- `street-gymnastic-revival-debug.apk` — installable English-only Android debug package.
- `street-gymnastic-curriculum-v2.json` — complete English-only application catalog: six levels, 100 workouts each.
- `street-gymnastic-forensic-export.json` — untouched bilingual extraction from the supplied Base APK.
- `EXTRACTION_REPORT.md` — source hashes, exact extraction counts, and missing-Pro findings.
- `CURRICULUM_METHOD.md` — provenance, fixed-sequence design, research, and safety boundaries.
- `SHA256SUMS.txt` — SHA-256 integrity values for every payload file listed above.

## Install

With Android Debug Bridge and a connected device:

```text
adb install -r street-gymnastic-revival-debug.apk
```

The debug application ID is `co.streetgymnastic.streetgymnastic.revival.debug`. Minimum Android version is API 23.

## Catalog

The app contains exactly 600 scheduled workouts: 100 in each of six levels.

- 90 detailed Base-APK workouts labeled `apk_authentic`
- 180 newly authored sessions for included empty Pro slots labeled `reconstructed_placeholder`
- 30 newly authored Level 3 continuations labeled `llm_extension`
- 300 newly authored Level 4-6 sessions labeled `new_level`

Another 109 empty original Pro placeholders above the balanced Level 1-2 boundary remain preserved in the forensic export. No authored entry is represented as recovered Street Gymnastic PRO data.

The Today screen assigns one fixed next workout. Future cards can be inspected but not started early. Validation is rolling rather than limited to stored week boundaries: within each level's authored portion, every consecutive seven authored sessions retain basic pulling, push-ups, dips or support, legs, core, and at least one recovery-like session at RPE 4 or below. From Level 3 onward, every such window also exposes muscle-up, front lever, back lever, dragon flag, and planche/elevated-plank goals in parallel. Daily preparation and stretching are validated separately.

Every authored skill or advanced movement has an exact prescription and listed regression. Explicit authored regression labels are validated never to point to a harder descendant. Labels with an unknown target or a self-reference, and cyclic regression chains, are rejected. End-of-chain basics use range, support, or dose scaling instead. The rolling seven-session guarantees apply only to authored content; authentic APK cards keep their extracted prescriptions and use the separate conservative scaling guidance.

All new workouts are static, individually authored JSON—not runtime-random or Python-generated sessions.

The app UI and bundled catalog are English-only. The separate forensic export retains Polish alongside English only because it is an immutable record of the original APK.

## Safety

New sessions are capped at RPE 8, with workload and hard-day limits checked during packaging. Authentic prescriptions are preserved and carry a prominent scaling warning in the app.

This is general educational programming, not medical advice. Use sound equipment and appropriate spotting. Follow the listed regression for authored skill work; scale base movements without a lower rung and authentic legacy exercises conservatively when control is unavailable. Stop for sharp pain, instability, numbness, dizziness, or unusual symptoms.
