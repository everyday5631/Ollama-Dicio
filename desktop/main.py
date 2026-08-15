"""Entry point for the packaged application.

PyInstaller runs its entry script as a top-level `__main__`, not as part of a package, so the
relative imports in `enclave_desktop/__main__.py` fail there ("attempted relative import with no
known parent package"). This module uses an absolute import instead and is what the spec file
points at; `python -m enclave_desktop` still goes through `__main__.py`.
"""

from __future__ import annotations

import sys

from enclave_desktop.gui.main_window import run


def main() -> int:
    return run()


if __name__ == "__main__":
    sys.exit(main())
