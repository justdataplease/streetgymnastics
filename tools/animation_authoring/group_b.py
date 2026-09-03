# Group B: bar work - hangs, pull-ups, rows, muscle-ups, levers, hanging core
from author import *

X = 100
HB = {"joint": "hand_near", "at": [X, 12]}     # high bar
MB = {"joint": "hand_near", "at": [X, 40]}     # mid (chest-height) bar

# ---------- hangs ----------
pose("hang_relaxed", facing=1, torso=0, arms=[0, 0], legs=[180, 180], leg_far=[182, 178], feet=180, shrug=2, pin=HB)
pose("hang_active", base="hang_relaxed", shrug=-2.5)
template("hang", 2000, [(0, "hang_relaxed"), (1, "hang_active")], apparatus=("high_bar",))
template("scapular_pull", 1800, [(0, "hang_relaxed"), (0.8, "hang_active"), (1, "hang_active")], apparatus=("high_bar",))

# hollow <-> arch swing
pose("swing_hollow", base="hang_active", torso=6, legs=[168, 172], leg_far=[170, 174], spine=-2)
pose("swing_arch", base="hang_active", torso=352, legs=[196, 200], leg_far=[198, 202], spine=2.5)
template("hollow_arch_swing", 1600, [(0, "swing_arch"), (1, "swing_hollow")], apparatus=("high_bar",))

# foot-assisted hang on a chest-height bar: knees bent, feet on the floor
pose("hang_assisted", facing=1, torso=0, arms=[0, 0], shrug=1, pin=MB)
reach_leg("hang_assisted", (X + 8, ANKLE), pref=RIGHT)
POSES["hang_assisted"]["leg_far"] = [POSES["hang_assisted"]["leg_near"][0] + 3, POSES["hang_assisted"]["leg_near"][1] - 3]
pose("hang_assisted_active", base="hang_assisted", shrug=-2)
reach_leg("hang_assisted_active", (X + 8, ANKLE), pref=RIGHT)
template("foot_assisted_hang", 2200, [(0, "hang_assisted"), (1, "hang_assisted_active")], apparatus=("mid_bar",))

# ---------- pull-ups ----------
pose("pull_mid", base="hang_active", shrug=-1, torso=354, arms=arm(-3, 8, RIGHT), legs=[178, 196], leg_far=[181, 199])
pose("pull_top", base="hang_active", shrug=0, torso=352, arms=arm(-2, -4, RIGHT), legs=[176, 200], leg_far=[180, 204])
pose("pull_chest", base="hang_active", shrug=0, torso=345, arms=arm(-6, -9, RIGHT), legs=[172, 204], leg_far=[176, 208])
template("pullup", 2000, [(0, "hang_active"), (0.5, "pull_mid"), (0.85, "pull_top"), (1, "pull_top")], apparatus=("high_bar",))
template("chest_to_bar", 2000, [(0, "hang_active"), (0.5, "pull_mid"), (0.85, "pull_chest"), (1, "pull_chest")], apparatus=("high_bar",))
template("eccentric_pullup", 3600,
         [(0, "pull_top"), (0.12, "pull_top"), (0.45, "pull_mid"), (0.78, "hang_active"), (0.88, "hang_active"), (1, "pull_top")],
         mode="loop", apparatus=("high_bar", "box_under"))
# head bangers: chin at bar, push away and pull back in
pose("banger_out", base="pull_top", torso=340, arms=arm(-14, 0, DOWN), legs=[168, 200], leg_far=[172, 204])
template("head_banger", 1300, [(0, "pull_top"), (1, "banger_out")], apparatus=("high_bar",))

# foot-assisted pull-up on a chest-height bar
pose("assist_pull_bottom", base="hang_assisted", shrug=-1)
pose("assist_pull_top", facing=1, torso=352, arms=arm(-2, -4, RIGHT), shrug=0, pin=MB)
reach_leg("assist_pull_top", (X + 8, ANKLE), pref=RIGHT)
template("assisted_pullup", 2000, [(0, "assist_pull_bottom"), (0.85, "assist_pull_top"), (1, "assist_pull_top")], apparatus=("mid_bar",))

