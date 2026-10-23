#!/usr/bin/env python3
"""Print a TeleportHQ UIDL token export as a compact indented tree.

One line per node: element type, name, then its style as `key=value` pairs (static values
unwrapped, data URIs shortened). Text leaves are printed quoted. Usage:

    tools/token_tree.py FILE.json [FILE.json ...]
"""

import json
import sys


def unwrap(value):
    if isinstance(value, dict) and "content" in value:
        return unwrap(value["content"])
    return value


def short(value):
    text = str(unwrap(value))
    if text.startswith("data:"):
        return text[:24] + f"…({len(text)}B)"
    return text


def walk(node, depth, out):
    pad = "  " * depth
    if not isinstance(node, dict):
        return
    if node.get("type") == "static":
        out.append(f"{pad}\"{short(node.get('content'))}\"")
        return
    if node.get("type") != "element":
        out.append(f"{pad}<{node.get('type')}> {short(node.get('content'))}")
        return
    content = node["content"]
    style = " ".join(f"{k}={short(v)}" for k, v in content.get("style", {}).items())
    attrs = " ".join(f"@{k}={short(v)}" for k, v in content.get("attrs", {}).items())
    out.append(f"{pad}{content.get('elementType')} [{content.get('name')}] {style} {attrs}".rstrip())
    for child in content.get("children", []):
        walk(child, depth + 1, out)


def main(paths):
    for path in paths:
        data = json.load(open(path))
        out = [f"# {path}"]
        for component in data.get("componentUIDLs", []):
            out.append(f"## component {component.get('name')}")
            walk(component.get("node"), 0, out)
        tokens = data.get("usedDesignTokens") or {}
        if tokens:
            out.append("## usedDesignTokens " + json.dumps(tokens))
        print("\n".join(out))


if __name__ == "__main__":
    main(sys.argv[1:])
