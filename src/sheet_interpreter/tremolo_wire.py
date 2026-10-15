# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Private TRS1 source extension. Does not change the guide version."""
import math
import struct


def decode_source(data, note_count):
    """Read one complete TRS1 frame, validating the same ownership as the writer."""
    data = memoryview(data)
    if len(data) < 8:
        raise ValueError('Truncated tremolo frame')
    magic, length = struct.unpack_from('>ii', data)
    if magic != 0x54525331 or not 0 <= length <= 32*1024*1024 or length != len(data)-8:
        raise ValueError('Invalid tremolo frame')
    offset = 8

    def take(code):
        nonlocal offset
        size = struct.calcsize('>'+code)
        if size > len(data)-offset:
            raise ValueError('Truncated tremolo record')
        values = struct.unpack_from('>'+code, data, offset)
        offset += size
        return values

    def count(maximum):
        return integer(take('i')[0], maximum=maximum)

    notes, stored = count(1_000_000), count(1_000_000)
    if notes != note_count or stored not in (0, notes):
        raise ValueError('Tremolo identity count mismatch')
    origins = []
    for _ in range(stored):
        page, note = take('ii')
        origins.append(None if (page, note) == (-1, -1) else dict(pageIndex=page, noteIndex=note))
    if not stored:
        origins = [None]*notes
    pairs = []
    for _ in range(count(100_000)):
        size = count(1024)
        if size > len(data)-offset:
            raise ValueError('Truncated tremolo identity')
        name = bytes(data[offset:offset+size]).decode('utf-8', errors='strict')
        offset += size
        pair = dict(id=name, strokes=count(8))
        for side in ('first', 'second'):
            pair[side] = [dict(zip(('pageIndex','noteIndex'), take('ii'))) for _ in range(count(128))]
        pair['evidence'] = [dict(zip(('pageIndex','left','top','right','bottom'), take('i4f')))
                            for _ in range(count(64))]
        pairs.append(pair)
    if offset != len(data):
        raise ValueError('Unexpected tremolo bytes')
    result = dict(pairs=pairs, noteOrigins=origins)
    encode_source(result, note_count)
    return result

def integer(value, minimum=0, maximum=2_147_483_647):
    if type(value) is not int or not minimum<=value<=maximum:
        raise ValueError('Invalid tremolo integer')
    return value

def anchor(value):
    if not isinstance(value,dict) or set(value)!={'pageIndex','noteIndex'}:
        raise ValueError('Invalid tremolo anchor')
    return integer(value['pageIndex']),integer(value['noteIndex'])

def sequence(value,minimum,maximum):
    if not isinstance(value,list) or not minimum<=len(value)<=maximum:
        raise ValueError('Invalid tremolo sequence')
    return value

def encode_source(source,note_count):
    integer(note_count,maximum=1_000_000)
    if source is None:source={'pairs':[],'noteOrigins':[None]*note_count}
    if not isinstance(source,dict) or set(source)!={'pairs','noteOrigins'}:
        raise ValueError('Invalid tremolo source fields')
    origins=sequence(source['noteOrigins'],note_count,note_count)
    pairs=sequence(source['pairs'],0,100_000)
    stored=origins if any(o is not None for o in origins) else []
    data=bytearray(struct.pack('>ii',note_count,len(stored)));seen=set()
    for origin in stored:
        point=(-1,-1) if origin is None else anchor(origin)
        if origin is not None:
            if point in seen:raise ValueError('Duplicate tremolo origin')
            seen.add(point)
        data.extend(struct.pack('>ii',*point))
    data.extend(struct.pack('>i',len(pairs)));ids=set();owners=set()
    java_whitespace='\t\n\v\f\r\x1c\x1d\x1e\x1f \u1680\u2000\u2001\u2002\u2003\u2004\u2005\u2006\u2008\u2009\u200a\u2028\u2029\u205f\u3000'
    for pair in pairs:
        if not isinstance(pair,dict) or set(pair)!={'id','first','second','strokes','evidence'}:
            raise ValueError('Invalid tremolo pair fields')
        name=pair['id']
        if not isinstance(name,str) or all(c in java_whitespace for c in name) or name in ids:
            raise ValueError('Invalid tremolo identity')
        if len(name.encode('utf-16-be'))//2>256:raise ValueError('Long tremolo identity')
        ids.add(name);encoded=name.encode('utf-8')
        data.extend(struct.pack('>i',len(encoded)));data.extend(encoded)
        data.extend(struct.pack('>i',integer(pair['strokes'],1,8)))
        for key in ('first','second'):
            points=[anchor(v) for v in sequence(pair[key],1,128)]
            data.extend(struct.pack('>i',len(points)))
            for point in points:
                if point in owners:raise ValueError('Ambiguous tremolo endpoint')
                owners.add(point);data.extend(struct.pack('>ii',*point))
        evidence=sequence(pair['evidence'],1,64);data.extend(struct.pack('>i',len(evidence)))
        for proof in evidence:
            if not isinstance(proof,dict) or set(proof)!={'pageIndex','left','top','right','bottom'}:
                raise ValueError('Invalid tremolo evidence')
            coordinates=[proof[k] for k in ('left','top','right','bottom')]
            if any(type(v) not in (float,int) or not math.isfinite(v) or not 0<=v<=1 for v in coordinates):
                raise ValueError('Invalid tremolo geometry')
            # Validate float32 geometry as the Java record will see it.
            coordinates=struct.unpack('>4f',struct.pack('>4f',*coordinates))
            if coordinates[0]>=coordinates[2] or coordinates[1]>=coordinates[3]:
                raise ValueError('Empty tremolo evidence')
            data.extend(struct.pack('>i4f',integer(proof['pageIndex']),*coordinates))
        if len(data)>32*1024*1024:raise ValueError('Tremolo source too large')
    return struct.pack('>ii',0x54525331,len(data))+data
