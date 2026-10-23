# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""MRG1 printed multi-rest identity. Never infer groups from empty/repeated bars."""
import struct
FIELDS=("firstMeasureIndex","measureCount","staffIndex","staffCount")
MAGIC=0x4D524731
MAX_GROUPS=2048

def checked(groups, measures):
    if groups is None: groups=[]
    if type(measures) is not int or not 0 <= measures <= 100000 or not isinstance(groups,list) or len(groups)>MAX_GROUPS:
        raise ValueError("Invalid printed rest group dimensions")
    used=set(); result=[]
    for g in groups:
        if not isinstance(g,dict) or set(g)!=set(FIELDS) or any(type(g[k]) is not int for k in FIELDS):
            raise ValueError("Invalid printed rest group record")
        first,count,staff,staffs=(g[k] for k in FIELDS)
        if not (0<=first<=measures-count and 2<=count<=32 and 0<=staff<staffs<=32):
            raise ValueError("Printed rest group outside score")
        for m in range(first,first+count):
            key=(m,staff,staffs)
            if key in used: raise ValueError("Overlapping printed rest groups")
            used.add(key)
        result.append(dict(g))
    return result

def checked_score(score):
    supplied=score.get("multiRestGroups")
    if supplied is None: return []
    if not isinstance(supplied,list): raise ValueError("Invalid printed rest group collection")
    if not supplied: return []
    groups=checked(supplied,len(score["measures"]))
    if len(score["notes"])>100000 or len(score["rests"])>100000:
        raise ValueError("Printed rest ownership input too large")
    note_measures={n["measureIndex"] for n in score["notes"]}
    note_lanes={(n["measureIndex"],n["staffIndex"],n["staffCount"]) for n in score["notes"]}
    full={};other=set();foreign=set()
    for rest in score["rests"]:
        key=(rest["measureIndex"],rest["staffIndex"],rest["staffCount"])
        kind=rest.get("kind")
        if ((type(kind) is str and kind=="FULL_MEASURE") or (type(kind) is int and kind==1)) and rest["durationBeats"]==4:
            full[key]=full.get(key,0)+1
        else: other.add(key)
        if rest["staffIndex"]!=0 or rest["staffCount"]!=1:foreign.add(rest["measureIndex"])
    for g in groups:
        first,count,staff,staffs=(g[k] for k in FIELDS)
        for m in range(first,first+count):
            key=(m,staff,staffs)
            if key in note_lanes or staffs==1 and m in note_measures or key in other or full.get(key)!=1 or staffs==1 and m in foreign:
                raise ValueError("Printed rest group conflicts with score")
    return groups

def encode_groups(groups, measures):
    groups=checked(groups,measures)
    return struct.pack(">iii",MAGIC,4+16*len(groups),len(groups))+b"".join(struct.pack(">iiii",*(g[k] for k in FIELDS)) for g in groups)

def decode_groups(data, measures):
    if len(data)<12: raise ValueError("Truncated printed rest frame")
    magic,length,count=struct.unpack_from(">iii",data)
    if magic!=MAGIC or not 0<=count<=MAX_GROUPS or length!=4+16*count or len(data)!=8+length:
        raise ValueError("Invalid printed rest frame")
    return checked([dict(zip(FIELDS,struct.unpack_from(">iiii",data,12+16*i)))for i in range(count)],measures)
