#!/usr/bin/env python3
# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Render octave-word templates from locally licensed fonts; no score images are used."""
import argparse
import base64
import re
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
WORDS = [('8va', 1), ('8vb', -1), ('15ma', 2), ('15mb', -2)]


def encoded(image, shift):
    ys, xs = np.where(np.asarray(image) < 165)
    image = image.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    aspect = image.width / image.height
    pixels = np.asarray(image.resize((48, 32), Image.Resampling.BILINEAR)) < 165
    bits = base64.b64encode(np.packbits(pixels.flatten())).decode('ascii')
    return f'        new Template({shift},{aspect:.6f}f,"{bits}"),'


def ink_crop(text, font):
    box = font.getbbox(text)
    image = Image.new('L', (box[2] - box[0] + 8, box[3] - box[1] + 8), 255)
    ImageDraw.Draw(image).text((4 - box[0], 4 - box[1]), text, font=font, fill=0)
    ys, xs = np.where(np.asarray(image) < 165)
    return image.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))


def render_music(music_font, suffix_fonts):
    """Original SMuFL renders and music numerals with independently typeset suffixes."""
    rows = []
    for code, shift in [(0xE511, 1), (0xE51C, -1), (0xE515, 2), (0xE51D, -2)]:
        for size in [32, 48, 64, 96]:
            font = ImageFont.truetype(str(music_font), size)
            rows.append(encoded(ink_crop(chr(code), font), shift))
    for size in [32, 40, 48, 64, 96]:
        font = ImageFont.truetype(str(music_font), size)
        for path in suffix_fonts:
            for ratio in [.4, .5, .6]:
                small = ImageFont.truetype(str(path), round(size * ratio))
                for text, shift in WORDS:
                    digits = 1 if text[0] == '8' else 2
                    numeral = ink_crop(chr(0xE510 if digits == 1 else 0xE514), font)
                    suffix = ink_crop(text[digits:], small)
                    offset = (round(numeral.height * .08) if shift > 0
                              else max(0, numeral.height - suffix.height))
                    image = Image.new('L', (numeral.width + 2 + suffix.width,
                                           max(numeral.height, offset + suffix.height)), 255)
                    image.paste(numeral, (0, 0))
                    image.paste(suffix, (numeral.width + 2, offset))
                    rows.append(encoded(image, shift))
    return rows


def render(fonts):
    rows = []
    for path in fonts:
        for size in [20, 32]:
            font = ImageFont.truetype(str(path), size)
            for text, shift in WORDS:
                for parentheses in [False, True]:
                    word = '(' + text + ')' if parentheses else text
                    box = font.getbbox(word)
                    image = Image.new('L', (box[2] - box[0] + 8, box[3] - box[1] + 8), 255)
                    ImageDraw.Draw(image).text((4 - box[0], 4 - box[1]), word, font=font, fill=0)
                    rows.append(encoded(image, shift))
    for path in fonts:
        for size in [24, 32, 48]:
            font = ImageFont.truetype(str(path), size)
            for ratio in [.7, .8, .9, 1.0]:
                small = ImageFont.truetype(str(path), round(size * ratio))
                for text, shift in WORDS:
                    for parentheses in [False, True]:
                        digits = '8' if text[0] == '8' else '15'
                        image = Image.new('L', (size * 6, size * 3), 255)
                        draw = ImageDraw.Draw(image)
                        x = size
                        if parentheses:
                            draw.text((x, size), '(', font=font, fill=0, anchor='lt')
                            x += draw.textlength('(', font=font)
                        draw.text((x, size), digits, font=font, fill=0, anchor='lt')
                        x += draw.textlength(digits, font=font)
                        suffix = text[len(digits):]
                        draw.text((x, size), suffix, font=small, fill=0, anchor='lt')
                        x += draw.textlength(suffix, font=small)
                        if parentheses:
                            draw.text((x, size), ')', font=font, fill=0, anchor='lt')
                        rows.append(encoded(image, shift))
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--font', action='append', type=Path,
                        help='Locally licensed font; repeat to select the template families')
    parser.add_argument('--music-font', type=Path, default=ROOT / 'java/assets/Bravura.otf',
                        help='OFL music font with SMuFL octave glyphs')
    parser.add_argument('--suffix-font', action='append', type=Path,
                        help='Locally licensed italic font for small suffixes; repeat as needed')
    parser.add_argument('--output', type=Path, default=ROOT / 'java/src/main/java/io/github/luckolite/interpreter/OctaveWordShapes.java')
    args = parser.parse_args()
    fonts = args.font or [Path('C:/Windows/Fonts') / name for name in
                          ['timesi.ttf', 'timesbi.ttf', 'ariali.ttf', 'georgiai.ttf']]
    if any(not font.is_file() for font in fonts):
        parser.error('Supply available font files with --font')
    suffix_fonts = args.suffix_font or [Path('C:/Windows/Fonts') / name for name in
                                      ['timesi.ttf', 'cambriai.ttf', 'georgiai.ttf']]
    if not args.music_font.is_file() or any(not font.is_file() for font in suffix_fonts):
        parser.error('Supply available music and suffix fonts')
    rows = render(fonts) + render_music(args.music_font, suffix_fonts)
    source = args.output.read_text(encoding='utf-8')
    tables = list(re.finditer(r'    private static final Template\[\] TEMPLATES\s*=\s*\{\n', source))
    if len(tables) != 1:
        parser.error('Expected one template table in the output Java source')
    start = tables[0].end()
    end = source.index('    };', start)
    args.output.write_text(source[:start] + '\n'.join(rows) + '\n' + source[end:], encoding='utf-8')
    print(f'Rendered {len(rows)} templates; verify accuracy before publishing changed templates.')


if __name__ == '__main__':
    main()
