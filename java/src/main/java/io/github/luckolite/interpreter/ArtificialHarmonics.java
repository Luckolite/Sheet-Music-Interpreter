// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;

/** A stopped note plus a hollow diamond a fourth above sounds two octaves higher. */
final class ArtificialHarmonics {
    private ArtificialHarmonics() { }
    static List<ScoreNoteEvent> apply(byte[] gray,int w,int h,List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,List<PlayingTechniqueDetector.Staff> staffs) {
        if(gray==null||notes.isEmpty())return notes;
        var replacements=new HashMap<ScoreNoteEvent,ScoreNoteEvent>();var remove=new HashSet<ScoreNoteEvent>();
        for(var n:notes) {
            if(n.octaveShift()!=0||n.measureIndex()<0||n.measureIndex()>=measures.size())continue;
            var m=measures.get(n.measureIndex());float x=(m.left()+n.positionInMeasure()*(m.right()-m.left()))*w,y=n.pageY()*h;
            PlayingTechniqueDetector.Staff staff=null;
            for(var s:staffs)if(s.index()==n.staffIndex()&&s.top()/h>=m.top()-.02f&&s.bottom()/h<=m.bottom()+.02f){staff=s;break;}
            if(staff==null)continue;
            float gap=staff.gap();
            if(!diamond(gray,w,h,x,y-1.5f*gap,gap))continue;
            // Do not treat an arbitrary diamond without a stopped, stemmed lower note as this technique.
            if(!stem(gray,w,h,x,y,gap,n.unbeamedDurationBeats()<ScoreNoteEvent.DURATION_HALF))continue;
            replacements.put(n,n.withOctaveShift(2));
            for(var upper:notes)if(upper!=n&&upper.measureIndex()==n.measureIndex()&&upper.staffIndex()==n.staffIndex()
                    &&upper.staffStep()-n.staffStep()==3&&Math.abs(upper.pageY()*h-(y-1.5f*gap))<gap*.35f
                    &&Math.abs((upper.positionInMeasure()-n.positionInMeasure())*(m.right()-m.left())*w)<gap*.55f)remove.add(upper);
        }
        if(replacements.isEmpty())return notes;
        var result=new ArrayList<ScoreNoteEvent>();for(var n:notes)if(!remove.contains(n))result.add(replacements.getOrDefault(n,n));
        return List.copyOf(result);
    }
    private static boolean stem(byte[] g,int w,int h,float x,float y,float gap,boolean filledStoppedHead) {
        // Both conventional stem directions occur in artificial harmonics.
        for(int direction:new int[]{-1,1}) {
        for(int offset=Math.round(gap*.3f);offset<=Math.round(gap*.95f);offset++) {
            // Hollow stopped heads need stronger shape evidence: ordinary open
            // fourths can otherwise resemble a diamond pair in low-resolution scans.
            if(direction>0&&!filledStoppedHead)continue;
            int xx=Math.round(x)-direction*offset;
            int count=0,total=0;
            for(int distance=Math.round(gap*.2f);distance<=Math.round(gap*1.2f);distance++) {
                int yy=Math.round(y)+direction*distance;
                total++;if(dark(g,w,h,xx,yy))count++;
            }
            if(total>0&&count>=total*.9) {
                if(direction<0)return true;
                // A down-stem also joins the touch diamond above the stopped head.
                // A detached fingering zero must not become an artificial harmonic.
                int joined=0,span=0;
                for(int yy=Math.round(y-gap*1.05f);yy<=Math.round(y-gap*.3f);yy++) {
                    span++;if(dark(g,w,h,xx,yy))joined++;
                }
                if(span>0&&joined>=span*.85f)return true;
            }
        }
        }
        return false;
    }
    static boolean diamond(byte[] g,int w,int h,float x,float y,float gap) {
        if(gap<8)return false;
        int horizontal=Math.round(gap*.4f),vertical=Math.round(gap*.25f);
        for(int dx=-horizontal;dx<=horizontal;dx++)for(int dy=-vertical;dy<=vertical;dy++)
        for(float size:new float[]{.5f,.6f,.7f,.8f}) {
            float cx=x+dx,cy=y+dy,r=gap*size;int hit=0,total=0;
            for(int side=0;side<4;side++)for(int i=1;i<=4;i++) {
                float t=i/5f,px=(1-t)*r,py=t*r;
                if(side==1){px=-px;}if(side==2){px=-px;py=-py;}if(side==3)py=-py;
                int xx=Math.round(cx+px),yy=Math.round(cy+py);total++;
                if(dark(g,w,h,xx,yy)||dark(g,w,h,xx-1,yy)||dark(g,w,h,xx+1,yy))hit++;
            }
            if(hit<total*.94)continue;
            int clear=0,inside=0;
            for(int yy=Math.round(cy-r*.3f);yy<=Math.round(cy+r*.3f);yy++) {
                // Ignore a staff or ledger line crossing the hollow center.
                if(dark(g,w,h,Math.round(cx-r-3),yy)&&dark(g,w,h,Math.round(cx+r+3),yy))continue;
                for(int xx=Math.round(cx-r*.25f);xx<=Math.round(cx+r*.25f);xx++){inside++;if(!dark(g,w,h,xx,yy))clear++;}
            }
            if(inside<4||clear<inside*.65)continue;
            int outside=0;
            for(int a:new int[]{-1,1})for(int b:new int[]{-1,1})if(!dark(g,w,h,Math.round(cx+a*r*.75f),Math.round(cy+b*r*.75f)))outside++;
            if(outside==4)return true;
        }
        return false;
    }
    private static boolean dark(byte[] g,int w,int h,int x,int y){return x>=0&&x<w&&y>=0&&y<h&&(g[y*w+x]&255)<165;}
}
