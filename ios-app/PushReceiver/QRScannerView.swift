@preconcurrency import AVFoundation
import SwiftUI
import UIKit

struct TopicRegistrationView: View {
    @Environment(\.dismiss) private var dismiss
    @StateObject private var viewModel = TopicRegistrationViewModel()
    @State private var cameraMessage: String?
#if DEBUG
    @State private var testQRValue = ""
#endif

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if shouldShowScanner { introduction }
                    scannerSection
                    subscriptionCard
                    actionSection
                }
                .padding(20)
            }
            .background(Color.deplYellow.ignoresSafeArea())
            .navigationTitle("기기 등록")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.black)
                            .frame(width: 32, height: 32)
                            .background(.white.opacity(0.62), in: Circle())
                    }
                    .accessibilityLabel("닫기")
                }
            }
            .task { await viewModel.resumePending() }
        }
        .preferredColorScheme(.light)
    }

    private var introduction: some View {
        VStack(spacing: 8) {
            Image(systemName: "qrcode.viewfinder")
                .font(.system(size: 38, weight: .semibold))
            Text("업무 시스템에 표시된 QR 코드를 촬영해 주세요.")
                .font(.system(size: 17, weight: .bold))
                .multilineTextAlignment(.center)
            Text("앱은 업무망에 접속하지 않고 Firebase 알림 Topic만 구독합니다.")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 4)
    }

    @ViewBuilder
    private var scannerSection: some View {
        if shouldShowScanner {
            ZStack {
                QRScannerCameraView(
                    onCode: { code in Task { await viewModel.process(scannedValue: code) } },
                    onError: { cameraMessage = $0 }
                )
                .frame(height: 250)
                .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))

                RoundedRectangle(cornerRadius: 18)
                    .stroke(.white, style: StrokeStyle(lineWidth: 3, dash: [12, 7]))
                    .frame(width: 190, height: 190)

                if viewModel.isScanLocked {
                    Color.black.opacity(0.44)
                        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
                    ProgressView().tint(.white).scaleEffect(1.3)
                }
            }
            if let cameraMessage {
                VStack(spacing: 10) {
                    Text(cameraMessage).font(.system(size: 13, weight: .semibold))
                    Button("시스템 설정 열기") {
                        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
                        UIApplication.shared.open(url)
                    }
                    .font(.system(size: 13, weight: .bold))
                }
            }
#if DEBUG
            debugInput
#endif
        } else {
            resultCard
        }
    }

    private var shouldShowScanner: Bool {
        switch viewModel.state {
        case .ready, .validating, .subscribing: true
        case .success, .failure: false
        }
    }

    @ViewBuilder
    private var resultCard: some View {
        switch viewModel.state {
        case .success:
            ResultPanel(
                icon: "checkmark.circle.fill",
                title: "기기 등록이 완료되었습니다.",
                message: "개인·부서·전체 공지 알림을 받을 수 있습니다.",
                color: .green
            )
        case .failure(let message):
            ResultPanel(
                icon: "exclamationmark.triangle.fill",
                title: "기기를 등록할 수 없습니다.",
                message: message,
                color: .red
            )
        default: EmptyView()
        }
    }

    private var subscriptionCard: some View {
        VStack(spacing: 0) {
            ForEach(Array(TopicKind.allCases.enumerated()), id: \.element) { index, kind in
                HStack {
                    Image(systemName: icon(for: kind))
                        .frame(width: 28)
                    Text(kind.title).font(.system(size: 15, weight: .semibold))
                    Spacer()
                    statusView(for: viewModel.topicStatus[kind] ?? .notStarted)
                }
                .padding(.vertical, 14)
                if index < TopicKind.allCases.count - 1 { Divider() }
            }
        }
        .padding(.horizontal, 16)
        .background(.white, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    @ViewBuilder
    private var actionSection: some View {
        if case .failure = viewModel.state {
            Button("다시 촬영") { viewModel.scanAgain() }
                .buttonStyle(DEPLPrimaryButtonStyle())
        }
        if viewModel.hasRegistration {
            Button("기기 등록 초기화", role: .destructive) {
                Task { await viewModel.reset() }
            }
            .font(.system(size: 14, weight: .bold))
            .disabled(viewModel.state == .subscribing)
        }
    }

    private func icon(for kind: TopicKind) -> String {
        switch kind {
        case .user: "person.fill"
        case .department: "person.3.fill"
        case .notice: "megaphone.fill"
        }
    }

    @ViewBuilder
    private func statusView(for status: TopicRegistrationViewModel.SubscriptionStatus) -> some View {
        switch status {
        case .notStarted:
            Text(viewModel.hasRegistration ? "등록됨" : "대기")
                .foregroundStyle(viewModel.hasRegistration ? .green : .secondary)
        case .working: ProgressView().tint(.black)
        case .success: Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
        case .failure: Image(systemName: "xmark.circle.fill").foregroundStyle(.red)
        }
    }

#if DEBUG
    private var debugInput: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("DEBUG · QR 문자열 테스트").font(.caption.bold())
            TextEditor(text: $testQRValue)
                .font(.system(.caption, design: .monospaced))
                .frame(height: 90)
                .padding(8)
                .background(.white, in: RoundedRectangle(cornerRadius: 12))
            Button("테스트 문자열 처리") {
                Task { await viewModel.process(scannedValue: testQRValue) }
            }
            .disabled(testQRValue.isEmpty)
        }
    }
