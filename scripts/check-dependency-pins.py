#!/usr/bin/env python3
"""Verify lock, gitlinks, checkout roots and canonical public submodule URLs."""
import json
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
lock = json.loads((root / "dependencies.lock.json").read_text())
failures = []

def git(*args, cwd=root):
    return subprocess.check_output(["git", *args], cwd=cwd, text=True, stderr=subprocess.DEVNULL).strip()

for name, dependency in lock["repositories"].items():
    relative = f"{lock['directory']}/{name}"
    path = root / relative
    expected = dependency["commit"]
    try:
        if Path(git("rev-parse", "--show-toplevel", cwd=path)).resolve() != path.resolve():
            raise ValueError("not an independent library checkout")
        actual = git("rev-parse", "HEAD", cwd=path)
        if actual != expected:
            failures.append(f"{name}: expected {expected}, found {actual}")
        index = git("ls-files", "--stage", "--", relative).split()
        if len(index) < 2 or index[:2] != ["160000", expected]:
            failures.append(f"{name}: gitlink differs from lock")
        url = git("config", "-f", ".gitmodules", "--get", f"submodule.{relative}.url")
        if url != dependency["url"] or not url.startswith("https://github.com/infiniforge/"):
            failures.append(f"{name}: public submodule URL differs from lock")
        if git("status", "--porcelain", "--untracked-files=no", cwd=path):
            failures.append(f"{name}: library checkout has tracked modifications")
    except (OSError, ValueError, subprocess.CalledProcessError) as error:
        failures.append(f"{name}: {error}")
if failures:
    raise SystemExit("Pinned library check failed:\n" + "\n".join(failures))
print(f"Verified {len(lock['repositories'])} public pinned library gitlinks.")