# jump to hang (loop): stand under the bar, dip, jump and catch, half pull-up hold, hang, land
pose("jump_stand", facing=1, torso=0, arms=[5, 5], arm_far=[8, 8], legs=[180, 180], leg_far=[183, 177],
     pin={"joint": "foot_near", "at": [X, ANKLE]})
pose("jump_dip", base="jump_stand", torso=12, arms=[215, 215], arm_far=[220, 220], legs=leg(-4, 24, RIGHT), leg_far=leg(-5, 24, RIGHT))
pose("jump_catch", base="hang_active", shrug=0, arms=arm(-3, 6, RIGHT), legs=[172, 188], leg_far=[176, 192])
pose("jump_hold", base="pull_mid")
pose("jump_hang", base="hang_active")
template("jump_to_hang", 3400,
         [(0, "jump_stand"), (0.15, "jump_dip"), (0.32, "jump_catch"), (0.5, "jump_hold"), (0.68, "jump_hang"), (0.85, "jump_stand"), (1, "jump_stand")],
         mode="loop", apparatus=("high_bar",))

# ---------- rows ----------
LB = {"joint": "hand_near", "at": [80, 66]}
pose("row_bottom", facing=1, torso=0, arms=[0, 0], legs=[0, 0], pin=LB)
def _row_body(name, shoulder_y, ankle_x, bar):
    """Straight body from the shoulder down to the heels on the floor."""
    j = solved(name)
    sx, sy = j["shoulder"]
    dx, dy = ankle_x - sx, ANKLE - sy
    a = ang(dx, dy)
    POSES[name]["torso"] = round((a + 180) % 360, 1)   # hip -> neck points away from the feet
    POSES[name]["legs"] = [round(a, 1), round(a, 1)]
    POSES[name]["leg_far"] = [round(a + 2, 1), round(a + 2, 1)]
    POSES[name]["feet"] = round((a + 90) % 360, 1)
pose("row_bottom", facing=1, torso=300, arms=[0, 0], legs=[120, 120], pin=LB)
_row_body("row_bottom", 92, 128, LB)
pose("row_top", facing=1, torso=300, arms=arm(2, -6, DOWN), legs=[120, 120], pin=LB)
_row_body("row_top", 72, 128, LB)
template("row", 2000, [(0, "row_bottom"), (0.85, "row_top"), (1, "row_top")], apparatus=("low_bar",))

# high-bar body row (chest-height bar, more upright)
pose("hrow_bottom", facing=1, torso=300, arms=[0, 0], legs=[120, 120], pin=MB)
_row_body("hrow_bottom", 66, 122, MB)
pose("hrow_top", facing=1, torso=300, arms=arm(2, -6, DOWN), legs=[120, 120], pin=MB)
_row_body("hrow_top", 44, 122, MB)
template("high_row", 2000, [(0, "hrow_bottom"), (0.85, "hrow_top"), (1, "hrow_top")], apparatus=("mid_bar",))

# feet-elevated row: heels on a box, body near horizontal
def _row_body_box(name):
    j = solved(name)
    sx, sy = j["shoulder"]
    dx, dy = 134 - sx, 78 - sy
    a = ang(dx, dy)
    POSES[name]["torso"] = round((a + 180) % 360, 1)
    POSES[name]["legs"] = [round(a, 1), round(a, 1)]
    POSES[name]["leg_far"] = [round(a + 2, 1), round(a + 2, 1)]
    POSES[name]["feet"] = round((a + 90) % 360, 1)
pose("erow_bottom", facing=1, torso=270, arms=[0, 0], legs=[90, 90], pin=MB)
_row_body_box("erow_bottom")
pose("erow_top", facing=1, torso=270, arms=arm(2, -6, DOWN), legs=[90, 90], pin=MB)
_row_body_box("erow_top")
template("feet_elevated_row", 2000, [(0, "erow_bottom"), (0.85, "erow_top"), (1, "erow_top")], apparatus=("mid_bar", "box_right"))

