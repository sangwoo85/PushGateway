# Screenshot-based user guide builders

The Markdown files and real device screenshots are maintained under `docs/` and `docs/assets/`. PDF outputs are under `output/pdf/`. These scripts do not connect to devices or send notifications.

Requirements: Python 3, `reportlab`, and a Korean TrueType font. Each script accepts `--font /path/to/font.ttf`; the default is macOS AppleGothic. English text uses Helvetica; the Korean font renders original UI labels. Do not commit system font files.

Run from the repository root:

```sh
python3 docs/tools/build_iphone_user_guide.py
python3 docs/tools/build_android_user_guide.py
python3 docs/tools/build_english_user_guides.py
```

The English builder reads `docs/IPHONE-USER-GUIDE.en.md` and `docs/ANDROID-USER-GUIDE.en.md`. Each `<!-- pagebreak -->` separates one PDF page. A page contains an `##` title, subtitle, optional screenshot with caption, and `###` sections. Keep this structure when editing the English source. The Korean builders have their layout copy in the Python scripts; update that copy and the corresponding Korean Markdown together.

Render every page after rebuilding and inspect clipping, readability, original screenshot proportions and the correspondence between Korean UI labels and English instructions. Do not describe an observed pending state as a successful registration. No valid QR, credentials, user identifiers or device serial numbers should appear in a published guide.
