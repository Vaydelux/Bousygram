# Bousygram Vector Branding

- Authored SVG master: [bousygram-mark.svg](bousygram-mark.svg). The visual language is an original layered TV/play/signal motif with vector-only geometry.
- Android icon resource: app/src/main/res/drawable/ic_bousygram_mark.xml.
- Android 26+ adaptive icon: app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml.
- Legacy pre-26 PNG launcher mipmaps are retained unchanged in this implementation branch until the deferred asset export/packaging pass.
- Favicon.ico is for a web app, not the Android launcher. A multi-resolution ICO export from this SVG has **not** been committed as a binary to GitHub.
- Theme coloring: the SVG supports brand-light, brand-primary, and brand-deep CSS variables (custom properties) with defaults. Android's vector resource supports tinting in Compose.
- Visual, contrast, small-size, packaging, and hardware inspection are intentionally deferred until the final verification phase. Do not claim the branding is fully qualified.
