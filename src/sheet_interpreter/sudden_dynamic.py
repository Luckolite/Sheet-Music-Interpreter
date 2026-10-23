# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Complete sudden labels paired through existing exported source ownership."""
import re
LEVEL=r'(ppp|pp|p|mp|mf|fff|ff|f)'
PREFIX=re.compile(r'^(subito\.?|sub\.?)\s*'+LEVEL+r'[.,:;]?$',re.I)
SUFFIX=re.compile(r'^'+LEVEL+r'\s+(subito\.?|sub\.?)[,:;]?$',re.I)

def parse(text):
    if not isinstance(text,str):return None
    text=text.strip();p=PREFIX.fullmatch(text)
    if p:return dict(level=p[2].lower(),qualifier=p[1],before=True)
    p=SUFFIX.fullmatch(text)
    return dict(level=p[1].lower(),qualifier=p[2],before=False)if p else None

def combine(marks):
    plans={};blocked=set()
    for d in marks:
        if not d.get('_absoluteDynamic') or not d.get('ownerIds'):continue
        candidates=[]
        for e in marks:
            if (e.get('_absoluteDynamic') or e.get('kind')!='UNRESOLVED_DIRECTION'
                    or e.get('scope')not in ('PART','UNRESOLVED')
                    or e.get('staffIndex')!=d.get('staffIndex') or e.get('staffCount')!=d.get('staffCount')
                    or not(e.get('targetEventId')or'').startswith('expression-column:')
                    or set(e.get('ownerIds',[]))!=set(d['ownerIds'])):continue
            texts=[]
            for v in e.get('evidence',[]):
                if (v.get('sourceId')!='printed-expression-word' or v.get('staffIndex')!=e.get('staffIndex')
                        or v.get('staffCount')!=e.get('staffCount')):continue
                p=parse(v.get('printedText'))
                if p and p['level']==d['_absoluteDynamic']:texts.append(p)
            if texts and all(p==texts[0]for p in texts):candidates.append((e['identity'],texts[0]))
        if len(candidates)>1:blocked.add(d['_dynamicGroup'])
        if len(candidates)==1:plans.setdefault(d['_dynamicGroup'],[]).append(candidates[0])
    valid={g:values for g,values in plans.items()if g not in blocked and all(v[1]==values[0][1]for v in values)}
    claims={}
    for group,values in valid.items():
        for identity,_ in values:claims.setdefault(identity,set()).add(group)
    valid={g:vs for g,vs in valid.items()if all(len(claims[v[0]])==1 for v in vs)}
    consumed={identity for values in valid.values()for identity,_ in values}
    result=[]
    for mark in marks:
        group=mark.get('_dynamicGroup')
        if group in valid:result.append(dict(mark,_suddenDynamic=valid[group][0][1]))
        elif mark['identity']not in consumed:result.append(mark)
    return result
