"""Authoring helpers for exercise_animations.json (schema 2).

Poses are described with absolute joint angles plus small inverse-kinematics helpers
(arm/leg/reach_arm/reach_leg) so that hands and feet land exactly on bars, boxes and the
floor. Run build.py in this folder to regenerate the JSON, then tools/preview_animations.py
to review the result. The JSON remains the asset the app reads; these scripts are its source.
"""
import json, math, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "exercise_animations.json")

# ---- rig constants (must match "figure" block) ----
HEAD_R, NECK, TORSO, UPPER, FORE, THIGH, SHIN, FOOT = 4.5, 3.0, 20.0, 13.5, 12.5, 16.0, 15.0, 5.0
FLOOR = 106.0
ANKLE = 104.0

def d(a):
    r = math.radians(a); return (math.sin(r), -math.cos(r))

def ang(vx, vy):
    """absolute angle (0 up, 90 right) of vector"""
    return (math.degrees(math.atan2(vx, -vy))) % 360

def ik(dx, dy, a, b, pref=(1, 0)):
    """Two-bone IK. Offset (dx,dy) root->end, lengths a,b. Returns (angle1, angle2).
    Of the two solutions, picks the one whose middle joint lies furthest along the
    preferred screen direction `pref` (x right, y down)."""
    dist = math.hypot(dx, dy)
    if dist < 1e-6:
        return (0.0, 180.0)
    dist = min(dist, a + b - 0.01)
    dist = max(dist, abs(a - b) + 0.01)
    cos_r = (a*a + dist*dist - b*b) / (2*a*dist)
    cos_r = max(-1, min(1, cos_r))
    r = math.degrees(math.acos(cos_r))
    base = ang(dx, dy)
    best = None
    for side in (1, -1):
        a1 = base + side * r
        jx, jy = d(a1)[0]*a, d(a1)[1]*a
        a2 = ang(dx - jx, dy - jy)
        score = jx * pref[0] + jy * pref[1]
        if best is None or score > best[0]:
            best = (score, (round(a1 % 360, 1), round(a2 % 360, 1)))
    return best[1]

UP, DOWN, LEFT, RIGHT = (0, -1), (0, 1), (-1, 0), (1, 0)

def arm(dx, dy, pref=RIGHT):
    return list(ik(dx, dy, UPPER, FORE, pref))

def leg(dx, dy, pref=RIGHT):
    return list(ik(dx, dy, THIGH, SHIN, pref))

sys.path.insert(0, os.path.join(ROOT, "tools"))
import preview_animations as pv

def solved(name):
    """Joint positions of an authored pose (canvas units)."""
    cache = {}
    pose = pv.Pose.from_json(name, POSES, cache)
    return pv.solve(pose, pv.Figure({"segments": {"head_radius": HEAD_R, "neck": NECK, "torso": TORSO,
        "upper_arm": UPPER, "forearm": FORE, "thigh": THIGH, "shin": SHIN, "foot": FOOT}}))

def reach_arm(name, target, pref=RIGHT, which=("arm_near", "arm_far")):
    """Solve arm angles so the hand lands on target (closed chain after the pin is applied)."""
    j = solved(name)
    sx, sy = j["shoulder"]
    angles = arm(target[0] - sx, target[1] - sy, pref)
    for w in which:
        POSES[name][w] = angles
    return angles

def reach_leg(name, target, pref=RIGHT, which=("leg_near", "leg_far")):
    j = solved(name)
    hx, hy = j["hip"]
    angles = leg(target[0] - hx, target[1] - hy, pref)
    for w in which:
        POSES[name][w] = angles
    return angles

POSES = {}
def pose(name, **kw):
    for k in ("arm_near","arm_far","arms","leg_near","leg_far","legs"):
        if k in kw:
            kw[k] = [round(float(v) % 360, 1) for v in kw[k]]
    POSES[name] = kw
    return name

