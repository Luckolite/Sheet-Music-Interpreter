# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Write a desktop score result in the current Android guide-cache wire format.

This is derived cache data. The source PDF and previously saved guides are never
modified by this module. Keep GUIDE_VERSION aligned with PdfPageRenderCache.
"""

import io
import math
import os
import struct
import tempfile
from pathlib import Path
from . import semantic_wire


# Recognition revisions 264/265/266/267/268/269/270/271/272/273/274/275 retain the complete framed 263 record layout.
RECOGNITION_REVISION = 11
GUIDE_VERSION = 282
SUPPORTED_GUIDE_VERSIONS = (260, 261, 262, 263, 264, 265, 266, 267, 268, 269, 270, 271, 272, 273, 274, 275, 276, 277, 278, 279, 280, 281, 282)
MAX_GUIDE_BYTES = 128 * 1024 * 1024


def _field(row, name):
    if name not in row:
        raise ValueError(f"Missing guide field: {name}")
    return row[name]


def _pack(stream, format_code, *values):
    if any(isinstance(value, float) and not math.isfinite(value) for value in values):
        raise ValueError("Non-finite guide value")
    stream.write(struct.pack(">" + format_code, *values))


def _records(stream, rows, fields, maximum):
    if not isinstance(rows, list) or len(rows) > maximum:
        raise ValueError("Invalid guide record count")
    _pack(stream, "i", len(rows))
    for row in rows:
        if not isinstance(row, dict):
            raise ValueError("Invalid guide record")
        _pack(stream, "".join(code for _, code in fields),
              *(_field(row, name) for name, _ in fields))


def encode(score, guide_version=GUIDE_VERSION):
    """Serialize the complete score object returned by the standalone reader."""
    if guide_version not in SUPPORTED_GUIDE_VERSIONS or not isinstance(score, dict):
        raise ValueError("Unsupported guide format")
    output = io.BytesIO()
    _pack(output, "i", guide_version)
    measures = score["measures"]
    _records(output, measures, (("left", "f"), ("right", "f"),
                                ("top", "f"), ("bottom", "f")), 10_000)
    _pack(output, "i", _field(score, "firstMeasureNumber"))
    note_fields = (
        ("measureIndex", "i"), ("positionInMeasure", "f"), ("staffStep", "i"),
        ("staffIndex", "i"), ("staffCount", "i"), ("pageY", "f"),
        ("tiedFromPrevious", "?"), ("augmentationDots", "i"), ("beamCount", "i"),
        ("writtenAccidental", "i"), ("unbeamedDurationBeats", "f"),
        ("tupletDivisor", "i"), ("followingRestBeats", "f"), ("articulations", "i"),
        ("clefBottomDiatonic", "i"), ("crossStaffBeam", "?"),
        ("leadingRestBeats", "f"), ("compactOpening", "?"), ("octaveShift", "i"))
    if guide_version >= 262:
        note_fields += (("boundaryTies", "i"),)
    if guide_version >= 276:
        note_fields += (("tupletNormalNotes", "i"),)
    if guide_version >= 281:
        note_fields += (("stemDirection", "i"),)
    if guide_version >= 282:
        note_fields += (("kind", "B"),)
    notes = score["notes"]
    if not isinstance(notes, list) or any(not isinstance(row, dict)
            or type(row.get("boundaryTies", 0)) is not int
            or not 0 <= row.get("boundaryTies", 0) <= 15 for row in notes):
        raise ValueError("Invalid boundary tie evidence")
    normalized_notes = []
    for row in notes:
        actual = _field(row, "tupletDivisor")
        default_normal = {3: 2, 5: 4, 6: 4, 7: 4}.get(actual, 1)
        normal = row.get("tupletNormalNotes", default_normal)
        if (type(actual) is not int or actual not in (1, 3, 5, 6, 7)
                or type(normal) is not int or not 1 <= normal <= 16
                or actual == 1 and normal != 1):
            raise ValueError("Invalid tuplet ratio")
        if guide_version < 276 and normal != default_normal:
            raise ValueError("Explicit tuplet ratio requires guide276")
        stem = row.get("stemDirection", 0)
        if type(stem) is not int or stem not in (-1, 0, 1):
            raise ValueError("Invalid printed stem direction")
        if guide_version < 281 and (stem != 0 or row.get("clefBottomDiatonic") in (22, 24)):
            raise ValueError("Printed stem direction and C clefs require guide281")
        kind = row.get("kind", "PITCHED")
        if type(kind) is not str or kind not in ("PITCHED", "UNPITCHED"):
            raise ValueError("Invalid attack kind")
        if guide_version < 282 and kind != "PITCHED":
            raise ValueError("Unpitched attacks require guide282")
        normalized_notes.append(dict(row, boundaryTies=row.get("boundaryTies", 0),
                                     tupletNormalNotes=normal, stemDirection=stem,
                                     kind=0 if kind == "PITCHED" else 1))
    _records(output, normalized_notes, note_fields, 250_000)
    _records(output, score["keyChanges"], (("measureIndex", "i"), ("fifths", "i")), len(measures))
    _records(output, score["tempoChanges"], (("measureIndex", "i"),
        ("positionInMeasure", "f"), ("bpm", "d"), ("beatUnit", "d")), len(measures) * 2)
    _records(output, score["meterChanges"], (("measureIndex", "i"),
        ("numerator", "i"), ("denominator", "i")), len(measures))
    _records(output, score["rests"], (("measureIndex", "i"),
        ("positionInMeasure", "f"), ("pageY", "f"), ("pageHeight", "f"),
        ("staffIndex", "i"), ("staffCount", "i"), ("durationBeats", "d")), 250_000)
    _records(output, score["techniqueChanges"], (("measureIndex", "i"),
        ("positionInMeasure", "f"), ("staffIndex", "i"), ("staffCount", "i"),
        ("technique", "i")), len(measures) * 16)
    dynamics = score["dynamicChanges"]
    if not isinstance(dynamics, list) or any(not isinstance(row, dict)
            or type(row.get("fixedTarget", False)) is not bool
            or type(row.get("sharedTiming", row.get("sharedStaffs", False))) is not bool for row in dynamics):
        raise ValueError("Invalid dynamic target")
    dynamic_fields = (("measureIndex", "i"), ("positionInMeasure", "f"),
        ("staffIndex", "i"), ("staffCount", "i"), ("endMeasureIndex", "i"),
        ("endPosition", "f"), ("decibels", "f"), ("direction", "i"), ("sharedStaffs", "?"))
    if guide_version >= 261:
        dynamic_fields += (("fixedTarget", "?"), ("sharedTiming", "?"))
    _records(output, [dict(row, fixedTarget=row.get("fixedTarget", False),
                          sharedTiming=row.get("sharedTiming", False) or row.get("sharedStaffs", False))
                      for row in dynamics], dynamic_fields, len(measures) * 32)
    directions = score.get("playbackDirections", [])
    expressions = score.get("expressiveEvents", [])
    if not isinstance(directions,list) or not isinstance(expressions,list):
        raise ValueError("Invalid semantic record lists")
    if guide_version >= 263:
        output.write(semantic_wire.encode(directions, expressions, len(measures), guide_version))
    else:
        if guide_version==260 and directions:
            raise ValueError("Guide260 cannot preserve playback directions")
        if expressions or any(semantic_wire.direction(row,len(measures))['details'] != semantic_wire.DEFAULT_DETAILS
                              or row['kind'] > 3 for row in directions):
            raise ValueError("Legacy guide format would lose semantic records")
    if guide_version < 263 and (not isinstance(directions, list) or any(
            not isinstance(row, dict) or type(row.get("measureBoundary")) is not int
            or not 0 <= row["measureBoundary"] <= len(measures)
            or type(row.get("kind")) is not int or not 0 <= row["kind"] <= 3
            for row in directions)):
        raise ValueError("Invalid playback direction")
    if 261 <= guide_version < 263:
        _records(output, directions, (("measureBoundary", "i"), ("kind", "i")), (len(measures)+1)*4)
    if output.tell() > MAX_GUIDE_BYTES:
        raise ValueError("Guide exceeds cache limit")
    return output.getvalue()


def write_new(path, score, guide_version=GUIDE_VERSION):
    """Create one candidate guide without replacing any existing result."""
    path = Path(path)
    if path.exists():
        raise FileExistsError(path)
    data = encode(score, guide_version)
    path.parent.mkdir(parents=True, exist_ok=True)
    descriptor, temporary = tempfile.mkstemp(prefix=".guide-", dir=path.parent)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
