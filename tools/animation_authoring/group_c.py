# Group C: parallel-bar support, dips, L-sits, push-ups and planks
from author import *

X = 100
PB = {"joint": "hand_near", "at": [X, 50]}     # parallel bars (near rail)
MB = {"joint": "hand_near", "at": [X, 40]}     # chest-height straight bar
PL = {"joint": "hand_near", "at": [X, 96]}     # parallettes


# ---------- support ----------
pose("support_up", facing=1, torso=0, arms=[180, 180], shrug=-2, legs=[180, 180], leg_far=[182, 178], feet=180, pin=PB)
pose("support_relaxed", base="support_up", shrug=2.5)
pose("dip_half", base="support_up", torso=8, shrug=0, arms=arm(-3, 17, LEFT), legs=[176, 200], leg_far=[178, 202], feet=195)
pose("dip_bottom", base="support_up", torso=14, shrug=0, arms=arm(-5, 10, LEFT), legs=[174, 206], leg_far=[176, 208], feet=200)
template("support", 2600, [(0, "support_up"), (1, {"pose": "support_up", "shrug": -1})], apparatus=("parallel_bars",))
template("support_shrug", 1800, [(0, "support_relaxed"), (1, "support_up")], apparatus=("parallel_bars",))
template("dip", 2000, [(0, "support_up"), (0.5, "dip_half"), (0.85, "dip_bottom"), (1, "dip_bottom")], apparatus=("parallel_bars",))
template("half_dip", 1800, [(0, "support_up"), (0.85, "dip_half"), (1, "dip_half")], apparatus=("parallel_bars",))
template("eccentric_dip", 3600,
         [(0, "support_up"), (0.12, "support_up"), (0.45, "dip_half"), (0.78, "dip_bottom"), (0.88, "dip_bottom"), (1, "support_up")],
         mode="loop", apparatus=("parallel_bars", "box_tall"))

# foot-assisted support / dip: feet rest on a tall step under the bars
pose("support_assisted", facing=1, torso=0, arms=[180, 180], shrug=-1, pin=PB)
reach_leg("support_assisted", (X + 6, 74), pref=RIGHT)
pose("dip_assisted_bottom", facing=1, torso=12, arms=arm(-5, 10, LEFT), pin=PB)
reach_leg("dip_assisted_bottom", (X + 6, 74), pref=RIGHT)
template("foot_assisted_support", 2600, [(0, {"pose": "support_assisted", "shrug": 1.5}), (1, "support_assisted")], apparatus=("parallel_bars", "box_tall"))
template("assisted_dip", 2000, [(0, "support_assisted"), (0.85, "dip_assisted_bottom"), (1, "dip_assisted_bottom")], apparatus=("parallel_bars", "box_tall"))

# hand walk along the bars
pose("walk_hands_a", base="support_up", arm_near=[166, 176], arm_far=[194, 184])
pose("walk_hands_b", base="support_up", arm_near=[194, 184], arm_far=[166, 176])
template("hand_walk", 1800, [(0, "walk_hands_a"), (0.5, "support_up"), (1, "walk_hands_b")], apparatus=("parallel_bars",))

# support knee raise
pose("support_knees", base="support_up", torso=355, legs=[85, 180], leg_far=[87, 180], feet=180)
template("support_knee_raise", 1900, [(0, "support_up"), (0.85, "support_knees"), (1, "support_knees")], apparatus=("parallel_bars",))

# straight-bar support and dips
pose("sb_support", facing=1, torso=8, arms=arm(-3, 26, LEFT), shrug=-2, legs=[172, 176], leg_far=[174, 178], feet=180, pin=MB)
pose("sb_dip_bottom", facing=1, torso=18, arms=arm(6, -1, LEFT), shrug=0, legs=[168, 195], leg_far=[170, 197], feet=190, pin=MB)
template("straight_bar_support", 2600, [(0, {"pose": "sb_support", "shrug": 1}), (1, "sb_support")], apparatus=("mid_bar",))
template("straight_bar_dip", 2000, [(0, "sb_support"), (0.85, "sb_dip_bottom"), (1, "sb_dip_bottom")], apparatus=("mid_bar",))

