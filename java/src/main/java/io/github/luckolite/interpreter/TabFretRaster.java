// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.util.*;

/** Fret-sized OCR crops, separated from string rules, barlines and staff labels. */
public final class TabFretRaster {
    private TabFretRaster() {}
    public record Crop(int left,int top,int right,int bottom,int string,float stringY,float gap) {}
    /** Tight OCR boxes can separate a fret from an attached slur or bend curve. */
    public static List<Crop> crops(byte[] gray,int w,int h,List<TablatureDecoder.Staff> tabs,List<TablatureDecoder.Word> words) {
        var result=new ArrayList<>(crops(gray,w,h,tabs));
        for(var t:tabs)for(var v:words) {
            if(!v.text().matches("[0-9]{1,2}"))continue;
            float x=(v.left()+v.right())*.5f*w,y=(v.top()+v.bottom())*.5f*h;
            int string=Math.round((y-t.top())/t.gap());float cy=t.top()+string*t.gap();
            if(string<0||string>5||Math.abs(y-cy)>t.gap()*.24f||(v.bottom()-v.top())*h<t.gap()*.5f
                    ||(v.bottom()-v.top())*h>t.gap()*1.1f||(v.right()-v.left())*w<t.gap()*.35f||(v.right()-v.left())*w>t.gap()*1.4f)continue;
            float first=result.stream().filter(c->Math.abs(c.stringY()-t.top()-c.string()*t.gap())<t.gap()*.2f).map(c->(float)c.left()).min(Float::compare).orElse((float)w);
            if(x<first-t.gap()*.3f||t.bars().stream().anyMatch(b->Math.abs(b-x)<t.gap()*.3f))continue;
            if(result.stream().anyMatch(c->Math.abs(c.stringY()-cy)<t.gap()*.3f&&x>c.left()-t.gap()*.3f&&x<c.right()+t.gap()*.3f))continue;
            if(obscured(gray,w,h,x,cy,t.gap()))continue;
            result.add(new Crop(Math.max(0,Math.round(v.left()*w-t.gap()*.2f)),Math.max(0,Math.min(Math.round(v.top()*h),Math.round(cy-t.gap()*.45f))),Math.min(w,Math.round(v.right()*w+t.gap()*.1f)),Math.min(h,Math.max(Math.round(v.bottom()*h),Math.round(cy+t.gap()*.45f))),string,cy,t.gap()));
        }
        return List.copyOf(result);
    }
    public static List<TablatureDecoder.Word> reconcile(List<TablatureDecoder.Staff> tabs,List<Crop> crops,
            List<TablatureDecoder.Word> isolated,List<TablatureDecoder.Word> legacy,int w,int h) {
        var result=new ArrayList<TablatureDecoder.Word>();
        for(var c:crops) {
            float x=(c.left()+c.right())*.5f;
            var votes=new LinkedHashMap<String,Integer>();
            for(var v:isolated)if(Math.abs((v.left()+v.right())*.5f*w-x)<1&&Math.abs((v.top()+v.bottom())*.5f*h-c.stringY())<1)
                votes.merge(v.text(),2,Integer::sum);
            for(var v:legacy)if(v.text().matches("[0-9]{1,2}|[xX]")&&Math.abs((v.left()+v.right())*.5f*w-x)<c.gap()*.3f
                    &&Math.abs((v.top()+v.bottom())*.5f*h-c.stringY())<c.gap()*.24f
                    &&(v.right()-v.left())*w>(c.right()-c.left())*.65f&&(v.right()-v.left())*w<(c.right()-c.left())*1.4f)votes.merge(v.text(),1,Integer::sum);
            String value=null;int best=0;
            for(var v:votes.entrySet())if(v.getValue()>best){value=v.getKey();best=v.getValue();}
            if(value!=null)result.add(new TablatureDecoder.Word(value,c.left()/(float)w,Math.max(0,(c.stringY()-c.gap()*.3f)/h),c.right()/(float)w,Math.min(1,(c.stringY()+c.gap()*.3f)/h)));
        }
        // Preserve annotations in the rhythm lanes; numerals above a string are
        // measure numbers or bend amounts, never frets on that string.
        for(var v:legacy)if(!v.text().matches(".*[0-9].*")&&tabs.stream().noneMatch(t->(v.top()+v.bottom())*.5f*h>=t.top()-t.gap()*.6f&&(v.top()+v.bottom())*.5f*h<=t.top()+t.gap()*5.6f))result.add(v);
        for(var v:legacy)if(v.text().matches(".*[hHpPbBrR/\\\\~<>].*"))for(var t:tabs) {
            float y=(v.top()+v.bottom())*.5f*h;int string=Math.round((y-t.top())/t.gap());
            if(string<0||string>5||Math.abs(y-t.top()-string*t.gap())>t.gap()*.35f)continue;
            var parsed=TabNotation.parse(v.text(),v.left()*w,v.right()*w,t.top()+string*t.gap(),string);
            if(parsed.isEmpty()||parsed.stream().noneMatch(f->f.marks()!=0))continue;
            var matched=new ArrayList<TablatureDecoder.Word>();
            for(var f:parsed)for(var note:result)if(note.text().equals(Integer.toString(f.fret()))
                    &&Math.abs((note.top()+note.bottom())*.5f*h-f.y())<t.gap()*.3f&&Math.abs((note.left()+note.right())*.5f*w-f.x())<t.gap()*.6f){matched.add(note);break;}
            if(matched.size()==parsed.size()){result.removeAll(matched);result.add(v);}
        }
        return List.copyOf(result);
    }
    public static byte[] clean(byte[] gray,int w,int h,List<TablatureDecoder.Staff> tabs) {
        byte[] out=gray.clone();
        for(var t:tabs)for(int s=0;s<6;s++) {
            int cy=Math.round(t.top()+s*t.gap()),a=h,b=-1;
            for(int y=Math.max(1,Math.round(cy-t.gap()*.18f));y<Math.min(h-1,Math.round(cy+t.gap()*.18f));y++) {
                int ink=0;for(int x=0;x<w;x++)if((gray[y*w+x]&255)<230)ink++;
                if(ink>w*.4f){a=Math.min(a,y);b=Math.max(b,y);}
            }
            if(b>=a)for(int x=0;x<w;x++)if((gray[(a-1)*w+x]&255)>=160||(gray[(b+1)*w+x]&255)>=160)
                for(int y=a;y<=b;y++)out[y*w+x]=(byte)255;
            for(float bar:t.bars())for(int y=Math.max(0,Math.round(cy-t.gap()*.7f));y<Math.min(h,Math.round(cy+t.gap()*.7f));y++)
                for(int x=Math.max(0,Math.round(bar-t.gap()*.13f));x<Math.min(w,Math.round(bar+t.gap()*.13f));x++)out[y*w+x]=(byte)255;
        }
        return out;
    }
    /** A long, centered enclosed counter distinguishes an open fret from OCR's O/1. */
    public static boolean openFret(byte[] clean,int w,Crop c) {
        int cw=c.right()-c.left(),ch=c.bottom()-c.top();if(cw<ch*.35f||cw>ch*.95f)return false;
        boolean[] seen=new boolean[cw*ch];int[] queue=new int[seen.length];int holes=0;
        for(int y=0;y<ch;y++)for(int x=0;x<cw;x++) {
            int id=y*cw+x;if(seen[id]||(clean[(c.top()+y)*w+c.left()+x]&255)<160)continue;
            int read=0,end=0,area=0,minY=ch,maxY=0;long sumY=0;boolean edge=false;queue[end++]=id;seen[id]=true;
            while(read<end){int p=queue[read++],px=p%cw,py=p/cw;area++;sumY+=py;minY=Math.min(minY,py);maxY=Math.max(maxY,py);edge|=px==0||py==0||px==cw-1||py==ch-1;
                for(int d:new int[]{-1,1,-cw,cw}){int next=p+d;if(next<0||next>=seen.length||Math.abs(next%cw-px)+Math.abs(next/cw-py)!=1||seen[next])continue;
                    if((clean[(c.top()+next/cw)*w+c.left()+next%cw]&255)>=160){seen[next]=true;queue[end++]=next;}}
            }
            if(!edge&&area>cw*ch*.15f&&maxY-minY>ch*.5f&&sumY/(float)area>ch*.38f&&sumY/(float)area<ch*.62f)holes++;
        }
        return holes==1;
    }
    private static boolean obscured(byte[] gray,int w,int h,float x,float cy,float gap) {
        for(float dy:new float[]{-gap*.8f,0,gap*.8f}) {
            int ink=0,total=0;
            for(int y=Math.max(0,Math.round(cy+dy-gap*.8f));y<Math.min(h,Math.round(cy+dy+gap*.8f));y++)
                for(int xx=Math.max(0,Math.round(x-gap*.8f));xx<Math.min(w,Math.round(x+gap*.8f));xx++){total++;if((gray[y*w+xx]&255)<180)ink++;}
            if(total>0&&ink>total*.65f)return true;
        }
        return false;
    }
    public static List<Crop> crops(byte[] gray,int w,int h,List<TablatureDecoder.Staff> tabs) {
        var result=new ArrayList<Crop>();
        for(var t:tabs) {
            var row=new ArrayList<Crop>();
            for(int s=0;s<6;s++) {
                int cy=Math.round(t.top()+s*t.gap()),lo=Math.max(0,Math.round(cy-t.gap()*.6f)),hi=Math.min(h,Math.round(cy+t.gap()*.6f));
                boolean[] rule=new boolean[hi-lo],vertical=new boolean[w];
                for(int y=lo;y<hi;y++) {
                    int ink=0,ruleInk=0;
                    for(int x=0;x<w;x++){int value=gray[y*w+x]&255;if(value<180)ink++;if(value<235)ruleInk++;}
                    // Antialiased string fringes can join separate frets into one
                    // overwide crop even when only the darker rule core is continuous.
                    rule[y-lo]=ink>w*.4f || Math.abs(y-cy)<=t.gap()*.18f && ruleInk>w*.4f;
                }
                int va=Math.max(0,Math.round(cy-t.gap()*.8f)),vb=Math.min(h,Math.round(cy+t.gap()*.8f));
                for(int x=0;x<w;x++){int ink=0;for(int y=va;y<vb;y++)if((gray[y*w+x]&255)<180)ink++;vertical[x]=ink>(vb-va)*.9f;}
                for(float bar:t.bars())for(int x=Math.max(0,Math.round(bar-t.gap()*.13f));x<Math.min(w,Math.round(bar+t.gap()*.13f));x++)vertical[x]=true;
                var runs=new ArrayList<int[]>();int start=-1;
                for(int x=0;x<=w;x++) {
                    boolean ink=false;if(x<w&&!vertical[x])for(int y=lo;y<hi;y++)if(!rule[y-lo]&&(gray[y*w+x]&255)<180){ink=true;break;}
                    if(ink&&start<0)start=x;
                    if(!ink&&start>=0){if(!runs.isEmpty()&&start-runs.get(runs.size()-1)[1]<t.gap()*.27f)runs.get(runs.size()-1)[1]=x;else runs.add(new int[]{start,x});start=-1;}
                }
                for(var run:runs) {
                    int a=run[0],b=run[1],yt=h,yb=-1;
                    if(b-a<t.gap()*.12f||b-a>t.gap()*1.7f)continue;
                    for(int y=lo;y<hi;y++)if(!rule[y-lo])for(int x=a;x<b;x++)if(!vertical[x]&&(gray[y*w+x]&255)<180){yt=Math.min(yt,y);yb=Math.max(yb,y+1);}
                    if(yb-yt<t.gap()*.35f||Math.abs((yt+yb)*.5f-cy)>t.gap()*.24f)continue;
                    // A meter numeral extends far beyond a fret's string lane.
                    int outside=0;
                    for(int x=a;x<b;x++)for(int y:new int[]{Math.max(0,Math.round(cy-t.gap()*.72f)),Math.min(h-1,Math.round(cy+t.gap()*.72f))})
                        if((gray[y*w+x]&255)<180)outside++;
                    if(!t.bars().isEmpty()&&a<t.bars().get(0)+t.gap()*5&&yb-yt>t.gap()*1.1f&&outside>(b-a)*.45f)continue;
                    if(b-a<t.gap()*.5f&&yb-yt>t.gap()*.95f&&outside>(b-a)*.22f)continue;
                    if(obscured(gray,w,h,(a+b)*.5f,cy,t.gap()))continue;
                    boolean longStem=false;
                    if(b-a<t.gap()*.2f||b-a<t.gap()*.8f&&yb-yt>t.gap()*.95f)for(int x=a;x<b;x++)for(int direction:new int[]{-1,1}) {
                        int count=0,total=0;for(int j=Math.round(-t.gap()*(b-a<t.gap()*.2f?.1f:.65f));j<=Math.round(t.gap()*1.5f);j++){int y=cy+direction*j;if(y<0||y>=h)continue;total++;if((gray[y*w+x]&255)<180)count++;}
                        if(count>total*.8f)longStem=true;
                    }
                    if(longStem)continue;
                    row.add(new Crop(a,yt,b,yb,s,t.top()+s*t.gap(),t.gap()));
                }
            }
            float header=0;
            if(!t.bars().isEmpty()&&t.bars().get(0)<w*.2f)header=t.bars().get(0)+t.gap()*1.8f;
            // Unbarred ASCII tabs still have a vertical column of string names.
            for(var c:row)if(c.left()<w*.15f&&(t.bars().isEmpty()||t.bars().get(0)>=w*.2f||c.left()<t.bars().get(0))) {
                long aligned=row.stream().filter(v->Math.abs(v.left()-c.left())<t.gap()*.2f).map(Crop::string).distinct().count();
                if(aligned>=4)header=Math.max(header,c.right()+t.gap()*.15f);
            }
            for(var c:row)if(c.left()>header)result.add(c);
        }
        return List.copyOf(result);
    }
}
