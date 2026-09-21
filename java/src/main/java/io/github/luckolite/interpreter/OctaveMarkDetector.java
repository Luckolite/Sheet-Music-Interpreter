// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Applies octave directions to sounding pitch while preserving written staff geometry. */
final class OctaveMarkDetector {
    private OctaveMarkDetector() { }
    static int shift(String text) {
        if(text==null)return 0;
        String s=text.toLowerCase(Locale.ROOT).replaceAll("[\\s()\\[\\].,:;_\\-–—]","");
        return switch(s) {
            case "8va","8vaa","8vaalta","ottava" -> 1;
            case "8vb","8vab","8vabassa" -> -1;
            case "15ma","15maa","15maalta" -> 2;
            case "15mb","15mab","15mabassa" -> -2;
            default -> 0;
        };
    }
    private record Span(PlayingTechniqueDetector.Staff staff,float left,float right,int shift) { }

    static List<ScoreNoteEvent> apply(List<PlayingTechniqueDetector.Word> words,
            List<PlayingTechniqueDetector.Staff> staffs,List<MeasureRegion> measures,
            List<ScoreNoteEvent> notes,byte[] gray,int width,int height) {
        if(staffs==null||staffs.isEmpty()||notes.isEmpty())return notes;
        List<PlayingTechniqueDetector.Word> combined=new ArrayList<>(words==null?List.of():words);
        combined.addAll(printedWords(gray,width,height,staffs));
        if(combined.isEmpty())return notes;
        List<Span> spans=new ArrayList<>();
        for(var word:combined) {
            int shift=shift(word.text());if(shift==0)continue;
            var owner=owner(word,staffs,shift,height);if(owner==null)continue;
            float gap=owner.gap(),left=word.left()*width-gap*.65f;
            float textRight=Math.min(word.right()*width,word.left()*width+
                    Math.max(gap*2,(word.bottom()-word.top())*height*3.5f));
            float right=dashEnd(gray,width,height,textRight,word.top()*height,word.bottom()*height,gap);
            if(right<0) {
                // An isolated octave direction applies to the nearest attack or chord only.
                float first=Float.POSITIVE_INFINITY;
                for(var note:notes)if(onStaff(note,owner,staffs,height)) {
                    float x=x(note,measures,width);
                    if(x>=left&&x<=textRight+gap*1.5f)first=Math.min(first,x);
                }
                if(!Float.isFinite(first))continue;
                left=first-gap*.35f;right=first+gap*.35f;
            }
            spans.add(new Span(owner,left,right,shift));
        }
        if(spans.isEmpty())return notes;
        List<Span> ambiguous=new ArrayList<>();
        for(var a:spans)for(var b:spans)if(a!=b&&a.staff.equals(b.staff)&&a.shift!=b.shift
                &&Math.abs(a.left-b.left)<a.staff.gap()*.75f){ambiguous.add(a);ambiguous.add(b);}
        spans.removeAll(ambiguous);
        spans.sort(Comparator.comparingDouble(Span::left));
        for(int i=0;i<spans.size();i++) {
            var a=spans.get(i);float right=a.right;
            for(int j=i+1;j<spans.size();j++) {
                var b=spans.get(j);
                if(a.staff.equals(b.staff)&&b.left>a.left+a.staff.gap()*.75f)right=Math.min(right,b.left-.01f);
            }
            if(right!=a.right)spans.set(i,new Span(a.staff,a.left,right,a.shift));
        }
        List<ScoreNoteEvent> result=new ArrayList<>(notes.size());
        for(var note:notes) {
            float x=x(note,measures,width);Span selected=null;
            for(var span:spans)if(onStaff(note,span.staff,staffs,height)&&x>=span.left&&x<=span.right) {
                if(selected==null||span.left>selected.left)selected=span;
            }
            result.add(selected==null?note:note.withOctaveShift(selected.shift));
        }
        return List.copyOf(result);
    }
    private record InkBox(int left,int top,int right,int bottom,int area) { }
    static List<PlayingTechniqueDetector.Word> printedWords(byte[] gray,int width,int height,
            List<PlayingTechniqueDetector.Staff> staffs) {
        List<PlayingTechniqueDetector.Word> words=new ArrayList<>();
        if(gray==null||gray.length!=width*height)return words;
        for(var staff:staffs)for(boolean below:new boolean[]{false,true}) {
            float gap=staff.gap();
            int top=Math.max(0,Math.round(below?staff.bottom()+gap*.3f:staff.top()-gap*9));
            int bottom=Math.min(height-1,Math.round(below?staff.bottom()+gap*9:staff.top()-gap*.3f));
            if(top>=bottom)continue;
            int h=bottom-top+1;boolean[] visited=new boolean[width*h];int[] stack=new int[width*h];
            List<InkBox> boxes=new ArrayList<>();
            for(int origin=0;origin<visited.length;origin++) {
                if(visited[origin]||(gray[top*width+origin]&255)>=165)continue;
                int count=0,size=0,left=width,right=-1,y1=h,y2=-1;stack[size++]=origin;visited[origin]=true;
                while(size>0) {
                    int at=stack[--size],x=at%width,y=at/width;count++;
                    left=Math.min(left,x);right=Math.max(right,x);y1=Math.min(y1,y);y2=Math.max(y2,y);
                    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++) {
                        int xx=x+dx,yy=y+dy;if(xx<0||xx>=width||yy<0||yy>=h)continue;
                        int next=yy*width+xx;
                        if(!visited[next]&&(gray[top*width+next]&255)<165){visited[next]=true;stack[size++]=next;}
                    }
                }
                if(count>=Math.max(4,gap*gap*.025f)&&y2-y1+1>=gap*.55f&&y2-y1+1<=gap*3.2f
                        &&right-left+1>=gap*.12f&&right-left+1<=gap*8)
                    boxes.add(new InkBox(left,top+y1,right,top+y2,count));
            }
            boxes.sort(Comparator.comparingInt(InkBox::left));boolean[] used=new boolean[boxes.size()];
            var bareBoxes=new ArrayList<>(boxes);
            bareBoxes.addAll(attachedEightBoxes(gray,width,height,top,bottom,gap));
            for(var box:bareBoxes) {
                int bw=box.right-box.left+1,bh=box.bottom-box.top+1;
                if(bw<gap*.3f||bw>gap*1.6f||bh<gap*.65f||bh>gap*2.3f||bh<bw*.9f
                        ||OctaveClefDigit.holes(gray,width,box.left,box.top,bw,bh)!=2)continue;
                if(dashEnd(gray,width,height,box.right+1,box.top,box.bottom,gap,6)<0)continue;
                // A bare numeral has no va/vb suffix. Use only the nearest stave;
                // do not apply the same inter-system mark to both adjacent rows.
                PlayingTechniqueDetector.Staff nearest=null;float nearestDistance=Float.POSITIVE_INFINITY;
                boolean nearestBelow=false;
                for(var candidate:staffs) {
                    float above=(candidate.top()-box.bottom)/candidate.gap();
                    float under=(box.top-candidate.bottom())/candidate.gap();
                    float distance=above>0?above:under;
                    if(distance>=.25f&&distance<nearestDistance){nearest=candidate;nearestDistance=distance;nearestBelow=under>0;}
                }
                if(!staff.equals(nearest)||below!=nearestBelow)continue;
                // An attached fragment between systems cannot establish a lower octave
                // for the preceding staff without an explicit vb direction.
                if(below&&!boxes.contains(box))continue;
                words.add(new PlayingTechniqueDetector.Word(below?"8vb":"8va",box.left/(float)width,
                        box.top/(float)height,(box.right+1)/(float)width,(box.bottom+1)/(float)height));
            }
            for(int i=0;i<boxes.size();i++) {
                if(used[i])continue;var box=boxes.get(i);int left=box.left,right=box.right,a=box.top,b=box.bottom;used[i]=true;
                for(int j=i+1;j<boxes.size();j++) {
                    var next=boxes.get(j);if(next.left>right+gap*.7f)break;
                    if(used[j]||next.right<left||Math.min(b,next.bottom)-Math.max(a,next.top)<gap*.3f
                            ||Math.max(right,next.right)-left>gap*8)continue;
                    used[j]=true;right=Math.max(right,next.right);a=Math.min(a,next.top);b=Math.max(b,next.bottom);
                }
                int shift=OctaveWordShapes.match(gray,width,left,a,right,b);
                if(shift==0||(below?shift>0:shift<0))continue;
                String text=switch(shift){case 1->"8va";case -1->"8vb";case 2->"15ma";default->"15mb";};
                words.add(new PlayingTechniqueDetector.Word(text,left/(float)width,a/(float)height,
                        (right+1)/(float)width,(b+1)/(float)height));
            }
        }
        return words;
    }

    private static List<InkBox> attachedEightBoxes(byte[] gray,int w,int h,int top,int bottom,float gap) {
        int band=bottom-top+1;boolean[] seen=new boolean[w*band];int[] queue=new int[w*band];
        var holes=new ArrayList<InkBox>();
        for(int seed=0;seed<seen.length;seed++) {
            if(seen[seed]||(gray[top*w+seed]&255)<165)continue;
            int take=0,size=1,x0=w,x1=-1,y0=band,y1=-1;boolean edge=false;seen[seed]=true;queue[0]=seed;
            while(take<size) {
                int at=queue[take++],x=at%w,y=at/w;
                x0=Math.min(x0,x);x1=Math.max(x1,x);y0=Math.min(y0,y);y1=Math.max(y1,y);
                if(x==0||x==w-1||y==0||y==band-1)edge=true;
                for(int d:new int[]{-1,1,-w,w}) {
                    int next=at+d;if(next<0||next>=seen.length||Math.abs(next%w-x)>1)continue;
                    if(!seen[next]&&(gray[top*w+next]&255)>=165){seen[next]=true;queue[size++]=next;}
                }
            }
            if(!edge&&size>=gap*gap*.015f&&size<=gap*gap*.65f&&x1-x0<gap&&y1-y0<gap)
                holes.add(new InkBox(x0,top+y0,x1,top+y1,size));
        }
        var result=new ArrayList<InkBox>();int pad=Math.max(2,Math.round(gap*.2f));
        for(var a:holes)for(var b:holes) {
            if(a.bottom>=b.top||b.top-a.bottom>gap*.5f||Math.abs(a.left+a.right-b.left-b.right)>gap*1.1f)continue;
            int left=Math.max(0,Math.min(a.left,b.left)-pad),right=Math.min(w-1,Math.max(a.right,b.right)+pad);
            int y0=Math.max(top,a.top-pad),y1=Math.min(bottom,b.bottom+pad);
            if(y1-y0<gap*.65f||y1-y0>gap*2.3f||right-left>gap*1.6f)continue;
            if(ledgerTouchesLeft(gray,w,h,left,right,y0,y1,gap))
                result.add(new InkBox(left,y0,right,y1,a.area+b.area));
        }
        return result;
    }

    /** Recover a numeral merged with a thin ledger, not a second reading of every
     * isolated numeral or a pair of counters inside neighboring text. */
    private static boolean ledgerTouchesLeft(byte[] gray,int w,int h,int left,int right,int top,int bottom,float gap) {
        int reach=Math.max(3,Math.round(gap*.8f));
        if(left<reach+1)return false;
        for(int y=Math.round(top+(bottom-top)*.3f);y<=Math.round(top+(bottom-top)*.7f);y++) {
            int ink=0;for(int x=left-reach;x<=left+2;x++)if((gray[y*w+x]&255)<165)ink++;
            if(ink<(reach+3)*.85f)continue;
            int x=left-reach/2,a=y,b=y;
            while(a>0&&(gray[(a-1)*w+x]&255)<165)a--;
            while(b+1<h&&(gray[(b+1)*w+x]&255)<165)b++;
            if(b-a+1<=Math.max(3,Math.round(gap*.35f)))return true;
        }
        return false;
    }

    private static float x(ScoreNoteEvent note,List<MeasureRegion> measures,int width) {
        var m=measures.get(note.measureIndex());return (m.left()+note.positionInMeasure()*(m.right()-m.left()))*width;
    }
    private static boolean onStaff(ScoreNoteEvent note,PlayingTechniqueDetector.Staff staff,
            List<PlayingTechniqueDetector.Staff> staffs,int height) {
        PlayingTechniqueDetector.Staff nearest=null;float best=Float.POSITIVE_INFINITY;
        for(var candidate:staffs)if(note.staffIndex()==candidate.index()&&note.staffCount()==candidate.count()) {
            float y=note.pageY()*height,distance=Math.max(0,Math.max(candidate.top()-y,y-candidate.bottom()))/candidate.gap();
            if(distance<=7&&distance<best){best=distance;nearest=candidate;}
        }
        return staff.equals(nearest);
    }
    private static PlayingTechniqueDetector.Staff owner(PlayingTechniqueDetector.Word word,
            List<PlayingTechniqueDetector.Staff> staffs,int shift,int height) {
        PlayingTechniqueDetector.Staff best=null;float distance=Float.POSITIVE_INFINITY;
        for(var staff:staffs) {
            float d=shift>0?(staff.top()-word.bottom()*height)/staff.gap()
                    :(word.top()*height-staff.bottom())/staff.gap();
            if(d<.25f||d>9||d>=distance)continue;
            distance=d;best=staff;
        }
        return best;
    }
    /** Find a horizontal chain of short printed dashes, stopping at its actual end. */
    private static float dashEnd(byte[] gray,int width,int height,float start,float top,float bottom,float gap) {
        return dashEnd(gray,width,height,start,top,bottom,gap,3);
    }
    private static float dashEnd(byte[] gray,int width,int height,float start,float top,float bottom,float gap,int minimum) {
        if(gray==null||gray.length!=width*height)return -1;
        int left=Math.max(0,Math.round(start-gap*.35f));
        int y1=Math.max(0,Math.round(top-gap*.15f)),y2=Math.min(height-1,Math.round(bottom+gap*.4f));
        int best=-1;
        for(int y=y1;y<=y2;y++) {
            int first=-1,last=-1,count=0,shortDots=0,interruptions=0,x=left;
            while(x<width) {
                int blank=0;
                while(x<width&&(gray[y*width+x]&255)>=165){blank++;x++;}
                if(blank>gap*(count==0?2:1.6f))break;
                int a=x;while(x<width&&(gray[y*width+x]&255)<165)x++;
                int length=x-a;
                if(length<2)continue;
                if(length>gap*1.65f)break;
                boolean tall=false;
                for(int cx=a;cx<x;cx++) {
                    int ya=y,yb=y;
                    while(ya>0&&(gray[(ya-1)*width+cx]&255)<165)ya--;
                    while(yb+1<height&&(gray[(yb+1)*width+cx]&255)<165)yb++;
                    if(yb-ya+1>Math.max(3,gap*.35f)){tall=true;break;}
                }
                if(tall){if(count==0&&a<start)continue;if(count>0&&++interruptions<=1)continue;break;}
                if(length<gap*.18f)shortDots++;
                if(first<0)first=a;last=x-1;count++;
            }
            if(count>=Math.max(minimum,shortDots>count/2?5:3)&&last-first>=gap*3)best=Math.max(best,last);
        }
        return best<0?-1:best+gap*.55f;
    }
}
