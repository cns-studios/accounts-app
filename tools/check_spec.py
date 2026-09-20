#!/usr/bin/env python3
"""Deterministic non-visual proof that the generated spec is complete."""

import json
import re
from pathlib import Path

root = Path(__file__).resolve().parents[1]
spec = json.loads((root / "docs/spec.json").read_text())
nodes = spec["nodes"]
assert len(nodes) == 78, len(nodes)
assert len({node["id"] for node in nodes}) == len(nodes)
assert nodes[0]["parentId"] is None
assert all(
    node["parentId"] is None or any(parent["id"] == node["parentId"] for parent in nodes)
    for node in nodes
)
for node in nodes:
    asset = node.get("asset") or {}
    if asset.get("src", "").startswith("data:image/"):
        assert asset.get("alt"), node["id"]
audit = (root / "docs/token_audit.md").read_text()
assert len(re.findall(r"^\| \d+ \|", audit, re.MULTILINE)) == len(nodes)
assert "PENDING" not in audit
print(f"coverage nodes: {len(nodes)}/{len(nodes)}")
print("parent links: PASS")
print("asset alt refs: PASS")
print("audit rows: PASS")
