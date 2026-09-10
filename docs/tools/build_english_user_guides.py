"""Render English Markdown guides with unchanged device screenshots.

Requirements: reportlab, a Korean TrueType font (only for original UI labels).
Each <!-- pagebreak --> section has a title, subtitle and ### text blocks.
"""
import argparse
import re
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.utils import ImageReader
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Paragraph

ROOT = Path(__file__).resolve().parents[2]
W, H = A4
INK = colors.HexColor("#202326")
GRAY = colors.HexColor("#626970")
YELLOW = colors.HexColor("#F8DA43")


def render(platform):
    source = ROOT / f"docs/{platform.upper()}-USER-GUIDE.en.md"
    output = ROOT / f"output/pdf/DEPL-{platform}-QR-History-User-Guide-EN.pdf"
    pages = source.read_text().split("<!-- pagebreak -->")
    c = canvas.Canvas(str(output), pagesize=A4)
    c.setTitle(f"DEPL {platform} - QR Registration and Notification History User Guide")
    c.setAuthor("DEPL")

    def text(value, x, top, width, size=10, color=INK, bold=False):
        markup = re.sub(r"([\uac00-\ud7a3]+)", r'<font name="Korean">\1</font>', escape(value))
        p = Paragraph(markup, ParagraphStyle("body", fontName="Helvetica-Bold" if bold else "Helvetica",
            fontSize=size, leading=size*1.4, textColor=color, splitLongWords=False))
        _, height = p.wrap(width, H)
        if top-height < 51:
            raise ValueError(f"{platform}: page text crosses footer: {value[:50]}")
        c.setFillColor(color)
        p.drawOn(c, x, top-height)
        return top-height

    for number, page in enumerate(pages,1):
        title = re.search(r"^## (.+)$",page,re.M).group(1)
        after_title = page.split("## "+title,1)[1].strip()
        subtitle = after_title.split("\n\n",1)[0]
        picture = re.search(r"!\[(.*?)\]\((.*?)\)",page)
        chunks = re.split(r"^### ",page,flags=re.M)[1:]
        blocks = [(s.split("\n",1)[0],s.split("\n",1)[1].strip()) for s in chunks]
        c.setFillColor(YELLOW)
        c.rect(0,H-12,W,12,fill=1,stroke=0)
        text("DEPL",38,H-28,70,15,bold=True)
        for x,y,r,color in [(90,H-43,2,"#087DB9"),(97,H-39,3,"#087DB9"),(106,H-34,4,"#EF343B")]:
            c.setFillColor(colors.HexColor(color)); c.circle(x,y,r,fill=1,stroke=0)
        text(f"{platform} user guide | English",335,H-31,220,9,GRAY)
        text(title,38,H-73,W-76,23,bold=True)
        text(subtitle,38,H-111,W-76,10,GRAY)
        c.setStrokeColor(colors.HexColor("#DDE0E3")); c.line(38,43,W-38,43)
        c.setFillColor(GRAY); c.setFont("Helvetica",8)
        c.drawString(38,25,"Actual device screenshots | 10 September 2026")
        c.drawRightString(W-38,25,f"{number} / {len(pages)}")
        if picture:
            image=ImageReader(str(source.parent/picture.group(2)))
            iw,ih=image.getSize(); height=226*ih/iw
            c.drawImage(image,38,H-154-height,226,height)
            text(picture.group(1),38,H-164-height,226,8,GRAY)
            top=H-155
            for i,(heading,body) in enumerate(blocks):
                if i<3:
                    c.setFillColor(YELLOW); c.circle(286,top-7,10,fill=1,stroke=0)
                    text(str(i+1),283,top,13,9,bold=True)
                    heading=re.sub(r"^\d+\. ","",heading)
                    top=text(heading,304,top+1,250,12,bold=True)
                else:
                    c.setFillColor(YELLOW); c.rect(277,top,277,3,fill=1,stroke=0)
                    top=text(heading,277,top-10,277,11,bold=True)
                top=text(body,277,top-9,277,10)-22
        else:
            top=H-152
            for heading,body in blocks:
                top=text(heading,38,top,W-76,11,bold=True)
                top=text(body,38,top-6,W-76,9.7)-15
        c.showPage()
    c.save()
    print(output)


if __name__ == "__main__":
    parser=argparse.ArgumentParser()
    parser.add_argument("--font",default="/System/Library/Fonts/Supplemental/AppleGothic.ttf")
    args=parser.parse_args()
    pdfmetrics.registerFont(TTFont("Korean",args.font))
    for platform in ("iPhone","Android"):
        render(platform)
