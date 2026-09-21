// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Adapted from Music Sheets: standalone package and platform-independent diagnostics.
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Reads printed triplets and beamed five- or seven-in-four groups from numeral and attack geometry. */
final class TripletRhythmDetector {
    private TripletRhythmDetector() { }

    record Rhythm(List<ScoreNoteEvent> notes,List<ScoreRestEvent> rests) { }

    /** A printed rest occupies one rhythmic slot just as a chord attack does.
     * Virtual events are used only for grouping and never returned as sounding notes. */
    static Rhythm withRests(List<ScoreNoteEvent> notes,List<ScoreRestEvent> rests,
            List<MeasureRegion> measures,byte[] gray,int width,int height) {
        if(rests==null||rests.isEmpty())return new Rhythm(apply(notes,measures,gray,width,height),rests);
        List<ScoreNoteEvent> slots=new ArrayList<>(notes);
        List<Integer> restIndices=new ArrayList<>();
        for(int restIndex=0;restIndex<rests.size();restIndex++) {
            ScoreRestEvent rest=rests.get(restIndex);
            double value=rest.durationBeats();
            int beams=value==.25?2:value==.5?1:value==1?0:-1;
            restIndices.add(restIndex);
            // Unsupported or already scaled rest values remain barriers between
            // attack columns; omitting them could join notes across real silence.
            slots.add(new ScoreNoteEvent(rest.measureIndex(),rest.positionInMeasure(),0,
                    rest.staffIndex(),rest.staffCount(),rest.pageY(),false,0,Math.max(0,beams),2,
                    beams<=0?(float)value:0,beams<0?2:1));
        }
        List<ScoreNoteEvent> marked=apply(slots,measures,gray,width,height);
        List<ScoreRestEvent> scaled=new ArrayList<>(rests);
        for(int i=0;i<restIndices.size();i++) {
            int index=restIndices.get(i);ScoreRestEvent rest=rests.get(index);
            if(marked.get(notes.size()+i).tupletDivisor()<=1)continue;
            scaled.set(index,new ScoreRestEvent(rest.measureIndex(),rest.positionInMeasure(),
                    rest.pageY(),rest.pageHeight(),rest.staffIndex(),rest.staffCount(),
                    rest.durationBeats()*marked.get(notes.size()+i).durationScale()));
        }
        List<ScoreNoteEvent> result=new ArrayList<>();
        for(int i=0;i<notes.size();i++) {
            ScoreNoteEvent original=notes.get(i),note=marked.get(i);
            float next=1.01f;
            for(ScoreNoteEvent other:notes)if(sameVoice(original,other)
                    &&other.positionInMeasure()>original.positionInMeasure()+.018f)
                next=Math.min(next,other.positionInMeasure());
            boolean first=notes.stream().noneMatch(other->sameVoice(original,other)
                    &&other.positionInMeasure()<original.positionInMeasure()-.018f);
            double before=0,after=0;
            for(int j=0;j<rests.size();j++) {
                ScoreRestEvent rest=rests.get(j);
                double delta=rest.durationBeats()-scaled.get(j).durationBeats();
                if(delta==0||rest.measureIndex()!=note.measureIndex()||rest.staffIndex()!=note.staffIndex()
                        ||rest.staffCount()!=note.staffCount())continue;
                if(rest.positionInMeasure()<note.positionInMeasure()
                        &&(first||!ScoreNoteTiming.hasIndependentSustain(original)
                        &&notes.stream().anyMatch(other->sameVoice(original,other)
                        &&ScoreNoteTiming.hasIndependentSustain(other)
                        &&Math.abs(other.positionInMeasure()-rest.positionInMeasure())<=.018f))
                        &&original.leadingRestBeats()+.001>=rest.durationBeats())before+=delta;
                if(rest.positionInMeasure()>note.positionInMeasure()&&rest.positionInMeasure()<next
                        &&(!ScoreNoteTiming.hasIndependentSustain(original)
                        ||rest.positionInMeasure()>note.positionInMeasure()+.018f)
                        &&original.followingRestBeats()+.001>=rest.durationBeats())after+=delta;
            }
            result.add(new ScoreNoteEvent(note.measureIndex(),note.positionInMeasure(),note.staffStep(),
                    note.staffIndex(),note.staffCount(),note.pageY(),note.tiedFromPrevious(),note.augmentationDots(),
                    note.beamCount(),note.writtenAccidental(),note.unbeamedDurationBeats(),note.tupletDivisor(),
                    (float)Math.max(0,note.followingRestBeats()-after),note.articulations(),note.clefBottomDiatonic(),
                    note.crossStaffBeam(),(float)Math.max(0,note.leadingRestBeats()-before),note.compactOpening()));
        }
        return new Rhythm(List.copyOf(result),List.copyOf(scaled));
    }

