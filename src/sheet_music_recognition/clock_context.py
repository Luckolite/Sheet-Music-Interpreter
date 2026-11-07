# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Guide299 typed source-clock context, strictly after the complete Guide298 body.

An observation or geometric model does not select a timing policy. All unbound,
contrary and unavailable requirements remain explicit records.
"""
import io
import math
import re
import struct
MAGIC = b'CTX1'
MAX_BYTES = 4 * 1024 * 1024
_SCHEMA = {'Origin': [['documentSha256', 's'], ['physicalPageIndex', 'i'], ['physicalPageCount', 'i'], ['width', 'i'], ['height', 'i'], ['rasterSha256', 's'], ['labelsSha256', 's']], 'Box': [['left', 'f'], ['top', 'f'], ['right', 'f'], ['bottom', 'f']], 'RasterBox': [['left', 'i'], ['top', 'i'], ['right', 'i'], ['bottom', 'i']], 'PartWord': [['ordinal', 'i'], ['system', 'i'], ['firstMeasure', 'i'], ['measureAfterLast', 'i'], ['text', 's'], ['textSha256', 's'], ['canonicalPart', 's'], ['bounds', 'Box'], ['role', 'EWordRole'], ['sampledMembers', 'i'], ['sampledSha256', 's'], ['status', 'EStatus']], 'NumberRead': [['ordinal', 'i'], ['system', 'i'], ['text', 's'], ['value', 'i'], ['originCandidate', 'i'], ['bounds', 'Box'], ['route', 'ENumberRoute'], ['status', 'EStatus']], 'MeterRead': [['ordinal', 'i'], ['measure', 'i'], ['text', 's'], ['numerator', 'i'], ['denominator', 'i'], ['bounds', 'RasterBox'], ['firstLine', 'f'], ['gap', 'f'], ['votes', 'i'], ['sampledMembers', 'i'], ['sampledSha256', 's'], ['route', 'EMeterRoute'], ['originalProducerAccepted', 'b'], ['status', 'EStatus']], 'PrintedValue': [['noteIndex', 'i'], ['measure', 'i'], ['staff', 'i'], ['staffs', 'i'], ['kind', 'EValueKind'], ['baseBeats', 'f'], ['dots', 'i'], ['beams', 'i'], ['route', 'EValueRoute'], ['originalExit', 's'], ['admissionFlags', 'i'], ['selectedMembers', 'i'], ['parentMembers', 'i'], ['parentLeaves', 'i'], ['residualMembers', 'i'], ['selectedSha256', 's'], ['parentSha256', 's'], ['model', 'EModel'], ['status', 'EStatus']], 'GlyphLedger': [['measure', 'i'], ['semanticLabel', 'i'], ['population', 'l'], ['coveredUnique', 'l'], ['noteMembers', 'l'], ['nonRhythmicMembers', 'l'], ['restMembers', 'l'], ['unknownMembers', 'l'], ['coverageSha256', 's'], ['producerRoute', 's'], ['status', 'EStatus']], 'RestValue': [['restIndex', 'i'], ['measure', 'i'], ['staff', 'i'], ['staffs', 'i'], ['kind', 'ERestKind'], ['baseBeats', 'f'], ['dots', 'i'], ['membersSha256', 's'], ['producerRoute', 's'], ['status', 'EStatus']], 'VoiceGroup': [['measure', 'i'], ['staff', 'i'], ['staffs', 'i'], ['voiceId', 's'], ['ordinal', 'i'], ['kind', 'EGroupKind'], ['noteIndices', 'Li'], ['restIndices', 'Li'], ['membersSha256', 's'], ['sourceRoute', 's'], ['status', 'EStatus']], 'SyncAnchor': [['measure', 'i'], ['firstVoice', 's'], ['firstThroughOrdinal', 'i'], ['secondVoice', 's'], ['secondThroughOrdinal', 'i'], ['kind', 'ESyncKind'], ['bounds', 'Box'], ['membersSha256', 's'], ['sourceRoute', 's'], ['status', 'EStatus']], 'Missing': [['requirement', 'ERequirement'], ['measure', 'i']]}
_LISTS = [['partWords', 'PartWord', 10000], ['numbers', 'NumberRead', 20000], ['meters', 'MeterRead', 10000], ['printedValues', 'PrintedValue', 100000], ['glyphLedgers', 'GlyphLedger', 100000], ['restValues', 'RestValue', 100000], ['voiceGroups', 'VoiceGroup', 100000], ['syncAnchors', 'SyncAnchor', 100000], ['missing', 'Missing', 100000]]
_ENUMS = {'Status': ['UNKNOWN', 'OBSERVATION', 'SOURCE_PREMISE', 'CONTRARY', 'UNREAD', 'UNSUPPORTED'], 'WordRole': ['OUTSIDE_PART_SLOT', 'PART_LABEL', 'UNKNOWN_LABEL_RIVAL'], 'NumberRoute': ['OUTSIDE_HEADER', 'TRUSTED_ASSIGNED', 'SINGLE_MEASURE_ONE_RETURN', 'UNASSIGNED_HEADER_OBSERVATION', 'UNREAD_HEADER_WORD'], 'MeterRoute': ['RASTER_UNBOUNDED', 'READER_BOUNDED', 'UNREAD_CANDIDATE', 'UNKNOWN', 'HEADER_MODEL_EXCLUSION'], 'ValueKind': ['UNKNOWN', 'WHOLE', 'HALF', 'QUARTER', 'BEAMED'], 'ValueRoute': ['UNKNOWN', 'OPAQUE_DEFAULT', 'OPEN_WITH_STEM', 'OPEN_WITHOUT_STEM', 'BEAM_INPUT', 'FILLED_PHYSICAL_TREMOLO_ENDPOINT'], 'Model': ['NONE', 'GEOMETRIC_ELLIPSE_SOURCE_CONTACT'], 'RestKind': ['UNKNOWN', 'FULL_BAR', 'WHOLE', 'HALF', 'QUARTER', 'EIGHTH', 'SIXTEENTH', 'THIRTY_SECOND', 'SIXTY_FOURTH'], 'GroupKind': ['ATTACK_CHORD', 'REST', 'GRACE'], 'SyncKind': ['SAME_PRINTED_ATTACK_COLUMN', 'SHARED_BEAM_BOUNDARY'], 'Requirement': ['DOCUMENT_ORIGIN', 'PART_CONTINUITY', 'METER_SCOPE', 'PRINTED_VALUES', 'RHYTHMIC_GLYPH_CLOSURE', 'VOICE_OWNERSHIP', 'REST_OWNERSHIP', 'SYNCHRONIZED_ANCHORS', 'CAPTURE_BUDGET']}

_JAVA_SPACES = frozenset(range(9,14)) | frozenset(range(28,33)) | {0x1680,0x2028,0x2029,0x205f,0x3000} | frozenset(range(0x2000,0x2007)) | frozenset(range(0x2008,0x200b))
def _blank(value):
    return all(ord(c)in _JAVA_SPACES for c in value)

def _i(v, lo=-2147483648, hi=2147483647):
    if type(v) is not int or not lo <= v <= hi: raise ValueError('Invalid clock integer')
    return v

def _f(v, lo=-3.4028235e38, hi=3.4028235e38):
    if type(v) not in (int,float) or not math.isfinite(v) or not lo <= v <= hi:
        raise ValueError('Invalid clock float')
    try: return struct.unpack('>f',struct.pack('>f',v))[0]
    except (OverflowError,struct.error) as e: raise ValueError('Invalid clock float') from e

def _text(v, maximum=1024):
    if not isinstance(v,str) or '\0' in v: raise ValueError('Invalid clock text')
    try:
        if len(v.encode('utf-16-be'))//2 > maximum or len(v.encode('utf-8')) > 4096:
            raise ValueError('Clock text limit')
    except UnicodeError as e: raise ValueError('Invalid clock unicode') from e
    return v

def _hash(v):
    if not isinstance(v,str) or (v and not re.fullmatch('[0-9a-f]{64}',v)):
        raise ValueError('Invalid clock source hash')
    return v

def _list(v, maximum):
    if not isinstance(v,list) or len(v)>maximum: raise ValueError('Clock collection limit')
    return v

def _keys(v, expected):
    if not isinstance(v,dict) or set(v)!=set(expected): raise ValueError('Invalid clock record fields')

def _record(kind, value):
    annotation=None
    if kind=='Origin' and isinstance(value,dict):
        value=dict(value);annotation=value.pop('annotationRasterSha256',value.get('rasterSha256',''))
    fields=_SCHEMA[kind];_keys(value,[name for name,t in fields]);v={}
    for name,t in fields:
        x=value[name]
        if t=='i': x=_i(x)
        elif t=='l': x=_i(x,-9223372036854775808,9223372036854775807)
        elif t=='f': x=_f(x)
        elif t=='s':
            x=_text(x,160 if name=='canonicalPart' else 256 if name in ('originalExit','voiceId','firstVoice','secondVoice','producerRoute','sourceRoute') else 1024)
            if name.endswith('Sha256'): _hash(x)
        elif t=='b':
            if type(x) is not bool: raise ValueError('Invalid clock boolean')
        elif t=='Li':
            x=[_i(n,0,999999)for n in _list(x,128)]
            if len(set(x))!=len(x): raise ValueError('Duplicate clock index')
        elif t.startswith('E'):
            if x not in _ENUMS[t[1:]] or not isinstance(x,str): raise ValueError('Invalid clock enum')
        else:x=_record(t,x)
        v[name]=x
    if kind=='Origin':
        _hash(annotation);v['annotationRasterSha256']=annotation
        for n,lo,hi in [('physicalPageIndex',-1,9999),('physicalPageCount',0,10000),('width',0,20000000),('height',0,20000000)]:_i(v[n],lo,hi)
        if (v['width']==0)!=(v['height']==0) or v['width']*v['height']>20000000 or (v['physicalPageCount']>0 and v['physicalPageIndex']>=v['physicalPageCount']):raise ValueError('Clock origin domain')
    elif kind=='Box':
        for n in ('left','top','right','bottom'):_f(v[n],0,1)
        if v['left']>v['right'] or v['top']>v['bottom']:raise ValueError('Clock box order')
    elif kind=='RasterBox':
        if list(v.values())!=[-1,-1,-1,-1]:
            for n in ('left','top','right','bottom'):_i(v[n],0,20000000)
            if v['left']>v['right'] or v['top']>v['bottom']:raise ValueError('Clock raster box')
    elif kind=='PartWord':
        for n,lo,hi in [('ordinal',0,9999),('system',-1,9999),('firstMeasure',-1,10000),('measureAfterLast',-1,10000),('sampledMembers',0,1000000)]:_i(v[n],lo,hi)
        if v['firstMeasure']>=0 and v['measureAfterLast']<=v['firstMeasure']:raise ValueError('Part span')
        if v['status']=='SOURCE_PREMISE' and (v['role']!='PART_LABEL' or _blank(v['canonicalPart']) or not v['sampledMembers'] or not v['sampledSha256'] or v['bounds']['left']==v['bounds']['right'] or v['bounds']['top']==v['bounds']['bottom']):raise ValueError('Unbound part premise')
    elif kind=='NumberRead':
        for n,lo,hi in [('ordinal',0,19999),('system',-1,9999),('value',0,9999),('originCandidate',-10000,10000)]:_i(v[n],lo,hi)
    elif kind=='MeterRead':
        for n,lo,hi in [('ordinal',0,9999),('measure',-1,10000),('numerator',0,128),('denominator',0,128),('votes',0,10000),('sampledMembers',0,1000000)]:_i(v[n],lo,hi)
        _f(v['firstLine'],-1,20000000);_f(v['gap'],0,20000000)
        if v['status']=='SOURCE_PREMISE' and (not v['originalProducerAccepted'] or not v['numerator'] or not v['denominator'] or v['bounds']['left']<0 or not v['sampledMembers'] or not v['sampledSha256']):raise ValueError('Unbound meter premise')
    elif kind in ('PrintedValue','RestValue','VoiceGroup'):
        for n,lo,hi in [('measure',0,9999),('staff',0,127),('staffs',1,128)]:_i(v[n],lo,hi)
        if v['staff']>=v['staffs']:raise ValueError('Clock staff')
        if kind=='PrintedValue':
            for n,lo,hi in [('noteIndex',0,999999),('dots',0,4),('beams',0,8),('admissionFlags',0,65535),('selectedMembers',0,1000000),('parentMembers',v['selectedMembers'],1000000),('parentLeaves',0,10000),('residualMembers',0,v['parentMembers'])]:_i(v[n],lo,hi)
            _f(v['baseBeats'],0,32)
            if v['status']=='SOURCE_PREMISE' and (not v['selectedMembers'] or not v['parentLeaves'] or v['residualMembers'] or not v['selectedSha256'] or not v['parentSha256']):raise ValueError('Unbound printed premise')
        elif kind=='RestValue':_i(v['restIndex'],0,999999);_f(v['baseBeats'],0,64);_i(v['dots'],0,4)
        else:
            _i(v['ordinal'],0,999999)
            bad_members=(bool(v['noteIndices']) or not v['restIndices'])if v['kind']=='REST' else (not v['noteIndices'] or bool(v['restIndices']))
            if v['status']=='SOURCE_PREMISE' and (_blank(v['voiceId']) or not v['membersSha256'] or _blank(v['sourceRoute']) or bad_members):raise ValueError('Unbound voice premise')
    elif kind=='GlyphLedger':
        _i(v['measure'],-1,9999);_i(v['semanticLabel'],0,255)
        for n in ('population','coveredUnique','noteMembers','nonRhythmicMembers','restMembers','unknownMembers'):_i(v[n],0,20000000)
        assigned=v['noteMembers']+v['nonRhythmicMembers']+v['restMembers']
        if v['coveredUnique']>v['population'] or assigned+v['unknownMembers']>v['coveredUnique']:raise ValueError('Clock accounting')
        if v['status']=='SOURCE_PREMISE' and (v['coveredUnique']!=v['population'] or v['unknownMembers'] or assigned!=v['coveredUnique'] or not v['coverageSha256']):raise ValueError('Incomplete glyph premise')
    elif kind=='SyncAnchor':
        _i(v['measure'],0,9999);_i(v['firstThroughOrdinal'],0,999999);_i(v['secondThroughOrdinal'],0,999999)
        if v['status']=='SOURCE_PREMISE' and v['firstVoice']==v['secondVoice']:raise ValueError('Self synchronization')
    elif kind=='Missing':_i(v['measure'],-1,9999)
    return v

def unavailable(note_count,measure_count,rest_count):
    return dict(origin=dict(documentSha256='',physicalPageIndex=-1,physicalPageCount=0,width=0,height=0,rasterSha256='',labelsSha256=''),noteCount=note_count,measureCount=measure_count,restCount=rest_count,**{name:[]for name,_,_ in _LISTS if name!='missing'},missing=[dict(requirement=n,measure=-1)for n in _ENUMS['Requirement']if n!='CAPTURE_BUDGET'])

def checked(source,note_count,measure_count,rest_count):
    _i(note_count,0,1000000);_i(measure_count,0,10000);_i(rest_count,0,1000000)
    if source is None:source=unavailable(note_count,measure_count,rest_count)
    _keys(source,['origin','noteCount','measureCount','restCount']+[name for name,_,_ in _LISTS])
    if [source['noteCount'],source['measureCount'],source['restCount']]!=[note_count,measure_count,rest_count]:raise ValueError('Clock page counts')
    for key,maximum in [('noteCount',1000000),('measureCount',10000),('restCount',1000000)]:_i(source[key],0,maximum)
    value=dict(origin=_record('Origin',source['origin']),noteCount=note_count,measureCount=measure_count,restCount=rest_count)
    for name,record,maximum in _LISTS:value[name]=[_record(record,row)for row in _list(source[name],maximum)]
    if sum(len(value[name])for name,_,_ in _LISTS)>100000:raise ValueError('Clock fact budget')
    for p in value['partWords']:
        if (p['firstMeasure']>=0 and p['firstMeasure']>=measure_count)or p['measureAfterLast']>measure_count:raise ValueError('Part outside page')
    for m in value['meters']:
        if m['measure']>measure_count or (m['bounds']['left']>=0 and (value['origin']['width']==0 or m['bounds']['right']>=value['origin']['width'] or m['bounds']['bottom']>=value['origin']['height'])):raise ValueError('Meter outside source')
    for name,idx,limit in [('printedValues','noteIndex',note_count),('restValues','restIndex',rest_count)]:
        used=set()
        for v in value[name]:
            if v[idx]>=limit or v['measure']>=measure_count or v[idx]in used:raise ValueError('Clock value owner')
            used.add(v[idx])
    for v in value['glyphLedgers']:
        if v['measure']>=measure_count:raise ValueError('Ledger outside page')
    groups=set()
    for g in value['voiceGroups']:
        key=(g['measure'],g['voiceId'],g['ordinal'])
        if g['measure']>=measure_count or key in groups or any(n>=note_count for n in g['noteIndices'])or any(n>=rest_count for n in g['restIndices']):raise ValueError('Voice group identity')
        groups.add(key)
    for v in value['syncAnchors']:
        if v['measure']>=measure_count:raise ValueError('Sync outside page')
    missing=set()
    for v in value['missing']:
        key=(v['requirement'],v['measure'])
        if v['measure']>=measure_count or key in missing:raise ValueError('Missing source identity')
        missing.add(key)
    return value

def require_legacy_compatible(source,note_count,measure_count,rest_count):
    value=checked(source,note_count,measure_count,rest_count)
    if value!=checked(unavailable(note_count,measure_count,rest_count),note_count,measure_count,rest_count):raise ValueError('Legacy guide cannot preserve source clock context')

def bind_document(source,note_count,measure_count,rest_count,document_sha,page_index,page_count):
    """Attach the actual held-document envelope without replacing producer raster hashes.

    A legacy/unavailable page gains an origin observation, never voice or meter authority.
    Conflicting existing provenance fails rather than being relabelled for another document.
    """
    value=checked(source,note_count,measure_count,rest_count)
    _hash(document_sha);_i(page_index,0,9999);_i(page_count,1,10000)
    if not document_sha or page_index>=page_count:raise ValueError('Unbound document origin')
    origin=value['origin']
    if (origin['documentSha256'] and origin['documentSha256']!=document_sha
        or origin['physicalPageIndex']>=0 and origin['physicalPageIndex']!=page_index
        or origin['physicalPageCount']>0 and origin['physicalPageCount']!=page_count):
        raise ValueError('Foreign source document/page')
    result=dict(value,origin=dict(origin,documentSha256=document_sha,
                physicalPageIndex=page_index,physicalPageCount=page_count))
    # DOCUMENT_ORIGIN remains missing without a genuine raster producer frame.
    if origin['rasterSha256'] and origin['width'] and origin['height']:
        result['missing']=[m for m in value['missing']if not(m['requirement']=='DOCUMENT_ORIGIN'and m['measure']==-1)]
    return checked(result,note_count,measure_count,rest_count)

class _Writer:
    def __init__(self):self.data=bytearray()
    def put(self,b):
        if len(b)>MAX_BYTES-len(self.data):raise ValueError('Clock frame budget')
        self.data.extend(b)
    def record(self,kind,value):
        for name,t in _SCHEMA[kind]:
            v=value[name]
            if t in ('i','l','f'):self.put(struct.pack('>'+{'i':'i','l':'q','f':'f'}[t],v))
            elif t=='s':
                b=v.encode('utf-8');self.put(struct.pack('>i',len(b)));self.put(b)
            elif t=='b':self.put(bytes([int(v)]))
            elif t=='Li':
                self.put(struct.pack('>i',len(v)))
                for n in v:self.put(struct.pack('>i',n))
            elif t.startswith('E'):self.put(bytes([_ENUMS[t[1:]].index(v)]))
            else:self.record(t,v)

class _Reader:
    def __init__(self,data):self.data=memoryview(data);self.at=0
    def take(self,n):
        if n<0 or n>len(self.data)-self.at:raise ValueError('Truncated clock context')
        out=self.data[self.at:self.at+n];self.at+=n;return out
    def scalar(self,t):return struct.unpack('>'+t,self.take(struct.calcsize('>'+t)))[0]
    def count(self,maximum):
        n=self.scalar('i')
        if n<0 or n>maximum or n>len(self.data)-self.at:raise ValueError('Clock collection count')
        return n
    def record(self,kind):
        value={}
        for name,t in _SCHEMA[kind]:
            if t in ('i','l','f'):v=self.scalar({'i':'i','l':'q','f':'f'}[t])
            elif t=='s':
                n=self.scalar('i')
                if not 0<=n<=4096:raise ValueError('Clock text length')
                try:v=bytes(self.take(n)).decode('utf-8','strict')
                except UnicodeError as e:raise ValueError('Invalid clock unicode')from e
            elif t=='b':
                n=self.scalar('B')
                if n>1:raise ValueError('Clock boolean')
                v=bool(n)
            elif t=='Li':v=[self.scalar('i')for _ in range(self.count(128))]
            elif t.startswith('E'):
                n=self.scalar('B');enum=_ENUMS[t[1:]]
                if n>=len(enum):raise ValueError('Clock enum')
                v=enum[n]
            else:v=self.record(t)
            value[name]=v
        return value

def encode(source,note_count,measure_count,rest_count):
    value=checked(source,note_count,measure_count,rest_count);out=_Writer();origin=value['origin'];schema=1 if origin['annotationRasterSha256']==origin['rasterSha256'] else 2
    out.put(struct.pack('>iiii',schema,note_count,measure_count,rest_count));out.record('Origin',origin)
    if schema==2:
        annotation=origin['annotationRasterSha256'].encode('utf-8');out.put(struct.pack('>i',len(annotation)));out.put(annotation)
    for name,record,_ in _LISTS:
        rows=value[name];out.put(struct.pack('>i',len(rows)))
        for row in rows:out.record(record,row)
    return MAGIC+struct.pack('>i',len(out.data))+out.data

def decode(frame,note_count,measure_count,rest_count):
    if not isinstance(frame,(bytes,bytearray))or not 88<=len(frame)<=MAX_BYTES+8 or frame[:4]!=MAGIC:raise ValueError('Clock frame header')
    n=struct.unpack_from('>i',frame,4)[0]
    if n!=len(frame)-8:raise ValueError('Clock frame length')
    source=_Reader(frame[8:])
    schema=source.scalar('i')
    if schema not in (1,2):raise ValueError('Clock schema')
    counts=[source.scalar('i')for _ in range(3)]
    if counts!=[note_count,measure_count,rest_count]:raise ValueError('Clock page counts')
    origin=source.record('Origin')
    if schema==2:
        n=source.scalar('i')
        if not 0<=n<=4096:raise ValueError('Clock annotation hash length')
        origin['annotationRasterSha256']=bytes(source.take(n)).decode('utf-8','strict')
    value=dict(origin=origin,noteCount=counts[0],measureCount=counts[1],restCount=counts[2]);total=0
    for name,record,maximum in _LISTS:
        n=source.count(maximum);total+=n
        if total>100000:raise ValueError('Clock fact budget')
        value[name]=[source.record(record)for _ in range(n)]
    if source.at!=len(source.data):raise ValueError('Trailing clock context')
    return checked(value,note_count,measure_count,rest_count)