#endif
}

private struct ResultPanel: View {
    let icon: String
    let title: String
    let message: String
    let color: Color

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon).font(.system(size: 42)).foregroundStyle(color)
            Text(title).font(.system(size: 18, weight: .bold))
            Text(message).font(.system(size: 14, weight: .medium)).foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
        .background(.white, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}

private struct DEPLPrimaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16, weight: .bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(.black.opacity(configuration.isPressed ? 0.72 : 1), in: RoundedRectangle(cornerRadius: 14))
    }
}

private struct QRScannerCameraView: UIViewControllerRepresentable {
    let onCode: (String) -> Void
    let onError: (String) -> Void

    func makeUIViewController(context: Context) -> QRScannerViewController {
        QRScannerViewController(onCode: onCode, onError: onError)
    }

    func updateUIViewController(_ uiViewController: QRScannerViewController, context: Context) {}
}

private final class QRScannerViewController: UIViewController, @preconcurrency AVCaptureMetadataOutputObjectsDelegate {
    private let session = AVCaptureSession()
    private let sessionQueue = DispatchQueue(label: "depl.qr-camera-session")
    private let onCode: (String) -> Void
    private let onError: (String) -> Void
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var configured = false
    private var deliveredCode = false

    init(onCode: @escaping (String) -> Void, onError: @escaping (String) -> Void) {
        self.onCode = onCode
        self.onError = onError
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        prepareCamera()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.bounds
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopSession()
    }

    private func prepareCamera() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: configureAndStart()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                DispatchQueue.main.async {
                    if granted { self?.configureAndStart() }
                    else { self?.onError("카메라 사용 권한이 필요합니다.") }
                }
            }
        case .denied, .restricted: onError("카메라 사용 권한이 필요합니다.")
        @unknown default: onError("카메라를 사용할 수 없습니다.")
        }
    }

    private func configureAndStart() {
        guard !configured else { startSession(); return }
        guard let camera = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: camera),
              session.canAddInput(input) else {
            onError("이 기기에서는 카메라를 사용할 수 없습니다.")
            return
        }
        session.addInput(input)
        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            onError("QR 스캐너를 시작할 수 없습니다.")
            return
        }
        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]

        let layer = AVCaptureVideoPreviewLayer(session: session)
        layer.videoGravity = .resizeAspectFill
        view.layer.insertSublayer(layer, at: 0)
        previewLayer = layer
        configured = true
        startSession()
    }

    private func startSession() {
        deliveredCode = false
        sessionQueue.async { [session] in
            if !session.isRunning { session.startRunning() }
        }
    }

    private func stopSession() {
        sessionQueue.async { [session] in
            if session.isRunning { session.stopRunning() }
        }
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !deliveredCode,
              let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              object.type == .qr,
              let value = object.stringValue else { return }
        deliveredCode = true
        stopSession()
        onCode(value)
    }
}
