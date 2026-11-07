# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Optional native PDF tablature text; geometry and symbol semantics remain in Java."""
import re

_TOKEN = re.compile(r"-(?:[12]|(?i:full))|(?i:full|whammy|w/bar|bar|upstroke|downstroke|strum up|strum down)|-?(?:1 1/2|1/4|1/2|3/4|1\u00bd|[\u00bc\u00bd\u00be])|[\uE846\uE847]|<[0-9]{1,2}>|\([0-9]{1,2}\)|(?<![0-9])[0-9]{1,2}[bB][0-9]{1,2}(?:[rR][0-9]{1,2})?~*|(?<![0-9])(?:[0-9]{1,2}|[xX])(?:[/\\hHpP][0-9]{1,2})+~*|(?<![0-9])[0-9]{1,3}(?![0-9])~*|[xXHPT=]|[\uE1E7\uE241\uE243\uE245\uE4E3-\uE4E8\uE4A2\uEAB2\uE080-\uE089\uECA5\uECA6\uECB7]")

def tuning_header(page):
    textpage = page.get_textpage()
    try:
        match = re.search(r"(?im)^\s*tuning\s*[:=]?[^\r\n]+", textpage.get_text_range())
        return match.group().strip() if match else ""
    finally:
        textpage.close()

def tuning_words(header):
    """Document tuning is metadata, independent of transformed page coordinates."""
    return [dict(text=header, left=.01, top=.001, right=.9, bottom=.009)] if header else []


def tab_words(page, header=""):
    textpage = page.get_textpage()
    words = []
    try:
        width, height = page.get_width(), page.get_height()
        text = textpage.get_text_range()
        tokens = set(_TOKEN.findall(text)) | {c for c in text if c in "0123456789"}
        for token in sorted(tokens, key=len, reverse=True):
            search = textpage.search(token)
            try:
                while (match := search.get_next()) is not None:
                    index, count = match
                    boxes = [textpage.get_charbox(i) for i in range(index, index + count)]
                    # PDF text search can concatenate frets on different strings into "55".
                    # Such a tall box must not suppress the two valid single-character boxes.
                    if len(token) > 1 and token.isdigit():
                        visible = [b for b in boxes if b[2] > b[0] and b[3] > b[1]]
                        centers = [(b[1]+b[3])/2 for b in visible]
                        if centers and max(centers)-min(centers) > max(b[3]-b[1] for b in visible):
                            continue
                    left = min(b[0] for b in boxes) / width
                    right = max(b[2] for b in boxes) / width
                    top = 1 - max(b[3] for b in boxes) / height
                    bottom = 1 - min(b[1] for b in boxes) / height
                    if 0 <= left < right <= 1 and 0 <= top < bottom <= 1:
                        words.append(dict(text=token, left=left, top=top, right=right, bottom=bottom))
            finally:
                search.close()
    finally:
        textpage.close()
    words.extend(tuning_words(header))
    return words
