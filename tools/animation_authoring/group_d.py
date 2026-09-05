# Group D: legs, floor core, dragon flag, planche, stretches, crawl, seated hip work
from author import *

X = 100
FOOT_PIN = {"joint": "foot_near", "at": [X, ANKLE]}

# ---------- squats ----------
pose("squat_mid", base="stand", torso=15, arms=[95, 95], leg_near=leg(5, 24, RIGHT), leg_far=leg(6, 24, RIGHT))
pose("squat_bottom", base="stand", torso=25, arms=[95, 95], leg_near=leg(8, 16, RIGHT), leg_far=leg(9, 16, RIGHT), head=15)
template("squat", 2000, [(0, "stand"), (0.5, "squat_mid"), (1, "squat_bottom")])
template("tempo_squat", 3200, [(0, "stand"), (0.7, "squat_mid"), (1, "squat_bottom")], easing="linear")
template("pause_squat", 2800, [(0, "stand"), (0.4, "squat_mid"), (0.7, "squat_bottom"), (1, "squat_bottom")])
pose("box_squat_bottom", base="stand", torso=22, arms=[95, 95], leg_near=leg(7, 13, RIGHT), leg_far=leg(8, 13, RIGHT),
     pin={"joint": "foot_near", "at": [X + 4, ANKLE]})
template("box_squat", 2200, [(0, "stand"), (0.5, "squat_mid"), (1, "box_squat_bottom")], apparatus=("box_under",))
# deep squat hold at a post (athlete faces the post)
pose("deep_squat", facing=-1, torso=340, leg_near=leg(-6, 12, LEFT), leg_far=leg(-7, 12, LEFT), head=350,
     pin={"joint": "foot_near", "at": [96, ANKLE]})
reach_arm("deep_squat", (62, 62), pref=DOWN)
pose("deep_squat_sway", base="deep_squat", torso=335, leg_near=leg(-7, 11, LEFT), leg_far=leg(-8, 11, LEFT))
reach_arm("deep_squat_sway", (62, 62), pref=DOWN)
template("deep_squat_hold", 3200, [(0, "deep_squat"), (1, "deep_squat_sway")], apparatus=("post_left",))
# pistol squats
pose("pistol_bottom", base="stand", torso=30, arms=[92, 92], leg_near=leg(8, 15, RIGHT), leg_far=[92, 92], foot_far=100, head=20)
pose("pistol_third", base="stand", torso=15, arms=[92, 92], leg_near=leg(4, 25, RIGHT), leg_far=[100, 100], foot_far=110)
pose("pistol_stand", base="stand", arms=[95, 95], leg_far=[120, 120], foot_far=130)
template("pistol", 2400, [(0, "pistol_stand"), (0.5, "pistol_third"), (1, "pistol_bottom")])
template("pistol_partial", 2000, [(0, "pistol_stand"), (1, "pistol_third")])
pose("pistol_assist_top", facing=-1, torso=0, leg_near=[180, 180], leg_far=[240, 240], foot_far=230, pin={"joint": "foot_near", "at": [96, ANKLE]})
reach_arm("pistol_assist_top", (62, 62), pref=DOWN, which=("arm_near",))
pose("pistol_assist_bottom", facing=-1, torso=330, leg_near=leg(-8, 15, LEFT), leg_far=[268, 268], foot_far=260, head=340,
     pin={"joint": "foot_near", "at": [96, ANKLE]})
reach_arm("pistol_assist_bottom", (62, 62), pref=DOWN, which=("arm_near",))
template("pistol_assisted", 2400, [(0, "pistol_assist_top"), (1, "pistol_assist_bottom")], apparatus=("post_left",))
# squat jump (loop)
pose("jump_air", base="stand", torso=0, arm_near=[10, 10], arm_far=[14, 14], legs=[180, 180], leg_far=[183, 177], feet=150,
     pin={"joint": "foot_near", "at": [X, ANKLE - 10]})
