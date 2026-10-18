import SwiftUI
import UIKit
import Shared

/// The shared Compose UI (MainViewController.kt in shared/src/iosMain).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Full screen: Compose applies the safe area insets itself (status bar, home indicator).
        ComposeView()
            .ignoresSafeArea()
            .background(Color.black)
    }
}