# foot-assisted tuck front-lever row under a chest-height bar
pose("tuck_row_low", facing=1, torso=318, arms=[0, 0], legs=[35, 178], leg_far=[37, 180], pin=MB)
pose("tuck_row_high", facing=1, torso=272, arms=arm(4, -8, DOWN), legs=[325, 215], leg_far=[327, 217], pin=MB)
template("tuck_front_row", 2200, [(0, "tuck_row_low"), (0.85, "tuck_row_high"), (1, "tuck_row_high")], apparatus=("mid_bar",))

# ---------- hanging core ----------
pose("knee_raise_up", base="hang_active", torso=352, legs=[90, 180], leg_far=[92, 180], feet=180)
pose("knee_hip_raise", base="hang_active", torso=345, legs=[50, 150], leg_far=[52, 150], feet=150)
pose("leg_raise_up", base="hang_active", torso=350, legs=[92, 92], leg_far=[94, 94], feet=100)
pose("toes_bar", base="hang_active", torso=335, legs=[22, 22], leg_far=[24, 24], feet=30)
pose("wiper_left", base="hang_active", torso=340, legs=[10, 10], leg_far=[12, 12], feet=20)
pose("wiper_right", base="hang_active", torso=350, legs=[60, 60], leg_far=[62, 62], feet=70)
template("hanging_knee_raise", 1900, [(0, "hang_active"), (0.85, "knee_raise_up"), (1, "knee_raise_up")], apparatus=("high_bar",))
template("knee_hip_raise", 2000, [(0, "hang_active"), (0.5, "knee_raise_up"), (1, "knee_hip_raise")], apparatus=("high_bar",))
template("hanging_leg_raise", 2100, [(0, "hang_active"), (0.85, "leg_raise_up"), (1, "leg_raise_up")], apparatus=("high_bar",))
template("toes_to_bar", 2200, [(0, "hang_active"), (0.5, "leg_raise_up"), (1, "toes_bar")], apparatus=("high_bar",))
template("hanging_lsit", 2600, [(0, "leg_raise_up"), (1, {"pose": "leg_raise_up", "legs": [88, 88], "leg_far": [90, 90]})], apparatus=("high_bar",))
template("windshield_wiper", 1800, [(0, "wiper_left"), (1, "wiper_right")], apparatus=("high_bar",))

# ---------- muscle-ups (chest-height bar so the whole path fits) ----------
pose("mu_hang", facing=1, torso=0, arms=[0, 0], shrug=-1, legs=[176, 190], leg_far=[178, 192], feet=180, pin=MB)
pose("mu_pull", facing=1, torso=350, arms=arm(-4, -6, DOWN), legs=[172, 200], leg_far=[175, 203], feet=190, pin=MB)
pose("mu_transition", facing=1, torso=20, arms=arm(-8, 10, DOWN), legs=[170, 205], leg_far=[173, 208], feet=190, head=45, pin=MB)
pose("mu_press", facing=1, torso=8, arms=arm(-3, 18, LEFT), legs=[172, 195], leg_far=[175, 198], feet=180, pin=MB)
pose("mu_support", facing=1, torso=6, arms=arm(-2, 26, LEFT), legs=[174, 178], leg_far=[176, 180], feet=180, pin=MB)
template("muscleup", 3200,
         [(0, "mu_hang"), (0.3, "mu_pull"), (0.5, "mu_transition"), (0.65, "mu_press"), (0.8, "mu_support"), (0.9, "mu_support"), (1, "mu_hang")],
         mode="loop", apparatus=("mid_bar",))
template("negative_muscleup", 3600,
         [(0, "mu_support"), (0.1, "mu_support"), (0.3, "mu_press"), (0.5, "mu_transition"), (0.72, "mu_pull"), (0.88, "mu_hang"), (1, "mu_support")],
         mode="loop", apparatus=("mid_bar",))
template("two_thirds_muscleup", 2400, [(0, "mu_hang"), (0.5, "mu_pull"), (0.85, "mu_transition"), (1, "mu_transition")], apparatus=("mid_bar",))
template("jump_muscleup", 3200,
         [(0, "mu_hang"), (0.3, "mu_pull"), (0.5, "mu_transition"), (0.65, "mu_press"), (0.8, "mu_support"), (0.9, "mu_support"), (1, "mu_hang")],
         mode="loop", apparatus=("mid_bar", "box_under"))
