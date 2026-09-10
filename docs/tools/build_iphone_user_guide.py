"""Build the iPhone guide from real device screenshots; requires reportlab and a Korean TTF."""
import argparse
from pathlib import Path
from xml.sax.saxutils import escape

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas
from reportlab.platypus import Paragraph

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "docs/assets/iphone-guide"
OUTPUT = ROOT / "output/pdf/DEPL-iPhone-QR-History-User-Guide.pdf"
W, H = A4
INK = colors.HexColor("#202326")
GRAY = colors.HexColor("#626970")
YELLOW = colors.HexColor("#F8DA43")
BLUE = colors.HexColor("#087DB9")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--font", default="/System/Library/Fonts/Supplemental/AppleGothic.ttf")
    args = parser.parse_args()
    pdfmetrics.registerFont(TTFont("Korean", args.font))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(OUTPUT), pagesize=A4)
    c.setTitle("DEPL iPhone - QR 등록 및 알림 내역 사용자 가이드")
    c.setAuthor("DEPL")

    def text(value, x, top, width, size=10.5, color=INK, leading=None):
        style = ParagraphStyle("body", fontName="Korean", fontSize=size,
                               leading=leading or size * 1.55, textColor=color,
                               wordWrap="CJK", splitLongWords=True)
        p = Paragraph(escape(value).replace("\n", "<br/>"), style)
        _, height = p.wrap(width, H)
        if top - height < 48:
            raise ValueError(f"Text would cross footer: {value[:40]}")
        p.drawOn(c, x, top - height)
        return top - height

    def header(number, title, subtitle):
        c.setFillColor(YELLOW)
        c.rect(0, H - 12, W, 12, fill=1, stroke=0)
        c.setFont("Helvetica-Bold", 15)
        c.setFillColor(INK)
        c.drawString(38, H - 45, "DEPL")
        for x, y, radius, color in [(89, H-43, 2, BLUE), (96, H-39, 3, BLUE),
                                     (105, H-34, 4, colors.HexColor("#EF343B"))]:
            c.setFillColor(color)
            c.circle(x, y, radius, fill=1, stroke=0)
        text("iPhone 사용자 가이드", 342, H-31, 215, 9, GRAY)
        text(title, 38, H-72, W-76, 24)
        text(subtitle, 38, H-113, W-76, 10, GRAY)
        c.setStrokeColor(colors.HexColor("#DDE0E3"))
        c.line(38, 43, W-38, 43)
        c.setFont("Korean", 8)
        c.setFillColor(GRAY)
        c.drawString(38, 25, "실제 iPhone 15 Pro 캡처 · 2026-09-10")
        c.setFont("Helvetica", 8)
        c.setFillColor(GRAY)
        c.drawRightString(W-38, 25, f"{number} / 4")

    def screenshot(name, caption):
        x, top, width = 38, H-154, 235
        height = width * 2556 / 1179
        c.drawImage(str(ASSETS/name), x, top-height, width, height)
        text(caption, x, top-height-10, width, 8, GRAY)

    def section(number, title, body, top):
        c.setFillColor(YELLOW)
        c.circle(302, top-8, 11, fill=1, stroke=0)
        text(str(number), 299, top-1, 14, 10, INK)
        bottom = text(title, 322, top+1, 232, 13)
        bottom = text(body, 291, bottom-13, 263, 10.5)
        return bottom-27

    def notice(title, body, top, color=YELLOW):
        c.setFillColor(color)
        c.roundRect(291, top-5, 263, 4, 2, fill=1, stroke=0)
        bottom = text(title, 291, top-16, 263, 12)
        return text(body, 291, bottom-9, 263, 10, GRAY)

    header(1, "처음 시작하기", "QR로 본인 알림을 연결하고, 앱 안에서 저장된 알림을 확인합니다.")
    screenshot("01-main-history.png", "실제 메인 화면 · 권한 허용 / 알림 내역 1개\n상단 상태는 ‘확인 중’이며 완료 화면이 아닙니다.")
    top = H-156
    top = section(1, "인터넷 연결과 알림 권한", "회사에서 배포한 DEPL을 열고 알림 권한을 허용하세요. 앱은 Wi-Fi 또는 이동통신을 사용하며 업무망 Gateway에 직접 접속하지 않습니다.", top)
    top = section(2, "QR로 기기 등록", "검은색 ‘QR로 기기 등록’ 버튼을 누르세요. 업무 시스템에서 발급받은 본인용 QR로 개인·부서·전체 공지 알림을 연결합니다.", top)
    top = section(3, "알림 내역 확인", "메인 화면 아래에서 문구와 기록 시각을 확인하세요. 오른쪽 숫자는 앱에 저장된 목록 개수입니다. 스크롤하면 이전 내역을 볼 수 있습니다.", top)
    notice("권한 허용과 등록 완료는 다릅니다", "‘알림 권한 · 허용’만으로 세 종류의 알림 연결이 완료된 것은 아닙니다. 다음 페이지의 등록 상태까지 확인하세요.", top)
    c.showPage()

    header(2, "QR 촬영으로 연결하기", "업무 시스템의 QR은 PC에 표시하고, iPhone의 DEPL 카메라로 촬영합니다.")
    screenshot("02-qr-scanner.png", "실제 QR 촬영 화면\n보안상 실제 QR은 담지 않았습니다. 달력은 촬영 배경입니다.")
    top = H-156
    top = section(1, "본인용 최신 QR 준비", "업무 시스템에서 본인 사용자와 실제 소속 부서의 QR을 발급받으세요. 다른 사람의 QR을 사용하거나 QR 이미지를 공유하지 마세요.", top)
    top = section(2, "카메라로 QR 전체 촬영", "카메라 권한을 허용하고 QR 전체를 흰색 테두리 안에 맞추세요. 화면 반사를 피하고 거리를 조절한 뒤 검증과 연결을 기다립니다.", top)
    top = section(3, "만료되면 다시 발급", "QR은 현재 기본 3분 동안 유효합니다. 회사 설정에 따라 달라질 수 있으며 최대 10분입니다. 만료 오류가 나오면 새 QR로 다시 촬영하세요.", top)
    notice("DEBUG 입력칸은 사용하지 않습니다", "캡처는 개발용 설치본입니다. ‘QR 문자열 테스트’는 개발자용이므로 일반 사용자는 카메라 촬영만 이용하세요.", top)
    c.showPage()

    header(3, "등록 상태 확인하기", "개인·부서·전체 공지 세 항목의 연결 상태를 함께 확인하세요.")
    screenshot("03-registration-status.png", "실제 기존 등록 상태 · 세 항목 모두 ‘등록됨’\n이번 캡처를 위해 새 등록이나 초기화를 실행하지 않았습니다.")
    top = H-156
    top = section(1, "세 항목을 모두 확인", "처음 등록할 때는 ‘기기 등록이 완료되었습니다.’와 세 항목의 성공 표시를 확인하세요. 기존 등록은 캡처처럼 ‘등록됨’으로 보일 수 있습니다.", top)
    top = section(2, "닫고 수신 확인", "왼쪽 위 X로 메인 화면으로 돌아갑니다. 관리자에게 본인 대상 테스트 알림을 요청하고, 화면 표시와 앱 내부 내역을 각각 확인하세요.", top)
    top = section(3, "부서 또는 기기 변경", "관리자에게 새 QR을 요청해 재등록하세요. 기존 정보가 맞는지 확인만 할 때는 ‘기기 등록 초기화’를 누르지 마세요.", top)
    notice("초기화는 알림 연결을 해제합니다", "초기화는 단순 화면 새로고침이 아닙니다. 기존 구독을 해제하므로 이후 알림을 받으려면 새 QR 등록이 필요합니다.", top, colors.HexColor("#E76258"))
    c.showPage()

    header(4, "알림 내역과 문제 해결", "알림이 잠금 화면에 표시되는 것과 앱 내부에 저장되는 것은 별개입니다.")
    y = H-157
    y = text("내역은 최대 3,000건 · 서버 동기화는 제공하지 않음", 38, y, W-76, 15)
    y = text("현재 소스는 최신 3,000건을 보관하고 초과분은 오래된 항목부터 제외합니다. 같은 이벤트는 중복 저장하지 않습니다. 아래로 당기는 새로고침은 기기에 저장된 내역만 읽습니다. 업무 상세는 기존 업무 시스템에서 확인하세요.", 38, y-12, W-76, 11)-22
    y = text("iPhone에서 알아둘 제한", 38, y, W-76, 15)
    y = text("앱이 열린 동안 수신하거나 알림을 눌러 처리될 때 내역이 저장됩니다. 백그라운드·종료 상태에서 표시된 모든 Push가 앱 내역에 저장되는 것은 보장하지 않습니다. 목록에 없다고 전송 실패로 단정하지 마세요. 앱 삭제 전에는 로컬 내역의 복구 가능 여부를 관리자에게 문의하세요.", 38, y-12, W-76, 11)-23
    rows = [
        ("카메라가 보이지 않음", "iPhone 설정에서 DEPL의 카메라 권한을 확인합니다."),
        ("QR 만료 / 서명 오류", "새 QR로 재시도합니다. 계속되면 회사 앱·QR 조합을 관리자에게 확인합니다."),
        ("확인 중 / 구독 실패", "인터넷 연결과 앱 버전을 확인합니다. 상태 문구와 발생 시각을 관리자에게 전달합니다."),
        ("알림 또는 소리가 안 남", "알림 허용, 잠금 화면, 집중 모드·무음 설정과 세 항목 등록 상태를 확인합니다."),
        ("앱 내부 내역이 누락됨", "현재 iOS 저장 제한일 수 있습니다. 앱을 열거나 해당 시스템 알림을 눌러 확인합니다."),
    ]
    for title, body in rows:
        c.setFillColor(colors.HexColor("#F3F5F6"))
        c.roundRect(38, y-57, W-76, 57, 7, fill=1, stroke=0)
        text(title, 49, y-9, 145, 10.5)
        text(body, 207, y-9, 336, 10.5)
        y -= 66
    text("문의 시: 앱 버전·발생 시각·오류 문구·등록 상태만 전달하세요. QR 원문, 토큰, 개인키는 보내지 않습니다. 화면은 설치본 기준, 저장 동작은 현재 소스 기준입니다. Android 실기기 캡처 가이드는 기기 연결 후 별도로 제작합니다.", 38, y-4, W-76, 9, GRAY)
    c.save()
    print(OUTPUT)


if __name__ == "__main__":
    main()
