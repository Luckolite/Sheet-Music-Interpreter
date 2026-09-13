// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
// Adapted from Music Sheets: standalone package and platform-independent diagnostics.
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.List;

/** Reads the two left-facing bulbs and descending diagonal tail from raw score ink.
 * the model often labels this entire glyph as background, so semantic symbol components cannot seed it. */
final class SixteenthRestDetector {
    record Staff(float top, float bottom, float gap, int index, int count, StaffPitchTrack pitchTrack) {
        Staff(float top,float bottom,float gap,int index,int count){this(top,bottom,gap,index,count,null);}
    }

    record RestDot(float x,float y,ScoreRestEvent rest) { }
    record Detection(List<ScoreRestEvent> rests,List<RestDot> dots) { }
    private record InkDot(float x,float y) { }

    static List<ScoreRestEvent> detect(byte[] gray, int width, int height,
            List<MeasureRegion> measures, List<Staff> staffs, List<ScoreNoteEvent> notes) {
        return detectWithDots(gray,width,height,measures,staffs,notes).rests();
    }

    static Detection detectWithDots(byte[] gray, int width, int height,
            List<MeasureRegion> measures, List<Staff> staffs, List<ScoreNoteEvent> notes) {
        if (gray == null || gray.length != width * height) return new Detection(List.of(),List.of());
        if(staffs.stream().anyMatch(staff->staff.pitchTrack()!=null)) {
            List<Staff> straight=new ArrayList<>();
            for(Staff staff:staffs)if(staff.pitchTrack()==null)straight.add(staff);
            Detection plain=detectWithDots(gray,width,height,measures,straight,notes);
            List<ScoreRestEvent> combined=new ArrayList<>(plain.rests());
            List<RestDot> dots=new ArrayList<>(plain.dots());
            for(Staff staff:staffs)if(staff.pitchTrack()!=null) {
                Detection curved=detectOnPrintedStaff(gray,width,height,measures,staff,notes);
                combined.addAll(curved.rests());dots.addAll(curved.dots());
            }
            return collected(combined,dots);
        }
        List<ScoreRestEvent> result = new ArrayList<>();
        List<RestDot> restDots=new ArrayList<>();
        List<Staff> placements=new ArrayList<>(staffs);
        // In polyphonic engraving rests for the upper voice move one space above their
        // usual centre to clear the simultaneously held lower voice.
        for(Staff s:staffs)for(int offset=1;offset<=2;offset++)
            placements.add(new Staff(s.top()-offset*s.gap(),s.bottom()-offset*s.gap(),s.gap(),s.index(),s.count()));
        for (Staff staff : placements) {
            float gap = staff.gap();
            int top = Math.max(0, Math.round(staff.top() + gap * .25f));
            boolean highVoice=staffs.stream().anyMatch(s->s.index()==staff.index()
                    &&s.count()==staff.count()&&Math.abs(s.top()-staff.top()-gap*2)<gap*.1f);
            int bottom = Math.min(height - 1, Math.round(staff.bottom() + (highVoice?0:gap*.3f)));
            boolean[] line = new boolean[bottom - top + 1];
            // Remove only long horizontal ink rows, including a line's antialiased edge.
            for (int y = top; y <= bottom; y++) {
                int dark = 0,longest=0,run=0;
                for (int x = 0; x < width; x++) {
                    if ((gray[y * width + x] & 255) < 170) { dark++;longest=Math.max(longest,++run); }
                    else run=0;
                }
                float nearestLine = staff.top() + Math.round((y - staff.top()) / gap) * gap;
                // Rectification can leave only a local antialiased edge of a rule.
                // A continuous five-gap segment still establishes line ink; rest
                // bulbs are far narrower and cannot satisfy this support.
                line[y - top] = Math.abs(y - nearestLine) <= gap * .2f
                        && (dark > width * .25f || longest >= gap*5);
            }
            int start = -1;
            for (int x = 0; x <= width; x++) {
                int ink = 0;
                if (x < width) for (int y = top; y <= bottom; y++)
                    if (!line[y - top] && (gray[y * width + x] & 255) < 170) ink++;
                if (ink >= 2) { if (start < 0) start = x; }
                else if (start >= 0) {
                    boolean ordinary=staffs.stream().anyMatch(s->s.index()==staff.index()
                            &&s.count()==staff.count()&&Math.abs(s.top()-staff.top())<gap*.1f);
                    inspect(gray, width, height, measures, notes, staff, top, bottom,
                            line, start, x - 1, result,restDots,ordinary);
                    start = -1;
                }
            }
        }
        return collected(result,restDots);
    }

