# Undefined source values

- `fontFamily=Geist`: no Geist font file is present in the repository; the implementation uses the Compose sans fallback.
- `fontFamily=Rubik`: resolved with bundled Google Fonts resources `res/font/rubik_regular.ttf` and `res/font/rubik_medium.ttf`.
- `fontFamily=JetBrains Mono`: resolved with bundled Google Fonts resource `res/font/jetbrains_mono_regular.ttf`.
- `fontFamily=Instrument Serif`: resolved with bundled Google Fonts resource `res/font/instrument_serif_regular.ttf`.
- `lineHeight=normal`: the export does not provide a numeric line height for these text nodes, so no numeric conversion is possible.
- Rendered region diff: `reference/home.png` exists, but this environment has no Android emulator, adb, Paparazzi, or Roborazzi renderer. A generated `docs/diff_regions/` comparison cannot be truthfully produced here.
