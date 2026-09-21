// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import java.util.regex.*;

/** Conservative numeric tab syntax and explicit rhythm; never invents a duration from spacing. */
public final class TabNotation {
    private TabNotation() { }
    public static List<TablatureDecoder.Fret> parse(String text,float left,float right,float y,int string) {
        text=text.trim();var out=new ArrayList<TablatureDecoder.Fret>();
        int flags=0;
        if(text.endsWith("~")){flags|=TabEffect.VIBRATO;text=text.replaceAll("~+$","");}
        var harmonic=Pattern.compile("<([0-9]{1,2})>").matcher(text);
        if(harmonic.matches()) {
            int f=Integer.parseInt(harmonic.group(1));if(TabEffect.harmonicOffset(f)<0)return List.of();
            return List.of(fret((left+right)/2,y,string,f,flags|TabEffect.encode(TabEffect.HARMONIC,0)));
        }
        var bend=Pattern.compile("([0-9]{1,2})[bB]([0-9]{1,2})(?:[rR]([0-9]{1,2}))?").matcher(text);
        if(bend.matches()) {
            int f=Integer.parseInt(bend.group(1)),to=Integer.parseInt(bend.group(2));
            if(f>36||to>36||to<=f||to-f>12||bend.group(3)!=null&&Integer.parseInt(bend.group(3))!=f)return List.of();
            return List.of(fret(left+(right-left)*bend.group(1).length()/(2*text.length()),y,string,f,
                    flags|TabEffect.encode(bend.group(3)==null?TabEffect.BEND:TabEffect.BEND_RELEASE,to-f)));
        }
        if(!text.matches("(?:[0-9]{1,2}|[xX])(?:[/\\\\hHpP](?:[0-9]{1,2}))*"))return List.of();
        var tokens=Pattern.compile("[0-9]{1,2}|[xX]").matcher(text);int previous=-1,lastEnd=0;
        while(tokens.find()) {
            int f=tokens.group().equalsIgnoreCase("x")?-1:Integer.parseInt(tokens.group());
            if(f>36)return List.of();
            int marks=flags;
            if(previous>=0&&tokens.start()>lastEnd) {
                char op=text.charAt(lastEnd);int kind=op=='h'||op=='H'?TabEffect.HAMMER:op=='p'||op=='P'?TabEffect.PULL:TabEffect.SLIDE;
                if(kind==TabEffect.HAMMER&&f<=previous||kind==TabEffect.PULL&&f>=previous)return List.of();
                if(Math.abs(previous-f)>24)return List.of();
                marks|=TabEffect.encode(kind,previous-f);
            }
            float x=left+(right-left)*(tokens.start()+tokens.end())/(2f*text.length());
            out.add(fret(x,y,string,f,marks));previous=f;lastEnd=tokens.end();
        }
        return out;
    }
    private static TablatureDecoder.Fret fret(float x,float y,int string,int f,int marks) {
        return new TablatureDecoder.Fret(x,y,string,f,0,0,0,marks);
    }
    /** Q/E/S/T, H/W duration labels and Unicode note/rest glyphs in a separate rhythm lane. */
    public static float duration(String text) {
        String s=text.replace(".","").trim();return switch(s){
            case "W","𝅝","𝄻"->4;case "H","𝅗𝅥","𝄼"->2;case "Q","♩","𝅘𝅥","𝄽"->1;
            case "E","♪","𝅘𝅥𝅮","𝄾"->.5f;case "S","𝅘𝅥𝅯","𝄿"->.25f;case "T","𝅘𝅥𝅰","𝅀"->.125f;default->0;};
    }
    public static boolean rest(String s){return s.codePoints().anyMatch(c->c>=0x1d13b&&c<=0x1d140);}
    public static List<TablatureDecoder.Staff> rhythmWords(List<TablatureDecoder.Staff> tabs,List<TablatureDecoder.Word> words,int w,int h) {
        var result=new ArrayList<TablatureDecoder.Staff>();
        for(var t:tabs) {
            var fs=new ArrayList<>(t.frets());
            if(t.standardTop()<0)for(var word:words) {
                float x=(word.left()+word.right())*.5f*w,y=(word.top()+word.bottom())*.5f*h,d=duration(word.text());
                if(d==0||y<t.top()-t.gap()*3||y>t.top()+t.gap()*8)continue;
                if(!rest(word.text())&&y>=t.top()-t.gap()*.6f&&y<=t.top()+t.gap()*5.6f)continue;
                int dots=Math.min(2,(int)word.text().chars().filter(c->c=='.').count());
                if(rest(word.text())) {
                    if(fs.stream().noneMatch(f->Math.abs(f.x()-x)<t.gap()*.4f))fs.add(new TablatureDecoder.Fret(x,y,0,-2,d,0,dots,0));
                } else for(int i=0;i<fs.size();i++) {
                    var f=fs.get(i);if(Math.abs(f.x()-x)<t.gap()*.6f)fs.set(i,withRhythm(f,d,0,dots));
                }
            }
            fs.sort(Comparator.comparingDouble(TablatureDecoder.Fret::x));
            result.add(new TablatureDecoder.Staff(t.top(),t.gap(),t.standardTop(),List.copyOf(fs),t.bars()));
        }
        return List.copyOf(result);
    }
    static TablatureDecoder.Fret withRhythm(TablatureDecoder.Fret f,float duration,int beams,int dots) {
        if(duration<.25f&&beams==0){beams=duration==.125f?3:4;duration=0;}
        return new TablatureDecoder.Fret(f.x(),f.y(),f.string(),f.fret(),duration,beams,dots,f.marks());
    }
    /** External stems and beams used by common tab engraving; ambiguous/no-stem values stay unknown. */
    public static List<TablatureDecoder.Staff> rasterRhythm(List<TablatureDecoder.Staff> tabs,byte[] gray,int w,int h) {
        var result=new ArrayList<TablatureDecoder.Staff>();
        for(var t:tabs) {
            var frets=new ArrayList<TablatureDecoder.Fret>();
            for(var f:t.frets()) {
                if(t.standardTop()>=0||f.duration()>0||f.beams()>0||f.fret()<0){frets.add(f);continue;}
                int beams=-1;
                for(int direction:new int[]{-1,1}) {
                    float edge=t.top()+(direction==1?5*t.gap():0);
                    for(int x=Math.max(0,Math.round(f.x()-t.gap()*.3f));x<=Math.min(w-1,Math.round(f.x()+t.gap()*.3f));x++) {
                        int start=Math.round(edge+direction*t.gap()*.55f),end=start,ink=0;
                        for(int j=0;j<Math.round(t.gap()*3.3f);j++) {
                            int y=start+direction*j;if(y<0||y>=h)break;
                            if((gray[y*w+x]&255)<180){ink++;end=y;}else if(j>t.gap()*.4f)break;
                        }
                        if(ink<t.gap()*.85f)continue;
                        int count=0;boolean run=false;
                        for(int y=Math.min(start,end);y<=Math.max(start,end);y++) {
                            int across=0,total=0;
                            for(int dx=Math.round(t.gap()*.25f);dx<=Math.round(t.gap()*.9f);dx++) {
                                if(x+dx<w){total++;if((gray[y*w+x+dx]&255)<180)across++;}
                            }
                            boolean b=total>0&&across>=total*.8f;
                            if(b&&!run)count++;run=b;
                        }
                        if(count<=4)beams=Math.max(beams,count);
                    }
                }
                frets.add(beams<0?f:withRhythm(f,beams==0?1:0,beams,0));
            }
            result.add(new TablatureDecoder.Staff(t.top(),t.gap(),t.standardTop(),List.copyOf(frets),t.bars()));
        }
        return List.copyOf(result);
    }
}
