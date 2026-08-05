import Foundation
import UIKit

/// Local-only persistence for documents — mirrors the web app's IndexedDB
/// store / the Android app's Room database. No backend: "upload" just flips
/// a document's status after a simulated delay.
@MainActor
final class DocumentStore: ObservableObject {
    @Published private(set) var documents: [Document] = []

    private let indexURL: URL
    private let filesDirectory: URL

    init() {
        let documentsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
        filesDirectory = documentsDir.appendingPathComponent("DocumentFiles", isDirectory: true)
        indexURL = documentsDir.appendingPathComponent("documents.json")
        try? FileManager.default.createDirectory(at: filesDirectory, withIntermediateDirectories: true)
        load()
    }

    func pending(for appNo: String) -> [Document] {
        documents.filter { $0.applicationNumber == appNo && $0.status == .pending }
    }

    func uploaded(for appNo: String) -> [Document] {
        documents.filter { $0.applicationNumber == appNo && $0.status == .uploaded }
    }

    /// Saves image data as a new pending document.
    func addImage(_ data: Data, applicationNumber: String, fileName: String, kind: DocumentKind) {
        let localFileName = UUID().uuidString + ".jpg"
        let fileURL = filesDirectory.appendingPathComponent(localFileName)
        do {
            try data.write(to: fileURL)
        } catch {
            return
        }
        let document = Document(
            applicationNumber: applicationNumber,
            fileName: fileName,
            localFileName: localFileName,
            mimeType: "image/jpeg",
            sizeBytes: data.count,
            kind: kind
        )
        documents.append(document)
        save()
    }

    /// Copies an externally-picked file (image or PDF) into local storage as a new pending document.
    func addFile(from sourceURL: URL, applicationNumber: String) {
        let isPdf = sourceURL.pathExtension.lowercased() == "pdf"
        let ext = isPdf ? "pdf" : "jpg"
        let localFileName = UUID().uuidString + "." + ext
        let destination = filesDirectory.appendingPathComponent(localFileName)

        let accessed = sourceURL.startAccessingSecurityScopedResource()
        defer { if accessed { sourceURL.stopAccessingSecurityScopedResource() } }

        guard let data = try? Data(contentsOf: sourceURL) else { return }
        do {
            try data.write(to: destination)
        } catch {
            return
        }

        let document = Document(
            applicationNumber: applicationNumber,
            fileName: sourceURL.lastPathComponent,
            localFileName: localFileName,
            mimeType: isPdf ? "application/pdf" : "image/jpeg",
            sizeBytes: data.count,
            kind: isPdf ? .pdf : .gallery
        )
        documents.append(document)
        save()
    }

    func fileURL(for document: Document) -> URL {
        filesDirectory.appendingPathComponent(document.localFileName)
    }

    func image(for document: Document) -> UIImage? {
        guard document.kind != .pdf else { return nil }
        return UIImage(contentsOfFile: fileURL(for: document).path)
    }

    func delete(_ document: Document) {
        try? FileManager.default.removeItem(at: fileURL(for: document))
        documents.removeAll { $0.id == document.id }
        save()
    }

    /// Simulates an upload: marks each pending doc for the application as uploaded,
    /// invoking `onProgress` after each one (0...1).
    func upload(applicationNumber: String, onProgress: @escaping (Double) -> Void) async {
        let ids = pending(for: applicationNumber).map(\.id)
        guard !ids.isEmpty else { return }
        for (index, id) in ids.enumerated() {
            try? await Task.sleep(nanoseconds: 600_000_000)
            if let i = documents.firstIndex(where: { $0.id == id }) {
                documents[i].status = .uploaded
            }
            onProgress(Double(index + 1) / Double(ids.count))
        }
        save()
    }

    private func load() {
        guard let data = try? Data(contentsOf: indexURL) else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        documents = (try? decoder.decode([Document].self, from: data)) ?? []
    }

    private func save() {
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        guard let data = try? encoder.encode(documents) else { return }
        try? data.write(to: indexURL)
    }
}
