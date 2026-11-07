# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Lightweight Java runtime lookup shared by inference and decoded-data exports."""
import os
import shutil
from pathlib import Path


def java_executable():
    if os.environ.get('JAVA_HOME'):
        path = Path(os.environ['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
        if path.is_file():
            return str(path)
    path = shutil.which('java')
    if not path:
        raise RuntimeError('Install Java 17 or newer and put java on PATH or set JAVA_HOME')
    return path
