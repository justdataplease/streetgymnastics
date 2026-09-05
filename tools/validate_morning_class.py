"""Behavioral checks for the effective morning classes and corrected motion contacts."""
import json
import math
import re
from pathlib import Path
from preview_animations import Figure, Template

ROOT = Path(__file__).resolve().parents[1]
catalog = json.loads((ROOT / "generated/curriculum_v2.json").read_text(encoding="utf-8"))
asset = json.loads((ROOT / "app/src/main/assets/exercise_animations.json").read_text(encoding="utf-8"))
plan = json.loads((ROOT / "curriculum/morning_class.json").read_text(encoding="utf-8"))
movements = {m["id"]: m for m in catalog["movement_library"]}
progression = json.loads((ROOT / "curriculum/class_progression.json").read_text(encoding="utf-8"))
limits_by_level = {p["level"]: p for p in progression["levels"]}
assert list(limits_by_level) == list(range(1, 7))
budgets = [p["main_sets_including_practice"] for p in progression["levels"]]
assert budgets == sorted(budgets) and budgets[0] < budgets[-1]
changed_workouts = 0

def dose_values(value, sets):
    if not isinstance(value, str):
        return [value] * sets
    match = re.fullmatch(r"(\d+(?:-\d+)*)(/side)?", value)
    if not match:
        return [value] * sets
    values = match[1].split("-")
    assert len(values) in (1, sets), (value, sets)
    return [(v, bool(match[2])) for v in (values * sets if len(values) == 1 else values)]
new_ids = {"overhead_line", "kneeling_side_plank", "pike_handstand_hold", "wall_handstand", "handstand_toe_pull", "handstand_balance", "flag_ground_support", "assisted_tuck_flag", "tuck_flag"}
seen = set()
recovery_count = 0
for level in catalog["levels"]:
    limits = limits_by_level[level["number"]]
    counts = {"handstand": 0, "human_flag": 0}
    timetable = plan["levels"][level["number"]-1]["days"]
    for p in level["programs"]:
        assert p["title"]["en"].strip() and not p["title"]["en"].startswith("Workout "), p["id"]
        assert p["readiness"]["en"] and p["safety"]["en"], p["id"]
        if p["origin"] != "apk_authentic" and level["number"] <= 4:
            assert p["schedule"]["rpe"] <= (6 if level["number"] == 1 else 7)
        practice = p["practice"]
        day = (p["number"] - 1) % 7 + 1
        expected = timetable[day-1]["practice"]
        if p["schedule"]["rpe"] is not None and p["schedule"]["rpe"] <= 4:
            expected = []
        assert practice == expected, p["id"]
        for step in practice:
            mid, sets, reps, seconds, rest, tempo, regression = step
            assert mid in movements and mid in asset["movements"]
            assert sets == 1 and reps is None and rest == 60
            assert 0 < int(str(seconds).split("/")[0]) <= 15
            if "human_flag" in movements[mid]["patterns"]:
                assert str(seconds).endswith("/side"), p["id"]
            fallback = regression or movements[mid]["regression_id"]
            if fallback in new_ids:
                assert fallback in seen, (p["id"], mid, fallback)
            seen.add(mid)
            for goal in counts:
                if goal in movements[mid]["patterns"]:
                    counts[goal] += 1
        if p["origin"] == "apk_authentic" and day in (4, 7):
            assert p["morning_recovery"] is True
            assert p["main"] == plan["early_recovery"] and not practice
            assert p["content"]["exercises"], "Original source must stay auditable"
            recovery_count += 1
        else:
            assert not p.get("morning_recovery", False)
            if p["origin"] == "apk_authentic":
                working = p["training_content"]["exercises"]
                assert len(working) == len(p["content"]["exercises"])
                assert sum(len(e["sets"]) for e in working) + sum(s[1] for s in practice) <= limits["main_sets_including_practice"]
                assert p["training_rpe"] == (6 if level["number"] == 1 else 7)
                for e in working:
                    assert 1 <= len(e["sets"]) <= 3
                    for dose in e["sets"]:
                        assert not dose["repetitions_max"]
                        assert (dose["repetitions"] or 0) <= 12
                        assert (dose["duration_seconds"] or 0) <= 30
                        assert dose["break_seconds"] >= 60

        if p["origin"] != "apk_authentic":
            working = p["training_main"]
            original = p["main"]
            assert len(working) == len(original), p["id"]
            assert sum(s[1] for s in working + practice) <= limits["main_sets_including_practice"], p["id"]
            for source, actual in zip(original, working):
                assert len(actual) == 7 and actual[0] == source[0], p["id"]
                assert 1 <= actual[1] <= min(source[1], limits["sets_per_exercise"]), p["id"]
                assert actual[4:] == source[4:], "Rest, tempo and regression must survive the projection"
                for column in (2, 3):
                    assert dose_values(actual[column], actual[1]) == dose_values(source[column], source[1])[:actual[1]], (p["id"], source, actual)
            changed_workouts += working != original
        overlay = p.get("coaching_overlay") or p
        assert len(overlay["cooldown"]) >= 3
        assert sum(movements[s[0]]["category"] in ("static_stretch", "static_cooldown") for s in overlay["cooldown"]) >= 2

    assert min(counts.values()) >= 10, (level["number"], counts)
