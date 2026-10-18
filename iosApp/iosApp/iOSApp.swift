import SwiftUI
import Shared

@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
                // Files other apps hand to QdeQ (CFBundleDocumentTypes in project.yml): copied
                // into the Documents folder (IosFileImport.kt).
                .onOpenURL { url in IosFileImportKt.importSharedFile(url: url) }
        }
    }
}
