import Foundation

enum DocumentKind: String, Codable {
    case scan
    case gallery
    case pdf
}

enum UploadStatus: String, Codable {
    case pending
    case uploaded
}

/// A single uploaded/pending document. Mirrors DocumentEntity (Android) /
/// the IndexedDB record shape (web) — metadata only, the actual image/PDF
/// bytes live in a file named `localFileName` inside the Documents directory.
struct Document: Identifiable, Codable, Equatable {
    let id: UUID
    var applicationNumber: String
    var fileName: String
    var localFileName: String
    var mimeType: String
    var sizeBytes: Int
    var kind: DocumentKind
    var status: UploadStatus
    var createdAt: Date

    init(
        id: UUID = UUID(),
        applicationNumber: String,
        fileName: String,
        localFileName: String,
        mimeType: String,
        sizeBytes: Int,
        kind: DocumentKind,
        status: UploadStatus = .pending,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.applicationNumber = applicationNumber
        self.fileName = fileName
        self.localFileName = localFileName
        self.mimeType = mimeType
        self.sizeBytes = sizeBytes
        self.kind = kind
        self.status = status
        self.createdAt = createdAt
    }
}
