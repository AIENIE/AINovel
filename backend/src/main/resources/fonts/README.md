# Embedded PDF font

`AINovelSansSC-Regular.ttf` is a static weight-400 instance of Noto Sans SC 2.004,
renamed for this application. It is distributed under SIL OFL 1.1; the full
copyright and license are in `OFL-NotoSansSC.txt`. The reserved name `Source` is
not used in the derived font's family or PostScript name.

- Upstream: https://github.com/google/fonts/tree/23e54b51ddffbc7713c583748e3bd86f62b1fa4a/ofl/notosanssc
- Source file: `NotoSansSC[wght].ttf`
- Source SHA-256: `a3041811a78c361b1de50f953c805e0244951c21c5bd412f7232ef0d899af0da`
- Bundled SHA-256: `e409839f5050f12679e3679632ec6c3239e75d40689d1d3c0cbbd2651190d6d8`
- Bundled size: 10,595,960 bytes
- Conversion tool: fonttools 4.60.0, Python 3.12, `recalcTimestamp=False`.

Reproduction: load the pinned source with `TTFont`, call
`instantiateVariableFont(font, {'wght': 400}, inplace=True, optimize=True)`, and
replace name IDs 1/16 with `AINovel Sans SC`, IDs 2/17 with `Regular`, ID 3 with
`AINovelSansSC-Regular-2.004`, ID 4 with `AINovel Sans SC Regular`, and ID 6 with
`AINovelSansSC-Regular`, preserving every name record's platform/encoding/language.
Save without recalculating timestamps. No character subset is removed from this
bundled font. PDFBox embeds only the glyphs actually used in each PDF.

Runtime needs no host-installed font and makes no font network requests. The
bundled hash is checked before PDF generation. Chinese and supported supplementary
characters are emitted as Unicode text. Characters absent from this font cause a
`PDF_UNSUPPORTED_CHARACTER_U+...` error; they are never replaced with `?` or tofu.
The author may choose TXT, DOCX or EPUB for unsupported glyphs.
