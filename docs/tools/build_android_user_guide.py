"""Android guide using original USB-device screenshots. Requires reportlab and Korean TTF."""
import argparse
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
ASSETS = ROOT / "docs/assets/android-guide"
OUTPUT = ROOT / "output/pdf/DEPL-Android-QR-History-User-Guide.pdf"
W, H = A4
INK, GRAY = colors.HexColor("#202326"), colors.HexColor("#626970")
YELLOW, BLUE = colors.HexColor("#F8DA43"), colors.HexColor("#087DB9")

PAGES = [
    ("처음 시작하기", "메인 화면에서 QR 등록과 기기에 저장된 알림을 확인합니다.",
     "01-main-history.png", "실제 Android 메인 화면 · 저장된 알림 3개\n알림 설정을 접은 상태입니다.", [
        ("인터넷 연결 확인", "회사에서 배포한 DEPL을 열고 Wi-Fi 또는 이동통신 연결을 확인하세요. 앱은 Firebase를 사용하며 업무망 Gateway에 직접 접속하지 않습니다."),
        ("QR로 기기 등록", "검은색 ‘QR로 기기 등록’ 버튼을 눌러 본인·부서·전체 공지 알림을 연결합니다. ‘기기 등록 초기화’는 별도 기능이므로 누르지 마세요."),
        ("알림 내역 확인", "문구와 기록 시각을 최신순으로 확인합니다. 오른쪽 숫자는 저장 개수이며 스크롤하면 이전 내역을 볼 수 있습니다. 실제 업무 처리는 기존 업무 시스템에서 진행하세요.")
     ], "휴지통은 새로고침이 아닙니다", "알림 내역 옆 휴지통은 전체 삭제 기능입니다. 확인 창에서 승인하면 기기에 저장된 내역이 삭제됩니다. 서버에서 복원하는 기능은 없습니다."),
    ("알림 설정 확인하기", "‘알림 설정’을 누르면 펼쳐지고, 다시 누르면 접힙니다.",
     "02-notification-settings.png", "실제 설정 화면 · 알림 권한 허용 / 배터리 최적화 사용 중\n촬영 과정에서 설정값을 변경하지 않았습니다.", [
        ("알림 권한", "시스템 앱 알림 설정에서 허용 여부를 확인하세요. Android 13 이상은 최초 권한 요청이 나올 수 있습니다. 캡처 기기는 Android 10입니다."),
        ("배터리 최적화", "‘사용 중’이면 ‘배터리 최적화 제외’를 눌러 시스템 안내를 확인하고 사용자가 허용 여부를 선택합니다. 이미 ‘제외됨’이면 다시 설정할 필요가 없습니다."),
        ("잠금 화면 · 팝업 설정", "이 버튼은 시스템 알림 설정을 엽니다. 잠금 화면 내용 표시, 소리·진동, 알림 채널을 확인하세요. 무음·방해 금지·절전 설정도 영향을 줄 수 있습니다.")
     ], "앱 표시와 시스템 설정을 함께 확인", "‘알림 권한 · 허용’만으로 모든 채널·소리·잠금 화면 설정이 정상임을 보장하지 않습니다. 제조사와 OS에 따라 메뉴 이름·표시 방식이 다릅니다."),
    ("QR로 연결하고 확인하기", "업무 시스템의 본인용 QR을 Android의 DEPL 카메라로 촬영합니다.",
     "03-qr-scanner.png", "실제 Android QR 촬영 화면\n유효한 QR이나 토큰은 문서에 포함하지 않습니다.", [
        ("최신 QR 준비", "업무 시스템에서 본인 사용자와 실제 소속 부서의 QR을 발급받으세요. 기본 유효시간은 3분, 최대 10분이며 회사 설정에 따라 달라집니다."),
        ("카메라로 촬영", "카메라 권한이 필요하면 허용하고 QR 전체가 보이도록 맞춥니다. 검증·구독을 기다리세요. 만료되면 새 QR을 발급받아 재시도합니다."),
        ("등록 완료 확인", "‘기기 등록 완료’와 ‘개인 · 부서 · 공지 알림을 받을 준비가 되었습니다.’ 안내를 확인하고 ‘확인’을 누릅니다. 관리자에게 본인 대상 테스트 알림을 요청하세요.")
     ], "이번 문서는 촬영 절차를 설명합니다", "등록 완료 안내는 현재 소스 기준 설명입니다. 캡처를 위해 신규 등록·테스트 발송·기존 구독 초기화를 실행하지 않았습니다. QR 이미지는 공유하지 마세요."),
]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--font", default="/System/Library/Fonts/Supplemental/AppleGothic.ttf")
    args = parser.parse_args()
    pdfmetrics.registerFont(TTFont("Korean", args.font))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    c = canvas.Canvas(str(OUTPUT), pagesize=A4)
    c.setTitle("DEPL Android - QR 등록 및 알림 내역 사용자 가이드")
    c.setAuthor("DEPL")

    def text(value, x, top, width, size=10.5, color=INK):
        p = Paragraph(escape(value).replace("\n", "<br/>"), ParagraphStyle(
            "body", fontName="Korean", fontSize=size, leading=size*1.55,
            textColor=color, wordWrap="CJK", splitLongWords=True))
        _, height = p.wrap(width, H)
        if top-height < 49:
            raise ValueError(f"Text crosses footer: {value[:35]}")
        c.setFillColor(color)
        p.drawOn(c, x, top-height)
        return top-height

    def header(number, title, subtitle):
        c.setFillColor(YELLOW)
        c.rect(0, H-12, W, 12, fill=1, stroke=0)
        text("DEPL", 38, H-28, 70, 15)
        for x,y,r,color in [(89,H-43,2,BLUE),(96,H-39,3,BLUE),
                             (105,H-34,4,colors.HexColor("#EF343B"))]:
            c.setFillColor(color)
            c.circle(x,y,r,fill=1,stroke=0)
        text("Android 사용자 가이드", 342, H-31, 215, 9, GRAY)
        text(title, 38, H-72, W-76, 24)
        text(subtitle, 38, H-113, W-76, 10, GRAY)
        c.setStrokeColor(colors.HexColor("#DDE0E3"))
        c.line(38,43,W-38,43)
        c.setFillColor(GRAY)
        c.setFont("Korean",8)
        c.drawString(38,25,"실제 SM-G960N · Android 10 · 2026-09-10")
        c.drawRightString(W-38,25,f"{number} / 4")

    for number,(title,subtitle,filename,caption,steps,notice_title,notice) in enumerate(PAGES,1):
        header(number,title,subtitle)
        img = ImageReader(str(ASSETS/filename))
        iw,ih = img.getSize()
        image_height = 235*ih/iw
        c.drawImage(img,38,H-154-image_height,235,image_height)
        text(caption,38,H-164-image_height,235,8,GRAY)
        top=H-156
        for i,(step,body) in enumerate(steps,1):
            c.setFillColor(YELLOW)
            c.circle(302,top-8,11,fill=1,stroke=0)
            text(str(i),299,top-1,14,10)
            top=text(step,322,top+1,232,13)
            top=text(body,291,top-13,263)-27
        c.setFillColor(YELLOW)
        c.roundRect(291,top-5,263,4,2,fill=1,stroke=0)
        top=text(notice_title,291,top-16,263,12)
        text(notice,291,top-9,263,10,GRAY)
        c.showPage()

    header(4,"내역 관리와 문제 해결","기기에 저장되는 알림 내역과 알림 연결 정보는 별도로 관리합니다.")
    y=text("최신 3,000건 보관 · 30건씩 목록 읽기",38,H-157,W-76,15)
    y=text("현재 소스는 기기 DB에 최신 3,000건을 보관하며 초과분은 오래된 순서로 정리합니다. 30건은 목록 읽기 단위이지 보관 상한이 아닙니다. 동일 eventId는 중복 저장하지 않지만, 같은 문구라도 다른 이벤트이면 별도 알림입니다.",38,y-12,W-76,11)-22
    y=text("전체 삭제와 기기 등록 초기화는 다릅니다",38,y,W-76,15)
    y=text("휴지통은 확인 후 저장 내역을 전체 삭제합니다. ‘기기 등록 초기화’는 확인 후 알림 구독을 해제하므로 새 QR 등록이 필요합니다. 서버 내역 복원 기능은 없습니다. 앱 삭제·데이터 삭제 전에도 복구 가능 여부를 관리자에게 문의하세요.",38,y-12,W-76,11)-23
    rows=[
        ("카메라가 보이지 않음","시스템 설정에서 DEPL의 카메라 권한을 확인합니다."),
        ("QR 만료 / 서명 오류","새 QR로 재시도합니다. 계속되면 회사 앱·QR 조합을 관리자에게 확인합니다."),
        ("등록 중 멈춤 / 실패","인터넷 연결을 확인하고 오류 문구·발생 시각을 관리자에게 전달합니다."),
        ("알림·진동·소리가 안 남","시스템 앱 알림, 채널, 잠금 화면, 무음·방해 금지 상태를 확인합니다."),
        ("지연 또는 내역 누락","절전·네트워크·앱 강제 종료 등의 영향을 확인합니다. 모든 Push 전달·저장은 보장되지 않습니다."),
    ]
    for title,body in rows:
        c.setFillColor(colors.HexColor("#F3F5F6"))
        c.roundRect(38,y-57,W-76,57,7,fill=1,stroke=0)
        text(title,49,y-9,145)
        text(body,207,y-9,336)
        y-=66
    text("문의 시: 앱·OS 버전, 발생 시각, 오류 문구를 전달하세요. QR 원문·FCM 토큰·개인키는 보내지 않습니다. 화면은 설치본 기준, 저장 동작은 현재 소스 기준이며 제조사·버전에 따라 다를 수 있습니다.",38,y-4,W-76,9,GRAY)
    c.save()
    print(OUTPUT)


if __name__ == "__main__":
    main()
