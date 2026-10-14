// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
/** Original text and procedural staff evidence; no private score phrase or raster. */
public class PrintedTempoCaptionTest {
    static final int W=500,H=300;
    static byte[] rails(){var g=new byte[W*H];Arrays.fill(g,(byte)255);
        for(int y=100;y<=164;y+=16)for(int x=20;x<460;x++)g[y*W+x]=0;return g;}
    static ScorePageInterpretation score(List<ScoreTempoChange> tempos){
        var n=new ScoreNoteEvent(0,.25f,0,0,1,164f/H,false,0,0,0,1,1,0);
        return new ScorePageInterpretation(List.of(new MeasureRegion(.1f,.9f,.25f,.65f)),
            List.of(n),1,List.of(),tempos,List.of(),List.of());}
    static ScorePageInterpretation score(){return score(List.of(new ScoreTempoChange(0,.25f,120,1)));}
    static PlayingTechniqueDetector.Word word(String text){return new PlayingTechniqueDetector.Word(text,.30f,.20f,.70f,.25f);}
    static ScorePageInterpretation detect(String text,ScorePageInterpretation s,byte[] gray){
        return ScoreExpressionDetector.apply(s,List.of(word(text)),
            List.of(new PlayingTechniqueDetector.Staff(100,164,16,0,1)),gray,W,H);}
    static String printed(ScorePageInterpretation score){assertEquals(1,score.expressiveEvents().size());
        return score.expressiveEvents().get(0).evidence().get(0).printedText();}
    @Test public void genericCaptionNeedsExistingVerifiedMetronome(){
        assertEquals("With relaxed swing",printed(detect("With relaxed swing ♩ = 120",score(),rails())));}
    @Test public void knownCaptionDoesNotDuplicateEquation(){
        assertEquals("dolce",printed(detect("dolce ♩ = 120",score(),rails())));}
    @Test public void printedPulseRateRatherThanQuarterBpmOwnsCaption(){
        var s=score(List.of(new ScoreTempoChange(0,.25f,60,.5)));
        assertEquals("Lightly dancing",printed(detect("Lightly dancing ♪ = 120",s,rails())));}
    @Test public void arbitraryProseCannotInventTempo(){
        assertTrue(detect("With relaxed swing ♩ = 120",score(List.of()),rails()).expressiveEvents().isEmpty());
        assertTrue(detect("With relaxed swing",score(),rails()).expressiveEvents().isEmpty());}
    @Test public void mismatchedRateIsNotRetained(){
        assertTrue(detect("With relaxed swing ♩ = 132",score(),rails()).expressiveEvents().isEmpty());}
    @Test public void completeStaffEvidenceRemainsRequired(){
        byte[] blank=new byte[W*H];Arrays.fill(blank,(byte)255);
        assertTrue(detect("With relaxed swing ♩ = 120",score(),blank).expressiveEvents().isEmpty());}
    @Test public void numericEquationAndTrailingProseDoNotBecomeCaption(){
        for(String text:List.of("♩ = 120","120","With relaxed swing = 120","With relaxed swing ♩ = 120 later","Book 12 ♩ = 120"))
            assertNull(text,PrintedTempoCaption.from(word(text),score()));}
    @Test public void tempoMustOccupyTheSameLineAndSystem(){
        assertNull(PrintedTempoCaption.from(new PlayingTechniqueDetector.Word("With relaxed swing ♩ = 120",.6f,.2f,.8f,.25f),score()));
        assertNull(PrintedTempoCaption.from(new PlayingTechniqueDetector.Word("With relaxed swing ♩ = 120",.3f,.01f,.7f,.03f),
            new ScorePageInterpretation(List.of(new MeasureRegion(.1f,.9f,.7f,.8f)),score().notes(),1,List.of(),score().tempoChanges(),List.of(),List.of())));}
    @Test public void ambiguousMultipleMarksDoNotBorrowOwnership(){
        assertNull(PrintedTempoCaption.from(word("With relaxed swing ♩ = 120"),
            score(List.of(new ScoreTempoChange(0,.25f,120,1),new ScoreTempoChange(0,.5f,120,1)))));}
    @Test public void explicitGlyphMustAgreeWithPrintedPulse(){
        assertNull(PrintedTempoCaption.from(word("Lightly dancing ♩ = 120"),
            score(List.of(new ScoreTempoChange(0,.25f,60,.5)))));}
    @Test public void dottedPulseKeepsItsMarkedRate(){
        var s=score(List.of(new ScoreTempoChange(0,.25f,90,.75)));
        assertEquals("Lightly dancing",printed(detect("Lightly dancing ♪. = 120",s,rails())));}
    @Test public void sourceNotesAndTempoStayExact(){
        var s=score();var out=detect("With relaxed swing ♩ = 120",s,rails());
        assertEquals(s.notes(),out.notes());assertEquals(s.rests(),out.rests());assertEquals(s.tempoChanges(),out.tempoChanges());}
}