# low-bar transition with feet on the floor
pose("lbt_under", facing=1, torso=350, arms=[0, 0], shrug=-1, pin=MB)
reach_leg("lbt_under", (X + 10, ANKLE), pref=RIGHT)
pose("lbt_chest", facing=1, torso=345, arms=arm(-4, -6, DOWN), pin=MB)
reach_leg("lbt_chest", (X + 10, ANKLE), pref=RIGHT)
pose("lbt_over", facing=1, torso=15, arms=arm(-6, 12, DOWN), head=40, pin=MB)
reach_leg("lbt_over", (X + 10, ANKLE), pref=RIGHT)
pose("lbt_support", facing=1, torso=6, arms=arm(-2, 26, LEFT), pin=MB)
reach_leg("lbt_support", (X + 6, ANKLE), pref=RIGHT)
template("low_bar_transition", 3000,
         [(0, "lbt_under"), (0.3, "lbt_chest"), (0.55, "lbt_over"), (0.8, "lbt_support"), (1, "lbt_support")],
         apparatus=("mid_bar",))

# pullover: hang -> hips to the bar -> rotate over -> support
pose("po_tuck", facing=1, torso=200, arms=arm(-3, -9, LEFT), legs=[330, 330], leg_far=[332, 332], feet=330, head=200, pin=MB)
pose("po_over", facing=1, torso=255, arms=arm(-8, 4, DOWN), legs=[262, 262], leg_far=[264, 264], feet=270, head=240, pin=MB)
pose("po_top", facing=1, torso=340, arms=arm(-3, 24, LEFT), legs=[190, 200], leg_far=[192, 202], feet=190, pin=MB)
pose("po_knees", facing=1, torso=345, arms=arm(-3, 0, DOWN), legs=[320, 215], leg_far=[322, 217], feet=210, pin=MB)
template("pullover", 3800,
         [(0, "mu_hang"), (0.18, "po_knees"), (0.38, "po_tuck"), (0.55, "po_over"), (0.75, "po_top"), (0.88, "po_top"), (1, "mu_hang")],
         mode="loop", apparatus=("mid_bar",))
template("pullover_lowbar", 3800,
         [(0, "lbt_under"), (0.18, "po_knees"), (0.38, "po_tuck"), (0.55, "po_over"), (0.75, "po_top"), (0.88, "po_top"), (1, "lbt_under")],
         mode="loop", apparatus=("mid_bar", "mat"))

# ---------- front lever (high bar) ----------
pose("fl_tuck", facing=1, torso=270, arms=[0, 0], legs=[335, 145], leg_far=[337, 147], feet=150, head=280, pin=HB)
pose("fl_tuck_low", base="fl_tuck", torso=290, arms=[350, 350], legs=[355, 165], leg_far=[357, 167], feet=170)
pose("fl_adv", base="fl_tuck", legs=[0, 90], leg_far=[2, 90], feet=90)
pose("fl_one_leg", base="fl_tuck", leg_near=[90, 90], leg_far=[2, 90], foot_near=100, foot_far=90)
pose("fl_straddle", base="fl_tuck", leg_near=[96, 96], leg_far=[84, 84], feet=100)
pose("fl_full", base="fl_tuck", legs=[90, 90], leg_far=[92, 92], feet=100)
template("front_lever_tuck", 2600, [(0, "fl_tuck_low"), (0.7, "fl_tuck"), (1, "fl_tuck")], apparatus=("high_bar",))
template("front_lever_adv", 2600, [(0, "fl_tuck"), (0.7, "fl_adv"), (1, "fl_adv")], apparatus=("high_bar",))
template("front_lever_one_leg", 2600, [(0, "fl_adv"), (0.7, "fl_one_leg"), (1, "fl_one_leg")], apparatus=("high_bar",))
template("front_lever_straddle", 2600, [(0, "fl_adv"), (0.7, "fl_straddle"), (1, "fl_straddle")], apparatus=("high_bar",))
template("front_lever_full", 2600, [(0, "fl_adv"), (0.7, "fl_full"), (1, "fl_full")], apparatus=("high_bar",))
pose("fl_raise_mid", base="hang_active", torso=315, legs=[135, 135], leg_far=[137, 137], feet=145, head=320)
template("front_lever_raise", 2600, [(0, "hang_active"), (0.5, "fl_raise_mid"), (0.85, "fl_full"), (1, "fl_full")], apparatus=("high_bar",))

