# Group A: standing warm-ups, mobility, floor mobility, breathing
from author import *

X = 100
FOOT_PIN = {"joint": "foot_near", "at": [X, ANKLE]}

# ---------- standing base ----------
pose("stand", facing=1, torso=0, arms=[180, 180], leg_near=[180, 180], leg_far=[183, 177], pin=FOOT_PIN)
pose("reach_front", base="stand", arms=[90, 90])
pose("reach_up", base="stand", arm_near=[5, 5], arm_far=[8, 8])
template("standing_mobility", 2200, [(0, "stand"), (0.5, "reach_front"), (1, "reach_up")])

# ---------- march / walk (loop) ----------
pose("march_a", base="stand", leg_near=[60, 170], leg_far=[182, 178], foot_near=140,
     arm_near=[205, 195], arm_far=[140, 100], pin={"joint": "foot_far", "at": [X, ANKLE]})
pose("march_pass", base="stand", leg_near=[176, 184], arm_near=[185, 160], arm_far=[178, 165],
     pin={"joint": "foot_far", "at": [X, ANKLE]})
pose("march_b", base="stand", leg_far=[60, 170], leg_near=[182, 178], foot_far=140,
     arm_far=[205, 195], arm_near=[140, 100], pin={"joint": "foot_near", "at": [X, ANKLE]})
pose("march_pass2", base="stand", leg_far=[176, 184], arm_far=[185, 160], arm_near=[178, 165],
     pin={"joint": "foot_near", "at": [X, ANKLE]})
template("march", 1400, [(0, "march_a"), (0.25, "march_pass"), (0.5, "march_b"), (0.75, "march_pass2"), (1, "march_a")],
         mode="loop", easing="linear")

pose("walk_a", base="stand", leg_near=[155, 172], leg_far=[205, 210], foot_far=250, foot_near=80,
     arm_near=[200, 200], arm_far=[160, 150], pin={"joint": "foot_near", "at": [X + 6, ANKLE]})
pose("walk_pass", base="stand", leg_near=[180, 180], leg_far=[170, 200], foot_far=110,
     arm_near=[182, 180], arm_far=[178, 172], pin={"joint": "foot_near", "at": [X, ANKLE]})
pose("walk_b", base="stand", leg_far=[155, 172], leg_near=[205, 210], foot_near=250, foot_far=80,
     arm_far=[200, 200], arm_near=[160, 150], pin={"joint": "foot_far", "at": [X + 6, ANKLE]})
pose("walk_pass2", base="stand", leg_far=[180, 180], leg_near=[170, 200], foot_near=110,
     arm_far=[182, 180], arm_near=[178, 172], pin={"joint": "foot_far", "at": [X, ANKLE]})
template("walk", 1500, [(0, "walk_a"), (0.25, "walk_pass"), (0.5, "walk_b"), (0.75, "walk_pass2"), (1, "walk_a")],
         mode="loop", easing="linear")

# ---------- low-impact jack ----------
pose("jack_mid", base="stand", arms=[90, 90], leg_near=[184, 176], leg_far=[176, 184])
pose("jack_top", base="stand", arm_near=[8, 5], arm_far=[12, 8], leg_near=[188, 172], leg_far=[172, 188])
template("jack", 1500, [(0, "stand"), (0.5, "jack_mid"), (1, "jack_top")])

# ---------- wrist circles (standing, forearms forward) ----------
pose("wrist_a", base="stand", arms=[170, 80])
pose("wrist_b", base="stand", arms=[172, 95])
pose("wrist_c", base="stand", arms=[168, 100])
pose("wrist_d", base="stand", arms=[166, 85])
template("wrist_circles", 1600, [(0, "wrist_a"), (0.25, "wrist_b"), (0.5, "wrist_c"), (0.75, "wrist_d"), (1, "wrist_a")],
         mode="loop", easing="linear")

# ---------- quadruped: wrist rocks, cat-cow ----------
KNEE_PIN = {"joint": "knee_near", "at": [118, 102]}
pose("quadruped", facing=-1, torso=270, legs=[180, 90], feet=90, leg_far=[178, 90], pin=KNEE_PIN)
reach_arm("quadruped", (80, ANKLE), pref=LEFT)
pose("quad_forward", base="quadruped", torso=276)
reach_arm("quad_forward", (80, ANKLE), pref=LEFT)
pose("quad_back", base="quadruped", torso=264)
reach_arm("quad_back", (80, ANKLE), pref=LEFT)
template("wrist_rock", 1800, [(0, "quad_back"), (1, "quad_forward")])

pose("cat", base="quadruped", spine=-4.5, head=225)
pose("cow", base="quadruped", spine=4, head=300)
template("cat_cow", 2400, [(0, "cat"), (1, "cow")])

