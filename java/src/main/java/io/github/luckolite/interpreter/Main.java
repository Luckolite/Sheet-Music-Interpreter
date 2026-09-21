// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** File bridge used by the Python image/PDF frontend. No Android classes or external JARs. */
public final class Main {
    private Main() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=5) {
            System.err.println("Usage: java -jar interpreter.jar page.page.gz output.json numerator denominator key-fifths");
            System.exit(2);
        }
        int numerator=Integer.parseInt(args[2]),denominator=Integer.parseInt(args[3]);
        var initialMeter=new ScoreMeterChange(0,numerator,denominator);
        int initialKey=Integer.parseInt(args[4]);if(initialKey < -7||initialKey>7)throw new IllegalArgumentException("Key must be -7..7");
        byte[] labels,gray;int width,height;var annotations=SheetInterpreter.Annotations.EMPTY;
        try(var in=new DataInputStream(new GZIPInputStream(Files.newInputStream(Path.of(args[0]))))) {
            if(in.readInt()!=0x52535031)throw new IOException("Not an RSP1 page");
            width=in.readInt();height=in.readInt();long size=(long)width*height;
            if(width<1||height<1||size>20_000_000)throw new IOException("Invalid page dimensions");
            labels=in.readNBytes((int)size);gray=in.readNBytes((int)size);
            if(labels.length!=size||gray.length!=size)throw new EOFException("Truncated page pixels");
            int first=in.read();
            if(first!=-1) {
                int magic=(first<<24)|(in.readUnsignedByte()<<16)|(in.readUnsignedByte()<<8)|in.readUnsignedByte();
                if(magic!=0x4f435231)throw new IOException("Unsupported annotation data");
                var numbers=readNumbers(in);var tempos=readNumbers(in);var rests=readNumbers(in);
                var words=new ArrayList<SheetInterpreter.Word>();int count=readCount(in);
                for(int i=0;i<count;i++) {
                    int length=readCount(in);byte[] text=in.readNBytes(length);if(text.length!=length)throw new EOFException();
                    words.add(new SheetInterpreter.Word(new String(text,StandardCharsets.UTF_8),in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat()));
                }
                var meters=new ArrayList<ScoreMeterChange>();count=readCount(in);
                for(int i=0;i<count;i++)meters.add(new ScoreMeterChange(in.readInt(),in.readInt(),in.readInt()));
                if(in.read()!=-1)throw new IOException("Unexpected trailing bytes");
                annotations=new SheetInterpreter.Annotations(numbers,tempos,rests,words,meters);
            }
        }
        var tabs=TablatureDecoder.withWords(TablatureDecoder.detect(gray,width,height),
                annotations.words().stream().map(w->new TablatureDecoder.Word(w.text(),w.left(),w.top(),w.right(),w.bottom())).toList(),width,height);
        var tabWarnings=new ArrayList<String>();
        if(tabs.stream().anyMatch(t->t.frets().isEmpty()))tabWarnings.add("Tablature detected but no reliable fret OCR supplied; paired notation is retained where available.");
        tabs=TabNotation.rasterRhythm(tabs,gray,width,height);
        if(tabs.stream().filter(t->t.standardTop()<0).flatMap(t->t.frets().stream()).anyMatch(f->f.duration()==0&&f.beams()==0))tabWarnings.add("Some standalone tab durations are unknown and playback timing is estimated.");
        if(!tabs.isEmpty())tabWarnings.add("Guitar effects require explicit OCR symbols; unsupported graphical bend curves, ties or rhythm glyphs are not inferred.");
        if(!tabs.isEmpty())tabWarnings.add("Tab pitch uses standard six-string guitar tuning unless the Java tuning/capo overload is supplied.");
        var score=SheetInterpreter.analyze(labels,gray,width,height,annotations);
        float[] beats=new float[score.measures().size()];Arrays.fill(beats,initialMeter.quarterBeats());
        for(var change:score.meterChanges().stream().sorted(Comparator.comparingInt(ScoreMeterChange::measureIndex)).toList())
            Arrays.fill(beats,change.measureIndex(),beats.length,change.quarterBeats());
        double[] starts=new double[beats.length+1];for(int i=0;i<beats.length;i++)starts[i+1]=starts[i]+beats[i];
        var events=new ArrayList<Map<String,Object>>();
        for(var note:score.notes()) {
            int bar=note.measureIndex();var region=score.measures().get(bar);
            int key=initialKey;for(var k:score.keyChanges())if(k.measureIndex()<=bar)key=k.fifths();
            int clef=note.clefBottomDiatonic();boolean guessed=clef==ScoreNoteEvent.CLEF_UNKNOWN;
            if(guessed)clef=note.staffCount()>1&&note.staffIndex()>0?ScoreNoteEvent.CLEF_BASS:ScoreNoteEvent.CLEF_TREBLE;
            int diatonic=clef+note.staffStep(),letter=Math.floorMod(diatonic,7),octave=Math.floorDiv(diatonic,7);
            int accidental=note.writtenAccidental();
            if(accidental==ScoreNoteEvent.ACCIDENTAL_FROM_KEY) {
                accidental=0;int[] order=key>=0?new int[]{3,0,4,1,5,2,6}:new int[]{6,2,5,1,4,0,3};
                for(int i=0;i<Math.abs(key);i++)if(order[i]==letter)accidental=key>0?1:-1;
            }
            int midi=(octave+1+note.octaveShift())*12+new int[]{0,2,4,5,7,9,11}[letter]+ScoreNoteEvent.accidentalSemitones(accidental);
            double duration=ScoreNoteTiming.resolvedWrittenDurationBeats(note,score.notes(),beats[bar]);
            boolean estimated=!Double.isFinite(duration)||duration<=0;
            if(estimated)duration=.5;
            var event=new LinkedHashMap<String,Object>();event.put("measureIndex",bar);event.put("staffIndex",note.staffIndex());
            event.put("staffCount",note.staffCount());
            if(note.octaveShift()!=0)event.put("octaveShift",note.octaveShift());event.put("midi",midi);event.put("clefInferred",guessed);
            event.put("startBeat",starts[bar]+ScoreNoteTiming.beatInMeasure(note,score.notes(),beats[bar]));
            if(NoteOrnament.tremoloBeams(note.articulations())>0)event.put("tremoloBeats",NoteOrnament.tremoloBeats(note.articulations()));
            int guitar=note.articulations()&TabEffect.ALL;
            if(guitar!=0)event.put("guitarEffect",Map.of("type",TabEffect.name(guitar),"semitones",TabEffect.kind(guitar)==0?0:TabEffect.delta(guitar),"vibrato",(guitar&TabEffect.VIBRATO)!=0,"palmMute",(guitar&TabEffect.PALM_MUTE)!=0));
            event.put("durationBeats",duration);event.put("durationFallback",estimated);event.put("tiedFromPrevious",note.tiedFromPrevious());
            event.put("x",(region.left()+note.positionInMeasure()*(region.right()-region.left()))*width);
            event.put("y",note.pageY()*height);events.add(event);
        }
        var result=new LinkedHashMap<String,Object>();result.put("schemaVersion",1);result.put("width",width);result.put("height",height);
        result.put("score",score);result.put("events",events);result.put("tablatureWarnings",tabWarnings);result.put("measureBeats",beats);result.put("totalBeats",starts[beats.length]);
        Files.writeString(Path.of(args[1]),json(result)+"\n",StandardCharsets.UTF_8);
    }
    private static int readCount(DataInputStream in)throws IOException {
        int count=in.readInt();if(count<0||count>10000)throw new IOException("Annotation limit exceeded");return count;
    }
    private static List<SheetInterpreter.NumberToken> readNumbers(DataInputStream in)throws IOException {
        int count=readCount(in);var result=new ArrayList<SheetInterpreter.NumberToken>();
        for(int i=0;i<count;i++)result.add(new SheetInterpreter.NumberToken(in.readInt(),in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat(),in.readFloat()));
        return result;
    }
    static String json(Object value)throws Exception {
        if(value==null)return "null";
        if(value instanceof String s) {
            var quoted=new StringBuilder("\"");
            for(int i=0;i<s.length();i++) {
                char c=s.charAt(i);
                if(c=='\\'||c=='\"')quoted.append('\\').append(c);
                else if(c<32)quoted.append(String.format(Locale.ROOT,"\\u%04x",(int)c));
                else quoted.append(c);
            }
            return quoted.append('"').toString();
        }
        if(value instanceof Number n)return Double.isFinite(n.doubleValue())?n.toString():"null";
        if(value instanceof Boolean)return value.toString();
        var items=new ArrayList<String>();
        if(value instanceof Map<?,?> map){for(var e:map.entrySet())items.add(json(e.getKey().toString())+":"+json(e.getValue()));return "{"+String.join(",",items)+"}";}
        if(value.getClass().isRecord()) {
            for(var c:value.getClass().getRecordComponents())items.add(json(c.getName())+":"+json(c.getAccessor().invoke(value)));
            return "{"+String.join(",",items)+"}";
        }
        if(value instanceof Iterable<?> values)for(var item:values)items.add(json(item));
        else if(value.getClass().isArray())for(int i=0;i<java.lang.reflect.Array.getLength(value);i++)items.add(json(java.lang.reflect.Array.get(value,i)));
        else throw new IllegalArgumentException("Unsupported JSON value "+value.getClass());
        return "["+String.join(",",items)+"]";
    }
}
