# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""SLT1 source identities and SLP1 printed arcs. No recognition or endpoint inference."""
import math
import struct

MAX_NOTES = 1_000_000
MAX_SPANS = 100_000
MAX_PAGE_BYTES = 16 * 1024 * 1024
MAX_SOURCE_BYTES = 32 * 1024 * 1024
PLACEMENTS = {"AUTO": 0, "ABOVE": 1, "BELOW": 2}
LINES = {"SOLID": 0, "DASHED": 1, "DOTTED": 2}


def integer(value, minimum=0, maximum=2_147_483_647):
    if type(value) is not int or not minimum <= value <= maximum:
        raise ValueError("Invalid slur integer")
    return value


def anchor(value):
    if not isinstance(value, dict) or set(value) != {"pageIndex", "noteIndex"}:
        raise ValueError("Invalid slur source anchor")
    return integer(value["pageIndex"]), integer(value["noteIndex"])


def java_blank(value):
    # Character.isWhitespace used by Java String.isBlank, including its NBSP exclusions.
    return all(c in "\t\n\v\f\r\x1c\x1d\x1e\x1f \u1680\u2000\u2001\u2002\u2003"
                   "\u2004\u2005\u2006\u2008\u2009\u200a\u2028\u2029\u205f\u3000" for c in value)


def encode_pages(spans):
    if not isinstance(spans, list) or len(spans) > MAX_SPANS:
        raise ValueError("Invalid page-local slur count")
    output = bytearray(struct.pack(">ii", 0x534C5031, len(spans)))
    identities = set()
    for span in spans:
        if not isinstance(span, dict) or set(span) != {"id", "start", "end", "placement", "lineType", "evidence"}:
            raise ValueError("Invalid page-local slur fields")
        identity = span["id"]
        if not isinstance(identity, str) or java_blank(identity) or identity in identities:
            raise ValueError("Invalid or duplicate slur identity")
        if len(identity.encode("utf-16-be")) // 2 > 256:
            raise ValueError("Slur identity too long")
        identities.add(identity)
        encoded = identity.encode("utf-8")
        start, end = anchor(span["start"]), anchor(span["end"])
        if start == end:
            raise ValueError("Identical printed slur endpoints")
        placement, line = span["placement"], span["lineType"]
        if not isinstance(placement, str) or placement not in PLACEMENTS or not isinstance(line, str) or line not in LINES:
            raise ValueError("Invalid slur style")
        evidence = span["evidence"]
        if not isinstance(evidence, list) or not 1 <= len(evidence) <= 64:
            raise ValueError("Invalid slur evidence count")
        frame = bytearray(struct.pack(">i", len(encoded))) + encoded
        frame.extend(struct.pack(">7i", *start, *end, PLACEMENTS[placement], LINES[line], len(evidence)))
        for arc in evidence:
            if not isinstance(arc, dict) or set(arc) != {"pageIndex", "startX", "startY", "endX", "endY"}:
                raise ValueError("Invalid slur evidence fields")
            geometry = [arc[name] for name in ("startX", "startY", "endX", "endY")]
            if any(type(value) not in (int, float) or not math.isfinite(value) or not 0 <= value <= 1 for value in geometry):
                raise ValueError("Invalid slur evidence geometry")
            frame.extend(struct.pack(">i4f", integer(arc["pageIndex"]), *geometry))
        if len(frame) > 8192 or len(output) + len(frame) + 4 > MAX_PAGE_BYTES:
            raise ValueError("Page-local slur extension too large")
        output.extend(struct.pack(">i", len(frame)))
        output.extend(frame)
    return bytes(output)


def encode_source(source, note_count):
    integer(note_count, maximum=MAX_NOTES)
    if source is None:
        # An omitted extension has no inferred original identities.
        origins, spans, candidates = None, [], []
    else:
        if not isinstance(source, dict) or set(source) not in ({"noteOrigins", "spans"}, {"noteOrigins", "spans", "candidates"}):
            raise ValueError("Invalid retained slur source fields")
        origins, spans = source["noteOrigins"], source["spans"]
        candidates = source.get("candidates", [])
        if not isinstance(origins, list) or len(origins) != note_count:
            raise ValueError("Retained slur identities do not match score notes")
    stored_origins = origins if origins is not None and any(origin is not None for origin in origins) else []
    frame = bytearray(struct.pack(">ii", note_count, len(stored_origins)))
    identities = set()
    for origin in stored_origins:
        pair = (-1, -1) if origin is None else anchor(origin)
        if origin is not None and pair in identities:
            raise ValueError("Duplicate original slur note identity")
        identities.add(pair)
        frame.extend(struct.pack(">ii", *pair))
    confirmed = encode_pages(spans)
    deferred = encode_pages(candidates)
    ids = [arc['id'] for arc in spans] + [arc['id'] for arc in candidates]
    if len(set(ids)) != len(ids):
        raise ValueError("Duplicate source arc identity")
    frame.extend(confirmed)
    if candidates:
        frame.extend(deferred)
    if len(frame) > MAX_SOURCE_BYTES:
        raise ValueError("Retained slur source too large")
    return struct.pack(">ii", 0x534C5432 if candidates else 0x534C5431, len(frame)) + frame


def decode_source(data, note_count):
    # Reuse bounded primitive reads; semantic and slur sections retain distinct framing.
    from .semantic_wire import Reader
    integer(note_count, maximum=MAX_NOTES)
    source = Reader(data)
    magic = source.take('i')
    if magic not in (0x534C5431, 0x534C5432):
        raise ValueError('Unknown retained slur source layout')
    frame = Reader(source.raw(source.count(MAX_SOURCE_BYTES)))
    source.finish()
    count, stored = frame.take('ii')
    if count != note_count or stored not in (0, count):
        raise ValueError('Retained slur identities do not match source notes')
    origins = []
    for _ in range(stored):
        page, note = frame.take('ii')
        origins.append(None if (page, note) == (-1, -1) else {'pageIndex': page, 'noteIndex': note})
    if stored == 0:
        origins = [None] * count
    def pages():
        start = frame.offset
        if frame.take('i') != 0x534C5031:
            raise ValueError('Unknown page-local slur layout')
        spans = []
        for _ in range(frame.count(MAX_SPANS)):
            record = Reader(frame.raw(frame.count(8192)))
            if frame.offset - start > MAX_PAGE_BYTES:
                raise ValueError('Page-local slur section too large')
            identity = bytes(record.raw(record.count(1024))).decode('utf-8', errors='strict')
            a_page, a_note, b_page, b_note, placement, line = record.take('6i')
            evidence = []
            for _ in range(record.count(64)):
                page, a_x, a_y, b_x, b_y = record.take('i4f')
                evidence.append(dict(pageIndex=page, startX=a_x, startY=a_y, endX=b_x, endY=b_y))
            record.finish()
            integer(placement, maximum=2); integer(line, maximum=2)
            spans.append(dict(id=identity, start=dict(pageIndex=a_page, noteIndex=a_note),
                              end=dict(pageIndex=b_page, noteIndex=b_note),
                              placement=list(PLACEMENTS)[placement], lineType=list(LINES)[line], evidence=evidence))
        return spans
    spans = pages()
    candidates = pages() if magic == 0x534C5432 else []
    frame.finish()
    result = dict(noteOrigins=origins, spans=spans)
    if candidates:
        result["candidates"] = candidates
    encode_source(result, count)  # Apply the same identity, geometry and uniqueness validation.
    return result


def is_empty_source(source, note_count):
    """Validate all fields before proving that omitting this extension loses nothing."""
    return encode_source(source, note_count) == encode_source(None, note_count)
