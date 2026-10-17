# Copyright 2026 Luckolite
# SPDX-License-Identifier: Apache-2.0
"""Reproduce original 16x24 numeral masks from the repository's SIL-OFL fonts.

Uses Java AWT at 24pt with threshold175. No score or model input is accepted.
Place GenerateClefNumerals.java next to this script when porting publicly.
"""
from pathlib import Path
import argparse,hashlib,os,re,subprocess,tempfile
from decimal import Decimal

def numeral_tuples(source):
    """Read constructor values regardless of indentation or formatter line wraps."""
    pattern=(r'\bnew\s+Numeral\s*\(\s*(\d+)\s*,\s*'
             r'(\d+(?:\.\d+)?(?:[Ee][+-]?\d+)?)[fF]\s*,\s*'
             r'"([A-Za-z0-9+/=]+)"\s*\)')
    return [(int(digit),Decimal(aspect),bits)
            for digit,aspect,bits in re.findall(pattern,source)]

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository',type=Path,required=True)
    parser.add_argument('--java-home',type=Path)
    parser.add_argument('--check',type=Path,help='Check the embedded rows in ClefOctaveNumerals.java')
    parser.add_argument('--output',type=Path)
    args=parser.parse_args();root=args.repository.resolve()
    fonts=[root/'java/assets/Bravura.otf',root/'training/fonts/NotoSerif.ttf']
    expected=['cdf0f893ee1fdb64b7f6713d71ee0dcfc349c0ac01429a8e451b01a9e79f5f3b','4d8e6761424656867019081a1a01336f3cb086982682698714054fc33f782713']
    for path,digest in zip(fonts,expected):
        if hashlib.sha256(path.read_bytes()).hexdigest()!=digest:raise ValueError('Font provenance mismatch: '+str(path))
    suffix='.exe'if os.name=='nt'else''
    exe=lambda name:str(args.java_home/'bin'/(name+suffix))if args.java_home else name
    source=Path(__file__).with_name('GenerateClefNumerals.java')
    with tempfile.TemporaryDirectory(prefix='clef-numerals-')as temp:
        folder=Path(temp);output=folder/'masks.txt'
        subprocess.run([exe('javac'),'--release','17','-d',str(folder),str(source)],check=True)
        subprocess.run([exe('java'),'-Djava.awt.headless=true','-cp',str(folder),'GenerateClefNumerals']+list(map(str,fonts))+[str(output)],check=True)
        text=output.read_text();rows=numeral_tuples(text)
        if len(rows)!=30:raise ValueError('Expected three original font faces and ten digits')
        if args.check and rows!=numeral_tuples(args.check.read_text()):raise ValueError('Embedded masks differ; review runtime/font rasterization before changing data')
        if args.output:args.output.write_text(text)
        print('PASS 30 original masks; SHA256 '+hashlib.sha256(text.encode()).hexdigest())

if __name__=='__main__':main()