    private static Detection collected(List<ScoreRestEvent> result,List<RestDot> restDots) {
        result.sort(java.util.Comparator.comparingInt(ScoreRestEvent::measureIndex)
                .thenComparingDouble(ScoreRestEvent::positionInMeasure));
        List<ScoreRestEvent> unique=new ArrayList<>();
        for(ScoreRestEvent rest:result)if(unique.stream().noneMatch(r->r.measureIndex()==rest.measureIndex()
                &&r.staffIndex()==rest.staffIndex()&&Math.abs(r.positionInMeasure()-rest.positionInMeasure())<.018f))unique.add(rest);
        return new Detection(List.copyOf(unique),List.copyOf(restDots));
    }

    /** Translate columns in a narrow staff band; map results back to the source page.
     * Keep glyph height intact: small local spacing errors must not stretch the
     * rest and leave partial staff rules connected to its hook. */
    private static Detection detectOnPrintedStaff(byte[] gray,int width,int height,
            List<MeasureRegion> measures,Staff staff,List<ScoreNoteEvent> notes) {
        int first=Math.max(0,(int)Math.floor(staff.top()-staff.gap()*3));
        int last=Math.min(height,(int)Math.ceil(staff.bottom()+staff.gap()));
        if(last<=first)return new Detection(List.of(),List.of());
        int bandHeight=last-first;
        byte[] flat=new byte[width*bandHeight];
        for(int x=0;x<width;x++) {
            float[] local=staff.pitchTrack().at(x);
            for(int y=0;y<bandHeight;y++) {
                int sourceY=Math.round(local[0]+(first+y-staff.bottom()));
                flat[y*width+x]=sourceY>=0&&sourceY<height?gray[sourceY*width+x]:(byte)255;
            }
        }
        List<MeasureRegion> mappedMeasures=new ArrayList<>();
        for(MeasureRegion region:measures) {
            float x=(region.left()+region.right())*.5f*width;
            mappedMeasures.add(new MeasureRegion(region.left(),region.right(),
                    (flatY(staff,x,region.top()*height)-first)/bandHeight,
                    (flatY(staff,x,region.bottom()*height)-first)/bandHeight));
        }
        List<ScoreNoteEvent> mappedNotes=new ArrayList<>();
        for(ScoreNoteEvent n:notes) {
            MeasureRegion region=measures.get(n.measureIndex());
            float x=(region.left()+n.positionInMeasure()*(region.right()-region.left()))*width;
            float y=(flatY(staff,x,n.pageY()*height)-first)/bandHeight;
            mappedNotes.add(new ScoreNoteEvent(n.measureIndex(),n.positionInMeasure(),n.staffStep(),
                    n.staffIndex(),n.staffCount(),y,n.tiedFromPrevious(),n.augmentationDots(),n.beamCount(),
                    n.writtenAccidental(),n.unbeamedDurationBeats(),n.tupletDivisor(),n.followingRestBeats(),
                    n.articulations(),n.clefBottomDiatonic(),n.crossStaffBeam(),n.leadingRestBeats(),n.compactOpening()));
        }
        Staff rectified=new Staff(staff.top()-first,staff.bottom()-first,staff.gap(),staff.index(),staff.count());
        Detection detected=detectWithDots(flat,width,bandHeight,mappedMeasures,List.of(rectified),mappedNotes);
        List<ScoreRestEvent> rests=new ArrayList<>();List<RestDot> dots=new ArrayList<>();
        for(ScoreRestEvent rest:detected.rests())rests.add(sourceRest(rest,staff,measures,width,height,first,bandHeight));
        for(RestDot dot:detected.dots())dots.add(new RestDot(dot.x(),
                sourceY(staff,dot.x(),first+dot.y()),sourceRest(dot.rest(),staff,measures,width,height,first,bandHeight)));
        return new Detection(List.copyOf(rests),List.copyOf(dots));
    }

    private static float flatY(Staff staff,float x,float sourceY) {
        float[] local=staff.pitchTrack().at(x);
        return staff.bottom()+(sourceY-local[0]);
    }

    private static float sourceY(Staff staff,float x,float flatY) {
        float[] local=staff.pitchTrack().at(x);
        return local[0]+(flatY-staff.bottom());
    }

    private static ScoreRestEvent sourceRest(ScoreRestEvent rest,Staff staff,List<MeasureRegion> measures,
            int width,int height,int first,int bandHeight) {
        MeasureRegion region=measures.get(rest.measureIndex());
        float x=(region.left()+rest.positionInMeasure()*(region.right()-region.left()))*width;
        float y=sourceY(staff,x,first+rest.pageY()*bandHeight)/height;
        float h=rest.pageHeight()*bandHeight/height;
        return new ScoreRestEvent(rest.measureIndex(),rest.positionInMeasure(),y,h,
                rest.staffIndex(),rest.staffCount(),rest.durationBeats());
    }