    /** A chord contributes one attack column, regardless of how many heads it contains. */
    private record Onset(List<Integer> indices,float position,float top,float bottom) { }

    private static List<Onset> onsets(List<ScoreNoteEvent> notes) {
        List<Integer> order=new ArrayList<>();
        for(int i=0;i<notes.size();i++)order.add(i);
        order.sort(Comparator.comparingInt((Integer i)->notes.get(i).measureIndex())
                .thenComparingInt(i->notes.get(i).staffIndex())
                .thenComparingInt(i->notes.get(i).staffCount())
                .thenComparingDouble(i->notes.get(i).positionInMeasure()));
        List<Onset> result=new ArrayList<>();
        for(int i=0;i<order.size();) {
            ScoreNoteEvent first=notes.get(order.get(i));
            List<Integer> members=new ArrayList<>();float top=first.pageY(),bottom=top;
            int j=i;
            while(j<order.size()) {
                ScoreNoteEvent n=notes.get(order.get(j));
                if(!sameVoice(first,n)||n.positionInMeasure()-first.positionInMeasure()>.012f)break;
                members.add(order.get(j));top=Math.min(top,n.pageY());bottom=Math.max(bottom,n.pageY());j++;
            }
            result.add(new Onset(List.copyOf(members),first.positionInMeasure(),top,bottom));i=j;
        }
        return result;
    }

    private static boolean sameVoice(ScoreNoteEvent a,ScoreNoteEvent b) {
        return a.measureIndex()==b.measureIndex()&&a.staffIndex()==b.staffIndex()&&a.staffCount()==b.staffCount();
    }

    /** Keep independent held voices out of a moving chord's rhythmic group.
     * The three attack columns themselves remain consecutive: an intervening
     * different-value attack must not be skipped to manufacture a triplet. */
    private static Onset matching(Onset onset,List<ScoreNoteEvent> notes,ScoreNoteEvent first) {
        double value=ScoreNoteTiming.writtenDurationBeats(first);
        if(!Double.isFinite(value)||value<=0||value>1)return null;
        List<Integer> members=new ArrayList<>();float top=Float.POSITIVE_INFINITY,bottom=Float.NEGATIVE_INFINITY;
        for(int index:onset.indices()) {
            ScoreNoteEvent n=notes.get(index);
            double written=ScoreNoteTiming.writtenDurationBeats(n);
            if(!Double.isFinite(written)||!sameVoice(first,n)||n.beamCount()!=first.beamCount()||n.augmentationDots()!=0
                    ||n.tupletDivisor()!=1||(n.articulations()&NoteOrnament.GRACE)!=0
                    ||Math.abs(written-value)>.001)continue;
            members.add(index);top=Math.min(top,n.pageY());bottom=Math.max(bottom,n.pageY());
        }
        return members.isEmpty()?null:new Onset(List.copyOf(members),onset.position(),top,bottom);
    }

    private static List<List<Onset>> triples(Onset a,Onset b,Onset c,List<ScoreNoteEvent> notes,boolean beamed) {
        List<List<Onset>> result=new ArrayList<>();
        float ab=b.position()-a.position(),bc=c.position()-b.position();
        if(ab<.022f||bc<.022f||Math.max(ab,bc)>Math.min(ab,bc)*1.5f)return result;
        for(int index:a.indices()) {
            ScoreNoteEvent first=notes.get(index);
            if(beamed&&first.beamCount()<1)continue;
            boolean seen=false;
            for(List<Onset> group:result)if(group.get(0).indices().contains(index)){seen=true;break;}
            if(seen)continue;
            Onset aa=matching(a,notes,first),bb=matching(b,notes,first),cc=matching(c,notes,first);
            if(aa!=null&&bb!=null&&cc!=null)result.add(List.of(aa,bb,cc));
        }
        return result;
    }