pose("jump_load", base="squat_mid", arms=[210, 210])
template("squat_jump", 2000, [(0, "stand"), (0.25, "jump_load"), (0.5, "jump_air"), (0.75, "jump_load"), (1, "stand")], mode="loop")
# calf raise at a post
pose("calf_flat", facing=-1, torso=0, legs=[180, 180], leg_far=[182, 178], feet=270, pin={"joint": "toe_near", "at": [88, FLOOR]})
reach_arm("calf_flat", (62, 60), pref=DOWN, which=("arm_near",))
POSES["calf_flat"]["arm_far"] = [180, 180]
pose("calf_up", base="calf_flat", feet=305)
template("calf_raise", 1800, [(0, "calf_flat"), (0.8, "calf_up"), (1, "calf_up")], apparatus=("post_left",))
# step-up onto a box on the right
pose("step_bottom", facing=1, torso=8, leg_far=[180, 180], arms=[165, 165], pin={"joint": "foot_far", "at": [104, ANKLE]})
reach_leg("step_bottom", (132, 80), pref=RIGHT, which=("leg_near",))
pose("step_mid", facing=1, torso=6, arms=[170, 170], leg_far=[150, 200], foot_far=110, pin={"joint": "foot_near", "at": [132, 80]})
reach_leg("step_mid", (132, 80), pref=RIGHT, which=("leg_near",))
POSES["step_mid"]["leg_near"] = leg(6, 22, RIGHT)
pose("step_top", facing=1, torso=0, arms=[180, 180], leg_near=[180, 180], leg_far=[170, 200], foot_far=110,
     pin={"joint": "foot_near", "at": [132, 80]})
template("step_up", 2200, [(0, "step_bottom"), (0.5, "step_mid"), (0.85, "step_top"), (1, "step_top")], apparatus=("box_right",))
# lunges
pose("lunge_front", base="stand", torso=5, arms=[178, 178], leg_near=leg(16, 18, RIGHT), leg_far=[215, 265], foot_far=190,
     pin={"joint": "foot_near", "at": [X + 18, ANKLE]})
pose("lunge_back", base="stand", torso=5, arms=[178, 178], leg_near=leg(2, 18, RIGHT), leg_far=[215, 265], foot_far=190,
     pin={"joint": "foot_near", "at": [X + 4, ANKLE]})
template("front_lunge", 2000, [(0, "stand"), (0.85, "lunge_front"), (1, "lunge_front")])
template("reverse_lunge", 2000, [(0, "stand"), (0.85, "lunge_back"), (1, "lunge_back")])
# split squat at a post (post on the right, near hand rests on it)
pose("split_top", facing=1, torso=3, leg_near=[170, 188], leg_far=[200, 205], foot_far=195, arm_far=[180, 180],
     pin={"joint": "foot_near", "at": [X + 6, ANKLE]})
reach_arm("split_top", (138, 58), pref=DOWN, which=("arm_near",))
pose("split_bottom", facing=1, torso=5, leg_near=leg(2, 18, RIGHT), leg_far=[215, 265], foot_far=190, arm_far=[180, 180],
     pin={"joint": "foot_near", "at": [X + 6, ANKLE]})
reach_arm("split_bottom", (138, 58), pref=DOWN, which=("arm_near",))
template("split_squat", 2000, [(0, "split_top"), (1, "split_bottom")], apparatus=("post_right",))
# jumping lunges (loop)
pose("jl_air", base="stand", torso=5, arms=[190, 190], legs=[178, 190], leg_far=[182, 186], feet=150,
     pin={"joint": "foot_near", "at": [X, ANKLE - 8]})
pose("jl_far_front", base="stand", torso=5, arms=[178, 178], leg_far=leg(2, 18, RIGHT), leg_near=[215, 265], foot_near=190,
     pin={"joint": "foot_far", "at": [X + 4, ANKLE]})