# tuck L-sit on parallettes
pose("lsit_floor", facing=1, torso=0, arms=[180, 180], shrug=-2, legs=[100, 130], leg_far=[102, 130], pin=PL)
pose("lsit_tuck", base="lsit_floor", legs=[60, 150], leg_far=[62, 150], feet=160)
template("tuck_lsit", 2400, [(0, "lsit_floor"), (0.8, "lsit_tuck"), (1, "lsit_tuck")], apparatus=("parallettes",))

# ---------- prone helper ----------
def prone_pose(name, shoulder_y, hand_x=None, hand_y=105.0, shoulder_dx=0.0, pref=RIGHT, extra=None, protract=0.0, toe_x=146):
    """Face-down body line (head left) with toes tucked on the floor.

    The body angle is solved so the shoulder sits at shoulder_y, then the whole figure is
    shifted so the shoulder sits shoulder_dx to the right of the hand contact (hand_x, hand_y),
    and finally the arms are solved to reach that contact. Hands therefore always touch the
    floor or the box they are meant to rest on."""
    extra = dict(extra or {})
    lo, hi = 0.0, 80.0
    for _ in range(40):
        theta = (lo + hi) / 2
        spec = dict(facing=-1, torso=270 + theta, legs=[90 + theta, 90 + theta], leg_far=[92 + theta, 92 + theta],
                    feet=185 + theta, pin={"joint": "toe_near", "at": [toe_x, FLOOR]}, protract=protract)
        spec.update(extra)
        pose(name, **spec)
        sy = solved(name)["shoulder"][1]
        if sy > shoulder_y:
            lo = theta
        else:
            hi = theta
    sx = solved(name)["shoulder"][0]
    if hand_x is None:
        hand_x = sx - shoulder_dx
    shift = (hand_x + shoulder_dx) - sx
    POSES[name]["pin"] = {"joint": "toe_near", "at": [round(toe_x + shift, 1), FLOOR]}
    if "arms" not in extra and "arm_near" not in extra:
        reach_arm(name, (hand_x, hand_y), pref=pref)
    return name

# push-ups
prone_pose("push_top", 82, shoulder_dx=3)
prone_pose("push_bottom", 97, shoulder_dx=-2)
template("pushup", 1800, [(0, "push_top"), (1, "push_bottom")])
template("pause_pushup", 2600, [(0, "push_top"), (0.6, "push_bottom"), (1, "push_bottom")])
prone_pose("close_top", 82, shoulder_dx=1)
prone_pose("close_bottom", 96, shoulder_dx=-5)
template("close_pushup", 1800, [(0, "close_top"), (1, "close_bottom")])
# incline push-up: hands on a box (top y = 82)
prone_pose("incline_top", 60, hand_x=57, hand_y=82)
prone_pose("incline_bottom", 73, hand_x=57, hand_y=82, shoulder_dx=2)
template("incline_pushup", 1800, [(0, "incline_top"), (1, "incline_bottom")], apparatus=("box_left",))
# scapular push-ups (straight arms, shoulder blades glide)
prone_pose("scap_sink", 82, shoulder_dx=3, protract=-2.5)
prone_pose("scap_push", 82, shoulder_dx=3, protract=3)
template("scapular_pushup", 1800, [(0, "scap_sink"), (1, "scap_push")])
prone_pose("scap_sink_incline", 60, hand_x=57, hand_y=82, protract=-2.5)
prone_pose("scap_push_incline", 60, hand_x=57, hand_y=82, protract=3)
template("scapular_pushup_incline", 1800, [(0, "scap_sink_incline"), (1, "scap_push_incline")], apparatus=("box_left",))
# hand-release push-up (loop): top, chest down, hands lifted, hands down, press
prone_pose("chest_down", 99)
prone_pose("hands_off", 99, extra={"arms": [60, 230]})
template("hand_release_pushup", 3000,
         [(0, "push_top"), (0.3, "chest_down"), (0.45, "hands_off"), (0.6, "hands_off"), (0.72, "chest_down"), (1, "push_top")],
         mode="loop")
