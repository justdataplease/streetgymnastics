"""Handstand and human-flag preparation, with visible apparatus and assistance."""
from author import *

pose("overhead_line", facing=1, torso=0, arms=[0, 0], legs=[180,180], feet=90,
     pin={"joint":"foot_near", "at":[100,104]})
template("overhead_line", 3000, [(0,"overhead_line"),(1,"overhead_line")])

# Stack the elbow under the shoulder and raise the shoulder/hip/knee line together.
# The old diagonal upper arm left the shoulder close to the floor.
pose("kneeling_side", facing=1, torso=292, arms=[180,90], arm_far=[0,0],
     legs=[112,90], feet=90, pin={"joint":"knee_near", "at":[120,104]})

template("kneeling_side_plank", 3000, [(0,"kneeling_side"),(1,"kneeling_side")])

APPARATUS["pike_box"]=[{"type":"round_rect","values":[116,59,146,FLOOR],"filled":True}]
pose("box_pike", facing=1, torso=180, arms=[180,180], legs=[90,90], feet=90,
     pin={"joint":"hand_near","at":[90,105]})
template("pike_handstand_hold", 3000, [(0,"box_pike"),(1,"box_pike")], apparatus=("pike_box",))

pose("handstand_wall", facing=-1, torso=180, arms=[180,180], legs=[0,0], feet=90,
     head=180, pin={"joint":"hand_near","at":[145,105]})
pose("handstand_balance", base="handstand_wall", legs=[355,355], feet=0)
template("wall_handstand",3000,[(0,"handstand_wall"),(1,"handstand_wall")],apparatus=("wall_right",))
template("handstand_toe_pull",3200,[(0,"handstand_wall"),(.7,"handstand_balance"),(1,"handstand_balance")],apparatus=("wall_right",))
template("handstand_balance",3200,[(0,"handstand_balance"),(1,"handstand_balance")],apparatus=("wall_right",))

# Split grip: one hand pulls above the shoulder and the other pushes below it.
APPARATUS["flag_foothold"]=[{"type":"round_rect","values":[106,84,130,FLOOR],"filled":True}]
def flag(name,torso,neck):
    pose(name,facing=1,torso=torso,feet=90,pin={"joint":"neck","at":neck})
    reach_arm(name,(62,42),pref=UP,which=("arm_far",))
    reach_arm(name,(62,78.76),pref=DOWN,which=("arm_near",))
flag("flag_ground",330,[80,60])
reach_leg("flag_ground",(99,104),pref=RIGHT)
flag("flag_assisted",270,[80,60])
reach_leg("flag_assisted",(113,84),pref=UP)
flag("flag_tuck",270,[80,60])
POSES["flag_tuck"].update(legs=[35,155])
template("flag_ground_support",3000,[(0,"flag_ground"),(1,"flag_ground")],apparatus=("post_left",))
template("assisted_tuck_flag",3000,[(0,"flag_assisted"),(1,"flag_assisted")],apparatus=("post_left","flag_foothold"))
template("tuck_flag",3000,[(0,"flag_tuck"),(1,"flag_tuck")],apparatus=("post_left",))
