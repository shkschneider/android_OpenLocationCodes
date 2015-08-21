OpenLocationCodes [![JitPack](https://img.shields.io/github/tag/shkschneider/android_OpenLocationCodes.svg?label=JitPack)](https://jitpack.io/#shkschneider/android_OpenLocationCodes/1.2.2) [![Android Arsenal](https://img.shields.io/badge/Android%20Arsenal-OpenLocationCodes-brightgreen.svg?style=flat)](http://android-arsenal.com/details/3/1607)
=================

[![Google Play](https://developer.android.com/images/brand/en_generic_rgb_wo_45.png)](https://play.google.com/store/apps/details?id=me.shkschneider.openlocationcodes.demo)

Open Location Codes are short, generated codes that can be used like street addresses, for places where street addresses don't exist.

http://openlocationcode.com

Open Location Codes were developed at Google's Zurich engineering office, and then open sourced so that they can be freely used.

This application is just a demo of my personal Java implementation of its algorithm, licensed under Apache 2.0 license.

https://github.com/shkschneider/android_OpenLocationCodes

**Usage**

    // Encode
    final String openLocationCode = OpenLocationCodes.encode(latitude, longitude);

    // Decode
    final OpenLocationCodes.CodeArea codeArea = OpenLocationCodes.decode(openLocationCode);
    codeArea.northwest();
    codeArea.southwest();
    codeArea.southeast();
    codeArea.northeast();

**Versions**

- Version 1.2+ uses the (new) *xxxxxx+xx* format as of [224cc1e](https://github.com/google/open-location-code/commit/224cc1ef2d60214669896279c4fcafc6eecc739a).
- Version 1.1+ used the (old) *+xxxx.xxxxxx* format as of [7701fcb](https://github.com/google/open-location-code/commit/7701fcbf65c5b6143495de05eeffc5e417751e0c).