assert seen == new_ids
prescribed = {step[0] for level in catalog["levels"] for p in level["programs"] for step in p.get("main", [])}
assert {"full_planche", "straddle_planche", "full_front", "straddle_front", "full_back", "straddle_back", "dragon_flag"} <= prescribed
for level in catalog["levels"]:
    for p in level["programs"]:
        for step in p.get("main", []):
            if step[0] in {"full_planche", "straddle_planche", "full_front", "full_back"}:
                assert step[1] <= 3 and isinstance(step[3], int) and step[3] <= 8, p["id"]
assert recovery_count == 24, recovery_count

assert changed_workouts > 0
# These are one-sided stretches: their visible instructions must disambiguate the dose.
for mid in ("forearm_stretch", "chest_stretch", "hip_flexor_stretch", "hamstring_stretch", "quad_stretch", "calf_stretch", "supine_twist", "adductor_stretch", "ninety_ninety_hip_stretch"):
    assert "Each timed set is for one side" in movements[mid]["description"]["en"], mid

figure = Figure(asset["figure"])
cache = {}
def template(name):
    return Template(name, asset["templates"][name], asset["poses"], cache)
def distance(a, b):
    return math.hypot(a[0]-b[0], a[1]-b[1])
# Sample between keyframes too: endpoints alone missed floating feet in the old asset.
contacts = {
    "pushup": {"foot_near": (146,101), "hand_near": (96,105)},
    "incline_pushup": {"foot_near": (105,101), "hand_near": (57,82)},
    "assisted_dip": {"foot_near": (106,74), "hand_near": (100,50)},
    "low_bar_transition": {"foot_near": (108,92), "hand_near": (100,66)},
    "row": {"foot_near": (128,104), "hand_near": (80,66)},
    "high_row": {"foot_near": (125,104), "hand_near": (100,40)},
    "feet_elevated_row": {"foot_near": (145,80), "hand_near": (100,40)},
    "assisted_pullup": {"foot_near": (108,104), "hand_near": (100,58)},
    "tuck_front_row": {"foot_near": (111,104), "hand_near": (80,66)},
}
for name, pins in contacts.items():
    motion = template(name)
    for i in range(201):
        joints = motion.pose_at(i/200, figure)
        for joint, target in pins.items():
            assert distance(joints[joint], target) < 0.2, (name, i, joint, joints[joint])
# Handstand guides retain loaded contacts throughout interpolation, with straight
# supporting arms, head clearance, and an actual release from the wall.
for name in ("pike_handstand_hold", "wall_handstand", "handstand_toe_pull", "handstand_balance"):
    motion = template(name)
    for i in range(201):
        joints = motion.pose_at(i / 200, figure)
        hand = joints["hand_near"]
        assert abs(hand[1] - 105) < 0.05, (name, i, hand)
        assert abs(distance(joints["shoulder"], hand) - 26) < 0.05, (name, i)
        assert joints["head"][1] + figure.head_radius < 105, (name, i)
        if name == "pike_handstand_hold":
            assert distance(joints["foot_near"], (121, 59)) < 0.05
            assert abs(joints["hip"][0] - hand[0]) < 0.05
        elif name == "wall_handstand":
            assert abs(joints["toe_near"][0] - 150) < 0.05
        else:
            assert joints["toe_near"][0] <= 150.05
assert template("handstand_toe_pull").pose_at(0.5, figure)["toe_near"][0] < 148
side = template("kneeling_side_plank").pose_at(0, figure)
assert abs(side["shoulder"][0] - side["elbow_near"][0]) < 0.05
assert abs(side["elbow_near"][1] - 104) < 0.1
assert abs(side["hand_near"][1] - 104) < 0.1
assert side["shoulder"][1] < side["hip"][1] < side["knee_near"][1]

for name in ("dragon_flag", "dragon_negative"):
    motion = template(name)
    for i in range(101):
        j = motion.pose_at(i/100, figure)
        body = distance(j["neck"],j["foot_near"])
        assert abs(body-51) < 0.05, (name, i, body)
for i in range(101):
    j = template("planche_lean").pose_at(i/100,figure)
    assert abs(distance(j["shoulder"],j["hand_near"])-26) < 0.01
    assert abs(j["toe_near"][1]-106) < 0.1
print(f"Morning-class checks passed: 600 named sessions, {recovery_count} early recovery classes, {changed_workouts} authored workload projections, flexibility, handstand supports and sampled motion contacts.")

