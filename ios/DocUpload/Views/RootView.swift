import SwiftUI

struct RootView: View {
    @EnvironmentObject private var session: SessionStore
    @State private var path: [Route] = []

    var body: some View {
        Group {
            if session.isLoggedIn {
                NavigationStack(path: $path) {
                    SearchView(path: $path)
                        .navigationDestination(for: Route.self) { route in
                            switch route {
                            case .upload(let appNo):
                                UploadView(applicationNumber: appNo, path: $path)
                            case .uploaded(let appNo):
                                UploadedView(applicationNumber: appNo)
                            }
                        }
                }
            } else {
                LoginView()
            }
        }
        .onChange(of: session.isLoggedIn) { isLoggedIn in
            if !isLoggedIn { path = [] }
        }
    }
}