# ---------- inverted hang, skin the cat, back lever (chest-height bar so the shape fits) ----------
pose("inv_tuck", facing=1, torso=180, arms=[0, 0], legs=[305, 130], leg_far=[307, 132], feet=140, head=180, pin=MB)
pose("inv_hang", facing=1, torso=180, arms=[0, 0], legs=[0, 0], leg_far=[2, 2], feet=0, head=180, pin=MB)
pose("bl_third", facing=1, torso=215, arms=[0, 0], legs=[35, 35], leg_far=[37, 37], feet=35, head=200, pin=MB)
pose("bl_tuck", facing=-1, torso=270, arms=[0, 0], legs=[215, 45], leg_far=[217, 47], feet=40, head=280, pin=MB)
pose("bl_tuck_high", base="bl_tuck", torso=245, legs=[195, 30], leg_far=[197, 32], feet=25, head=255)
pose("bl_adv", base="bl_tuck", legs=[180, 90], leg_far=[182, 90], feet=90)
pose("bl_one_leg", base="bl_tuck", leg_near=[90, 90], leg_far=[182, 90], foot_near=100, foot_far=90)
pose("bl_straddle", base="bl_tuck", leg_near=[96, 96], leg_far=[84, 84], feet=100)
pose("bl_full", base="bl_tuck", legs=[90, 90], leg_far=[92, 92], feet=100)
pose("gh_partial", facing=-1, torso=235, arms=[0, 0], legs=[190, 30], leg_far=[192, 32], feet=25, head=245, pin=MB)
template("inverted_hang", 2800, [(0, "inv_tuck"), (0.6, "inv_hang"), (1, "inv_hang")], apparatus=("mid_bar",))
template("inverted_hang_lower", 3000, [(0, "inv_hang"), (0.3, "inv_hang"), (1, "bl_third")], apparatus=("mid_bar",))
template("skin_the_cat", 3200, [(0, "mu_hang"), (0.35, "po_knees"), (0.7, "inv_tuck"), (1, "gh_partial")], apparatus=("mid_bar", "mat"))
template("back_lever_entry", 2800, [(0, "inv_tuck"), (0.3, "inv_tuck"), (1, "bl_tuck_high")], apparatus=("mid_bar", "mat"))
template("back_lever_tuck", 2600, [(0, "bl_tuck_high"), (0.7, "bl_tuck"), (1, "bl_tuck")], apparatus=("mid_bar",))
template("back_lever_adv", 2600, [(0, "bl_tuck"), (0.7, "bl_adv"), (1, "bl_adv")], apparatus=("mid_bar",))
template("back_lever_one_leg", 2600, [(0, "bl_adv"), (0.7, "bl_one_leg"), (1, "bl_one_leg")], apparatus=("mid_bar",))
template("back_lever_straddle", 2600, [(0, "bl_adv"), (0.7, "bl_straddle"), (1, "bl_straddle")], apparatus=("mid_bar",))
template("back_lever_full", 2600, [(0, "bl_adv"), (0.7, "bl_full"), (1, "bl_full")], apparatus=("mid_bar",))
pose("bl_tuck_pull", base="bl_tuck", arms=arm(-3, -8, UP))
template("back_lever_pullup", 2000, [(0, "bl_tuck"), (0.85, "bl_tuck_pull"), (1, "bl_tuck_pull")], apparatus=("mid_bar",))

template("active_hang", 2400, [(0, "hang_active"), (1, {"pose": "hang_active", "shrug": -1.5})], apparatus=("high_bar",))