# ---------- shoulder circles / arm swings ----------
pose("circle_0", base="stand", arm_near=[180, 180])
pose("circle_1", base="stand", arm_near=[90, 90])
pose("circle_2", base="stand", arm_near=[0, 0])
pose("circle_3", base="stand", arm_near=[270, 270])
template("shoulder_circles", 2600,
         [(0, "circle_0"), (0.25, "circle_1"), (0.5, "circle_2"), (0.75, "circle_3"), (1, "circle_0")],
         mode="loop", easing="linear")

pose("swing_front", base="stand", arm_near=[85, 80], arm_far=[95, 90])
pose("swing_back", base="stand", arm_near=[235, 235], arm_far=[225, 225])
template("arm_swings", 1600, [(0, "swing_back"), (1, "swing_front")])

# ---------- hip circles ----------
pose("hips_neutral", base="stand", arms=[160, 65])
pose("hips_forward", base="stand", arms=[160, 65], torso=352, leg_near=[170, 182], leg_far=[172, 180])
pose("hips_back", base="stand", arms=[160, 65], torso=8, leg_near=[190, 178], leg_far=[192, 176])
template("hip_circles", 2200, [(0, "hips_forward"), (0.5, "hips_neutral"), (1, "hips_back")])

# ---------- half-kneeling: ankle rocks, hip flexor stretch ----------
pose("half_kneel", facing=1, torso=5, arms=[150, 80], leg_far=[180, 270], foot_far=270,
     pin={"joint": "knee_far", "at": [88, 102]})
reach_leg("half_kneel", (109, ANKLE), pref=RIGHT, which=("leg_near",))
pose("ankle_rock_fwd", base="half_kneel", torso=12, leg_far=[165, 270],
     pin={"joint": "knee_far", "at": [88, 102]})
reach_leg("ankle_rock_fwd", (109, ANKLE), pref=RIGHT, which=("leg_near",))
template("ankle_rock", 1700, [(0, "half_kneel"), (1, "ankle_rock_fwd")])

pose("hip_flexor", base="half_kneel", torso=0, arms=[175, 175], leg_far=[172, 270])
reach_leg("hip_flexor", (109, ANKLE), pref=RIGHT, which=("leg_near",))
pose("hip_flexor_fwd", base="half_kneel", torso=356, arms=[178, 178], leg_far=[160, 270])
reach_leg("hip_flexor_fwd", (109, ANKLE), pref=RIGHT, which=("leg_near",))
template("hip_flexor_stretch", 3000, [(0, "hip_flexor"), (1, "hip_flexor_fwd")], apparatus=("mat",))

# ---------- supported leg swings (post on the left, athlete faces the post) ----------
pose("swing_base", facing=-1, torso=0, arm_far=[180, 180], leg_far=[180, 180], pin={"joint": "foot_far", "at": [92, ANKLE]})
reach_arm("swing_base", (62, 58), pref=UP, which=("arm_near",))
pose("leg_back", base="swing_base", leg_near=[140, 150], foot_near=60)
pose("leg_front", base="swing_base", leg_near=[235, 230], foot_near=320)
template("leg_swing", 1500, [(0, "leg_back"), (1, "leg_front")], apparatus=("post_left",))

# ---------- wall slide (back to wall on the right) ----------
pose("wall_slide_low", facing=-1, torso=0, legs=[180, 180], arms=[60, 0], pin={"joint": "foot_near", "at": [143, ANKLE]})
pose("wall_slide_high", base="wall_slide_low", arms=[12, 5])
template("wall_slide", 2000, [(0, "wall_slide_low"), (1, "wall_slide_high")], apparatus=("wall_right",))

# ---------- breathing (supine, hand on belly) ----------
pose("breathe", facing=1, torso=270, arm_far=[90, 90], leg_near=[40, 150], leg_far=[42, 150],
     spine=1.2, pin={"joint": "neck", "at": [70, 100]})
reach_arm("breathe", (86, 95), pref=DOWN, which=("arm_near",))
pose("breathe_full", base="breathe", spine=3.2)
template("breathing", 4200, [(0, "breathe"), (1, "breathe_full")], apparatus=("mat",))

# ---------- thoracic rotation (quadruped, hand behind head, elbow opens to the ceiling) ----------
pose("trot_closed", base="quadruped", arm_near=[300, 60], head=250)
pose("trot_open", base="quadruped", arm_near=[20, 110], head=290, spine=1.5)
template("thoracic_rotation", 2600, [(0, "trot_closed"), (1, "trot_open")])

# ---------- supine twist (side view: knees stacked, rolling gently toward the mat) ----------
pose("twist_center", facing=1, torso=270, arm_near=[270, 270], arm_far=[275, 275], legs=[30, 130], leg_far=[34, 130],
     pin={"joint": "neck", "at": [70, 100]})
pose("twist_side", base="twist_center", legs=[70, 160], leg_far=[74, 160], feet=70)
template("supine_twist", 3000, [(0, "twist_center"), (1, "twist_side")], apparatus=("mat",))
