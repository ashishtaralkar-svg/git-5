import Foundation

/// Demo-only session state, mirrors the web app's localStorage session /
/// the Android app's DataStore-backed session. No real auth backend.
@MainActor
final class SessionStore: ObservableObject {
    private static let demoUsername = "hdfc"
    private static let demoPassword = "123"
    private static let usernameKey = "session.username"

    @Published private(set) var isLoggedIn: Bool
    @Published private(set) var username: String?

    init() {
        let storedUsername = UserDefaults.standard.string(forKey: Self.usernameKey)
        username = storedUsername
        isLoggedIn = storedUsername != nil
    }

    /// Returns nil on success, or an error message to display.
    func login(username: String, password: String) -> String? {
        guard username == Self.demoUsername, password == Self.demoPassword else {
            return "Invalid Username or Password."
        }
        UserDefaults.standard.set(username, forKey: Self.usernameKey)
        self.username = username
        isLoggedIn = true
        return nil
    }

    func logout() {
        UserDefaults.standard.removeObject(forKey: Self.usernameKey)
        username = nil
        isLoggedIn = false
    }
}
