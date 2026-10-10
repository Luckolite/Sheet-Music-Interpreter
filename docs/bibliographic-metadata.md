# Bibliographic metadata

The Java core includes `ScoreCreditsDetector` for title, artist, composer,
arranger and unresolved credit extraction. Pass OCR text with its original page
coordinates. The caller owns PDF rendering, OCR inference and metadata writes.
This API does not run the musical decoder or download another model.

```java
import io.github.luckolite.interpreter.ScoreCreditsDetector;
import java.util.List;
import java.util.Set;

var page = new ScoreCreditsDetector.Page(1800, 2400, 420, List.of(
        new ScoreCreditsDetector.Line("Over the Sea", 450, 80, 1350, 160),
        new ScoreCreditsDetector.Line("Music by Mira North", 1150, 220, 1650, 250)));
var credits = ScoreCreditsDetector.detect("Imported score", List.of(page), Set.of());
// credits.title(): "Over the Sea"
// credits.composer(): "Mira North"
```

`notationTop` is the first staff's vertical position, or a negative value when
none was detected. Mark continuation pages `creditsOnly` to collect their
credits without treating each local section as a new document title.
`photographicCover` describes source-image evidence; it does not identify a
performer. Ambiguous names can remain in `unclassifiedCredits` for review.
Optional `knownNames` help reconcile spelling without declaring a person's role.
An absent heading retains the caller's title hint; the hint is not a credit.

A dated name may infer Composer when the score header supplies clear work
context, including a smaller, centered “from …” caption beneath the title.
Dates alone do not establish the role. Publisher text, distant side text and
captions below the notation do not supply that source-work evidence.

Title selection preserves separate printed rows inside a broad OCR envelope,
filters duplicate contained fragments, and distinguishes staff-sized section
captions from document headings. Direction words remain eligible when their
baseline proves they belong to a split heading. Spatial audio-access badges do
not become contributor names merely because staff detection changes.

`TrackedHeadingWordSpaces.refine` can recover word spaces from an original
uppercase heading raster. It requires matching glyph counts and the same gap
classification at two ink thresholds. It preserves letters, punctuation and
uniformly tracked acronyms, and returns the original string when evidence is
ambiguous. Use `trackedHeadingWordSpaceCandidate` and
`mergeTrackedHeadingWordSpaces` to retain heading scope and coordinates.
The input is a tightly bounded ARGB raster, with one array entry per pixel.

The portable crop, cover and embedded-text helpers expose their evidence rules
without Android dependencies. The Android adapter additionally owns bitmap
lifetimes, original-PDF crop rendering, provider calls and timeout handling.
Those platform operations are not supplied by this Java API.

Regression examples use fictional text, generated rectangles and synthetic ink.
They do not establish accuracy on arbitrary PDFs. No commercial score scans,
private library records, device logs, signing material or new model weights are
distributed with this module.