template("jumping_lunge", 2000, [(0, "lunge_back"), (0.25, "jl_air"), (0.5, "jl_far_front"), (0.75, "jl_air"), (1, "lunge_back")], mode="loop")
# cossack squat / adductor stretch (front view)
pose("cossack_wide", facing=1, torso=3, leg_near=leg(19, 28, RIGHT), leg_far=leg(-19, 28, LEFT), foot_near=90, foot_far=300,
     arm_near=[150, 150], arm_far=[210, 210], pin={"joint": "foot_near", "at": [124, ANKLE]})
pose("cossack_bottom", facing=1, torso=8, leg_near=leg(8, 12, RIGHT), leg_far=leg(-30, 12, LEFT), foot_near=90, foot_far=300,
     arm_near=[110, 60], arm_far=[250, 300], pin={"joint": "foot_near", "at": [124, ANKLE]})
template("cossack", 2400, [(0, "cossack_wide"), (1, "cossack_bottom")])
template("cossack_hold", 3000, [(0, "cossack_bottom"), (1, {"pose": "cossack_bottom", "torso": 14})])
# glute bridge
pose("bridge_down", facing=1, torso=270, arm_near=[95, 95], arm_far=[97, 97], pin={"joint": "neck", "at": [70, 100]})
reach_leg("bridge_down", (114, ANKLE), pref=UP)
pose("bridge_up", facing=1, torso=244, arm_near=[95, 95], arm_far=[97, 97], pin={"joint": "neck", "at": [70, 100]})
reach_leg("bridge_up", (114, ANKLE), pref=UP)
template("glute_bridge", 2000, [(0, "bridge_down"), (0.85, "bridge_up"), (1, "bridge_up")], apparatus=("mat",))
pose("sl_bridge_down", base="bridge_down", leg_far=[55, 55], foot_far=60)
pose("sl_bridge_up", base="bridge_up", leg_far=[50, 50], foot_far=55)
template("single_leg_bridge", 2000, [(0, "sl_bridge_down"), (0.85, "sl_bridge_up"), (1, "sl_bridge_up")], apparatus=("mat",))

# ---------- floor core ----------
pose("hollow", facing=1, torso=305, head=300, arms=[80, 80], legs=[30, 90], leg_far=[32, 92], feet=90, hip=[100, 100])
pose("hollow_press", base="hollow", torso=310, legs=[25, 95], leg_far=[27, 97], feet=95)
template("hollow_hold", 3000, [(0, "hollow"), (1, "hollow_press")], apparatus=("mat",))
pose("deadbug_neutral", facing=1, torso=270, arms=[0, 0], legs=[0, 90], leg_far=[2, 90], feet=90, pin={"joint": "neck", "at": [70, 100]})
pose("deadbug_a", base="deadbug_neutral", arm_near=[285, 285], leg_far=[78, 78], foot_far=85)
pose("deadbug_b", base="deadbug_neutral", arm_far=[285, 285], leg_near=[78, 78], foot_near=85)
template("dead_bug", 2600, [(0, "deadbug_a"), (0.25, "deadbug_neutral"), (0.5, "deadbug_b"), (0.75, "deadbug_neutral"), (1, "deadbug_a")], mode="loop")
pose("birddog_a", base="quadruped", arm_near=[270, 270], leg_far=[90, 90], foot_far=100)
pose("birddog_b", base="quadruped", arm_far=[270, 270], leg_near=[90, 90], foot_near=100)
template("bird_dog", 2800, [(0, "birddog_a"), (0.25, "quadruped"), (0.5, "birddog_b"), (0.75, "quadruped"), (1, "birddog_a")], mode="loop")
# knee side plank (front view: bottom forearm on the floor)
pose("side_plank_up", facing=1, torso=292, arm_far=[180, 90], arm_near=[0, 0], leg_near=[74, 55], leg_far=[76, 62], foot_near=60, foot_far=60,
     pin={"joint": "neck", "at": [74, 90]})
