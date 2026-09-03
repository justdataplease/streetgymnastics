#!/usr/bin/env python3
"""Render contact sheets of the offline exercise animations for visual review.

This is a development aid only. It re-implements the figure rig used by
ExerciseAnimationView.kt (schema 2: fixed bone lengths, absolute joint angles,
pinned joints) so that pose edits in exercise_animations.json can be checked
without building the app.

Usage:
    python tools/preview_animations.py [--out generated/animation_previews] [--frames 6]
                                       [--only template_name ...]
Requires Pillow.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover
    print("Pillow is required: pip install pillow", file=sys.stderr)
    raise

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_JSON = os.path.join(ROOT, "app", "src", "main", "assets", "exercise_animations.json")

JOINT_NAMES = [
    "hip", "neck", "head", "shoulder",
    "elbow_near", "hand_near", "elbow_far", "hand_far",
    "knee_near", "foot_near", "toe_near", "knee_far", "foot_far", "toe_far",
    "hands", "feet",
]


def direction(angle_deg: float) -> tuple[float, float]:
    """0 = up, 90 = right, 180 = down, 270 = left (screen coordinates)."""
    radians = math.radians(angle_deg)
    return math.sin(radians), -math.cos(radians)


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def lerp_angle(a: float, b: float, t: float) -> float:
    delta = (b - a + 180.0) % 360.0 - 180.0
    return a + delta * t


class Figure:
    def __init__(self, spec: dict):
        seg = spec.get("segments", {})
        self.head_radius = float(seg.get("head_radius", 4.5))
        self.neck = float(seg.get("neck", 3))
        self.torso = float(seg.get("torso", 20))
        self.upper_arm = float(seg.get("upper_arm", 13.5))
        self.forearm = float(seg.get("forearm", 12.5))
        self.thigh = float(seg.get("thigh", 16))
        self.shin = float(seg.get("shin", 15))
        self.foot = float(seg.get("foot", 5))


class Pose:
    FIELDS = ("torso", "head", "spine", "shrug", "protract")

    def __init__(self):
        self.facing = 1.0
        self.torso = 0.0
        self.head = None
        self.spine = 0.0
        self.shrug = 0.0
        self.protract = 0.0
        self.arm_near = [180.0, 180.0]
        self.arm_far = [180.0, 180.0]
        self.leg_near = [180.0, 180.0]
        self.leg_far = [180.0, 180.0]
        self.foot_near = None
        self.foot_far = None
        self.pin_joint = "hip"
        self.pin_at = [100.0, 73.0]

    @staticmethod
    def from_json(name: str, poses_json: dict, cache: dict, stack: tuple = ()) -> "Pose":
        if name in cache:
            return cache[name]
        if name in stack:
            raise ValueError(f"pose inheritance cycle at {name}")
        raw = poses_json[name]
        if "base" in raw:
            base = Pose.from_json(raw["base"], poses_json, cache, stack + (name,))
            pose = base.copy()
        else:
            pose = Pose()
        pose.apply(raw)
        cache[name] = pose
        return pose

    def copy(self) -> "Pose":
        other = Pose()
        other.__dict__.update({k: (list(v) if isinstance(v, list) else v) for k, v in self.__dict__.items()})
        return other

    def apply(self, raw: dict) -> None:
        if "facing" in raw:
            self.facing = 1.0 if float(raw["facing"]) >= 0 else -1.0
        for field in self.FIELDS:
            if field in raw:
                self.__dict__[field] = None if raw[field] is None else float(raw[field])
        if "arms" in raw:
            self.arm_near = [float(v) for v in raw["arms"]]
            self.arm_far = [float(v) for v in raw["arms"]]
        if "legs" in raw:
            self.leg_near = [float(v) for v in raw["legs"]]
            self.leg_far = [float(v) for v in raw["legs"]]
        for field in ("arm_near", "arm_far", "leg_near", "leg_far"):
            if field in raw:
                self.__dict__[field] = [float(v) for v in raw[field]]
        if "feet" in raw:
            self.foot_near = None if raw["feet"] is None else float(raw["feet"])
            self.foot_far = self.foot_near
        for field in ("foot_near", "foot_far"):
            if field in raw:
                self.__dict__[field] = None if raw[field] is None else float(raw[field])
        if "pin" in raw:
            pin = raw["pin"]
            self.pin_joint = str(pin.get("joint", "hip"))
            self.pin_at = [float(pin["at"][0]), float(pin["at"][1])]
        if "hip" in raw:
            self.pin_joint = "hip"
            self.pin_at = [float(raw["hip"][0]), float(raw["hip"][1])]

    def interpolate(self, other: "Pose", t: float) -> "Pose":
        out = Pose()
        out.facing = self.facing if t < 0.5 else other.facing
        out.torso = lerp_angle(self.torso, other.torso, t)
        head_a = self.torso if self.head is None else self.head
        head_b = other.torso if other.head is None else other.head
        out.head = lerp_angle(head_a, head_b, t)
        out.spine = lerp(self.spine, other.spine, t)
        out.shrug = lerp(self.shrug, other.shrug, t)
        out.protract = lerp(self.protract, other.protract, t)
        for field in ("arm_near", "arm_far", "leg_near", "leg_far"):
            a, b = self.__dict__[field], other.__dict__[field]
            out.__dict__[field] = [lerp_angle(a[0], b[0], t), lerp_angle(a[1], b[1], t)]
        out.foot_near = _lerp_optional(self.foot_near, other.foot_near, t, self, other)
        out.foot_far = _lerp_optional(self.foot_far, other.foot_far, t, self, other)
        out.pin_joint = self.pin_joint if self.pin_joint == other.pin_joint else "__hip_lerp__"
        out.pin_at = [lerp(self.pin_at[0], other.pin_at[0], t), lerp(self.pin_at[1], other.pin_at[1], t)]
        return out


def _lerp_optional(a, b, t, pose_a, pose_b):
    if a is None and b is None:
        return None
    if a is None:
        a = default_foot(pose_a, "near")
    if b is None:
        b = default_foot(pose_b, "near")
    return lerp_angle(a, b, t)


def default_foot(pose: Pose, side: str) -> float:
    shin = pose.leg_near[1] if side == "near" else pose.leg_far[1]
    return shin - 90.0 * pose.facing


def solve(pose: Pose, fig: Figure, hip_override=None) -> dict:
    """Forward kinematics in canvas units. Returns joint name -> (x, y)."""
    hip = (0.0, 0.0)
    td = direction(pose.torso)
    neck = (hip[0] + td[0] * fig.torso, hip[1] + td[1] * fig.torso)
    head_angle = pose.torso if pose.head is None else pose.head
    hd = direction(head_angle)
    head = (neck[0] + hd[0] * (fig.neck + fig.head_radius), neck[1] + hd[1] * (fig.neck + fig.head_radius))
    front = (-td[1] * pose.facing, td[0] * pose.facing)
    shoulder = (
        neck[0] + td[0] * pose.shrug + front[0] * pose.protract,
        neck[1] + td[1] * pose.shrug + front[1] * pose.protract,
    )

    def chain(origin, angles, lengths):
        points = []
        current = origin
        for angle, length in zip(angles, lengths):
            d = direction(angle)
            current = (current[0] + d[0] * length, current[1] + d[1] * length)
            points.append(current)
        return points

    elbow_n, hand_n = chain(shoulder, pose.arm_near, (fig.upper_arm, fig.forearm))
    elbow_f, hand_f = chain(shoulder, pose.arm_far, (fig.upper_arm, fig.forearm))
    knee_n, foot_n = chain(hip, pose.leg_near, (fig.thigh, fig.shin))
    knee_f, foot_f = chain(hip, pose.leg_far, (fig.thigh, fig.shin))
    foot_angle_n = default_foot(pose, "near") if pose.foot_near is None else pose.foot_near
    foot_angle_f = default_foot(pose, "far") if pose.foot_far is None else pose.foot_far
    (toe_n,) = chain(foot_n, (foot_angle_n,), (fig.foot,))
    (toe_f,) = chain(foot_f, (foot_angle_f,), (fig.foot,))
    joints = {
        "hip": hip, "neck": neck, "head": head, "shoulder": shoulder,
        "elbow_near": elbow_n, "hand_near": hand_n, "elbow_far": elbow_f, "hand_far": hand_f,
        "knee_near": knee_n, "foot_near": foot_n, "toe_near": toe_n,
        "knee_far": knee_f, "foot_far": foot_f, "toe_far": toe_f,
    }
    joints["hands"] = ((hand_n[0] + hand_f[0]) / 2, (hand_n[1] + hand_f[1]) / 2)
    joints["feet"] = ((foot_n[0] + foot_f[0]) / 2, (foot_n[1] + foot_f[1]) / 2)

    if hip_override is not None:
        offset = hip_override
    else:
        anchor = joints.get(pose.pin_joint, hip)
        offset = (pose.pin_at[0] - anchor[0], pose.pin_at[1] - anchor[1])
    return {k: (v[0] + offset[0], v[1] + offset[1]) for k, v in joints.items()}


class Template:
    def __init__(self, name: str, raw: dict, poses_json: dict, pose_cache: dict):
        self.name = name
        self.duration_ms = int(raw.get("duration_ms", 1600))
        self.mode = raw.get("mode", "ping_pong")
        self.easing = raw.get("easing", "smooth")
        self.reduced_motion_frame = float(raw.get("reduced_motion_frame", 0.5))
        self.apparatus = list(raw.get("apparatus", []))
        self.keyframes = []
        for frame in raw["keyframes"]:
            if "pose" in frame:
                pose = Pose.from_json(frame["pose"], poses_json, pose_cache)
                if any(k for k in frame if k not in ("at", "pose")):
                    pose = pose.copy()
                    pose.apply({k: v for k, v in frame.items() if k not in ("at", "pose")})
            else:
                pose = Pose()
                pose.apply(frame)
            self.keyframes.append((float(frame.get("at", 0.0)), pose))
        self.keyframes.sort(key=lambda kf: kf[0])
        if len(self.keyframes) == 1:
            self.keyframes.append((1.0, self.keyframes[0][1]))

    def timeline(self, phase: float) -> float:
        if self.mode == "ping_pong":
            return phase * 2 if phase <= 0.5 else (1 - phase) * 2
        return phase

    def pose_at(self, phase: float, fig: Figure) -> dict:
        t = self.timeline(phase)
        first_at, first = self.keyframes[0]
        second_at, second = self.keyframes[-1]
        for index in range(len(self.keyframes) - 1):
            if t <= self.keyframes[index + 1][0]:
                first_at, first = self.keyframes[index]
                second_at, second = self.keyframes[index + 1]
                break
        span = max(second_at - first_at, 1e-4)
        raw = min(max((t - first_at) / span, 0.0), 1.0)
        fraction = raw if self.easing == "linear" else raw * raw * (3 - 2 * raw)
        blended = first.interpolate(second, fraction)
        if blended.pin_joint == "__hip_lerp__":
            hip_a = solve(first, fig)["hip"]
            hip_b = solve(second, fig)["hip"]
            return solve(blended, fig, hip_override=(lerp(hip_a[0], hip_b[0], fraction), lerp(hip_a[1], hip_b[1], fraction)))
        return solve(blended, fig)


def parse_color(value: str, fallback=(0, 0, 0)):
    try:
        value = value.lstrip("#")
        if len(value) == 8:
            value = value[2:]
        return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))
    except Exception:
        return fallback


def draw_frame(draw: ImageDraw.ImageDraw, ox: float, oy: float, scale: float, library: dict, template: Template,
               joints: dict, fig: Figure):
    palette = library["palette"]
    figure_color = parse_color(palette["figure"])
    far_color = tuple(int(c * 0.55 + 255 * 0.45) for c in figure_color)
    apparatus_color = parse_color(palette["apparatus"])
    floor_color = parse_color(palette["floor"])
    accent = parse_color(palette["accent"])
    fill = parse_color(palette["apparatus"])
    fill = tuple(int(c * 0.15 + 255 * 0.85) for c in fill)

    def P(pt):
        return (ox + pt[0] * scale, oy + pt[1] * scale)

    for name in template.apparatus:
        for prim in library["apparatus"].get(name, []):
            role = prim.get("role", "apparatus")
            color = floor_color if role == "floor" else accent if role == "accent" else apparatus_color
            width = max(1, int(round((1.6 if role != "floor" else 1.4) * scale)))
            values = prim["values"]
            kind = prim["type"]
            if kind == "line":
                draw.line([P(values[0:2]), P(values[2:4])], fill=color, width=width)
            elif kind == "polyline":
                pts = [P(values[i:i + 2]) for i in range(0, len(values), 2)]
                draw.line(pts, fill=color, width=width, joint="curve")
            elif kind in ("rect", "round_rect"):
                box = [P(values[0:2]), P(values[2:4])]
                if prim.get("filled"):
                    draw.rectangle(box, fill=fill, outline=color, width=width)
                else:
                    draw.rectangle(box, outline=color, width=width)
            elif kind == "circle":
                cx, cy = P(values[0:2])
                r = values[2] * scale
                draw.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=width)

    w_near = max(2, int(round(2.6 * scale)))
    w_far = max(2, int(round(2.2 * scale)))

    def bone(a, b, color, width):
        draw.line([P(joints[a]), P(joints[b])], fill=color, width=width)
        for pt in (a, b):
            x, y = P(joints[pt])
            r = width / 2 - 0.5
            draw.ellipse([x - r, y - r, x + r, y + r], fill=color)

    bone("hip", "knee_far", far_color, w_far)
    bone("knee_far", "foot_far", far_color, w_far)
    bone("foot_far", "toe_far", far_color, w_far)
    bone("shoulder", "elbow_far", far_color, w_far)
    bone("elbow_far", "hand_far", far_color, w_far)

    # torso as a quadratic curve
    hip, neck = joints["hip"], joints["neck"]
    dx, dy = neck[0] - hip[0], neck[1] - hip[1]
    length = max(math.hypot(dx, dy), 1e-3)
    spine = joints.get("__spine", 0.0)
    facing = joints.get("__facing", 1.0)
    front = (-dy / length * facing, dx / length * facing)
    mid = ((hip[0] + neck[0]) / 2 + front[0] * spine, (hip[1] + neck[1]) / 2 + front[1] * spine)
    pts = []
    for i in range(13):
        t = i / 12
        x = (1 - t) ** 2 * hip[0] + 2 * (1 - t) * t * mid[0] + t * t * neck[0]
        y = (1 - t) ** 2 * hip[1] + 2 * (1 - t) * t * mid[1] + t * t * neck[1]
        pts.append(P((x, y)))
    draw.line(pts, fill=figure_color, width=w_near, joint="curve")
    bone("neck", "head", figure_color, w_near)
    hx, hy = P(joints["head"])
    r = fig.head_radius * scale
    draw.ellipse([hx - r, hy - r, hx + r, hy + r], fill=figure_color)

    bone("hip", "knee_near", figure_color, w_near)
    bone("knee_near", "foot_near", figure_color, w_near)
    bone("foot_near", "toe_near", figure_color, w_near)
    bone("shoulder", "elbow_near", figure_color, w_near)
    bone("elbow_near", "hand_near", figure_color, w_near)
    for hand in ("hand_far", "hand_near"):
        x, y = P(joints[hand])
        rr = 1.6 * scale
        draw.ellipse([x - rr, y - rr, x + rr, y + rr], fill=accent)


def render_sheet(library: dict, templates: list[Template], fig: Figure, frames: int, scale: float) -> Image.Image:
    canvas = library.get("canvas", {"width": 200, "height": 120})
    cw, ch = int(canvas["width"] * scale), int(canvas["height"] * scale)
    label_h = 18
    gap = 6
    sheet_w = frames * (cw + gap) + gap
    sheet_h = len(templates) * (ch + label_h + gap) + gap
    image = Image.new("RGB", (sheet_w, sheet_h), (255, 255, 255))
    draw = ImageDraw.Draw(image)
    try:
        font = ImageFont.truetype("arial.ttf", 13)
    except Exception:
        font = ImageFont.load_default()
    background = parse_color(library["palette"]["background"])
    border = parse_color(library["palette"]["border"])
    for row, template in enumerate(templates):
        top = gap + row * (ch + label_h + gap)
        draw.text((gap, top), f"{template.name}  ({template.mode}, {template.duration_ms} ms)", fill=(40, 40, 40), font=font)
        for col in range(frames):
            phase = col / (frames - 1) if frames > 1 else 0.0
            if template.mode == "ping_pong":
                phase *= 0.5  # only the forward half; the return mirrors it
            left = gap + col * (cw + gap)
            ox, oy = left, top + label_h
            draw.rectangle([ox, oy, ox + cw, oy + ch], fill=background, outline=border)
            joints = template.pose_at(phase, fig)
            blended = _blend_meta(template, phase)
            joints["__spine"] = blended[0]
            joints["__facing"] = blended[1]
            draw_frame(draw, ox, oy, scale, library, template, joints, fig)
    return image


def _blend_meta(template: Template, phase: float):
    t = template.timeline(phase)
    first_at, first = template.keyframes[0]
    second_at, second = template.keyframes[-1]
    for index in range(len(template.keyframes) - 1):
        if t <= template.keyframes[index + 1][0]:
            first_at, first = template.keyframes[index]
            second_at, second = template.keyframes[index + 1]
            break
    span = max(second_at - first_at, 1e-4)
    raw = min(max((t - first_at) / span, 0.0), 1.0)
    fraction = raw if template.easing == "linear" else raw * raw * (3 - 2 * raw)
    return lerp(first.spine, second.spine, fraction), (first.facing if fraction < 0.5 else second.facing)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--json", default=DEFAULT_JSON)
    parser.add_argument("--out", default=os.path.join(ROOT, "generated", "animation_previews"))
    parser.add_argument("--frames", type=int, default=6)
    parser.add_argument("--scale", type=float, default=1.6)
    parser.add_argument("--rows", type=int, default=12, help="templates per sheet")
    parser.add_argument("--only", nargs="*", default=None)
    args = parser.parse_args()

    with open(args.json, encoding="utf-8") as handle:
        library = json.load(handle)
    fig = Figure(library.get("figure", {}))
    pose_cache: dict = {}
    templates = [Template(name, raw, library["poses"], pose_cache) for name, raw in library["templates"].items()]
    if args.only:
        templates = [t for t in templates if t.name in set(args.only)]
    os.makedirs(args.out, exist_ok=True)
    for start in range(0, len(templates), args.rows):
        chunk = templates[start:start + args.rows]
        sheet = render_sheet(library, chunk, fig, args.frames, args.scale)
        path = os.path.join(args.out, f"sheet_{start // args.rows + 1:02d}.png")
        sheet.save(path)
        print(path, ", ".join(t.name for t in chunk))
    return 0


if __name__ == "__main__":
    sys.exit(main())
