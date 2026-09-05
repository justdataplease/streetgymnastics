# Street Gymnastic Revival curriculum method

## Morning-class revision (September 2026)

The intended audience is recreational athletes building from fundamentals through intermediate strength, with the complete advanced skill ladders retained. Foundations effort is capped at RPE 6, Levels 2-4 at RPE 7, and later authored sessions at RPE 8. Planche, straddle and full-lever variations remain in the later progression as short practice ceilings, with easier regressions. The most advanced full-planche/full-lever exposures use at most three short sets; completing a level never requires mastery of those shapes. Early standalone muscle-ups use assistance, lever raises use tuck holds, and windshield-wiper work uses knee raises. Progress follows control and tolerance, with no deadline.


The app now implements the requested class-style daily practice, with no goal selection or deadline for any skill. Front lever, back lever, muscle-up, human flag and handstand are the main long-term skills; planche and dragon flag remain supporting goals. The six levels specify practice opportunities and easier options, not readiness guarantees.

The source-preservation statements below describe the audit record. The effective app prescriptions now differ from the 90 original sessions: the original `content` is retained unchanged, while 66 strength sessions use a conservative `training_content` projection and 24 use an authored recovery routine on session days four and seven. Strength and skill practice together are capped at 20 sets, with at most three sets per exercise, 12 repetitions or 30-second holds. Former max sets are capped at eight repetitions and all targets remain ceilings at RPE 7 or below. Set allocation retains every exercise before adding second and third sets. Rest is at least 60 seconds and is also offered between exercises. Existing authored sessions retain their main prescriptions and recovery waves.

An explicit seven-session timetable adds one brief human-flag practice set on days one and five and one handstand practice set on days three and six. Existing RPE 4-or-lower recovery sessions receive no extra skill work. These are easy technique exposures at RPE 5 or below, with supported regressions; they are not extra maximal skill attempts. The timetable is written in JSON and mechanically expanded during assembly. This exception to the original per-session-only authoring format is intentional: the daily class remains deterministic, inspectable and shared by everyone.

Handstand practice moves from overhead alignment to supported box pike, chest-to-wall control, light toe releases and brief balance attempts. Human-flag practice moves from kneeling side plank to grounded split-grip support, foot-assisted tuck support and brief tuck practice. Long-leg/full flags are a later coaching progression, not a required endpoint of the 600-session catalog. A controlled exit is a prerequisite for inversion and unsupported work.

This is an evidence-informed coaching plan, not a scientifically validated 600-day protocol. General resistance-training evidence supports consistent, individualized loading; specific skill ladders and the timetable are coaching judgments. Daily recovery practice supports the habit without prescribing hard daily loading.