    static List<ScoreNoteEvent> apply(List<ScoreNoteEvent> notes,List<MeasureRegion> measures,
            byte[] gray,int width,int height) {
        if(notes==null||notes.size()<3||measures==null||gray==null
                ||width<1||height<1||gray.length!=width*height)return notes;
        List<ScoreNoteEvent> result=new ArrayList<>(notes);List<Onset> groups=onsets(notes);
        for(int i=0;i+2<groups.size();i++) {
            boolean marked=false;
            for(List<Onset> group:triples(groups.get(i),groups.get(i+1),groups.get(i+2),result,false)) {
                Onset a=group.get(0),b=group.get(1),c=group.get(2);
                ScoreNoteEvent first=result.get(a.indices().get(0));
                if(first.measureIndex()<0||first.measureIndex()>=measures.size())continue;
                MeasureRegion region=measures.get(first.measureIndex());
                float gap=Math.max(4,(region.bottom()-region.top())*height/(8*first.staffCount()));
                float x1=(region.left()+a.position()*(region.right()-region.left()))*width;
                float x3=(region.left()+c.position()*(region.right()-region.left()))*width;
                if(x3-x1<gap*2||x3-x1>gap*18)continue;
                float y1=Math.min(a.top(),Math.min(b.top(),c.top()))*height;
                float y2=Math.max(a.bottom(),Math.max(b.bottom(),c.bottom()))*height;
                Glyph numeral=findPrintedThree(gray,width,height,x1,x3,y1,y2,gap,
                        first.beamCount()>0,Float.NaN,Float.NaN);
                if(numeral==null||insideOtherSystem(numeral,region,measures,width,height))continue;
                // A finger number must not regroup attacks across two separate beams.
                // A real tuplet bracket remains authoritative across beam breaks.
                float x2=(region.left()+b.position()*(region.right()-region.left()))*width;
                boolean bracket=bracketArm(gray,width,height,Math.round(x1-gap*.3f),
                        numeral.left()-2,numeral.top(),numeral.bottom(),gap*.25f)
                        &&bracketArm(gray,width,height,numeral.right()+2,
                        Math.round(x3+gap*.3f),numeral.top(),numeral.bottom(),gap*.25f);
                if(first.beamCount()>0&&!bracket
                        &&(SeparateBeamGroups.between(gray,width,height,x1,a.top()*height,x2,b.top()*height,gap)
                        ||SeparateBeamGroups.between(gray,width,height,x2,b.top()*height,x3,c.top()*height,gap)))continue;
                // Finger numbers can sit under a four-note beam, centered on its last
                // three notes. Require another finger numeral and the larger beam before
                // rejecting the apparent triplet; an explicit bracket always wins.
                if(!bracket&&first.beamCount()>0&&a.indices().size()==1&&b.indices().size()==1&&c.indices().size()==1) {
                    boolean fingering=false;
                    for(int adjacent:new int[]{i-1,i+3}) {
                        if(adjacent<0||adjacent>=groups.size())continue;
                        Onset fourth=matching(groups.get(adjacent),result,first);
                        if(fourth==null||fourth.indices().size()!=1)continue;
                        var extra=result.get(fourth.indices().get(0));
                        if(extra.measureIndex()!=first.measureIndex())continue;
                        float xx=(region.left()+fourth.position()*(region.right()-region.left()))*width;
                        float spacing=adjacent<i?x1-xx:xx-x3;
                        if(spacing<(x3-x1)*.30f||spacing>(x3-x1)*.75f)continue;
                        float leftX=adjacent<i?xx:x1,leftY=(adjacent<i?fourth.top():a.top())*height;
                        float rightX=adjacent<i?x3:xx,rightY=(adjacent<i?c.top():fourth.top())*height;
                        int side=numeral.top()>y2?-1:1;
                        if(SeparateBeamGroups.connected(gray,width,height,leftX,leftY,rightX,rightY,gap,side)
                                &&hasNearbyFour(gray,width,height,numeral,gap)){fingering=true;break;}
                    }
                    if(fingering)continue;
                }
                // Vertically stacked small numbers assign fingers to chord tones.
                // They do not turn the surrounding three chord attacks into a tuplet.
                if(a.indices().size()>1&&b.indices().size()>1&&c.indices().size()>1
                        &&hasStackedFingering(gray,width,height,numeral,gap))continue;
                for(Onset onset:List.of(a,b,c))for(int index:onset.indices()) {
                    ScoreNoteEvent n=result.get(index);
                    result.set(index,new ScoreNoteEvent(n.measureIndex(),n.positionInMeasure(),n.staffStep(),
                            n.staffIndex(),n.staffCount(),n.pageY(),n.tiedFromPrevious(),n.augmentationDots(),
                            n.beamCount(),n.writtenAccidental(),n.unbeamedDurationBeats(),3,n.followingRestBeats(),
                            n.articulations(),n.clefBottomDiatonic(),n.crossStaffBeam(),n.leadingRestBeats(),n.compactOpening()));
                }
                marked=true;
            }
            if(marked)i+=2;
        }
        return beamedTuplets(beamedTuplets(result,measures,gray,width,height,7),measures,gray,width,height,5);
    }

