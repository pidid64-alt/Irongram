#!/usr/bin/env python3
"""Emit GitHub Actions annotations for the errors found in a Gradle build log.

Workflow logs are not always downloadable from outside CI, so the interesting
compiler errors are republished as check-run annotations, which are readable
through the GitHub API. Usage::

    python tools/ci/report_gradle_errors.py gradle-build.log
"""

import pathlib
import re
import sys

PATTERN = re.compile(
    r"(^e: |^error:|error: |cannot find symbol|Could not (resolve|find|create)|"
    r"^FAILURE: |What went wrong|> Task .* FAILED|Caused by:|"
    r"Execution failed for task|^\s*> )"
)
# Lines after these markers carry the actual cause of a Gradle failure.
WINDOW_MARKERS = ("What went wrong", "Execution failed for task", "FAILURE: Build failed")
WINDOW_SIZE = 6
MAX_ANNOTATIONS = 10


def escape(text):
    return text.replace("%", "%25").replace("\r", "").strip()[:900]


def main():
    log_path = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "gradle-build.log")
    if not log_path.exists():
        print("::error title=Gradle::Build log was not created")
        return 0
    lines = log_path.read_text(errors="replace").splitlines()
    errors = []
    for index, line in enumerate(lines):
        if any(marker in line for marker in WINDOW_MARKERS):
            errors.append(" | ".join(
                part.strip() for part in lines[index:index + WINDOW_SIZE] if part.strip()
            ))
        elif PATTERN.search(line):
            errors.append(line)
    seen = set()
    emitted = 0
    for message in errors:
        if message in seen:
            continue
        seen.add(message)
        print("::error title=Gradle::" + escape(message))
        emitted += 1
        if emitted >= MAX_ANNOTATIONS:
            break
    print("::group::Gradle log tail")
    print("\n".join(lines[-60:]))
    print("::endgroup::")
    return 0


if __name__ == "__main__":
    sys.exit(main())