pose("side_plank_low", base="side_plank_up", torso=302, leg_near=[68, 50], leg_far=[70, 56])
template("side_plank", 2400, [(0, "side_plank_low"), (0.8, "side_plank_up"), (1, "side_plank_up")])
# lying knee raise / bent-leg lowering
pose("lying_feet_down", facing=1, torso=270, arms=[92, 92], legs=[40, 150], leg_far=[42, 150], pin={"joint": "neck", "at": [70, 100]})
pose("lying_knees_in", base="lying_feet_down", legs=[330, 110], leg_far=[332, 112], feet=100)
pose("lying_legs_out", base="lying_feet_down", legs=[62, 132], leg_far=[64, 134], feet=120)
template("lying_knee_raise", 2000, [(0, "lying_feet_down"), (1, "lying_knees_in")], apparatus=("mat",))
template("lying_leg_lower", 2600, [(0, "lying_knees_in"), (1, "lying_legs_out")], apparatus=("mat",))
# prone arch hold
pose("arch_flat", facing=-1, torso=270, head=270, arms=[270, 270], legs=[90, 90], leg_far=[92, 92], feet=170, hip=[104, 101])
pose("arch_up", base="arch_flat", torso=279, head=290, arms=[283, 283], legs=[83, 83], leg_far=[85, 85], feet=160, spine=3)
template("arch_hold", 2600, [(0, "arch_flat"), (0.7, "arch_up"), (1, "arch_up")], apparatus=("mat",))
# candlestick roll (loop)
pose("candle_seat", facing=1, torso=340, arms=[75, 75], legs=[20, 110], leg_far=[22, 112], feet=100, hip=[100, 99])
pose("candle_up", facing=1, torso=15, head=270, arms=[100, 100], legs=[5, 5], leg_far=[7, 7], feet=10, pin={"joint": "neck", "at": [84, 100]})
template("candlestick", 3200, [(0, "candle_seat"), (0.5, "candle_up"), (1, "candle_seat")], mode="loop", apparatus=("mat",))
# bear crawl
pose("crawl_a", facing=-1, torso=270, leg_far=[118, 176], leg_near=[130, 200], foot_near=230, pin={"joint": "toe_far", "at": [126, FLOOR]})
reach_arm("crawl_a", (68, 105), pref=RIGHT, which=("arm_near",)); reach_arm("crawl_a", (80, 105), pref=RIGHT, which=("arm_far",))
pose("crawl_b", facing=-1, torso=270, leg_near=[118, 176], leg_far=[130, 200], foot_far=230, pin={"joint": "toe_near", "at": [126, FLOOR]})
reach_arm("crawl_b", (80, 105), pref=RIGHT, which=("arm_near",)); reach_arm("crawl_b", (68, 105), pref=RIGHT, which=("arm_far",))
template("bear_crawl", 1600, [(0, "crawl_a"), (1, "crawl_b")])
# seated 90/90
pose("nn_seat", facing=1, torso=2, arms=[150, 60], leg_near=[92, 250], leg_far=[268, 110], foot_near=250, foot_far=110, hip=[100, 100])
pose("nn_lean", base="nn_seat", torso=42, head=30, arms=[140, 140])
pose("nn_center", base="nn_seat", legs=[40, 150], leg_far=[42, 150], feet=None, arms=[150, 60])
pose("nn_other", base="nn_seat", leg_near=[268, 110], leg_far=[92, 250], foot_near=110, foot_far=250)
template("ninety_ninety_stretch", 3000, [(0, "nn_seat"), (1, "nn_lean")], apparatus=("mat",))
template("ninety_ninety_switch", 2600, [(0, "nn_seat"), (0.5, "nn_center"), (1, "nn_other")], apparatus=("mat",))

