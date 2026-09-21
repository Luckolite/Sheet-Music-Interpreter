# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Local image/PDF to JSON, with an optional MIDI and MusicXML exports."""
import argparse
import json
from pathlib import Path
import sys
from PIL import Image
from .reader import Interpreter
from .midi import write_midi
from .musicxml import write_musicxml


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path)
    parser.add_argument("--output", "-o", required=True, type=Path)
    parser.add_argument("--midi", type=Path)
    parser.add_argument("--musicxml", type=Path, help="Optional concert-pitch MusicXML export")
    parser.add_argument("--meter", default="4/4", help="Initial meter; read by the caller (default: 4/4)")
    parser.add_argument("--bpm", type=float, default=120, help="Initial quarter-note BPM for exports")
    parser.add_argument("--key-fifths", type=int, default=0, help="Fallback key: sharps positive, flats negative")
    parser.add_argument("--width", type=int, default=2048)
    parser.add_argument("--threads", type=int, default=2)
    parser.add_argument("--model", type=Path, help="Custom TFLite model with the documented tensor contract")
    parser.add_argument("--pages", help="One-based comma-separated PDF pages, in reading order")
    parser.add_argument("--annotations", type=Path, help="JSON list of annotation objects, one per selected page")
    args = parser.parse_args()
    try:
        meter = tuple(int(n) for n in args.meter.split("/"))
        if len(meter) != 2:
            raise ValueError("Use --meter numerator/denominator")
        if not 15 <= args.bpm <= 400:
            raise ValueError("BPM must be 15..400")
        annotations = json.loads(args.annotations.read_text(encoding="utf-8")) if args.annotations else None
        if annotations is not None and not isinstance(annotations, list):
            raise ValueError("Annotations must be a JSON list, one object per selected page")
        outputs = [p for p in (args.output, args.midi, args.musicxml) if p]
        if len({p.resolve() for p in outputs}) != len(outputs):
            raise ValueError("JSON, MIDI and MusicXML outputs must be different files")
        initial_meter = meter
        for output in outputs:
            if output and output.resolve() == args.input.resolve():
                raise ValueError("Output must not overwrite the input score")
        if args.midi and args.midi.resolve() == args.output.resolve():
            raise ValueError("JSON and MIDI outputs must be different files")
        engine = Interpreter(args.model, args.threads)
        results = []
        key = args.key_fifths

        def process(image, page_number):
            nonlocal key, meter
            index = len(results)
            if annotations is not None and index >= len(annotations):
                raise ValueError("One annotation object is required for every selected page")
            print(f"Reading page {page_number}", file=sys.stderr)
            page = engine.interpret(image, meter=meter, key_fifths=key, width=args.width,
                                    annotations=annotations[index] if annotations is not None else None)
            page["sourcePage"] = page_number
            results.append(page)
            if page["score"]["keyChanges"]:
                key = page["score"]["keyChanges"][-1]["fifths"]
            if page["score"]["meterChanges"]:
                last = max(page["score"]["meterChanges"], key=lambda x: x["measureIndex"])
                meter = (last["numerator"], last["denominator"])

        if args.input.suffix.lower() == ".pdf":
            try:
                import pypdfium2 as pdfium
            except ImportError as error:
                raise ValueError("PDF input needs the pdf extra: pip install 'music-sheets-interpreter[pdf]'") from error
            pdf = pdfium.PdfDocument(args.input)
            try:
                pages = [int(n) - 1 for n in args.pages.split(",")] if args.pages else list(range(len(pdf)))
                if not pages or any(n < 0 or n >= len(pdf) for n in pages):
                    raise ValueError("PDF page number out of range")
                if pages != sorted(set(pages)):
                    raise ValueError("Choose unique PDF pages in increasing reading order")
                for number in pages:
                    page = pdf[number]
                    bitmap = page.render(scale=2400 / page.get_width())
                    try:
                        process(bitmap.to_pil(), number + 1)
                    finally:
                        bitmap.close()
                        page.close()
            finally:
                pdf.close()
        else:
            if args.pages:
                raise ValueError("--pages is for PDF input")
            with Image.open(args.input) as image:
                process(image, 1)
        if annotations is not None and len(annotations) != len(results):
            raise ValueError("Annotation count must match the number of selected pages")
        document = {"schemaVersion": 1, "inputName": args.input.name, "initialBpm": args.bpm, "initialMeter": initial_meter, "initialKeyFifths": args.key_fifths, "pages": results}
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(document, indent=2, allow_nan=False) + "\n", encoding="utf-8")
        if args.midi:
            args.midi.parent.mkdir(parents=True, exist_ok=True)
            write_midi(document, args.midi, args.bpm)
        if args.musicxml:
            args.musicxml.parent.mkdir(parents=True, exist_ok=True)
            write_musicxml(document, args.musicxml)
        print(f"Wrote {len(results)} page(s), {sum(len(p['events']) for p in results)} detected notes to {args.output}", file=sys.stderr)
    except (ValueError, RuntimeError, OSError, KeyError, TypeError) as error:
        parser.exit(1, str(error) + "\n")


if __name__ == "__main__":
    main()