# plank walk (hands step forward / back alternately)
prone_pose("plank_walk_a", 82, shoulder_dx=3)
j = solved("plank_walk_a"); sx = j["shoulder"][0]
reach_arm("plank_walk_a", (sx - 9, 105), pref=RIGHT, which=("arm_near",)); reach_arm("plank_walk_a", (sx + 3, 105), pref=RIGHT, which=("arm_far",))
prone_pose("plank_walk_b", 82, shoulder_dx=3)
reach_arm("plank_walk_b", (sx + 3, 105), pref=RIGHT, which=("arm_near",)); reach_arm("plank_walk_b", (sx - 9, 105), pref=RIGHT, which=("arm_far",))
template("plank_walk", 1600, [(0, "plank_walk_a"), (0.5, "push_top"), (1, "plank_walk_b")])
# plank with single-arm front raise
prone_pose("plank_arm_raise", 82, extra={"arm_near": [275, 275]})
reach_arm("plank_arm_raise", (solved("plank_arm_raise")["shoulder"][0], 105), pref=RIGHT, which=("arm_far",))
template("plank_arm_raise", 2400, [(0, "push_top"), (0.7, "plank_arm_raise"), (1, "plank_arm_raise")])
# forearm plank, plank-up, incline plank, planche lean
prone_pose("plank_forearm", 92, extra={"arms": [180, 270]})
prone_pose("plank_up_mid", 88, extra={"arm_far": [180, 270]})
reach_arm("plank_up_mid", (solved("plank_up_mid")["shoulder"][0] - 2, 105), pref=RIGHT, which=("arm_near",))
template("plank_forearm", 3000, [(0, "plank_forearm"), (1, {"pose": "plank_forearm", "protract": 2})])
template("plank_up", 2400, [(0, "plank_forearm"), (0.5, "plank_up_mid"), (1, "push_top")])
template("incline_plank", 3000, [(0, "incline_top"), (1, {"pose": "incline_top", "protract": 2.5})], apparatus=("box_left",))
prone_pose("lean_neutral", 82, shoulder_dx=2, protract=1)
prone_pose("lean_forward", 84, protract=3, shoulder_dx=-9)
template("planche_lean", 2400, [(0, "lean_neutral"), (1, "lean_forward")])
# pseudo-planche push-up: hands beside the hips, shoulders ahead of the hands
prone_pose("pp_top", 84, shoulder_dx=-10)
prone_pose("pp_bottom", 96, shoulder_dx=-15)
template("pseudo_planche_pushup", 2000, [(0, "pp_top"), (1, "pp_bottom")])
# pike push-up
pose("pike_top", facing=-1, torso=234, legs=[150, 150], leg_far=[152, 152], feet=240, head=205, pin={"joint": "foot_near", "at": [134, ANKLE]})
reach_arm("pike_top", (84, 105), pref=RIGHT)
pose("pike_bottom", facing=-1, torso=226, legs=[147, 147], leg_far=[149, 149], feet=237, head=215, pin={"joint": "foot_near", "at": [134, ANKLE]})
reach_arm("pike_bottom", (84, 105), pref=RIGHT)
template("pike_pushup", 1900, [(0, "pike_top"), (1, "pike_bottom")])
# wall push-up
pose("wall_push_top", facing=1, torso=10, legs=[190, 190], leg_far=[192, 192], pin={"joint": "foot_near", "at": [118, ANKLE]})
reach_arm("wall_push_top", (149, 50), pref=DOWN)
pose("wall_push_bottom", facing=1, torso=20, legs=[200, 200], leg_far=[202, 202], pin={"joint": "foot_near", "at": [118, ANKLE]})
reach_arm("wall_push_bottom", (149, 50), pref=DOWN)
template("wall_pushup", 1800, [(0, "wall_push_top"), (1, "wall_push_bottom")], apparatus=("wall_right",))
