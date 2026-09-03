import sys, os, importlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import author
for g in ["group_a", "group_b", "group_c", "group_d"]:
    importlib.import_module(g)
import mappings as m
heur = [{"contains": c, "animation": a} for c, a in m.HEURISTICS]
missing = [t for t in list(m.MOVEMENTS.values()) + list(m.ALIASES.values()) + [h["animation"] for h in heur]
           + list(m.CATEGORY_DEFAULTS.values()) + list(m.EQUIPMENT_DEFAULTS.values()) if t not in author.TEMPLATES]
if missing:
    print("MISSING TEMPLATES:", sorted(set(missing)))
    sys.exit(1)
author.dump(m.MOVEMENTS, m.ALIASES, heur, m.CATEGORY_DEFAULTS, m.EQUIPMENT_DEFAULTS, m.FALLBACK)
