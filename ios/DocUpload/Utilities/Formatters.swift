import Foundation

enum Formatters {
    static func readableSize(_ bytes: Int) -> String {
        guard bytes > 0 else { return "0 B" }
        let units = ["B", "KB", "MB", "GB"]
        let exponent = min(units.count - 1, Int(log2(Double(bytes)) / log2(1024)))
        let value = Double(bytes) / pow(1024, Double(exponent))
        return String(format: exponent == 0 ? "%.0f %@" : "%.1f %@", value, units[exponent])
    }

    static func dateTime(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}