Additional coaching references: [GMB handstand progression](https://gmb.io/handstand/), [GMB wall-to-balance practice](https://gmb.io/freestanding-handstand/), and [School of Calisthenics on human-flag pushing/pulling preparation](https://www.schoolofcalisthenics.com/blog/home-gym-workout).

## What the APK actually contains

The supplied Base APK defines 379 workout slots across three levels, but complete exercise details exist for only 90: Level 1 workouts 1-40, Level 2 workouts 1-30, and Level 3 workouts 1-20. The remaining 289 records are entitlement placeholders. They contain identifiers and ordering metadata, but no hidden names, descriptions, exercises, sets, or videos.

The immutable extraction is `street_gymnastic_programs_levels.json`, protected by a fixed SHA-256 check. It retains the original English and Polish fields as forensic evidence. The bundled revival catalog is an English-only projection: authentic English prescriptions are preserved while Polish fields are removed before packaging.

## Balanced 600-workout catalog

The app contains exactly 600 concrete workouts: 100 in each of six levels.

- 90 `apk_authentic` workouts with details preserved from the Base APK
- 180 `reconstructed_placeholder` workouts newly authored for included empty Pro slots
- 30 `llm_extension` workouts filling Level 3 slots 71-100
- 300 `new_level` workouts across Levels 4-6

The original Level 1 and Level 2 contain 109 empty placeholder slots above the app's 100-workout-per-level boundary. They remain in the forensic export, and the corresponding authored research drafts remain auditable in the repository, but they are deliberately outside the balanced app sequence. No extracted record is destroyed or misrepresented.

Nothing labeled reconstructed, extended, or new is claimed to be recovered PRO content.

Every new workout is written as static JSON with explicit movements, sets, repetitions or hold times, rests, tempo, warm-up, and cooldown. Unilateral repetitions and one-sided hold durations say `/side` directly in the JSON; a plain 90/90 hip-switch repetition is explicitly defined as one alternating arrival. Skill and advanced movements include an explicit regression; end-of-chain basics without a lower rung are scaled through range, support, or dose. PowerShell scripts only merge and validate the authored records; they do not generate workout content. The app also has no runtime-random workout generator.

Release validation covers all 619 authored source records, including the 109 archived research drafts, as well as the 600-workout app catalog. It also requires skill fallbacks to be rehearsed earlier in the fixed sequence before they are first relied upon.

## One guided daily sequence

The Today screen assigns the first unfinished workout in catalog order. Future sessions can be read in the level browser, but they cannot be started early. Completing the assigned card advances the sequence.

The five goals develop in parallel rather than as separate user-selected paths. Validation uses rolling windows, not only stored week boundaries. Within each level's authored portion, every consecutive seven authored sessions include a basic pull-up or row, a basic push-up, a dip or support, legs, core, and at least one recovery-like day capped at RPE 4. From Level 3 onward, every such authored window also exposes muscle-up, front lever, back lever, dragon flag, and planche/elevated-plank work in parallel. Complete stored authored weeks are checked as well. Preparation and stretching remain daily requirements.

Each newly authored card prescribes specific variations. If a skill or advanced variation cannot be performed with clean control, its listed regression replaces it for the same assigned sets. Explicit authored regression labels are validated never to point to a harder descendant; unknown movements, self-references, and cyclic regression chains are also rejected. For an end-of-chain basic with no lower rung, range, support, or dose is reduced. Authentic APK workouts retain their extracted prescription and instead show prominent conservative scaling guidance. The rolling seven-session guarantees above apply only to authored content, not to those source-faithful authentic prescriptions. Readiness checks are considered together as feedback for the continuing sequence; they are not permission to abandon one goal for another.

## Six-level progression

1. **Foundations** — clean pulling, pushing, squatting, hinging, trunk control, active hangs, support, wrists, shoulders, hips, and ankles.
2. **Bar Strength** — stronger pulls, pull-over control, high pulling, straight-bar support, dips, and assisted transitions.
3. **Skill Transition** — strict muscle-up development plus structured lever, dragon-flag, and planche entry work.
4. **Skill Control** — reliable tuck shapes, joint-tolerant straight-arm strength, and continued basics.
5. **Intermediate Strength** — longer lever shapes, cleaner transitions, stronger dragon-flag work, and deeper planche control.
6. **Confident Calisthenics** — successive advanced-practice waves, collective checks, deloads, and permanent fundamental maintenance.

Each level contains 100 sessions. Difficulty waves upward gradually, then falls through recovery and consolidation cards before another build.

## Skill ladders

- **Muscle-up:** active hang and rows -> pull-ups and dips -> high pulls and straight-bar support -> assisted transitions and negatives -> low-kip/strict practice -> clean repeatable singles and density work.
- **Front lever:** hollow body and scapular control -> tuck rows and holds -> advanced-tuck and asymmetric shapes -> longer-lever or straddle practice -> brief full-shape exposures with the listed regression.
- **Back lever:** support and shoulder preparation -> controlled skin-the-cat/German-hang range -> tuck holds -> advanced-tuck and asymmetric shapes -> longer-lever practice while shoulders remain comfortable.
- **Dragon flag:** posterior pelvic tilt and hollow holds -> reverse crunch and hip lift -> controlled negatives -> bent-knee holds and repetitions -> straighter-lever negatives and full-quality repetitions.
- **Planche/elevated plank:** incline plank and scapular push-up -> wrist-tolerant leans -> pseudo-planche push-ups and tuck support -> advanced-tuck and longer leans -> brief high-skill exposures with the listed regression.

These ladders are coaching judgments, not scientifically validated promises or deadlines.

## Daily variation without daily maximal loading

A typical seven-session wave rotates emphasis:

1. Pulling and front-lever preparation
2. Legs and dragon-flag/core work
3. Pushing and planche preparation
4. Active recovery and mobility
5. Muscle-up and back-lever preparation
6. Moderate mixed fundamentals
7. Easy aerobic activity, stretching, or low-fatigue technique

Titles, grips, stances, tempos, pauses, ordering, accessories, mobility focus, and session formats vary to reduce monotony. The basics deliberately recur. Recovery cards are real scheduled activity, not hard sessions with softer names.

This follows the practical direction of the 2026 ACSM position stand: consistency, regular training of major muscle groups, individualization, and useful bodyweight resistance. The review also reports that constant failure training and elaborate periodization are unnecessary for most healthy adults, so authored sessions preserve repetitions in reserve and use straightforward loading waves.

Sources:

- [ACSM 2026 resistance-training update](https://acsm.org/resistance-training-guidelines-update-2026/)
- [ACSM resistance-training position stand](https://pmc.ncbi.nlm.nih.gov/articles/PMC12965823/)
- [WHO physical-activity recommendations](https://www.who.int/initiatives/behealthy/physical-activity)

## Preparation and stretching

Every daily card starts with movement-specific preparation and ends with calm stretching or down-regulation. Authentic APK workouts receive a clearly labeled authored overlay, so additions are never confused with recovered content. Recovery days use longer shoulder, wrist, hip, and ankle mobility.

A gymnastics-specific systematic review examined dynamic and static warm-up strategies. A separate meta-analysis found that static stretching improves flexibility while showing no additional flexibility benefit beyond modest cumulative doses, supporting repeatable cooldown doses instead of marathon stretching sessions.

Sources:

- [Gymnastics warm-up systematic review and meta-analysis](https://doi.org/10.52082/jssm.2024.156)
- [Static-stretching dose systematic review and meta-analysis](https://pubmed.ncbi.nlm.nih.gov/39614059/)

## Load and readiness rules

- New sessions never exceed target RPE 8.
- Recovery, restore, and mobility days never exceed RPE 4.
- No authored sequence contains more than three consecutive RPE 7+ sessions.
- At RPE 8, main work is capped at 20 total sets and 10 advanced-category sets.
- Automatic between-set rest cannot consume more than 85% of the scheduled time.
- Skill work stops before shape, symmetry, speed, or joint position breaks down.
- Progression normally changes repetitions, hold time, assistance, or lever length one variable at a time.
- German-hang and inversion range is never forced.

The 90 authentic workouts remain a prescription-faithful English projection of the extracted data and therefore do not inherit the new-session caps. The app places a prominent warning on their overview and active screens: treat “max” as a technical stop, keep two or three repetitions in reserve, cap effort near RPE 8, reduce range, add assistance, scale sets/reps, and omit behind-the-neck, head-banger, or painful movements.

The app cannot assess apparatus stability, technique quality, injury history, or medical readiness. Its schedule is a training map, not a guarantee that a harder variation is appropriate.

## Injury-aware boundaries

Advanced calisthenics makes the wrist, elbow, and shoulder weight-bearing and exposes them to repeated loading. The catalog includes wrist/scapular preparation, regression chains, controlled range, and movement-specific safety notes. Use sound equipment and appropriate spotting. Stop for sharp pain, instability, numbness, dizziness, unusual symptoms, or loss of controlled range.

These safeguards are consistent with reviews describing upper-extremity and overuse injury patterns in gymnastics. They do not replace assessment by a qualified coach or clinician, particularly after injury or with a medical condition.

Sources:

- [Common upper-extremity gymnastics injuries and return-to-play protocols](https://pmc.ncbi.nlm.nih.gov/articles/PMC12088353/)
- [Systematic review of artistic-gymnastics injury prevention](https://pmc.ncbi.nlm.nih.gov/articles/PMC12652705/)

World Gymnastics' Code of Points was used only to cross-check recognized strength/hold terminology. Competitive element values are not used as recreational readiness standards or completion promises.

- [World Gymnastics Men's Artistic Code of Points 2025-2028](https://www.gymnastics.sport/publicdir/rules/files/en_1.1%20-%20MAG%20CoP%202025-2028.pdf)
