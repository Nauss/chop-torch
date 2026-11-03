#!/usr/bin/env python
"""SYNTHETIC detector-only speed sweep; compile current checkout first.
No capture input, receipt/admission simulation, or physical success-rate claims.
--require-pair PERIOD_MS asserts one pair at amplitude6/sensitivity100/8ms.
"""
import argparse
import hashlib
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--require-pair', type=int, choices=range(80, 441), metavar='80..440')
    args = parser.parse_args()
    classes = ROOT / 'app/build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
    detector = classes / 'fr/nauss/choptorch/ChopDetector.class'
    if not detector.is_file():
        raise RuntimeError('Compile current production Kotlin detector first')
    cache = Path(os.environ.get('GRADLE_USER_HOME', str(Path.home() / '.gradle')))
    jars = list((cache / 'caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/2.2.10').glob('*/*.jar'))
    if len(jars) != 1:
        raise RuntimeError('Expected pinned Kotlin stdlib 2.2.10')
    print('Compiled detector SHA256=' + hashlib.sha256(detector.read_bytes()).hexdigest(), flush=True)
    for compiled in sorted(detector.parent.glob('ChopDetector$*.class')):
        print('Compiled detector class ' + compiled.name + ' SHA256=' + hashlib.sha256(compiled.read_bytes()).hexdigest(), flush=True)
    command = ['java', '-cp', os.pathsep.join([str(classes), str(jars[0])]), str(ROOT / 'tools/SpeedSweep.java')]
    if args.require_pair is not None:
        command += ['--require-pair', str(args.require_pair)]
    raise SystemExit(subprocess.run(command, cwd=ROOT).returncode)


if __name__ == '__main__':
    main()