    private static List<ScoreNoteEvent> beamedTuplets(List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,byte[] gray,int width,int height,int divisor) {
        List<ScoreNoteEvent> result=new ArrayList<>(notes);
        List<Onset> groups=onsets(notes);
        for(int i=0;i+divisor-1<groups.size();i++) {
            Onset firstGroup=groups.get(i);
            for(int index:firstGroup.indices()) {
                ScoreNoteEvent first=result.get(index);
                if(first.tupletDivisor()!=1||first.beamCount()<1||first.augmentationDots()!=0
                        ||first.measureIndex()<0||first.measureIndex()>=measures.size())continue;
                List<Onset> run=new ArrayList<>();float minimum=Float.MAX_VALUE,maximum=0;
                boolean valid=true;
                for(int j=0;j<divisor;j++) {
                    Onset onset=matching(groups.get(i+j),result,first);
                    if(onset==null){valid=false;break;}
                    if(j>0) {
                        float delta=onset.position()-run.get(j-1).position();
                        minimum=Math.min(minimum,delta);maximum=Math.max(maximum,delta);
                    }
                    run.add(onset);
                }
                if(!valid||minimum<.012f||maximum>minimum*1.6f)continue;
                // A longer uninterrupted run does not become a shorter tuplet merely
                // because a numeral happens to be nearby.
                if(i>0&&matching(groups.get(i-1),result,first)!=null
                        &&firstGroup.position()-groups.get(i-1).position()<minimum*1.5f)continue;
                if(i+divisor<groups.size()&&matching(groups.get(i+divisor),result,first)!=null
                        &&groups.get(i+divisor).position()-run.get(divisor-1).position()<minimum*1.5f)continue;
                MeasureRegion bar=measures.get(first.measureIndex());
                float gap=Math.max(4,(bar.bottom()-bar.top())*height/(8*first.staffCount()));
                float x1=(bar.left()+run.get(0).position()*(bar.right()-bar.left()))*width;
                float lastX=(bar.left()+run.get(divisor-1).position()*(bar.right()-bar.left()))*width;
                if(lastX-x1<gap*3||lastX-x1>gap*26)continue;
                float y1=Float.MAX_VALUE,y2=-Float.MAX_VALUE;
                for(Onset onset:run){y1=Math.min(y1,onset.top()*height);y2=Math.max(y2,onset.bottom()*height);}
                Glyph numeral=findPrintedNumeral(gray,width,height,x1,lastX,y1,y2,gap,true,
                        Float.NaN,Float.NaN,divisor);
                if(numeral==null||insideOtherSystem(numeral,bar,measures,width,height))continue;
                for(Onset onset:run)for(int at:onset.indices()) {
                    ScoreNoteEvent n=result.get(at);
                    result.set(at,new ScoreNoteEvent(n.measureIndex(),n.positionInMeasure(),n.staffStep(),
                            n.staffIndex(),n.staffCount(),n.pageY(),n.tiedFromPrevious(),n.augmentationDots(),
                            n.beamCount(),n.writtenAccidental(),n.unbeamedDurationBeats(),divisor,n.followingRestBeats(),
                            n.articulations(),n.clefBottomDiatonic(),n.crossStaffBeam(),n.leadingRestBeats(),n.compactOpening()));
                }
            }
        }
        return List.copyOf(result);
    }

