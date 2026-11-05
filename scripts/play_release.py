#!/usr/bin/env python3
"""Helpers for the manual Google Play release workflow (.github/workflows/play-release.yml).

Subcommands:
  version      Read versionName/versionCode from app/build.gradle.kts.
  notes        Parse release notes (Play Console <lang> tags, plain text or @file) into JSON.
  check-new    Fail if the versionCode is already recorded in the changelog.
  upload       Upload an AAB and release notes to a Play track (Google Play Developer API v3).
  changelog    Append a release entry to the changelog JSON file.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import sys

PACKAGE = "com.takeruf.nagi"
TRACKS = ("internal", "alpha", "beta", "production")
STATUSES = ("draft", "completed")
PLAY_NOTES_LIMIT = 500
NOTES_FILE_DIR = Path("docs/play-store")
LANG_BLOCK = re.compile(r"<([A-Za-z]{2,3}(?:-[A-Za-z0-9]{2,8})*)>\s*(.*?)\s*</\1>", re.S)


def read_version(gradle_file: Path) -> tuple[str, int]:
    text = gradle_file.read_text(encoding="utf-8")
    name = re.findall(r'^\s*versionName\s*=\s*"([^"]+)"', text, re.M)
    code = re.findall(r"^\s*versionCode\s*=\s*(\d+)", text, re.M)
    if len(name) != 1 or len(code) != 1:
        raise SystemExit(f"Expected exactly one literal versionName and versionCode in {gradle_file}")
    if not re.fullmatch(r"\d+\.\d+\.\d+", name[0]):
        raise SystemExit(f"versionName {name[0]!r} is not a stable x.y.z version")
    return name[0], int(code[0])


def parse_notes(raw: str, repo_root: Path = Path(".")) -> dict[str, str]:
    """Return {language: text}. Untagged text is treated as en-US."""
    raw = raw.strip()
    if raw.startswith("@"):
        target = (repo_root / raw[1:].strip()).resolve()
        allowed = (repo_root / NOTES_FILE_DIR).resolve()
        if allowed not in target.parents or not target.is_file():
            raise SystemExit(f"Release notes file must be an existing file under {NOTES_FILE_DIR}/")
        raw = target.read_text(encoding="utf-8").strip()
    elif "\n" not in raw:
        # The GitHub web form is single-line; allow literal \n escapes there.
        raw = raw.replace("\\n", "\n")
    if not raw:
        raise SystemExit("Release notes are empty")
    blocks = LANG_BLOCK.findall(raw)
    if blocks:
        leftover = LANG_BLOCK.sub("", raw).strip()
        if leftover:
            raise SystemExit(f"Text outside language tags: {leftover[:80]!r}")
        notes: dict[str, str] = {}
        for lang, text in blocks:
            if lang in notes:
                raise SystemExit(f"Duplicate language tag <{lang}>")
            notes[lang] = text.strip()
    else:
        notes = {"en-US": raw}
    for lang, text in notes.items():
        if not text:
            raise SystemExit(f"Release notes for {lang} are empty")
        if len(text) > PLAY_NOTES_LIMIT:
            raise SystemExit(f"Release notes for {lang} have {len(text)} characters; Play allows {PLAY_NOTES_LIMIT}")
    return notes


def load_changelog(path: Path) -> list[dict]:
    if not path.exists():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, list):
        raise SystemExit(f"{path} must contain a JSON array")
    return data


def check_new(path: Path, version_code: int) -> None:
    for entry in load_changelog(path):
        if entry.get("versionCode") == version_code:
            raise SystemExit(
                f"versionCode {version_code} ({entry.get('versionName')}) was already released to "
                f"{entry.get('track')} at {entry.get('releasedAt')}; bump the version first")


def append_changelog(path: Path, entry: dict) -> None:
    check_new(path, entry["versionCode"])
    entries = load_changelog(path)
    entries.append(entry)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(entries, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def upload(aab: Path, track: str, status: str, notes: dict[str, str], version_name: str,
           version_code: int, send_for_review: bool) -> dict:
    import socket
    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload

    socket.setdefaulttimeout(600)
    info = json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"])
    creds = service_account.Credentials.from_service_account_info(
        info, scopes=["https://www.googleapis.com/auth/androidpublisher"])
    edits = build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()
    edit_id = edits.insert(packageName=PACKAGE, body={}).execute()["id"]
    try:
        media = MediaFileUpload(str(aab), mimetype="application/octet-stream", resumable=True)
        bundle = edits.bundles().upload(packageName=PACKAGE, editId=edit_id, media_body=media).execute()
        if int(bundle["versionCode"]) != version_code:
            raise SystemExit(f"Uploaded bundle has versionCode {bundle['versionCode']}, expected {version_code}")
        release = {
            "name": f"{version_name} ({version_code})",
            "versionCodes": [str(version_code)],
            "status": status,
            "releaseNotes": [{"language": lang, "text": text} for lang, text in notes.items()],
        }
        edits.tracks().update(packageName=PACKAGE, editId=edit_id, track=track,
                              body={"track": track, "releases": [release]}).execute()
        edits.commit(packageName=PACKAGE, editId=edit_id,
                     changesNotSentForReview=not send_for_review).execute()
    except BaseException:
        try:
            edits.delete(packageName=PACKAGE, editId=edit_id).execute()
        except Exception as error:  # the edit expires on its own; report the original failure
            print(f"warning: could not delete edit {edit_id}: {error}", file=sys.stderr)
        raise
    return {"versionCode": int(bundle["versionCode"]), "sha256": bundle.get("sha256")}


def write_outputs(**values: object) -> None:
    target = os.environ.get("GITHUB_OUTPUT")
    lines = [f"{key}={value}" for key, value in values.items()]
    if target:
        with open(target, "a", encoding="utf-8") as stream:
            stream.write("\n".join(lines) + "\n")
    print("\n".join(lines))


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("version")
    p.add_argument("--gradle", type=Path, default=Path("app/build.gradle.kts"))

    p = sub.add_parser("notes")
    p.add_argument("--out", type=Path, required=True)
    p.add_argument("--repo-root", type=Path, default=Path("."))

    p = sub.add_parser("check-new")
    p.add_argument("--changelog", type=Path, required=True)
    p.add_argument("--version-code", type=int, required=True)

    p = sub.add_parser("upload")
    p.add_argument("--aab", type=Path, required=True)
    p.add_argument("--track", choices=TRACKS, required=True)
    p.add_argument("--status", choices=STATUSES, required=True)
    p.add_argument("--notes", type=Path, required=True)
    p.add_argument("--version-name", required=True)
    p.add_argument("--version-code", type=int, required=True)
    p.add_argument("--send-for-review", choices=("true", "false"), default="false")

    p = sub.add_parser("changelog")
    p.add_argument("--changelog", type=Path, required=True)
    p.add_argument("--entry-out", type=Path, required=True)
    p.add_argument("--notes", type=Path, required=True)
    p.add_argument("--version-name", required=True)
    p.add_argument("--version-code", type=int, required=True)
    p.add_argument("--track", choices=TRACKS, required=True)
    p.add_argument("--status", choices=STATUSES, required=True)
    p.add_argument("--released-at", required=True)
    p.add_argument("--commit", required=True)
    p.add_argument("--run-url", default="")
    p.add_argument("--aab-sha256", default="")

    args = parser.parse_args(argv)
    if args.command == "version":
        name, code = read_version(args.gradle)
        write_outputs(version_name=name, version_code=code)
    elif args.command == "notes":
        notes = parse_notes(os.environ.get("RELEASE_NOTES", ""), args.repo_root)
        args.out.write_text(json.dumps(notes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Parsed release notes for: {', '.join(notes)}")
    elif args.command == "check-new":
        check_new(args.changelog, args.version_code)
    elif args.command == "upload":
        notes = json.loads(args.notes.read_text(encoding="utf-8"))
        result = upload(args.aab, args.track, args.status, notes, args.version_name,
                        args.version_code, args.send_for_review == "true")
        write_outputs(uploaded_version_code=result["versionCode"], play_sha256=result["sha256"] or "")
    elif args.command == "changelog":
        entry = {
            "versionName": args.version_name,
            "versionCode": args.version_code,
            "releasedAt": args.released_at,
            "track": args.track,
            "status": args.status,
            "commit": args.commit,
            "runUrl": args.run_url,
            "aabSha256": args.aab_sha256,
            "releaseNotes": json.loads(args.notes.read_text(encoding="utf-8")),
        }
        append_changelog(args.changelog, entry)
        args.entry_out.write_text(json.dumps(entry, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Recorded {args.version_name} ({args.version_code}) in {args.changelog}")


if __name__ == "__main__":
    main()
