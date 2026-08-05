import SwiftUI

@main
struct DocUploadApp: App {
    @StateObject private var session = SessionStore()
    @StateObject private var store = DocumentStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .environmentObject(store)
        }
    }
}
