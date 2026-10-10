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

`PrintedServiceCreditMarks.retain(page, words, pixels)` can retain a transcription
service for review when original pixels prove a detached waveform mark between
the printed “by” and payload words. It uses bounded gaps from the caller's word
boxes. Connected letters and genuine initials do not establish that evidence.
The original OCR line stays unchanged; `Page.reviewCredits` carries a separate
`ReviewCredit(line, value)` for the detector. The detector binds it to that exact
line and requires a literal payload suffix after the transcription label and at
most one mark token. Case and horizontal whitespace may differ; changed names,
partial payloads and stale word readings cannot authorize review evidence. It
keeps the payload unresolved instead of assigning a contributor role.

The four-, five- and six-argument `Page` constructors remain available and default
to an empty review list. Callers serializing pages must also preserve the optional
`reviewCredits` array, whose entries contain `line` (text and four coordinates)
and `value`. Old inputs with no array mean an empty list. Do not drop this evidence
when replaying pages or storing intermediate metadata inputs.

Header context can be corroborated by two separately printed copies of a title,
even when an artist name precedes the title in the import label. Duplicate boxes
over the same ink do not supply a second copy. For photographic covers,
`LocatedCoverHeadingEvidence.fromOriginal` accepts three agreeing strong readings
of an already located original-PDF heading, retaining its literal independently
of the import label. Work identifiers and heading shape remain guarded. Merge
only into the unchanged nominated line, and retain accepted source evidence
through later fallback reads. OCR confidence is a selection threshold, not a
calibrated probability of correctness.

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

An inline `Arrangement` or `Arrangements` label followed by a name is a prefix
credit, including instrument-qualified forms such as `Piano Arrangement`.
It remains eligible for the existing three agreeing original-source readings.
An empty standalone arrangement label still locates its name above the label.
This distinction does not merge similar names across different credits.

For an already nominated damaged arranger label, three original-source readings
may disagree only on the role word. The original and every reading must retain
the same literal contributor payload, and one reading must actually print
`Arranged by`. Only bounded variations of that label qualify; valid competing
inflections, changed names, extra contributors and multiline text are rejected.
The existing source geometry, role and identifier checks still apply.

`coverFooterReviewCandidates` retains a separated bottom line on a simple,
staff-free opening cover as unresolved literal text. Call it only for the first
page. It excludes photographic pages, attributed credits and publication
captions, and leaves title and role selection unchanged. The geometry cannot
distinguish a person's name from an identically placed work name.

`mergeCoverFooterReviewConsensus` refines that line only after three strong
original-PDF readings agree. It requires an unchanged word anchor, protected
initials, punctuation and accents, and bounded spacing or internal I/L repair.
Failed rereads retain the original literal. The Android adapter uses its fixed
crop padding at scales 1, 2 and 4 and releases each bitmap after reading. This
route adds no service-credit evidence field, dictionary or model weights.

The portable crop, cover and embedded-text helpers expose their evidence rules
without Android dependencies. The Android adapter additionally owns bitmap
lifetimes, original-PDF crop rendering, provider calls and timeout handling.
Those platform operations are not supplied by this Java API.

Regression examples use fictional text, generated rectangles and synthetic ink.
They do not establish accuracy on arbitrary PDFs. No commercial score scans,
private library records, device logs, signing material or new model weights are
distributed with this module.