    /** Complete printed numeral evidence overrides spurious head/beam predictions on that glyph. */
    static List<ScoreNoteEvent> withoutNumeralHeads(List<ScoreNoteEvent> notes,
            List<MeasureRegion> measures,byte[] gray,int width,int height) {
        if(notes==null||notes.size()<4||measures==null||gray==null||width<1||height<1
                ||gray.length!=width*height)return notes;
        List<ScoreNoteEvent> result=new ArrayList<>(notes);
        for(ScoreNoteEvent candidate:notes) {
            if(candidate.tiedFromPrevious()||candidate.measureIndex()<0||candidate.measureIndex()>=measures.size())continue;
            MeasureRegion region=measures.get(candidate.measureIndex());
            float gap=Math.max(4,(region.bottom()-region.top())*height/(8*candidate.staffCount()));
            List<ScoreNoteEvent> voice=new ArrayList<>();
            for(ScoreNoteEvent n:result)if(n!=candidate&&sameVoice(n,candidate)
                    && !(Math.abs(n.positionInMeasure()-candidate.positionInMeasure())<=.018f
                    && Math.abs(n.pageY()-candidate.pageY())*height<=gap*2.3f))voice.add(n);
            List<Onset> groups=onsets(voice);
            float candidateX=(region.left()+candidate.positionInMeasure()*(region.right()-region.left()))*width;
            float candidateY=candidate.pageY()*height;
            for(int i=0;i+2<groups.size();i++) {
                for(List<Onset> group:triples(groups.get(i),groups.get(i+1),groups.get(i+2),voice,true)) {
                    Onset a=group.get(0),b=group.get(1),c=group.get(2);
                    float x1=(region.left()+a.position()*(region.right()-region.left()))*width;
                    float x3=(region.left()+c.position()*(region.right()-region.left()))*width;
                    float y1=Math.min(a.top(),Math.min(b.top(),c.top()))*height;
                    float y2=Math.max(a.bottom(),Math.max(b.bottom(),c.bottom()))*height;
                    if(x3-x1<gap*2||x3-x1>gap*18||candidateX<x1-gap||candidateX>x3+gap
                            ||candidateY>=y1-gap&&candidateY<=y2+gap)continue;
                    if(findPrintedThree(gray,width,height,x1,x3,y1,y2,gap,true,candidateX,candidateY)!=null) {
                        result.remove(candidate);break;
                    }
                }
                if(!result.contains(candidate))break;
            }
        }
        return List.copyOf(result);
    }

    /** A numeral printed inside another system cannot change this row's rhythm. */
    private static boolean insideOtherSystem(Glyph numeral,MeasureRegion current,
            List<MeasureRegion> measures,int width,int height) {
        float x=(numeral.left()+numeral.right())*.5f/width;
        float y=(numeral.top()+numeral.bottom())*.5f/height;
        if(y>=current.top()&&y<=current.bottom())return false;
        for(var other:measures)
            if((other.top()>current.bottom()||other.bottom()<current.top())
                    &&x>=other.left()&&x<=other.right()&&y>=other.top()&&y<=other.bottom())return true;
        return false;
    }

    private record Glyph(int left,int top,int right,int bottom) { }

    private static boolean hasPrintedThree(byte[] gray, int width, int height, float firstX,
                                            float lastX, float firstY, float lastY, float gap,
                                            boolean shortNotes) {
        return findPrintedThree(gray,width,height,firstX,lastX,firstY,lastY,gap,shortNotes,
                Float.NaN,Float.NaN)!=null;
    }

    private static Glyph findPrintedThree(byte[] gray,int width,int height,float firstX,
            float lastX,float firstY,float lastY,float gap,boolean shortNotes,float headX,float headY) {
        return findPrintedNumeral(gray,width,height,firstX,lastX,firstY,lastY,gap,shortNotes,headX,headY,3);
    }

