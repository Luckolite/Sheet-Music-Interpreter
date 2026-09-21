// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;

/** Six-string numeric tablature. Frets are absolute semitone offsets, never key-signature steps. */
public final class TablatureDecoder {
    public record Fret(float x,float y,int string,int fret,float duration,int beams,int dots,int marks) {
        public Fret(float x,float y,int string,int fret){this(x,y,string,fret,0,0,0,0);}
    }
    public record Staff(float top,float gap,float standardTop,List<Fret> frets,List<Float> bars) { }
    private static final int[] STANDARD={64,59,55,50,45,40};
    private TablatureDecoder() { }
    public static List<Staff> detect(byte[] gray,int w,int h) {
        if(gray==null||w<64||h<64||gray.length!=(long)w*h)return List.of();
        // Imported screenshots often use pale rules or enlarged string spacing.
        // Keep the darkest complete geometry first, then fill in missing systems.
        var found=new ArrayList<Staff>();
        for(int threshold:new int[]{180,200,220,235,245,248})for(var staff:detectAtThreshold(gray,w,h,threshold)) {
            int match=-1;for(int i=0;i<found.size();i++)if(Math.abs(found.get(i).top-staff.top)<Math.max(found.get(i).gap,staff.gap)*3){match=i;break;}
            if(match<0)found.add(staff);
            else {
                var old=found.get(match);var bars=new ArrayList<>(old.bars);
                if(Math.abs(old.top-staff.top)<old.gap*.2f&&Math.abs(old.gap-staff.gap)<old.gap*.1f)
                    for(float bar:staff.bars)if(bars.stream().noneMatch(x->Math.abs(x-bar)<old.gap*.6f))bars.add(bar);
                bars.sort(Float::compare);found.set(match,new Staff(old.top,old.gap,old.standardTop,old.frets,List.copyOf(bars)));
            }
        }
        found.sort(Comparator.comparingDouble(Staff::top));
        var result=new ArrayList<Staff>();
        for(var staff:found) {
            float paired=staff.standardTop;
            // A lighter pass may see only five rules of another tablature system.
            // Such a partial row is not paired standard notation.
            if(paired>=0)for(var previous:found)
                if(previous.top<staff.top&&paired>=previous.top-previous.gap*.5f
                        &&paired<=previous.top+previous.gap*5.5f)paired=-1;
            result.add(new Staff(staff.top,staff.gap,paired,staff.frets,staff.bars));
        }
        return List.copyOf(result);
    }
    private static List<Staff> detectAtThreshold(byte[] gray,int w,int h,int threshold) {
        var lines=new ArrayList<Integer>();var thickness=new ArrayList<Integer>();var strength=new ArrayList<Integer>();int start=-1,peak=0;
        for(int y=0;y<=h;y++) {
            int ink=0;if(y<h)for(int x=0;x<w;x++)if((gray[y*w+x]&255)<threshold)ink++;
            if(ink>w*.55){if(start<0){start=y;peak=0;}peak=Math.max(peak,ink);}
            else if(start>=0){if(y-start<Math.max(6,w/120)){lines.add((start+y-1)/2);thickness.add(y-start);strength.add(peak);}start=-1;}
        }
        var result=new ArrayList<Staff>();
        for(int i=0;i+5<lines.size();i++) {
            float gap=(lines.get(i+5)-lines.get(i))/5f;if(gap<6||gap>w*.15)continue;
            boolean regular=true;for(int n=1;n<6;n++)if(Math.abs(lines.get(i+n)-lines.get(i)-n*gap)>gap*.16f)regular=false;
            int minThickness=Integer.MAX_VALUE,maxThickness=0,minInk=w,maxInk=0;
            for(int n=0;n<6;n++){minThickness=Math.min(minThickness,thickness.get(i+n));maxThickness=Math.max(maxThickness,thickness.get(i+n));minInk=Math.min(minInk,strength.get(i+n));maxInk=Math.max(maxInk,strength.get(i+n));}
            // Preserve the stronger length agreement for dark conventional notation:
            // five staff rules plus a shorter beam must not become six tab strings.
            boolean strongRules=false;
            for(int n=0;n<6;n++) {
                int ink=0;
                for(int x=0;x<w;x++)if((gray[lines.get(i+n)*w+x]&255)<180)ink++;
                if(ink>w*.55f)strongRules=true;
            }
            if(strongRules&&minInk<maxInk*.88f)continue;
            if(!regular||maxThickness>minThickness*2+1||minInk<maxInk*.80f)continue;
            if(i>0&&Math.abs(lines.get(i)-lines.get(i-1)-gap)<gap*.16f)continue;
            if(i+6<lines.size()&&Math.abs(lines.get(i+6)-lines.get(i+5)-gap)<gap*.16f)continue;
            float top=lines.get(i);List<Fret> frets=List.of();
            float standard=-1;
            if(i>=5&&(result.isEmpty()||lines.get(i-1)>result.get(result.size()-1).top+result.get(result.size()-1).gap*6)) {
                float sg=(lines.get(i-1)-lines.get(i-5))/4f;
                boolean five=sg>=4&&top-lines.get(i-1)<gap*15;
                for(int n=1;n<5;n++)if(Math.abs(lines.get(i-5+n)-lines.get(i-5)-n*sg)>sg*.16f)five=false;
                if(five)standard=lines.get(i-5);
            }
            var bars=new ArrayList<Float>();int run=-1;
            for(int x=0;x<=w;x++) {
                int ink=0,total=0;
                if(x<w)for(int y=Math.round(top);y<=Math.round(top+5*gap);y++){total++;if((gray[y*w+x]&255)<threshold)ink++;}
                if(total>0&&ink>=total*.98){if(run<0)run=x;}
                else if(run>=0){float center=(run+x-1)*.5f;if(bars.isEmpty()||center-bars.get(bars.size()-1)>gap*.6f)bars.add(center);run=-1;}
            }
            // Pale rules interrupted by fret digits need enclosing vertical evidence.
            if(minInk<maxInk*.88f&&bars.size()<2)continue;
            result.add(new Staff(top,gap,standard,List.copyOf(frets),List.copyOf(bars)));i+=5;
        }
        return List.copyOf(result);
    }
    public record Word(String text,float left,float top,float right,float bottom) {
        public Word {
            Objects.requireNonNull(text);
            if(!Float.isFinite(left)||!Float.isFinite(top)||!Float.isFinite(right)||!Float.isFinite(bottom)
                    ||left<0||top<0||right>1||bottom>1||left>=right||top>=bottom)throw new IllegalArgumentException("Invalid tab OCR box");
        }
    }
    /** OCR boxes are normalized page coordinates. Unknown glyphs never become open strings. */
    public static List<Staff> withWords(List<Staff> staffs,List<Word> words,int w,int h) {
        var result=new ArrayList<Staff>();
        for(Staff staff:staffs) {
            var frets=new ArrayList<Fret>();
            for(Word word:words) {
                float y=(word.top+word.bottom)*.5f*h;
                int string=Math.round((y-staff.top)/staff.gap);
                if(string<0||string>=6||Math.abs(y-staff.top-string*staff.gap)>staff.gap*.47f)continue;
                for(var f:TabNotation.parse(word.text,word.left*w,word.right*w,staff.top+string*staff.gap,string)) {
                    if(staff.bars.stream().anyMatch(bar->Math.abs(bar-f.x)<staff.gap*.25f))continue;
                    int old=-1;for(int i=0;i<frets.size();i++)if(frets.get(i).string==string&&Math.abs(frets.get(i).x-f.x)<staff.gap*.3f){old=i;break;}
                    if(old<0)frets.add(f);else if(f.marks!=0&&frets.get(old).marks==0)frets.set(old,f);
                }
            }
            frets.sort(Comparator.comparingDouble(Fret::x));
            result.add(new Staff(staff.top,staff.gap,staff.standardTop,List.copyOf(frets),staff.bars));
        }
        return TabNotation.rhythmWords(List.copyOf(result),words,w,h);
    }
    public static byte[] withoutTabs(byte[] pixels,int w,int h,List<Staff> tabs,boolean grayscale) {
        if(tabs.isEmpty())return pixels;
        byte[] result=pixels.clone();
        for(Staff tab:tabs)for(int y=Math.max(0,Math.round(tab.top-tab.gap*.65f));y<=Math.min(h-1,Math.round(tab.top+tab.gap*5.65f));y++)
            Arrays.fill(result,y*w,(y+1)*w,(byte)(grayscale?255:0));
        return result;
    }
    public static int midi(int string,int fret,int[] tuning,int capo) {
        if(tuning.length!=6||string<0||string>=6||fret<0||fret>36||capo<0||capo>12)throw new IllegalArgumentException("Invalid six-string tab tuning/fret/capo");
        int result=tuning[string]+fret+capo;if(result<0||result>127)throw new IllegalArgumentException("Tab pitch outside MIDI range");return result;
    }
    public static List<MeasureRegion> reconcileMeasures(List<MeasureRegion> input,List<Staff> tabs,int w,int h) {
        if(tabs.isEmpty())return input;
        var measures=new ArrayList<>(input);
        for(Staff tab:tabs) {
            if(tab.standardTop<0||tab.bars.size()<2)continue;
            var old=new ArrayList<MeasureRegion>();
            for(var m:measures)if(tab.standardTop/h>=m.top()-tab.gap/h*2&&tab.standardTop/h<=m.bottom()&&m.bottom()*h<tab.top)old.add(m);
            if(old.isEmpty())continue;
            float left=old.stream().map(MeasureRegion::left).min(Float::compare).orElse(0f),right=old.stream().map(MeasureRegion::right).max(Float::compare).orElse(1f);
            var cuts=new ArrayList<Float>();cuts.add(left);
            for(float x:tab.bars)if(x/w>left+tab.gap/w*2&&x/w<right-tab.gap/w*2)cuts.add(x/w);
            cuts.add(right);int insertion=measures.indexOf(old.get(0));measures.removeAll(old);
            for(int i=0;i+1<cuts.size();i++)measures.add(insertion++,new MeasureRegion(cuts.get(i),cuts.get(i+1),old.get(0).top(),old.get(0).bottom()));
        }
        return List.copyOf(measures);
    }
    public static ScorePageInterpretation apply(ScorePageInterpretation score,List<Staff> tabs,int w,int h) {
        return apply(score,tabs,w,h,STANDARD,0);
    }
    public static ScorePageInterpretation apply(ScorePageInterpretation score,List<Staff> tabs,int w,int h,int[] tuning,int capo) {
        if(tabs.isEmpty())return score;
        for(int string=0;string<6;string++)midi(string,0,tuning,capo);
        // OCR measure numbers can create empty standard measures after the six
        // tab rules have been masked. They carry no musical events and must not
        // veto the frets. Preserve real notation, rests and paired staff timing.
        if(!score.measures().isEmpty() && score.notes().isEmpty() && score.rests().isEmpty()
                && tabs.stream().allMatch(t->t.standardTop<0))
            score=new ScorePageInterpretation(List.of(),List.of());
        var notes=new ArrayList<>(score.notes());var measures=new ArrayList<>(score.measures());
        var rests=new ArrayList<>(score.rests());
        for(Staff tab:tabs) {
            if(tab.standardTop<0) {
                // A simple tab without written rhythm has pitches but no provable durations.
                // Preserve unknown duration so callers can identify estimated playback.
                if(!score.measures().isEmpty()||tab.frets.isEmpty())continue;
                var bars=new ArrayList<>(tab.bars);
                if(bars.size()<2){bars.clear();bars.add(tab.frets.get(0).x-tab.gap);bars.add(tab.frets.get(tab.frets.size()-1).x+tab.gap);}
                else {
                    if(tab.frets.get(0).x<bars.get(0))bars.add(0,tab.frets.get(0).x-tab.gap);
                    if(tab.frets.get(tab.frets.size()-1).x>bars.get(bars.size()-1))bars.add(tab.frets.get(tab.frets.size()-1).x+tab.gap);
                }
                for(int bi=0;bi+1<bars.size();bi++) {
                    float left=bars.get(bi),right=bars.get(bi+1);if(right-left<tab.gap*2)continue;
                    int bar=measures.size();measures.add(new MeasureRegion(Math.max(0,left/w),Math.min(1,right/w),Math.max(0,(tab.top-tab.gap)/h),Math.min(1,(tab.top+tab.gap*6)/h)));
                    for(Fret f:tab.frets)if(f.x>left&&f.x<right) {
                        float position=(f.x-left)/(right-left);
                        if(f.fret==-2){rests.add(new ScoreRestEvent(bar,position,f.y/h,tab.gap/h,0,1,f.duration*(f.dots==0?1:f.dots==1?1.5f:1.75f)));continue;}
                        int marks=f.marks;if(f.fret<0)marks|=TabEffect.encode(TabEffect.DEAD,0);
                        int pitch=soundingPitch(f,tuning,capo);
                        var n=new ScoreNoteEvent(bar,position,0,0,1,f.y/h,false,f.dots,f.beams,0,f.duration).withArticulations(marks);
                        notes.add(pitched(n,pitch));
                    }
                }
                continue;
            }
            var indices=new ArrayList<Integer>();int down=0,same=0;
            for(int i=0;i<score.notes().size();i++) {
                var n=score.notes().get(i);var m=score.measures().get(n.measureIndex());
                if(tab.standardTop/h<m.top()-tab.gap/h*2||tab.standardTop/h>m.bottom()||m.bottom()*h>=tab.top+tab.gap)continue;
                indices.add(i);float x=(m.left()+n.positionInMeasure()*(m.right()-m.left()))*w;
                int printed=printedMidi(n,score);
                boolean lowerMatch=false,sameMatch=false;
                for(Fret f:tab.frets)if(f.fret>=0&&Math.abs(f.x-x)<tab.gap*.65f) {
                    int sounding=soundingPitch(f,tuning,capo);lowerMatch|=printed-sounding==12;sameMatch|=printed==sounding;
                }
                if(lowerMatch)down++;if(sameMatch)same++;
            }
            // Paired notation owns rhythm and note count. Require repeated cross-notation
            // agreement before resolving the guitar's commonly omitted octave transposition.
            if(down>=3&&down>same)for(int index:indices) {
                var n=score.notes().get(index);notes.set(index,n.withOctaveShift(Math.max(-2,n.octaveShift()-1)));
            }
        }
        // Attach standalone rests to the neighboring onset, preserving silent time in the clock.
        for(var r:rests)if(r.measureIndex()>=score.measures().size()) {
            float previous=-1,next=2;
            for(var n:notes)if(n.measureIndex()==r.measureIndex()){if(n.positionInMeasure()<r.positionInMeasure())previous=Math.max(previous,n.positionInMeasure());else next=Math.min(next,n.positionInMeasure());}
            for(int i=0;i<notes.size();i++){var n=notes.get(i);if(n.measureIndex()!=r.measureIndex())continue;
                if(previous>=0&&Math.abs(n.positionInMeasure()-previous)<.015f)notes.set(i,withRestAfter(n,(float)r.durationBeats()));
                else if(previous<0&&Math.abs(n.positionInMeasure()-next)<.015f)notes.set(i,n.withLeadingRest(n.leadingRestBeats()+(float)r.durationBeats()));}
        }
        // Only attach effects to a paired note when both its position and sounding pitch agree.
        for(var tab:tabs)if(tab.standardTop>=0)for(var f:tab.frets)if(f.fret>=0&&f.marks!=0) {
            for(int i=0;i<notes.size();i++){var n=notes.get(i);var m=measures.get(n.measureIndex());
                if(tab.standardTop/h<m.top()-tab.gap/h*2||tab.standardTop/h>m.bottom()||m.bottom()*h>=tab.top)continue;
                float x=(m.left()+n.positionInMeasure()*(m.right()-m.left()))*w;
                if(Math.abs(x-f.x)<tab.gap*.65f&&printedMidi(n,score)==soundingPitch(f,tuning,capo))notes.set(i,n.withArticulations((n.articulations()&~TabEffect.ALL)|f.marks));
            }
        }
        var handled=new HashSet<String>();
        for(Staff tab:tabs)if(tab.standardTop>=0)for(Fret fret:tab.frets)if(fret.fret<0) {
            if(tab.frets.stream().anyMatch(f->f.fret>=0&&Math.abs(f.x-fret.x)<tab.gap*.65f))continue;
            if(tab.frets.stream().filter(f->f.fret<0&&Math.abs(f.x-fret.x)<tab.gap*.65f).map(Fret::string).distinct().count()<3)continue;
            ScoreNoteEvent target=null;float closest=tab.gap*.65f;
            for(var n:notes) {
                var m=measures.get(n.measureIndex());
                if(tab.standardTop/h<m.top()-tab.gap/h*2||tab.standardTop/h>m.bottom()||m.bottom()*h>=tab.top)continue;
                float dx=Math.abs((m.left()+n.positionInMeasure()*(m.right()-m.left()))*w-fret.x);
                if(dx<closest){closest=dx;target=n;}
            }
            if(target==null)continue;
            var m=measures.get(target.measureIndex());float tolerance=tab.gap*.65f/((m.right()-m.left())*w);
            String key=target.measureIndex()+":"+Math.round(target.positionInMeasure()*1000);
            if(!handled.add(key))continue;
            float duration=target.beamCount()>0?1f/(1<<Math.max(1,Math.min(3,target.beamCount()))):target.unbeamedDurationBeats();
            if(duration<=0)continue;
            duration*=target.durationScale();if(target.augmentationDots()>0)duration*=2-1f/(1<<target.augmentationDots());
            final int bar=target.measureIndex();final float position=target.positionInMeasure();
            notes.removeIf(n->n.measureIndex()==bar&&Math.abs(n.positionInMeasure()-position)<tolerance);
            float previous=-1,next=2;
            for(var n:notes)if(n.measureIndex()==bar){if(n.positionInMeasure()<position)previous=Math.max(previous,n.positionInMeasure());else next=Math.min(next,n.positionInMeasure());}
            for(int i=0;i<notes.size();i++) {
                var n=notes.get(i);if(n.measureIndex()!=bar)continue;
                if(previous>=0&&Math.abs(n.positionInMeasure()-previous)<tolerance)notes.set(i,withRestAfter(n,duration));
                else if(previous<0&&Math.abs(n.positionInMeasure()-next)<tolerance)notes.set(i,n.withLeadingRest(n.leadingRestBeats()+duration));
            }
            rests.add(new ScoreRestEvent(bar,position,target.pageY(),tab.gap/h,target.staffIndex(),target.staffCount(),duration));
        }
        notes.sort(Comparator.comparingInt(ScoreNoteEvent::measureIndex).thenComparingDouble(ScoreNoteEvent::positionInMeasure).thenComparingInt(ScoreNoteEvent::staffStep));
        return new ScorePageInterpretation(measures,notes,score.firstMeasureNumber(),score.keyChanges(),score.tempoChanges(),score.meterChanges(),rests,score.techniqueChanges(),score.dynamicChanges());
    }
    private static int soundingPitch(Fret f,int[] tuning,int capo) {
        return TabEffect.kind(f.marks)==TabEffect.HARMONIC?tuning[f.string]+capo+TabEffect.harmonicOffset(f.fret):midi(f.string,Math.max(0,f.fret),tuning,capo);
    }
    private static ScoreNoteEvent withRestAfter(ScoreNoteEvent n,float beats) {
        return new ScoreNoteEvent(n.measureIndex(),n.positionInMeasure(),n.staffStep(),n.staffIndex(),n.staffCount(),n.pageY(),n.tiedFromPrevious(),n.augmentationDots(),n.beamCount(),n.writtenAccidental(),n.unbeamedDurationBeats(),n.tupletDivisor(),n.followingRestBeats()+beats,n.articulations(),n.clefBottomDiatonic(),n.crossStaffBeam(),n.leadingRestBeats(),n.compactOpening(),n.octaveShift());
    }
    private static int printedMidi(ScoreNoteEvent n,ScorePageInterpretation score) {
        int clef=n.clefBottomDiatonic();if(clef==ScoreNoteEvent.CLEF_UNKNOWN)clef=ScoreNoteEvent.CLEF_TREBLE;
        int pitch=clef+n.staffStep(),letter=Math.floorMod(pitch,7),acc=n.writtenAccidental();
        if(acc==ScoreNoteEvent.ACCIDENTAL_FROM_KEY) {
            int key=0;for(var k:score.keyChanges())if(k.measureIndex()<=n.measureIndex())key=k.fifths();
            acc=0;int[] order=key>=0?new int[]{3,0,4,1,5,2,6}:new int[]{6,2,5,1,4,0,3};
            for(int i=0;i<Math.abs(key);i++)if(order[i]==letter)acc=key>0?1:-1;
        }
        return (Math.floorDiv(pitch,7)+1+n.octaveShift())*12+new int[]{0,2,4,5,7,9,11}[letter]+ScoreNoteEvent.accidentalSemitones(acc);
    }
    private static ScoreNoteEvent pitched(ScoreNoteEvent n,int midi) {
        int pc=midi%12;int[] letters={0,0,1,1,2,3,3,4,4,5,5,6},acc={0,1,0,1,0,0,1,0,1,0,1,0};
        int step=(midi/12-1)*7+letters[pc]-ScoreNoteEvent.CLEF_TREBLE;
        return new ScoreNoteEvent(n.measureIndex(),n.positionInMeasure(),step,n.staffIndex(),n.staffCount(),n.pageY(),n.tiedFromPrevious(),n.augmentationDots(),n.beamCount(),acc[pc],n.unbeamedDurationBeats(),n.tupletDivisor(),n.followingRestBeats(),n.articulations(),ScoreNoteEvent.CLEF_TREBLE,n.crossStaffBeam(),n.leadingRestBeats(),n.compactOpening(),0);
    }
}
