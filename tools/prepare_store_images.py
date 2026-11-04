#!/usr/bin/env python
# SPDX-License-Identifier: GPL-3.0-or-later
"""Copy inspected native AVD screenshots; draw only the project's vector icon.
Screenshots are never reconstructed, composited or retouched.
"""
import hashlib
import json
from pathlib import Path
import shutil
from PIL import Image, ImageDraw
import verify_native_ui as ui

PROJECT=ui.ROOT.parents[1] if ui.ROOT.parent.name=='.worktrees' else ui.ROOT
EVIDENCE=PROJECT/'artifacts/finish/readiness/ui-final'

def main():
    assert 'ChopTorch_P1_API37' in ui.adb('emu','avd','name')
    ui.OUT=EVIDENCE
    ui.shell('cmd','locale','set-app-locales',ui.PKG,'--locales','en-US')
    ui.shell('cmd','uimode','night','no')
    ui.shell('am','force-stop',ui.PKG)
    ui.launch(); ui.assert_text('Enable to use the double chop.')
    ui.capture('store-en-light')
    # Exact vector geometry from ic_torch.xml, supersampled. Only icon is generated.
    scale=32; image=Image.new('RGBA',(48*scale,48*scale),(0,0,0,0)); draw=ImageDraw.Draw(image)
    draw.rounded_rectangle((0,0,48*scale-1,48*scale-1),radius=12*scale,fill='#242724')
    draw.polygon([(x*scale,y*scale) for x,y in [(14,9),(34,9),(34,17),(28,25),(28,39),(20,39),(20,25),(14,17)]],fill='#E5CA7B')
    draw.rectangle((17*scale,12*scale,31*scale-1,16*scale-1),fill='#242724')
    draw.rectangle((23*scale,28*scale,25*scale-1,34*scale-1),fill='#242724')
    image=image.resize((512,512),Image.Resampling.LANCZOS)
    manifest={}
    for locale,names in {'en-US':['store-en-light','04-dark-en-off'],'fr-FR':['09-light-fr-off','10-dark-fr-off']}.items():
        images=ui.ROOT/'fastlane/metadata/android'/locale/'images'
        (images/'phoneScreenshots').mkdir(parents=True,exist_ok=True)
        image.save(images/'icon.png')
        for index,name in enumerate(names,1):
            source=EVIDENCE/(name+'.png'); target=images/'phoneScreenshots'/f'{index}.png'
            shutil.copyfile(source,target)
            digest=hashlib.sha256(target.read_bytes()).hexdigest()
            assert digest==hashlib.sha256(source.read_bytes()).hexdigest()
            manifest[str(target.relative_to(ui.ROOT))]={'native_source':name+'.png','sha256':digest}
    (PROJECT/'artifacts/finish/readiness/store-image-provenance.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
    print('Prepared exact native screenshots and project icon, no personal data or synthetic screenshots.')

if __name__=='__main__': main()