    private static Glyph findPrintedNumeral(byte[] gray,int width,int height,float firstX,
            float lastX,float firstY,float lastY,float gap,boolean shortNotes,float headX,float headY,int number) {
        float centerX = (firstX + lastX) * .5f;
        // Numerals align with the beam/stems, which can sit to one side of the
        // oval centres. Include that offset without clipping an italic 3.
        int left = Math.max(0, Math.round(centerX - gap * 1.65f));
        int right = Math.min(width - 1, Math.round(centerX + gap * 1.65f));
        int top = Math.max(0, Math.round(firstY - gap * 7));
        int bottom = Math.min(height - 1, Math.round(lastY + gap * 7));
        int localWidth = right - left + 1, localHeight = bottom - top + 1;
        if (localWidth < 3 || localHeight < 3) return null;
        boolean[] visited = new boolean[localWidth * localHeight];
        int[] queue = new int[visited.length];
        for (int origin = 0; origin < visited.length; origin++) {
            if (visited[origin] || !dark(gray, width, left + origin % localWidth,
                    top + origin / localWidth)) continue;
            int count = 0, pending = 1; queue[0] = origin; visited[origin] = true;
            int minX = right, maxX = left, minY = bottom, maxY = top;
            while (pending > 0) {
                int current = queue[--pending]; count++;
                int x = current % localWidth, y = current / localWidth;
                minX = Math.min(minX, left + x); maxX = Math.max(maxX, left + x);
                minY = Math.min(minY, top + y); maxY = Math.max(maxY, top + y);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || nx >= localWidth || ny < 0 || ny >= localHeight) continue;
                    int next = ny * localWidth + nx;
                    if (!visited[next] && dark(gray, width, left + nx, top + ny)) {
                        visited[next] = true; queue[pending++] = next;
                    }
                }
            }
            int gw = maxX - minX + 1, gh = maxY - minY + 1;
            if (minX <= left || maxX >= right || minY <= top || maxY >= bottom
                    || gh < gap * .7f || gh > gap * 2.3f || gw < gh * .30f || gw > gh * .95f
                    || count < gw * gh * .15f || count > gw * gh * .70f
                    || !(maxY < firstY - gap || minY > lastY + gap)) continue;
            if(Float.isFinite(headX)&&(headX<minX-gap*.1f||headX>maxX+gap*.1f
                    ||headY<minY-gap*.1f||headY>maxY+gap*.1f))continue;
            if (!(number==7 ? looksLikeSeven(gray,width,minX,minY,gw,gh)
                    : number==5 ? looksLikeFive(gray,width,minX,minY,gw,gh)
                    : looksLikeThree(gray, width, minX, minY, gw, gh))) continue;
            // Quarter-note tuplets need the two bracket arms. For beamed/flagged short notes,
            // publishers routinely print only the numeral, so its shape/group alignment suffices.
            if (shortNotes || bracketArm(gray, width, height, Math.round(firstX - gap * .3f),
                    minX - 2, minY, maxY, gap)
                    && bracketArm(gray, width, height, maxX + 2, Math.round(lastX + gap * .3f),
                    minY, maxY, gap)) return new Glyph(minX,minY,maxX,maxY);
        }
        return null;
    }

    /** Look for a separate, similarly sized upright glyph stacked over or under
     * this 3. The caller requires chord tones at all three candidate attacks. */
    private static boolean hasStackedFingering(byte[] gray,int width,int height,Glyph three,float gap) {
        float cx=(three.left()+three.right())*.5f;
        int gh=three.bottom()-three.top()+1;
        int left=Math.max(0,Math.round(cx-gap)),right=Math.min(width-1,Math.round(cx+gap));
        int top=Math.max(0,Math.round(three.top()-gap*2.4f));
        int bottom=Math.min(height-1,Math.round(three.bottom()+gap*2.4f));
        int w=right-left+1,h=bottom-top+1;boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];
        for(int seed=0;seed<w*h;seed++) {
            if(seen[seed]||!dark(gray,width,left+seed%w,top+seed/w))continue;
            int take=0,size=1,minX=width,maxX=-1,minY=height,maxY=-1;
            queue[0]=seed;seen[seed]=true;
            while(take<size) {
                int at=queue[take++],x=at%w,y=at/w;
                minX=Math.min(minX,left+x);maxX=Math.max(maxX,left+x);
                minY=Math.min(minY,top+y);maxY=Math.max(maxY,top+y);
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int nx=x+dx,ny=y+dy;if(nx<0||nx>=w||ny<0||ny>=h)continue;
                    int next=ny*w+nx;
                    if(!seen[next]&&dark(gray,width,left+nx,top+ny)){seen[next]=true;queue[size++]=next;}
                }
            }
            if(minX<=left||maxX>=right||minY<=top||maxY>=bottom)continue;
            int cw=maxX-minX+1,ch=maxY-minY+1;
            if(ch<gap*.7f||ch>gap*2.3f||ch<gh*.65f||ch>gh*1.4f
                    ||cw<ch*.2f||cw>ch*.95f||size<cw*ch*.15f||size>cw*ch*.7f
                    ||Math.abs((minX+maxX)*.5f-cx)>gap*.35f)continue;
            int separation=maxY<three.top()?three.top()-maxY:
                    minY>three.bottom()?minY-three.bottom():-1;
            if(separation>=Math.max(2,gap*.2f)&&separation<=gap)return true;
        }
        return false;
    }

    private static boolean hasNearbyFour(byte[] gray,int width,int height,Glyph three,float gap) {
        float cx=(three.left()+three.right())*.5f;
        int gh=three.bottom()-three.top()+1;
        int left=Math.max(0,Math.round(cx-gap*11)),right=Math.min(width-1,Math.round(cx+gap*11));
        int top=Math.max(0,Math.round(three.top()-gap*.4f));
        int bottom=Math.min(height-1,Math.round(three.bottom()+gap*.4f));
        int w=right-left+1,h=bottom-top+1;boolean[] seen=new boolean[w*h];int[] queue=new int[w*h];
        for(int seed=0;seed<w*h;seed++) {
            if(seen[seed]||!dark(gray,width,left+seed%w,top+seed/w))continue;
            int take=0,size=1,minX=width,maxX=-1,minY=height,maxY=-1;
            queue[0]=seed;seen[seed]=true;
            while(take<size) {
                int at=queue[take++],x=at%w,y=at/w;
                minX=Math.min(minX,left+x);maxX=Math.max(maxX,left+x);
                minY=Math.min(minY,top+y);maxY=Math.max(maxY,top+y);
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int nx=x+dx,ny=y+dy;if(nx<0||nx>=w||ny<0||ny>=h)continue;
                    int next=ny*w+nx;
                    if(!seen[next]&&dark(gray,width,left+nx,top+ny)){seen[next]=true;queue[size++]=next;}
                }
            }
            if(minX<=left||maxX>=right||minY<=top||maxY>=bottom)continue;
            int cw=maxX-minX+1,ch=maxY-minY+1;
            if(ch<gap*.7f||ch>gap*2.3f||ch<gh*.65f||ch>gh*1.4f
                    ||cw<ch*.2f||cw>ch*.95f||size<cw*ch*.15f||size>cw*ch*.7f
                    ||Math.abs((minY+maxY-three.top()-three.bottom())*.5f)>gap*.3f)continue;
            if(Math.abs((minX+maxX)*.5f-cx)>gap*1.5f
                    &&looksLikeFingerFour(gray,width,size,minX,maxX,minY,maxY))return true;
        }
        return false;
    }

    private static boolean looksLikeFingerFour(byte[] gray, int width, int area,
                                         int minX, int maxX, int minY, int maxY) {
        int glyphWidth = maxX - minX + 1, glyphHeight = maxY - minY + 1;
        float fill = area / (float) (glyphWidth * glyphHeight);
        if (fill < .14f || fill > .66f) return false;
        int[] rows = new int[glyphHeight];
        int[] columns = new int[glyphWidth];
        int upperLeft = 0, lowerLeft = 0;
        for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
            if ((gray[y * width + x] & 0xff) > 165) continue;
            int localX = x - minX, localY = y - minY;
            rows[localY]++;
            columns[localX]++;
            if (localX < glyphWidth * .55f && localY < glyphHeight * .58f) upperLeft++;
            if (localX < glyphWidth * .45f && localY > glyphHeight * .72f) lowerLeft++;
        }
        int rightSpine = 0;
        for (int x = Math.max(0, Math.round(glyphWidth * .52f)); x < glyphWidth; x++)
            rightSpine = Math.max(rightSpine, columns[x]);
        int middleCrossbar = 0;
        for (int y = Math.max(0, Math.round(glyphHeight * .36f));
             y <= Math.min(glyphHeight - 1, Math.round(glyphHeight * .74f)); y++)
            middleCrossbar = Math.max(middleCrossbar, rows[y]);
        int topBar = 0;
        for (int y = 0; y < Math.max(1, Math.round(glyphHeight * .28f)); y++)
            topBar = Math.max(topBar, rows[y]);
        return rightSpine >= glyphHeight * .58f
                && middleCrossbar >= glyphWidth * .50f
                && topBar < glyphWidth * .68f
                && upperLeft >= Math.max(2, Math.round(area * .10f))
                && lowerLeft <= Math.max(2, Math.round(area * .16f));
    }

    private static boolean looksLikeSeven(byte[] gray,int width,int left,int top,int w,int h) {
        // Broad top bar followed by one descending diagonal, without the lower
        // bowl/base of 2, closed counters of 8, or two lobes of 3.
        int broad=0,upper=0,lower=0,upperRows=0,lowerRows=0;
        int previous=Integer.MAX_VALUE,reverse=0;
        for(int y=0;y<h;y++) {
            int min=w,max=-1,count=0;
            for(int x=0;x<w;x++)if(dark(gray,width,left+x,top+y)){min=Math.min(min,x);max=x;count++;}
            if(max<0)return false;
            if(y<h*.3f&&max-min>=w*.65f)broad++;
            if(y>=h*.35f) {
                if(max-min>w*.55f||y<h*.8f&&count<w*.12f)return false;
                int center=min+max;
                if(previous!=Integer.MAX_VALUE&&center>previous+2)reverse++;
                previous=center;
                if(y<h*.55f){upper+=center;upperRows++;}
                if(y>=h*.8f){lower+=center;lowerRows++;}
            }
        }
        return broad>=Math.max(1,h/12)&&upperRows>0&&lowerRows>0&&reverse<=h/10
                &&upper/(float)upperRows-lower/(float)lowerRows>=w*.45f;
    }

    private static boolean looksLikeFive(byte[] gray,int width,int left,int top,int w,int h) {
        int bars=0,leftStem=0,lowerOpen=0,lowerPocket=0,lowerEdge=-1,foot=-1;
        for(int y=0;y<h;y++) {
            int min=w,max=-1;
            for(int x=0;x<w;x++)if(dark(gray,width,left+x,top+y)){min=Math.min(min,x);max=Math.max(max,x);}
            if(y<h*.28f&&max-min>=w*.6f)bars++;
            if(y>=h*.18f&&y<h*.42f&&min<=w*.35f&&max<=w*.55f&&max>=min)leftStem++;
            if(y>=h*.48f&&y<h*.82f) {
                lowerEdge=Math.max(lowerEdge,max);
                if(min>=w*.45f&&max>=w*.7f)lowerOpen++;
                if(hasLobePocket(gray,width,left,top+y,w))lowerPocket++;
            }
            if(y>=h*.94f)foot=Math.max(foot,max);
        }
        return bars>=Math.max(2,h/12)&&leftStem>=Math.max(1,h/12)
                &&lowerOpen>=1&&lowerOpen+lowerPocket>=Math.max(2,h/8)
                &&lowerEdge-foot>=Math.max(1,(int)(w*.08f));
    }

    private static boolean looksLikeThree(byte[] gray, int width, int left, int top, int w, int h) {
        int[] min = new int[h], max = new int[h];
        for (int y = 0; y < h; y++) {
            min[y] = w; max[y] = -1;
            for (int x = 0; x < w; x++) if (dark(gray, width, left + x, top + y)) {
                min[y] = Math.min(min[y], x); max[y] = x;
            }
        }
        int upperOpen = 0, lowerOpen = 0, upperPocket = 0, lowerPocket = 0;
        int upperLobe = -1, lowerLobe = -1, waist = w;
        for (int y = 0; y < h; y++) {
            float fraction = y / (float) h;
            // A row occupies a whole pixel band; include a short opening that
            // crosses a lobe boundary instead of discarding it at small sizes.
            float nextFraction = (y + 1) / (float) h;
            if (nextFraction > .15f && fraction <= .36f + 1f / h) {
                upperLobe = Math.max(upperLobe, max[y]);
                if (min[y] >= w * .40f) upperOpen++;
                if (hasLobePocket(gray, width, left, top + y, w)) upperPocket++;
            }
            if (nextFraction > .60f && fraction <= .82f) {
                // The lower curve must bulge before the baseline; a 2 only widens at its foot.
                if(fraction<=.75f)lowerLobe = Math.max(lowerLobe, max[y]);
                if (min[y] >= w * .40f) lowerOpen++;
                if (hasLobePocket(gray, width, left, top + y, w)) lowerPocket++;
            }
            if (fraction >= .37f && fraction <= .55f) waist = Math.min(waist, max[y]);
        }
        int required = Math.max(2, h / 12);
        // Curled terminals put ink on the left of an otherwise open lobe. Allow
        // that ink only with a wide interior pocket and a truly open row in each
        // lobe: a closed 8 and the solid upper-left stem of a 5 still fail.
        boolean upper = upperOpen >= required || upperOpen >= 1 && upperPocket >= required;
        boolean lower = lowerOpen >= required || lowerOpen >= 1 && lowerPocket >= required;
        int indentation = Math.max(1, (int) Math.floor(w * .08f));
        int foot=-1;
        for(int y=(int)(h*.92);y<h;y++)foot=Math.max(foot,max[y]);
        return lowerLobe-foot>=indentation && upper && lower && upperLobe - waist >= indentation
                && lowerLobe - waist >= indentation;
    }

    private static boolean hasLobePocket(byte[] gray, int width, int left, int y, int w) {
        int run = 0;
        for (int x = Math.round(w * .25f); x < w; x++) {
            if (!dark(gray, width, left + x, y)) run++;
            else {
                if (run >= Math.max(2, Math.round(w * .22f)) && x >= w * .55f) return true;
                run = 0;
            }
        }
        return false;
    }

    private static boolean bracketArm(byte[] gray, int width, int height, int left, int right,
                                        int top, int bottom, float gap) {
        left = Math.max(0, left); right = Math.min(width - 1, right);
        if (right - left < gap) return false;
        int occupied = 0;
        for (int x = left; x <= right; x++) {
            // Sloped brackets can rise above the numeral at their outer ends.
            // Allow half a staff space while still requiring both long arms.
            for (int y = Math.max(0, top - Math.round(gap * .5f));
                    y <= Math.min(height - 1, bottom + Math.round(gap * .5f)); y++)
                if (dark(gray, width, x, y)) { occupied++; break; }
        }
        return occupied >= (right - left + 1) * .76f;
    }

    private static boolean dark(byte[] gray, int width, int x, int y) {
        return (gray[y * width + x] & 0xff) <= 165;
    }
}
