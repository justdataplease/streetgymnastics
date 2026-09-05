"""Reviewed contact geometry. Dense keyframes bound drift between IK solutions."""
from author import *

# Hands stay on the bar; feet stay on one floor/box point through the row.
def grounded_row(name, foot, bar, start, end, apparatus):
    frames = []
    for i in range(25):
        t = i / 24
        a = start + (end - start) * t
        key = f"{name}_contact_{i}"
        pose(key, facing=1, torso=a, legs=[a + 180, a + 180],
             feet=90, pin={"joint": "foot_near", "at": list(foot)})
        reach_arm(key, bar, pref=RIGHT)
        frames.append((t, key))
    template(name, 2400, frames, apparatus=apparatus, easing="linear")

grounded_row("row", (128, 104), (80, 66), 290, 310, ("low_bar",))
grounded_row("high_row", (125, 104), (100, 40), 320, 340, ("mid_bar",))
grounded_row("feet_elevated_row", (145, 80), (100, 40), 288, 306, ("mid_bar", "box_right"))

# A bar height that lets the knees stay bent and the feet provide assistance at the top.
APPARATUS["assisted_bar"] = [
    {"type": "line", "values": [60, 58, 140, 58]},
    {"type": "line", "role": "floor", "values": [64, 58, 64, FLOOR]},
    {"type": "line", "role": "floor", "values": [136, 58, 136, FLOOR]},
]
frames = []
for i in range(25):
    t = i / 24
    key = f"assisted_pull_contact_{i}"
    pose(key, facing=1, torso=0, arms=arm(0, -20 + 17*t, RIGHT),
         feet=90, pin={"joint": "hand_near", "at": [100, 58]})
    reach_leg(key, (108, 104), pref=RIGHT)
    frames.append((t, key))
template("assisted_pullup", 2600, frames, apparatus=("assisted_bar",), easing="linear")

frames = []
for i in range(25):
    t = i / 24
    key = f"tuck_row_contact_{i}"
    pose(key, facing=1, torso=300 - 20*t, feet=90,
         pin={"joint": "neck", "at": [80, 84 - 10*t]})
    reach_arm(key, (80, 66), pref=DOWN)
    reach_leg(key, (111, 104), pref=UP)
    frames.append((t, key))
template("tuck_front_row", 2600, frames, apparatus=("low_bar",), easing="linear")

# Straight elbows distinguish a planche lean from a shallow push-up.
frames = []
for i in range(25):
    t = i / 24
    key = f"straight_arm_lean_{i}"
    arm_angle = 180 - 16*t
    pose(key, facing=-1, torso=295, arms=[arm_angle, arm_angle], protract=2,
         pin={"joint": "hand_near", "at": [96, 105]})
    # Find the body angle at which straight legs reach the floor (the toes can pivot).
    lo, hi = 270.0, 330.0
    for _ in range(40):
        a = (lo + hi) / 2
        POSES[key].update(torso=a, legs=[a-180, a-180], feet=180)
        if solved(key)["toe_near"][1] > FLOOR:
            hi = a
        else:
            lo = a
    frames.append((t, key))
template("planche_lean", 3000, frames, easing="linear")

# Closed-chain push-ups: toes and hands remain planted instead of sliding at each end.
def grounded_push(name, foot, hand, start, end, apparatus=(), pause=False):
    frames=[]
    for i in range(25):
        t=i/24
        a=start+(end-start)*t
        key=f"{name}_contact_{i}"
        pose(key,facing=-1,torso=a,legs=[a-180,a-180],feet=180,
             pin={"joint":"foot_near","at":list(foot)})
        reach_arm(key,hand,pref=RIGHT)
        frames.append((t*(0.7 if pause else 1),key))
    if pause: frames.append((1,frames[-1][1]))
    template(name,2800 if pause else 2400,frames,apparatus=apparatus,easing="linear")
grounded_push("pushup",(146,101),(96,105),294,276)
grounded_push("pause_pushup",(146,101),(96,105),294,276,pause=True)
grounded_push("close_pushup",(146,101),(96,105),294,277)
grounded_push("incline_pushup",(105,101),(57,82),320,306,("box_left",))

# Side plank holds a long line on a forearm and the outside edge of the foot.
pose("side_plank_grounded",facing=1,torso=282,legs=[102,102],feet=90,
     arms=[180,90],arm_far=[0,0],pin={"joint":"foot_near","at":[136,104]})
# Shoulder is 10.6 units above the ground; place the elbow on the floor with fixed bone lengths.
j=solved("side_plank_grounded")
dy=104-j["shoulder"][1]
dx=math.sqrt(UPPER*UPPER-dy*dy)
POSES["side_plank_grounded"]["arm_near"]=[ang(dx,dy),90]
template("side_plank",3000,[(0,"side_plank_grounded"),(1,"side_plank_grounded")])

# Straight-leg raises must not silently show a bent-knee regression.
pose("lying_straight_up",base="lying_feet_down",legs=[0,0],leg_far=[2,2],feet=0)
pose("lying_straight_low",base="lying_straight_up",legs=[76,76],leg_far=[78,78],feet=80)
template("lying_leg_lower",3200,[(0,"lying_straight_up"),(1,"lying_straight_low")])
# The candle's hips rise above the supported shoulders.
POSES["candle_up"]["torso"]=185

# Assisted dips retain foot pressure on the step through the whole repetition.
frames=[]
for i in range(25):
    t=i/24
    key=f"assisted_dip_contact_{i}"
    pose(key,facing=1,torso=12*t,arms=arm(-5,25-15*t,LEFT),feet=90,
         pin={"joint":"hand_near","at":[100,50]})
    reach_leg(key,(106,74),pref=RIGHT)
    frames.append((t,key))
template("assisted_dip",2600,frames,apparatus=("parallel_bars","box_tall"),easing="linear")
template("foot_assisted_support",3000,[(0,frames[0][1]),(1,frames[0][1])],apparatus=("parallel_bars","box_tall"))

# Feet-assisted turnovers use a low bar and a step rather than floating into support.
frames=[]
for i in range(25):
    t=i/24
    key=f"transition_contact_{i}"
    pose(key,facing=1,torso=8*t,arms=arm(-6,-12+29*t,UP),feet=90,
         pin={"joint":"hand_near","at":[100,66]})
    reach_leg(key,(108,92),pref=UP)
    frames.append((t,key))
template("low_bar_transition",3400,frames,apparatus=("low_bar","box_under"),easing="linear")

# A hold demonstrates its assigned shape, not a repeated transition from an easier skill.
for name, shape in {
    "front_lever_tuck":"fl_tuck", "front_lever_adv":"fl_adv",
    "front_lever_one_leg":"fl_one_leg", "front_lever_straddle":"fl_straddle", "front_lever_full":"fl_full",
    "back_lever_tuck":"bl_tuck", "back_lever_adv":"bl_adv",
    "back_lever_one_leg":"bl_one_leg", "back_lever_straddle":"bl_straddle", "back_lever_full":"bl_full",
    "tuck_planche":"tp_hold", "adv_tuck_planche":"tp_adv", "straddle_planche":"tp_straddle", "full_planche":"tp_full",
}.items():
    TEMPLATES[name]["keyframes"]=[{"at":0,"pose":shape},{"at":1,"pose":shape}]