# ---------- dragon flag ----------
DF = {"joint": "neck", "at": [54, 100]}
def dragon(name, angle, shin=None, far_shin=None):
    legs = [angle, angle if shin is None else shin]
    leg_far = [angle + 2, (angle + 2) if far_shin is None else far_shin]
    pose(name, facing=1, torso=(angle + 180) % 360, head=270, legs=legs, leg_far=leg_far, feet=(legs[1] + 10) % 360, pin=DF)
    reach_arm(name, (41, 82), pref=UP)
dragon("dragon_top", 15)
dragon("dragon_low", 75)
dragon("dragon_bent_top", 15, shin=75, far_shin=77)
dragon("dragon_bent_low", 75, shin=135, far_shin=137)
dragon("dragon_one_top", 15, far_shin=75)
dragon("dragon_one_low", 75, far_shin=135)
template("dragon_flag", 2600, [(0, "dragon_top"), (1, "dragon_low")], apparatus=("mat", "anchor_left"))
template("dragon_negative", 3800, [(0, "dragon_top"), (0.12, "dragon_top"), (0.8, "dragon_low"), (0.9, "dragon_low"), (1, "dragon_top")],
         mode="loop", apparatus=("mat", "anchor_left"))
template("dragon_bent_negative", 3800, [(0, "dragon_bent_top"), (0.12, "dragon_bent_top"), (0.8, "dragon_bent_low"), (0.9, "dragon_bent_low"), (1, "dragon_bent_top")],
         mode="loop", apparatus=("mat", "anchor_left"))
template("dragon_one_leg_negative", 3800, [(0, "dragon_one_top"), (0.12, "dragon_one_top"), (0.8, "dragon_one_low"), (0.9, "dragon_one_low"), (1, "dragon_one_top")],
         mode="loop", apparatus=("mat", "anchor_left"))

# ---------- planche family ----------
HANDS = {"joint": "hand_near", "at": [90, 105]}
pose("frog_floor", facing=-1, torso=240, head=250, arms=[150, 205], legs=[200, 150], leg_far=[202, 152], pin={"joint": "hand_near", "at": [86, 105]})
pose("frog_up", base="frog_floor", legs=[205, 80], leg_far=[207, 82], feet=90)
template("frog_stand", 2600, [(0, "frog_floor"), (0.7, "frog_up"), (1, "frog_up")], apparatus=("mat",))
pose("tp_floor", facing=-1, torso=275, head=280, arms=[168, 168], protract=3, legs=[200, 130], leg_far=[202, 132], feet=220, pin=HANDS)
pose("tp_hold", base="tp_floor", arms=[165, 165], legs=[200, 100], leg_far=[202, 102], feet=190)
pose("tp_adv", base="tp_hold", arms=[163, 163], legs=[170, 90], leg_far=[172, 92], feet=180)
pose("tp_straddle", base="tp_hold", arms=[160, 160], leg_near=[93, 93], leg_far=[86, 86], feet=100)
pose("tp_full", base="tp_hold", arms=[160, 160], legs=[90, 90], leg_far=[92, 92], feet=100)
template("tuck_planche", 2600, [(0, "tp_floor"), (0.7, "tp_hold"), (1, "tp_hold")], apparatus=("mat",))
template("adv_tuck_planche", 2600, [(0, "tp_hold"), (0.7, "tp_adv"), (1, "tp_adv")], apparatus=("mat",))
template("straddle_planche", 2600, [(0, "tp_adv"), (0.7, "tp_straddle"), (1, "tp_straddle")], apparatus=("mat",))
template("full_planche", 2600, [(0, "tp_adv"), (0.7, "tp_full"), (1, "tp_full")], apparatus=("mat",))

