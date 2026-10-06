#!/usr/bin/env python3
"""Prepare verified APK/update metadata; no signing secrets or network mutation."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("apk", type=Path)
parser.add_argument("--notes", type=Path, required=True)
parser.add_argument("--out", type=Path, required=True)
args = parser.parse_args()
sdk = Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk")))
versions = sorted((sdk / "build-tools").iterdir(), key=lambda p: [int(v) for v in re.findall(r"\d+", p.name)])
tool = next(p for p in reversed(versions) if (p / "apksigner").exists() and (p / "aapt").exists())
subprocess.run([str(tool / "apksigner"), "verify", "--verbose", str(args.apk)], check=True)
metadata = subprocess.check_output([str(tool / "aapt"), "dump", "badging", str(args.apk)], text=True)
match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", metadata)
if not match or match[1] != "com.takeruf.nagi":
    raise SystemExit("Unexpected APK package")
name = match[3]
if not re.fullmatch(r"\d+\.\d+\.\d+", name) or "application-debuggable" in metadata:
    raise SystemExit("Expected a non-debuggable stable release APK")
min_sdk = int(re.search(r"sdkVersion:'(\d+)'", metadata)[1])
args.out.mkdir(parents=True, exist_ok=True)
output = args.out / f"nagi-{name}.apk"
if args.apk.resolve() != output.resolve():
    shutil.copy2(args.apk, output)
with output.open("rb") as stream:
    checksum = hashlib.file_digest(stream, "sha256").hexdigest()
manifest = dict(applicationId=match[1], versionCode=int(match[2]), versionName=name, minSdk=min_sdk,
    apkUrl=f"https://github.com/TakeruF/android-tab-browser/releases/download/v{name}/{output.name}",
    sha256=checksum, size=output.stat().st_size, notes=args.notes.read_text().strip())
(args.out / "update.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
(args.out / "SHA256SUMS").write_text(f"{checksum}  {output.name}\n")
print(f"Prepared {output.name}: {output.stat().st_size} bytes, SHA-256 {checksum}")
