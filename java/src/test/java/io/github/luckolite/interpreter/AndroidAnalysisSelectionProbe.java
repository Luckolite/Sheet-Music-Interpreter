// Copyright 2026 Luckolite
// SPDX-License-Identifier: Apache-2.0
package io.github.luckolite.interpreter;
import java.util.*;
/** Mechanically extracted actual Android native/local selection statements. */
final class AndroidAnalysisSelectionProbe {
 record Selected(OmrScoreInterpreter.Analysis score,OmrScoreInterpreter.Analysis clockAnalysis) {}
 static Selected select(OmrScoreInterpreter.Analysis nativeResult,byte[]labels,byte[]gray,int width,int height,List<MeasureRegion>measures) {
            OmrScoreInterpreter.Analysis score =
                    nativeResult;
            if (score == null)
                score = OmrScoreInterpreter.analyze(labels, gray, width, height, measures);
            var clockAnalysis = score;
        return new Selected(score,clockAnalysis);
 }
}
