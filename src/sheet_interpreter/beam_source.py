# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Guide297 literal beam topology. No timing or inferred grouping is encoded."""
import io
import struct
MAX = 1_000_000
MAX_BYTES = 32 * 1024 * 1024
DIRECTIONS = ('FORWARD', 'BACKWARD')
_JAVA_SPACES = frozenset(range(9,14)) | frozenset(range(28,33)) | {0x1680,0x2028,0x2029,0x205f,0x3000} | frozenset(range(0x2000,0x2007)) | frozenset(range(0x2008,0x200b))


def empty(note_count):
    _int(note_count, 0, MAX)
    return dict(groups=[], detachedColumns=[], noteOrigins=[None] * note_count)


def _int(n, lo=0, hi=2147483647):
    if type(n) is not int or not lo <= n <= hi:
        raise ValueError('Invalid beam integer')
    return n


def _keys(value, keys):
    if not isinstance(value, dict) or set(value) != set(keys):
        raise ValueError('Invalid beam record fields')


def _list(value, maximum=MAX):
    if not isinstance(value, list) or len(value) > maximum:
        raise ValueError('Invalid beam collection')
    return value


def _anchor(a):
    _keys(a, ('pageIndex', 'noteIndex'))
    return (_int(a['pageIndex']), _int(a['noteIndex'], 0, MAX-1))


def checked(source, note_count):
    """Validate exact source facts; repeated current origins deliberately remain valid."""
    _int(note_count, 0, MAX)
    if source is None:
        return empty(note_count)
    _keys(source, ('groups', 'detachedColumns', 'noteOrigins'))
    groups = _list(source['groups']); detached = _list(source['detachedColumns'])
    origins = _list(source['noteOrigins'])
    if len(origins) != note_count:
        raise ValueError('Beam note ownership mismatch')
    for a in origins:
        if a is not None: _anchor(a)
    used = set(); ids = set(); total = len(detached)
    def column(c):
        _keys(c, ('members',)); members = _list(c['members'],128)
        if not members: raise ValueError('Empty beam column')
        page = None
        for a in members:
            key = _anchor(a)
            if page is not None and key[0] != page:
                raise ValueError('Beam column crosses physical pages')
            page = key[0]
            if key in used: raise ValueError('Duplicate physical beam ownership')
            used.add(key)
        return page
    for g in groups:
        _keys(g, ('id', 'columns', 'bridges', 'hooks'))
        name = g['id']
        if (type(name) is not str or all(ord(ch) in _JAVA_SPACES for ch in name) or len(name.encode('utf-16-be'))//2 > 256
                or len(name.encode('utf-8')) > 1024 or name in ids):
            raise ValueError('Invalid beam group ID')
        ids.add(name); columns = _list(g['columns']); bridges = _list(g['bridges']); hooks = _list(g['hooks'],MAX*8)
        if len(columns) < 2 or len(bridges) != len(columns)-1 or len(hooks) > len(columns)*8:
            raise ValueError('Invalid beam group dimensions')
        total += len(columns)
        if total > MAX: raise ValueError('Too many beam columns')
        if len({column(c) for c in columns}) != 1:
            raise ValueError('Beam group crosses physical pages')
        for mask in bridges:
            _int(mask,1,255)
            if not mask & 1: raise ValueError('Beam bridge lacks primary')
        occupied = set()
        for h in hooks:
            _keys(h, ('columnIndex', 'level', 'direction'))
            c = _int(h['columnIndex'],0,len(columns)-1); level = _int(h['level'],1,8)
            if h['direction'] not in DIRECTIONS: raise ValueError('Invalid beam hook direction')
            bit = 1 << (level-1)
            if ((c,level) in occupied or c and bridges[c-1]&bit
                    or c < len(bridges) and bridges[c]&bit):
                raise ValueError('Conflicting beam hook')
            occupied.add((c,level))
    if total > MAX: raise ValueError('Too many beam columns')
    for c in detached: column(c)
    return source


def require_legacy_compatible(source, note_count):
    source = checked(source,note_count)
    if source['groups'] or source['detachedColumns'] or any(a is not None for a in source['noteOrigins']):
        raise ValueError('Legacy guide cannot preserve source beams')


def encode_source(source, note_count):
    source = checked(source,note_count); out=io.BytesIO()
    def put(fmt,*values):
        out.write(struct.pack('>'+fmt,*values))
        if out.tell()>MAX_BYTES: raise ValueError('Beam frame too large')
    def anchor(a): put('ii',a['pageIndex'],a['noteIndex'])
    def column(c):
        put('i',len(c['members']))
        for a in c['members']: anchor(a)
    put('i',len(source['groups']))
    for g in source['groups']:
        raw=g['id'].encode('utf-8');put('i',len(raw));out.write(raw)
        put('i',len(g['columns']))
        for c in g['columns']: column(c)
        put('i',len(g['bridges']))
        for mask in g['bridges']: put('i',mask)
        put('i',len(g['hooks']))
        for h in g['hooks']: put('iii',h['columnIndex'],h['level'],DIRECTIONS.index(h['direction']))
    put('i',len(source['detachedColumns']))
    for c in source['detachedColumns']: column(c)
    put('i',len(source['noteOrigins']))
    for a in source['noteOrigins']:
        put('B',int(a is not None))
        if a is not None: anchor(a)
    payload=out.getvalue();return struct.pack('>i',len(payload))+payload


def decode_source(data, note_count):
    if not 16 <= len(data) <= MAX_BYTES+4: raise ValueError('Invalid beam frame')
    view=memoryview(data); pos=0
    def take(fmt):
        nonlocal pos
        size=struct.calcsize('>'+fmt)
        if pos+size>len(view): raise ValueError('Truncated beam frame')
        result=struct.unpack_from('>'+fmt,view,pos);pos+=size;return result
    def count(maximum=MAX):
        n=_int(take('i')[0],0,maximum)
        if n>len(view)-pos: raise ValueError('Invalid beam count')
        return n
    def anchor():
        p,n=take('ii');return dict(pageIndex=p,noteIndex=n)
    def column(): return dict(members=[anchor() for _ in range(count(128))])
    if take('i')[0] != len(data)-4: raise ValueError('Invalid beam framing')
    groups=[]; total=0
    for _ in range(count()):
        size=count(1024); name=bytes(view[pos:pos+size]).decode('utf-8');pos+=size
        n=count();total+=n
        if total>MAX: raise ValueError('Too many beam columns')
        cs=[column() for _ in range(n)]; nb=count()
        if nb!=n-1: raise ValueError('Wrong beam bridge count')
        bridges=[take('i')[0] for _ in range(nb)]; hooks=[]
        for _ in range(count(n*8)):
            c,level,d=take('iii');_int(d,0,1);hooks.append(dict(columnIndex=c,level=level,direction=DIRECTIONS[d]))
        groups.append(dict(id=name,columns=cs,bridges=bridges,hooks=hooks))
    nd=count()
    if total+nd>MAX: raise ValueError('Too many beam columns')
    detached=[column() for _ in range(nd)]; no=count()
    if no!=note_count: raise ValueError('Beam note ownership mismatch')
    origins=[]
    for _ in range(no):
        present=_int(take('B')[0],0,1); origins.append(anchor() if present else None)
    if pos!=len(view): raise ValueError('Trailing beam bytes')
    return checked(dict(groups=groups,detachedColumns=detached,noteOrigins=origins),note_count)
