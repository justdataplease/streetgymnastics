# Base APK extraction report

## Source identity

- Supplied file: `Street+Gymnastic_32-base_APKPure.apk`
- Preserved reference SHA-256: `E375F38A0A4777C6E3927B584E72090119E129664C3BBC2A39E88C0C92A0BE9A`
- Android package: `co.streetgymnastic.streetgymnastic.base`
- Version name: `32-base`
- Immutable JSON export: `street_gymnastic_programs_levels.json`
- Export SHA-256: `AC8AAF41F477050EDDF9387DF1CD214FCBE3A29608AE4D5AE1E0C34D9F5EDA3D`

The original APK binary and decoded/decompiled working directories are deliberately excluded from Git. The extracted JSON, hashes, provenance notes, and owner-authorized visual assets needed by the recreation are retained.

## Extracted inventory

| Level | Slots | Detailed in Base APK | Empty Pro placeholders |
|---:|---:|---:|---:|
| 1 | 176 | 40 | 136 |
| 2 | 133 | 30 | 103 |
| 3 | 70 | 20 | 50 |
| **Total** | **379** | **90** | **289** |

The 90 detailed records contain 534 exercise entries and 1,594 set entries. Their English and Polish source fields, ordering, IDs, repetitions, durations, breaks, and available historic video IDs are represented in the forensic export.

## What was absent

All 289 missing entries form the locked tail of their level:

- Level 1 workouts 41-176
- Level 2 workouts 31-133
- Level 3 workouts 21-70

Those records contain slot identity/order and Pro entitlement state, but no workout name, description, exercise list, set list, or video content. Inspection of available Base versions did not reveal a hidden copy of those fields. Without an owner backup of the Pro database, Pro APK, or former server response, the exact original Pro curriculum cannot be reconstructed from this Base package.

## Revival treatment

The immutable forensic export is never overwritten. A separate assembly step:

1. Projects the 90 authentic workouts into English without altering their English prescriptions.
2. Adds a clearly labeled, newly authored warm-up and stretching overlay.
3. Fills the 180 empty Pro slots inside the balanced 100-workout Level 1-3 app sequence with distinct `reconstructed_placeholder` workouts.
4. Adds 30 clearly labeled `llm_extension` workouts to bring Level 3 from 70 to 100 slots.
5. Adds 300 `new_level` workouts across Levels 4-6.

The app catalog therefore has exactly 600 complete workouts - 100 in each of six levels - while preserving an auditable boundary between extracted and newly authored material. The 109 original empty Level 1-2 placeholders above the app's 100-workout boundary remain preserved in the forensic export. Polish remains only in that separate forensic export; the revived app and its bundled catalog are English-only.
