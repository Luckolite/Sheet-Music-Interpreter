# Numbered work titles above instrument-part headings

An article or quoted opening heading can identify a numbered work even when a
larger instrument-part label repeats above it. Geometry nominates a reread. Work
ownership then requires an exact public alias, matching printed work form and
unchanged series ordinal, one matching work entity, and no known-person or public
person-label collision. Public facts supply ownership evidence; every output
character comes from the source-bound OCR literals.

The heading, numbered caption and dominant part heading each need the complete
12-view medium-reader grid. Existing consensus requires a literal majority with
strong readings over at least two scales. The heading and dominant header may
change by at most one normalized glyph. A recognizable source work number and
form must survive, as must numeric and Roman identifiers. The dominant heading
must name a known instrument with a recognized part qualifier and repeat in a
comparable position on another page. Collection titles are protected.

Only the two witnessed work rows change. Explicit roles, service reviews, known
raw names, independent same-name rows, contributor fields and unrelated review
entries are preserved. An unresolved entry may be consumed only when it belongs
to that exact source-bound title row. Unknown or ambiguous work identities retain
the ordinary scan result. Catalogue absence alone never decides ownership.

`MusicMetadataCatalogue.load` accepts caller-owned Readers for the three pinned
TSV assets. It has no Android or network dependency. `MusicMetadataCatalogueSnapshot.load` verifies the exact bundled manifest
and each TSV size and SHA-256 before parsing. Its stream source has no Android
or network dependency. It opens only the four pinned runtime filenames, closes
every stream, rejects incomplete or excess bytes, and preserves cancellation. Person data is used only to veto a title/person collision;
edit distance against this incomplete vocabulary is not an automatic name repair.
Source queries, content hashes, language scope and CC0 provenance are in
`data/music-metadata-catalogue/manifest.json`.

The tests use invented work facts and synthetic original geometry under Apache-2.0.
Additional tests check portable bundled-asset integrity and cancellation.
They do not verify Android rendering, native OCR, desktop packaging
or accuracy on a user's full library. Those remain separate integration gates.
