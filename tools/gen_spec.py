#!/usr/bin/env python3
"""Generate a flattened, Compose-oriented specification from tokens.json."""

import json
import re
import sys
from pathlib import Path

PX_TO_DP = 1.0
KNOWN_STYLE_KEYS = {
    "backgroundColor", "color", "width", "height", "display", "alignItems",
    "justifyContent", "flexDirection", "flexShrink", "flexGrow", "alignSelf",
    "gap", "padding", "margin", "borderRadius", "position", "top", "left",
    "overflow", "textAlign", "lineHeight", "fontFamily", "fontSize",
    "fontStretch", "fontStyle", "fontWeight", "textDecoration",
}


def content(value):
    return value.get("content") if isinstance(value, dict) else value


def normalize(value, key):
    value = content(value)
    if isinstance(value, (int, float)):
        return {
            "raw": value,
            "compose": float(value) * PX_TO_DP,
            "unit": "sp" if key == "fontSize" else "dp",
        }
    if not isinstance(value, str):
        return value
    rgba = re.fullmatch(
        r"rgba\((\d+),\s*(\d+),\s*(\d+),\s*([0-9.]+)\)", value
    )
    if rgba:
        r, g, b, alpha = rgba.groups()
        return {
            "raw": value,
            "compose": f"0x{round(float(alpha) * 255):02X}{int(r):02X}{int(g):02X}{int(b):02X}",
            "unit": "color",
        }
    match = re.fullmatch(r"(-?[0-9.]+)(px)?", value)
    if match and match.group(1) != value:
        number = float(match.group(1)) * PX_TO_DP
        return {"raw": value, "compose": number, "unit": "dp"}
    if key == "fontSize" and re.fullmatch(r"-?[0-9.]+", value):
        return {"raw": value, "compose": float(value) * PX_TO_DP, "unit": "sp"}
    return {"raw": value, "compose": value, "unit": "literal"}


def walk(node, parent_id=None, depth=0, output=None):
    if output is None:
        output = []
    if not isinstance(node, dict) or node.get("type") != "element":
        return output
    node_content = node["content"]
    node_id = f"node-{len(output) + 1:03d}"
    styles = node_content.get("style", {})
    unknown = sorted(set(styles) - KNOWN_STYLE_KEYS)
    if unknown:
        print(
            f"WARNING: unsupported style keys on {node_content.get('name')}: {unknown}",
            file=sys.stderr,
        )
    text = "".join(
        str(content(child))
        for child in node_content.get("children", [])
        if isinstance(child, dict) and child.get("type") == "static"
    )
    entry = {
        "id": node_id,
        "name": node_content.get("name"),
        "type": node_content.get("elementType"),
        "parentId": parent_id,
        "depth": depth,
        "style": {key: normalize(value, key) for key, value in styles.items()},
        "text": text or None,
        "asset": {
            key: content(value)
            for key, value in node_content.get("attrs", {}).items()
        } or None,
    }
    output.append(entry)
    for child in node_content.get("children", []):
        walk(child, node_id, depth + 1, output)
    return output


def main():
    root = Path(__file__).resolve().parents[1]
    source = root / "tokens.json"
    destination = root / "docs/spec.json"
    data = json.loads(source.read_text())
    nodes = walk(data["componentUIDLs"][0]["node"])
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps({
        "source": str(source.relative_to(root)),
        "conversion": {"pxToDp": PX_TO_DP, "pxToSp": PX_TO_DP},
        "rootFrame": {"width": "100%", "heightDp": 874},
        "nodes": nodes,
    }, indent=2) + "\n")
    counts = {}
    for node in nodes:
        counts[node["type"]] = counts.get(node["type"], 0) + 1
    print(f"total nodes: {len(nodes)}")
    print("node types:", json.dumps(counts, sort_keys=True))


if __name__ == "__main__":
    main()
