import SwiftUI
import ComposeApp

/// Kotlin(Compose Multiplatform) UI 를 SwiftUI 안에 호스팅
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.all) // Compose 가 자체적으로 인셋을 처리
    }
}
