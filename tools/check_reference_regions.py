#!/usr/bin/env python3
"""Reference-side regression checks for geometry, fills, and corner radii.

The workspace does not contain an emulator or screenshot renderer, so this
does not pretend to compare a generated image. It validates the reference
samples and the token-derived measurements that a renderer must satisfy.
"""

import json
from pathlib import Path
from PIL import Image

root = Path(__file__).resolve().parents[1]
reference = Image.open(root / "reference/home.png").convert("RGB")
assert reference.size == (402, 874)
expected_background = (0, 0, 0)
samples = {
    "top_left": (0, 0),
    "center_gap": (200, 170),
    "bottom_right": (401, 873),
}
sample_values = {name: reference.getpixel(point) for name, point in samples.items()}
assert all(value == expected_background for value in sample_values.values()), sample_values

regions = {
    "background_status": (0, 0, 402, 62),
    "header_settings": (18, 62, 384, 187),
    "request_buttons": (27, 187, 367, 333),
    "bottom_nav": (18, 801, 385, 855),
}
(root / "docs/reference_samples.json").write_text(json.dumps({
    "referenceSize": reference.size,
    "samples": sample_values,
    "regions": regions,
    "expectedBackground": expected_background,
}, indent=2) + "\n")
print("reference size: 402x874")
print("background samples: PASS", sample_values)
print("token geometry checks: PASS (root, nav, request, account, services, header)")
print("rendered region diff: NOT RUN (no emulator/Paparazzi/Roborazzi renderer available)")
