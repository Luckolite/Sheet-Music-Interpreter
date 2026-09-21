# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Offline OMR. The inference runtime is imported only when an Interpreter is created."""
from .reader import Interpreter
from .musicxml import write_musicxml

__version__ = "0.1.2"
__all__ = ["Interpreter", "write_musicxml"]