# ---------- stretches ----------
pose("forearm_stretch", base="stand", arm_near=[90, 90])
reach_arm("forearm_stretch", (solved("forearm_stretch")["hand_near"][0] - 1, solved("forearm_stretch")["hand_near"][1] + 2), pref=DOWN, which=("arm_far",))
template("forearm_stretch", 3000, [(0, "forearm_stretch"), (1, {"pose": "forearm_stretch", "arm_near": [90, 96]})])
pose("lat_hinge", facing=-1, torso=300, legs=[172, 186], leg_far=[174, 188], head=300, pin={"joint": "foot_near", "at": [98, ANKLE]})
reach_arm("lat_hinge", (62, 50), pref=UP)
pose("lat_hinge_deep", facing=-1, torso=292, legs=[168, 190], leg_far=[170, 192], head=292, pin={"joint": "foot_near", "at": [100, ANKLE]})
reach_arm("lat_hinge_deep", (62, 50), pref=UP)
template("lat_stretch", 3200, [(0, "lat_hinge"), (1, "lat_hinge_deep")], apparatus=("post_left",))
pose("chest_open", facing=1, torso=0, arm_near=[90, 0], arm_far=[185, 185], pin={"joint": "foot_near", "at": [124, ANKLE]})
pose("chest_open_more", base="chest_open", torso=354, arm_far=[205, 205])
template("chest_stretch", 3200, [(0, "chest_open"), (1, "chest_open_more")], apparatus=("post_right",))
pose("ham_hinge", facing=-1, torso=300, legs=[176, 186], leg_far=[178, 188], head=300, arm_far=[200, 200], pin={"joint": "foot_near", "at": [96, ANKLE]})
reach_arm("ham_hinge", (62, 60), pref=UP, which=("arm_near",))
pose("ham_hinge_deep", base="ham_hinge", torso=286, head=286, legs=[174, 188], leg_far=[176, 190])
reach_arm("ham_hinge_deep", (62, 60), pref=UP, which=("arm_near",))
template("hamstring_stretch", 3200, [(0, "ham_hinge"), (1, "ham_hinge_deep")], apparatus=("post_left",))
pose("quad_hold", facing=-1, torso=0, leg_far=[180, 180], leg_near=[188, 20], foot_near=100, pin={"joint": "foot_far", "at": [94, ANKLE]})
reach_arm("quad_hold", (62, 55), pref=UP, which=("arm_far",))
reach_arm("quad_hold", solved("quad_hold")["foot_near"], pref=RIGHT, which=("arm_near",))
pose("quad_hold_deep", base="quad_hold", leg_near=[192, 10], foot_near=95)
reach_arm("quad_hold_deep", solved("quad_hold_deep")["foot_near"], pref=RIGHT, which=("arm_near",))
template("quad_stretch", 3200, [(0, "quad_hold"), (1, "quad_hold_deep")], apparatus=("post_left",))
pose("calf_wall", facing=1, torso=15, leg_far=[200, 200], foot_far=110, pin={"joint": "foot_far", "at": [108, ANKLE]})
reach_leg("calf_wall", (128, ANKLE), pref=RIGHT, which=("leg_near",))
reach_arm("calf_wall", (149, 52), pref=DOWN)
pose("calf_wall_deep", facing=1, torso=20, leg_far=[205, 205], foot_far=115, pin={"joint": "foot_far", "at": [108, ANKLE]})
reach_leg("calf_wall_deep", (128, ANKLE), pref=RIGHT, which=("leg_near",))
reach_arm("calf_wall_deep", (149, 52), pref=DOWN)
template("calf_stretch", 3200, [(0, "calf_wall"), (1, "calf_wall_deep")], apparatus=("wall_right",))
pose("child", facing=-1, torso=250, head=250, arms=[275, 275], legs=[230, 90], leg_far=[228, 90], feet=90, pin={"joint": "knee_near", "at": [114, 102]})
pose("child_deep", base="child", torso=256, head=252, arms=[278, 278])
template("child_pose", 3400, [(0, "child"), (1, "child_deep")], apparatus=("mat",))
