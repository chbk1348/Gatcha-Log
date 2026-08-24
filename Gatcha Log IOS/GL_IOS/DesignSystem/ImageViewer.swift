import SwiftUI
import UIKit
import Photos

// ════════════════════════════════════════════════════════════════════════════
// 이미지 뷰어 — 공지 본문 이미지를 탭하면 전체화면으로 크게 보고, 사진 앱에 저장한다.
//
// 본문 안에서는 이미지가 폭에 맞춰 작게 들어가 있어 표·수치가 안 읽힌다(공지 이미지는 대개 정보 표다).
// 확대(핀치)와 이동(드래그)을 붙여 실제로 읽을 수 있게 하고, 저장은 사진 앱으로 내보낸다.
//
// (Compose 패리티: GL_Android/ui/components/ImageViewer.kt)
// ════════════════════════════════════════════════════════════════════════════

struct GLGImageViewer: View {
    let url: String
    let onDismiss: () -> Void
    /// 저장 결과 안내(전역 토스트로 띄운다).
    let onSaved: (String) -> Void

    @State private var scale: CGFloat = 1
    @State private var lastScale: CGFloat = 1
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero
    @State private var image: UIImage? = nil
    @State private var failed = false

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            // AsyncImage 를 쓰지 않는다 — 저장 버튼에 쓸 원본 UIImage 가 어차피 필요해서 preload() 가
            // 같은 URL 을 따로 받고 있었다. 그러면 다운로드 2회·디코딩 2회다. 받은 것을 그대로 그린다.
            Group {
                if let image {
                    Image(uiImage: image).resizable().scaledToFit()
                        .scaleEffect(scale)
                        .offset(offset)
                        .gesture(zoom.simultaneously(with: pan))
                        .onTapGesture(count: 2) { reset() } // 두 번 탭 = 원래 크기로
                } else if failed {
                    Text("이미지를 불러오지 못했어요")
                        .font(.pretendard(size: 14)).foregroundStyle(.white.opacity(0.7))
                } else {
                    ProgressView().tint(.white)
                }
            }
            .task { await preload() }

            VStack {
                HStack {
                    viewerButton("xmark", "닫기") { onDismiss() }
                    Spacer()
                    viewerButton("arrow.down.to.line", "저장") { save() }
                }
                .padding(.horizontal, 12)
                .padding(.top, 8)
                Spacer()
            }
        }
        .statusBarHidden()
    }

    // 1배 미만으로는 줄지 않게(원본보다 작아지면 읽을 이유가 없다), 6배까지만.
    private var zoom: some Gesture {
        MagnificationGesture()
            .onChanged { value in scale = min(max(lastScale * value, 1), 6) }
            .onEnded { _ in
                lastScale = scale
                if scale <= 1 { reset() }
            }
    }

    // 확대 상태에서만 이동 — 1배에서 끌리면 화면이 흔들리는 것처럼 보인다.
    private var pan: some Gesture {
        DragGesture()
            .onChanged { value in
                guard scale > 1 else { return }
                offset = CGSize(width: lastOffset.width + value.translation.width,
                                height: lastOffset.height + value.translation.height)
            }
            .onEnded { _ in lastOffset = offset }
    }

    private func reset() {
        withAnimation(.easeOut(duration: 0.2)) {
            scale = 1; lastScale = 1
            offset = .zero; lastOffset = .zero
        }
    }

    /// 원본을 한 번만 받아 화면 표시와 저장에 함께 쓴다.
    /// 전체화면 뷰어라 축소 디코딩(GLGRemoteImage)은 쓰지 않는다 — 확대해서 보는 화면이다.
    private func preload() async {
        guard image == nil, let u = URL(string: url) else { failed = true; return }
        guard let (data, _) = try? await URLSession.shared.data(from: u),
              let decoded = UIImage(data: data) else {
            failed = true
            return
        }
        image = decoded
    }

    private func save() {
        guard let image else { onSaved("이미지를 아직 불러오는 중이에요"); return }
        // '추가 전용' 권한 — 사진을 읽지 않고 쓰기만 하므로 전체 라이브러리 접근을 요구하지 않는다.
        PHPhotoLibrary.requestAuthorization(for: .addOnly) { status in
            guard status == .authorized || status == .limited else {
                DispatchQueue.main.async { onSaved("사진 접근 권한이 필요해요 (설정에서 허용)") }
                return
            }
            PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.creationRequestForAsset(from: image)
            } completionHandler: { ok, _ in
                DispatchQueue.main.async { onSaved(ok ? "사진 앱에 저장했어요" : "저장하지 못했어요") }
            }
        }
    }

    private func viewerButton(_ icon: String, _ label: String, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: icon)
                .font(.pretendard(size: 17, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 40, height: 40)
                .background(.white.opacity(0.16), in: Circle())
        }
        .accessibilityLabel(label)
    }
}
