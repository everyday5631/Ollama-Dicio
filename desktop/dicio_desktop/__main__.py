"""Entry point: ``python -m dicio_desktop``."""

from __future__ import annotations

import sys


def main() -> int:
    from .gui.main_window import run
    return run()


if __name__ == "__main__":
    sys.exit(main())
