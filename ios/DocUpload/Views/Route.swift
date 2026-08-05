import Foundation

/// Navigation destinations pushed onto the root NavigationStack's path.
/// Kept as a plain Hashable enum (rather than `navigationDestination(item:)`,
/// an iOS 17+ API) so this targets iOS 16 without relying on it.
enum Route: Hashable {
    case upload(String)
    case uploaded(String)
}