    private static void inspect(byte[] gray, int width, int height, List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes, Staff staff, int top, int bottom, boolean[] line,
            int left, int right, List<ScoreRestEvent> result,List<RestDot> restDots,boolean ordinary) {
        float gap = staff.gap();
        if (right - left + 1 < gap * .7f || right - left + 1 > gap * 1.6f) return;
        int minY = bottom + 1, maxY = top - 1;
        int[] ink = new int[bottom - top + 1];
        for (int y = top; y <= bottom; y++) if (!line[y - top]) {
            for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) < 170)
                ink[y - top]++;
            if (ink[y - top] > 0) { minY = Math.min(minY, y); maxY = y; }
        }
        boolean half=ordinary&&halfRest(staff,top,line,ink,left,right,minY,maxY);
        boolean quarter = quarterRest(gray,width,staff,top,line,left,right,minY,maxY);
        boolean eighth = maxY-minY>=gap*1.3f && maxY-minY<=gap*2.2f
                && Math.abs(maxY-(staff.bottom()-gap))<=gap*.4f;
        boolean sixteenth = maxY-minY>=gap*2.35f && maxY-minY<=gap*3.25f
                && Math.abs(maxY-staff.bottom())<=gap*.35f;
        if (!quarter&&!half) {
            if ((!eighth && !sixteenth)
                    || minY < staff.top() + gap * .85f || minY > staff.top() + gap * 1.55f) return;
            // Interpolate removed staff rows before counting bulb lobes, so staff crossings do not
            // split a single bulb into several flags.
            for (int y = minY; y <= maxY; y++) if (line[y - top]) {
                int before = y - 1, after = y + 1;
                while (before >= minY && line[before - top]) before--;
                while (after <= maxY && line[after - top]) after++;
                if (before >= minY && after <= maxY)
                    ink[y - top] = Math.round((ink[before - top] + ink[after - top]) * .5f);
            }
            List<Integer> lobes = new ArrayList<>();
            int run = 0, runStart = 0;
            // A single eighth-rest bulb can span three raster rows at a
            // fractional staff scale. Preserve the stricter paired-bulb test.
            float bulbWidth=eighth?Math.max(2,Math.round(gap*.58f)):gap*.58f;
            float bulbRows=Math.max(2,eighth?Math.round(gap*.22f):gap*.22f);
            for (int y = minY; y <= maxY + 1; y++) {
                if (y <= maxY && ink[y - top] >= bulbWidth) {
                    if (run++ == 0) runStart = y;
                } else {
                    if (run >= bulbRows) lobes.add((runStart + y - 1) / 2);
                    run = 0;
                }
            }
            if (eighth ? lobes.size()!=1 || maxY-lobes.get(0)<gap*.8f
                    : lobes.size() != 2 || lobes.get(1) - lobes.get(0) < gap * .7f
                    || lobes.get(1) - lobes.get(0) > gap * 1.3f
                    || maxY - lobes.get(1) < gap * .8f) return;
            // Below the second bulb only a narrow tail remains. Its foot slopes left of the tip;
            // accidentals, paired dots and isolated note flags do not have this geometry.
            int footRight = -1;
            for (int y = maxY - Math.round(gap * .45f); y <= maxY; y++) if (!line[y - top]) {
                if (ink[y - top] > gap * .50f) return;
                for (int x = left; x <= right; x++) if ((gray[y * width + x] & 255) < 170)
                    footRight = Math.max(footRight, x);
            }
            if (footRight < 0 || right - footRight < gap * .15f) return;
        }
        float centerX = (left + right) * .5f / width;
        float centerY = (minY + maxY) * .5f / height;
        for (int m = 0; m < measures.size(); m++) {
            MeasureRegion region = measures.get(m);
            if (centerX <= region.left() || centerX >= region.right()
                    || centerY < region.top() || centerY > region.bottom()) continue;
            if ((m > 0 && region.equals(measures.get(m - 1)))
                    || (m + 1 < measures.size() && region.equals(measures.get(m + 1)))) return;
            for (ScoreNoteEvent note : notes) if (note.measureIndex() == m
                    && note.staffIndex() == staff.index() && note.staffCount() == staff.count()) {
                float noteX = (region.left() + note.positionInMeasure() * (region.right() - region.left())) * width;
                if (noteX >= left - gap * .65f && noteX <= right + gap * .65f) {
                    // A rest may share an attack column with a separate held voice. Keep
                    // rejecting note fragments unless the whole rest is clear of its head.
                    float noteY=note.pageY()*height;
                    if(!ScoreNoteTiming.hasIndependentSustain(note)
                            ||noteY>=minY-gap*.65f&&noteY<=maxY+gap*.65f)return;
                }
                if(note.writtenAccidental()!=ScoreNoteEvent.ACCIDENTAL_FROM_KEY
                        &&noteX>right&&noteX<right+gap*1.8f)return;
            }
            List<InkDot> dots=augmentationDots(gray,width,height,staff,right,region,notes,m);
            double duration=(half?2:quarter?1:eighth?.5:.25)*(dots.size()==2?1.75:dots.size()==1?1.5:1);
            ScoreRestEvent rest=new ScoreRestEvent(m, (centerX - region.left()) / (region.right() - region.left()),
                    centerY, (maxY - minY + 1f) / height, staff.index(), staff.count(),duration);
            result.add(rest);
            for(InkDot dot:dots)restDots.add(new RestDot(dot.x(),dot.y(),rest));
            return;
        }
    }
    /** Dots belong to a recognized rest only in the adjacent upper staff space. */
    private static List<InkDot> augmentationDots(byte[] gray,int width,int height,Staff staff,
            int restRight,MeasureRegion region,List<ScoreNoteEvent> notes,int measure) {
        float gap=staff.gap();
        int left=Math.max(0,restRight+Math.max(2,Math.round(gap*.12f)));
        int right=Math.min(width-1,Math.min(Math.round(region.right()*width)-1,
                restRight+Math.round(gap*2.4f)));
        int top=Math.max(0,Math.round(staff.top()+gap*1.03f));
        int bottom=Math.min(height-1,Math.round(staff.top()+gap*1.97f));
        if(left>=right||top>=bottom)return List.of();
        int w=right-left+1,h=bottom-top+1;boolean[] seen=new boolean[w*h];int[] stack=new int[w*h];
        // A dot can touch the antialiased edge of a thick staff rule. Exclude
        // only long rows at the expected rule height before tracing components;
        // otherwise that small round mark becomes a crop-wide rejected component.
        for(int y=top;y<=bottom;y++) {
            float nearestLine=staff.top()+Math.round((y-staff.top())/gap)*gap;
            if(Math.abs(y-nearestLine)>gap*.2f)continue;
            int dark=0;
            for(int x=0;x<width;x++)if((gray[y*width+x]&255)<170)dark++;
            if(dark>width*.25f)
                java.util.Arrays.fill(seen,(y-top)*w,(y-top+1)*w,true);
        }
        List<InkDot> dots=new ArrayList<>();
        for(int seed=0;seed<seen.length;seed++) {
            int sx=seed%w,sy=seed/w;
            if(seen[seed]||(gray[(top+sy)*width+left+sx]&255)>=170)continue;
            int size=0;stack[size++]=seed;seen[seed]=true;
            int area=0,minX=w,maxX=-1,minY=h,maxY=-1;
            while(size>0) {
                int at=stack[--size],x=at%w,y=at/w;area++;
                minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                    int nx=x+dx,ny=y+dy;
                    if(nx<0||nx>=w||ny<0||ny>=h)continue;
                    int next=ny*w+nx;
                    if(!seen[next]&&(gray[(top+ny)*width+left+nx]&255)<170) {
                        seen[next]=true;stack[size++]=next;
                    }
                }
            }
            int dw=maxX-minX+1,dh=maxY-minY+1;
            if(minX==0||maxX==w-1||minY==0||maxY==h-1
                    ||dw<gap*.16f||dh<gap*.16f||dw>gap*.7f||dh>gap*.7f
                    ||dw>dh*2||dh>dw*2||area<gap*gap*.025f||area>gap*gap*.32f
                    ||area<dw*dh*.5f)continue;
            float x=left+(minX+maxX)*.5f,y=top+(minY+maxY)*.5f;
            if(Math.abs(y-(staff.top()+gap*1.5f))>gap*.25f)continue;
            boolean ownedByNote=false;
            for(ScoreNoteEvent note:notes)if(note.measureIndex()==measure
                    &&note.staffIndex()==staff.index()&&note.staffCount()==staff.count()) {
                float nx=(region.left()+note.positionInMeasure()*(region.right()-region.left()))*width;
                if(Math.abs(nx-x)<gap*.65f) {ownedByNote=true;break;}
            }
            if(!ownedByNote)dots.add(new InkDot(x,y));
        }
        dots.sort(java.util.Comparator.comparingDouble(InkDot::x));
        List<InkDot> accepted=new ArrayList<>();float previous=restRight;
        for(InkDot dot:dots) {
            float distance=dot.x()-previous;
            if(accepted.isEmpty() ? distance<gap*.25f||distance>gap*1.3f
                    : distance<gap*.4f||distance>gap*1.1f)continue;
            accepted.add(dot);previous=dot.x();if(accepted.size()==2)break;
        }
        return List.copyOf(accepted);
    }

    /** A half-rest is a filled rectangle sitting on the middle staff rule.
     * Require flat, wide rows: an oval head or thin articulation is not a rest. */
    private static boolean halfRest(Staff staff,int top,boolean[] line,int[] ink,
            int left,int right,int minY,int maxY) {
        float gap=staff.gap(),middle=staff.top()+2*gap;
        int h=maxY-minY+1,w=right-left+1;
        // The last retained row lies on an integer raster after line-edge removal.
        // Round its allowed distance up so a fractional staff gap cannot reject
        // an otherwise complete rectangle by less than one pixel.
        if(h<gap*.25f||h>gap*.65f||minY<middle-gap*.7f
                ||maxY>middle||middle-maxY>Math.ceil(gap*.25f))return false;
        int rows=0;
        for(int y=minY;y<=maxY;y++)if(!line[y-top]) {
            if(ink[y-top]<w*.80f)return false;
            rows++;
        }
        return rows>=Math.max(3,Math.round(gap*.25f));
    }

    /** Quarter rests have a narrow zigzag above a left-facing lower hook. */
    private static boolean quarterRest(byte[] gray,int width,Staff staff,int top,boolean[] line,
            int left,int right,int minY,int maxY) {
        float gap=staff.gap();int h=maxY-minY+1;
        if(h<gap*2.1f || h>gap*3.6f
                ||minY<staff.top()+gap*.2f ||minY>staff.top()+gap*.9f
                ||maxY+1<staff.bottom()-gap*1.2f ||maxY>staff.bottom()-gap*.1f)return false;
        double[] centers=new double[h];java.util.Arrays.fill(centers,Double.NaN);
        int widest=0;
        for(int y=minY;y<=maxY;y++)if(!line[y-top]) {
            int n=0;double sum=0;
            for(int x=left;x<=right;x++)if((gray[y*width+x]&255)<170){n++;sum+=x-left;}
            if(n>0)centers[y-minY]=sum/n;
            widest=Math.max(widest,n);
        }
        if(widest<gap*.65f)return false;
        for(int i=0;i<h;i++)if(!Double.isFinite(centers[i])) {
            int a=i-1,b=i+1;
            while(a>=0&&!Double.isFinite(centers[a]))a--;
            while(b<h&&!Double.isFinite(centers[b]))b++;
            if(a<0||b>=h)return false;
            centers[i]=centers[a]+(centers[b]-centers[a])*(i-a)/(b-a);
        }
        double a=bandCenter(centers,0,.18),b=bandCenter(centers,.22,.38),
                c=bandCenter(centers,.43,.58),d=bandCenter(centers,.62,.73),
                e=bandCenter(centers,.80,.91),f=bandCenter(centers,.94,1);
        if(b-a>gap*.10 && b-c>gap*.055 && d-c>gap*.055
                && d-e>gap*.12 && f-e>gap*.10)return true;
        // Some engravings end the lower hook with a straight downstroke, without a curled foot.
        double hookRight=-Double.MAX_VALUE,hookLeft=Double.MAX_VALUE;
        int window=Math.max(2,Math.round(gap*.18f));
        for(int i=(int)(h*.58);i+window<=h*.80;i++) {
            double mean=0;for(int j=0;j<window;j++)mean+=centers[i+j];
            hookRight=Math.max(hookRight,mean/window);
        }
        for(int i=(int)(h*.78);i+window<=h*.94;i++) {
            double mean=0;for(int j=0;j<window;j++)mean+=centers[i+j];
            hookLeft=Math.min(hookLeft,mean/window);
        }
        return b-a>gap*.10 && b-c>gap*.055 && hookRight-c>gap*.08
                && hookRight-hookLeft>gap*.18 && f-hookLeft>=-gap*.06 && f-hookLeft<gap*.15;
    }

    private static double bandCenter(double[] rows,double from,double to) {
        double sum=0;int n=0;
        for(int i=(int)(from*(rows.length-1));i<=Math.min(rows.length-1,(int)(to*(rows.length-1)));i++){
            sum+=rows[i];n++;
        }
        return sum/Math.max(1,n);
    }

}
