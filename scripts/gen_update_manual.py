#!/usr/bin/env python3
"""Generate update-manual.json from app/build.gradle and the bundled hook rules.

The release ships four fixed-name assets reachable via GitHub's
``releases/latest/download/<name>`` link: bluetooth-modern.apk, bluetooth-legacy.apk,
hook_rules.json and this update-manual.json. The app reads this file to decide whether to
offer an APK upgrade (by versionCode) and/or a hook-rule update (by revision).
"""
import argparse
import json
import pathlib
import re
import sys


def read_app_version(gradle_path: str):
    text = pathlib.Path(gradle_path).read_text(encoding="utf-8")
    code = re.search(r"versionCode\s+(\d+)", text)
    name = re.search(r'versionName\s+"([^"]+)"', text)
    if not code or not name:
        sys.exit(f"versionCode/versionName not found in {gradle_path}")
    return int(code.group(1)), name.group(1)


def read_rules(rules_path: str):
    data = json.loads(pathlib.Path(rules_path).read_text(encoding="utf-8"))
    return int(data["schemaVersion"]), int(data["revision"])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gradle", default="app/build.gradle")
    ap.add_argument("--rules", default="app/src/main/resources/hook_rules.json")
    ap.add_argument("--out", default="dist/update-manual.json")
    ap.add_argument("--version", default="version.json")
    args = ap.parse_args()

    version_code, version_name = read_app_version(args.gradle)
    rules_schema, revision = read_rules(args.rules)
    version = json.loads(pathlib.Path(args.version).read_text(encoding="utf-8"))

    manifest = {
        "schemaVersion": 1,
        "app": {"versionCode": version_code, "versionName": version_name,
                "changes": version.get("changes", [])},
        "rules": {"schemaVersion": rules_schema, "revision": revision},
    }
    out = pathlib.Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(json.dumps(manifest, ensure_ascii=False))


if __name__ == "__main__":
    main()
