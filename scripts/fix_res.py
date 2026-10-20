# -*- coding: utf-8 -*-
"""Repack a patched APK: force every res/* entry byte-identical to the original
(apktool -r dedup + Windows case collisions corrupt some), then inject our
compiled classes as classes5.dex (ART loads all classesN.dex natively).

Usage: python fix_res.py <original.apk> <unsigned.apk> <classes.dex> <out.apk>
"""
import zipfile, hashlib, os, sys

def main():
    orig_p, new_p, dex_p, out_p = sys.argv[1:5]
    orig = zipfile.ZipFile(orig_p)
    new = zipfile.ZipFile(new_p)
    missing = [x for x in orig.namelist() if x not in set(new.namelist())]
    print('missing entries restored:', len(missing))

    zout = zipfile.ZipFile(out_p, 'w')
    onames = set(orig.namelist())
    for item in new.infolist():
        data = new.read(item)
        src = item
        if item.filename.startswith('res/') and item.filename in onames:
            src = orig.getinfo(item.filename)
            data = orig.read(src)
        zi = zipfile.ZipInfo(item.filename, date_time=src.date_time)
        zi.compress_type = src.compress_type
        zi.external_attr = src.external_attr
        zout.writestr(zi, data)
    for x in missing:
        item = orig.getinfo(x)
        zi = zipfile.ZipInfo(x, date_time=item.date_time)
        zi.compress_type = item.compress_type
        zi.external_attr = item.external_attr
        zout.writestr(zi, orig.read(x))

    if os.path.exists(dex_p):
        zi = zipfile.ZipInfo('classes5.dex', date_time=(2024, 1, 1, 0, 0, 0))
        zi.compress_type = zipfile.ZIP_DEFLATED
        zi.external_attr = 0o644 << 16
        zout.writestr(zi, open(dex_p, 'rb').read())
        print('injected classes5.dex')
    zout.close()
    print('written:', out_p)

if __name__ == '__main__':
    main()
