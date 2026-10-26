# Tremolo source ownership through instrument changes

Instrument selection and octave transposition must retain the printed pair and
its complete original sound-alias domain. Performed rows can duplicate original
owners after repeats or contain fewer rows after ties. An original endpoint index
cannot be interpreted as a performed-row index. Use actual source alias vectors
for endpoint membership and the actual performed interval index for payload lookup.

`ScoreTremoloSoundOwnership` contains the shared portable domain selection,
alias bounds and membership operations. It is data handling, not a Source/Current
or native producer certificate. Complete independent endpoint sounds and their
clock policy still require the existing performance validation.

Music Sheets' Android audio renderer, custom-instrument adapter and desktop page
audio preparation are platform integration code outside this standalone library.
Their reviewed Apache-2.0 integration diff is provided separately in
`integrations/music-sheets/custom-tremolo-playback.patch`; it preserves all legacy
constructor signatures and default-null behavior. The standalone Python paired
performance path already retains its source records; original pitch-transposition
and repeat regressions guard that behavior without adding an app-specific API.

The integration retains exact source boundaries and placements through copies,
adds the original domain to performed-audio cache identity, and attaches joined
page/retained pair records before desktop navigation. Audio100 invalidates older
completed app/Hub audio. Recognition117, Guide299, model weights and licensed
recording assets are unchanged. Source publication and matched packaged deployment
are separate gates; no packaged deployment is asserted by this document.

All supplied fixtures are original synthetic records and generated schedules.
No commercial score scans, personal library data or recorded device logs are included.
