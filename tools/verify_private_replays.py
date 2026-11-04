#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""PRIVATE regression replay against frozen accepted B3 output. Never upload its outputs."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]

def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--artifacts',type=Path,required=True); parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args(); args.output.mkdir(parents=True,exist_ok=True)
    references={
        'first':'pixel-first-test/choptorch-diagnostic.json',
        'strong':'pixel-0.2-test/choptorch-diagnostic-v2.json',
        'comfort':'pixel-comfort-test/choptorch-comfort-v2.json',
        'normal':'pixel-speed-test/choptorch-normal.json',
        'rapid':'pixel-speed-test/choptorch-rapid.json',
        'single-miss':'pixel-single-miss-test/choptorch-non-declenché.json',
    }
    report=[]
    for name,relative in references.items():
        source=args.artifacts/relative
        before=hashlib.sha256(source.read_bytes()).hexdigest()
        modes=['detector-only'] if name=='first' else ['recorded-freshness','recomputed-freshness']
        for mode in modes:
            result=subprocess.check_output([sys.executable,str(ROOT/'tools/replay_capture.py'),str(source),'--mode',mode,'--detail'])
            expected=(args.artifacts/'lot-b3/single-miss-investigation'/f'candidate-{name}-{mode}.txt').read_bytes()
            # Text-mode historical redirection may differ only in CRLF.
            assert result.replace(b'\r\n',b'\n')==expected.replace(b'\r\n',b'\n'), name+' '+mode+' changed'
            (args.output/f'{name}-{mode}.txt').write_bytes(result)
            report.append({'reference':name,'mode':mode,'identical':True,'input_sha256':before})
        assert hashlib.sha256(source.read_bytes()).hexdigest()==before
    (args.output/'verification-private.json').write_text(json.dumps(report,indent=2))
    print('PASS '+str(len(report))+' private replay outputs identical to frozen accepted B3, originals unchanged. Not a physical accuracy measurement.')

if __name__=='__main__': main()