TEMPLATES = {}
def template(name, duration, keyframes, mode="ping_pong", apparatus=("floor",), easing="smooth", reduced=0.5):
    frames = []
    for at, p in keyframes:
        if isinstance(p, str):
            frames.append({"at": at, "pose": p})
        else:
            fr = {"at": at}; fr.update(p); frames.append(fr)
    scene = ["floor"] + [a for a in apparatus if a not in ("floor", "mat")]
    TEMPLATES[name] = {"duration_ms": duration, "mode": mode, "easing": easing,
                       "reduced_motion_frame": reduced, "apparatus": scene, "keyframes": frames}
    return name

APPARATUS = {
    # Minimal scene: every template gets the single grey floor line; apparatus is simple lines and boxes.
    "floor": [{"type": "line", "role": "floor", "values": [8, FLOOR, 192, FLOOR]}],
    "high_bar": [
        {"type": "line", "values": [34, 12, 166, 12]},
        {"type": "line", "role": "floor", "values": [38, 12, 38, FLOOR]},
        {"type": "line", "role": "floor", "values": [162, 12, 162, FLOOR]},
    ],
    "mid_bar": [
        {"type": "line", "values": [34, 40, 166, 40]},
        {"type": "line", "role": "floor", "values": [38, 40, 38, FLOOR]},
        {"type": "line", "role": "floor", "values": [162, 40, 162, FLOOR]},
    ],
    "low_bar": [
        {"type": "line", "values": [42, 66, 118, 66]},
        {"type": "line", "role": "floor", "values": [46, 66, 46, FLOOR]},
        {"type": "line", "role": "floor", "values": [114, 66, 114, FLOOR]},
    ],
    "parallel_bars": [
        {"type": "line", "values": [50, 50, 150, 50]},
        {"type": "line", "role": "floor", "values": [58, 50, 58, FLOOR]},
        {"type": "line", "role": "floor", "values": [142, 50, 142, FLOOR]},
    ],
    "parallettes": [
        {"type": "line", "values": [86, 96, 114, 96]},
        {"type": "line", "role": "floor", "values": [90, 96, 90, FLOOR]},
        {"type": "line", "role": "floor", "values": [110, 96, 110, FLOOR]},
    ],
    "box_left": [{"type": "round_rect", "values": [40, 82, 74, FLOOR], "filled": True}],
    "box_right": [{"type": "round_rect", "values": [118, 80, 154, FLOOR], "filled": True}],
    "box_under": [{"type": "round_rect", "values": [84, 92, 116, FLOOR], "filled": True}],
    "box_tall": [{"type": "round_rect", "values": [86, 74, 114, FLOOR], "filled": True}],
    "wall_right": [{"type": "line", "values": [150, 6, 150, FLOOR]}],
    "post_left": [{"type": "line", "values": [62, 18, 62, FLOOR]}],
    "post_right": [{"type": "line", "values": [138, 18, 138, FLOOR]}],
    "anchor_left": [{"type": "line", "values": [40, 80, 40, FLOOR]},
                    {"type": "circle", "values": [40, 78, 2.5]}],
}

PALETTE = {"background": "#E3F2FD", "border": "#BBDEFB", "figure": "#0D47A1",
           "accent": "#EF5350", "apparatus": "#1565C0", "floor": "#90A4AE"}

def dump(movements, aliases, heuristics, category_defaults, equipment_defaults, fallback):
    root = {
        "schema_version": 2,
        "coordinate_system": "canvas_units_top_left",
        "canvas": {"width": 200, "height": 120},
        "figure": {"segments": {"head_radius": HEAD_R, "neck": NECK, "torso": TORSO, "upper_arm": UPPER,
                                "forearm": FORE, "thigh": THIGH, "shin": SHIN, "foot": FOOT}},
        "angle_convention": "degrees, 0 = up, 90 = right, 180 = down, 270 = left; facing 1 = front side to the right of the torso axis",
        "notice": "Simple offline movement cues. Written coaching and safety instructions remain authoritative.",
        "palette": PALETTE,
        "apparatus": APPARATUS,
        "poses": POSES,
        "templates": TEMPLATES,
        "movements": movements,
        "aliases": aliases,
        "name_heuristics": heuristics,
        "category_defaults": category_defaults,
        "equipment_defaults": equipment_defaults,
        "fallback": fallback,
    }
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(root, f, indent=1)
        f.write("\n")
    print("templates", len(TEMPLATES), "poses", len(POSES), "movements", len(movements))
