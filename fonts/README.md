# Optional Fonts Directory

Place additional `.ttf`, `.otf`, or font collection files here when you want deterministic local Apache FOP rendering outside Docker.

Examples:

- `NotoSansCJK-Regular.ttc`
- `NotoSerifCJK-Regular.ttc`
- `NotoNaskhArabic-Regular.ttf`
- `NotoSansHebrew-Regular.ttf`
- `DejaVuSans.ttf`

The service now resolves `./fonts` relative to the current working directory used to start the Java process. When you run the service from the repository root, Apache FOP can discover fonts from this folder in addition to system auto-detected fonts.