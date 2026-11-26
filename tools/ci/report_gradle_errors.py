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

COMPILER_PATTERN = re.compile(
    r"(^e: |^error:|error: |cannot find symbol|Caused by:|^\s+symbol:|^\s+location:)"
)
GRADLE_PATTERN = re.compile(
    r"(^FAILURE: |What went wrong|> Task .* FAILED|Execution failed for task|"
    r"Could not (resolve|find|create)|A problem occurred)"
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
    compiler_errors = []
    gradle_errors = []
    for index, line in enumerate(lines):
        if COMPILER_PATTERN.search(line):
            compiler_errors.append(line)
        elif any(marker in line for marker in WINDOW_MARKERS):
            gradle_errors.append(" | ".join(
                part.strip() for part in lines[index:index + WINDOW_SIZE] if part.strip()
            ))
        elif GRADLE_PATTERN.search(line):
            gradle_errors.append(line)

    # Compiler diagnostics matter most: Gradle prints many progress lines that would otherwise
    # use up the annotation limit.
    seen = set()
    emitted = 0
    for message in compiler_errors + gradle_errors:
        if message in seen:
            continue
        seen.add(message)
        title = "Compile error" if message in compiler_errors else "Gradle"
        print("::error title={}::".format(title) + escape(message))
        emitted += 1
        if emitted >= MAX_ANNOTATIONS:
            break
    if not emitted:
        print("::error title=Gradle::No error lines recognised in the build log")
    print("::group::Gradle log tail")
    print("\n".join(lines[-60:]))
    print("::endgroup::")
    return 0


if __name__ == "__main__":
    sys.exit(main())
