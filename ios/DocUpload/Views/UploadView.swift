import SwiftUI
import UIKit
import VisionKit

struct UploadView: View {
    let applicationNumber: String
    @Binding var path: [Route]

    @EnvironmentObject private var store: DocumentStore

    @State private var showScanner = false
    @State private var showFilePicker = false
    @State private var showSuccess = false
    @State private var isUploading = false
    @State private var uploadProgress: Double = 0
    @State private var alertMessage: String?
    @State private var previewDocument: Document?

    private var pendingDocuments: [Document] {
        store.pending(for: applicationNumber)
    }

    var body: some View {
        Group {
            if showSuccess {
                successView
            } else {
                uploadForm
            }
        }
        .navigationTitle("Upload Documents")
        .navigationBarTitleDisplayMode(.inline)
        .fullScreenCover(isPresented: $showScanner) {
            DocumentScannerView(
                onFinish: { images in
                    for (index, image) in images.enumerated() {
                        guard let data = image.jpegData(compressionQuality: 0.85) else { continue }
                        store.addImage(
                            data,
                            applicationNumber: applicationNumber,
                            fileName: "Scan_\(Int(Date().timeIntervalSince1970))_\(index + 1).jpg",
                            kind: .scan
                        )
                    }
                },
                onCancel: {},
                onError: { error in alertMessage = "Document scan failed: \(error.localizedDescription)" }
            )
            .ignoresSafeArea()
        }
        .sheet(isPresented: $showFilePicker) {
            FilePickerView { urls in
                for url in urls {
                    store.addFile(from: url, applicationNumber: applicationNumber)
                }
            }
            .ignoresSafeArea()
        }
        .sheet(item: $previewDocument) { document in
            DocumentPreviewSheet(document: document)
        }
        .alert(
            "Camera unavailable",
            isPresented: Binding(get: { alertMessage != nil }, set: { if !$0 { alertMessage = nil } })
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(alertMessage ?? "")
        }
    }

    private var uploadForm: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                HStack(spacing: 14) {
                    OptionCard(icon: "photo.on.rectangle", label: "Upload from Mobile") {
                        showFilePicker = true
                    }
                    OptionCard(icon: "camera.viewfinder", label: "Scan Document") {
                        startScan()
                    }
                }

                HStack {
                    Text("Documents").font(.headline)
                    Text("\(pendingDocuments.count)")
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 8).padding(.vertical, 2)
                        .background(Capsule().fill(.quaternary))
                }

                if pendingDocuments.isEmpty {
                    EmptyDocumentsView()
                } else {
                    VStack(spacing: 0) {
                        ForEach(pendingDocuments) { document in
                            DocumentRow(
                                document: document,
                                thumbnail: store.image(for: document),
                                onPreview: { previewDocument = document },
                                onDelete: { store.delete(document) }
                            )
                            Divider()
                        }
                    }
                }
            }
            .padding()
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 8) {
                if isUploading {
                    ProgressView(value: uploadProgress)
                    Text("Uploading… \(Int(uploadProgress * 100))%")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Button {
                    Task { await upload() }
                } label: {
                    Text("Upload").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .disabled(pendingDocuments.isEmpty || isUploading)
            }
            .padding()
            .background(.regularMaterial)
        }
    }

    private var successView: some View {
        VStack(spacing: 20) {
            Spacer()
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 64))
                .foregroundStyle(.green)
            Text("Documents uploaded successfully.")
                .font(.title3.weight(.semibold))
                .multilineTextAlignment(.center)
            VStack(spacing: 12) {
                Button {
                    path.append(.uploaded(applicationNumber))
                } label: {
                    Text("View Uploaded Documents").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)

                Button {
                    showSuccess = false
                } label: {
                    Text("Upload More").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
            .padding(.horizontal, 32)
            Spacer()
        }
        .padding()
    }

    private func startScan() {
        guard VNDocumentCameraViewController.isSupported else {
            alertMessage = "Document scanning isn't supported on this device."
            return
        }
        showScanner = true
    }

    private func upload() async {
        isUploading = true
        uploadProgress = 0
        await store.upload(applicationNumber: applicationNumber) { progress in
            uploadProgress = progress
        }
        isUploading = false
        showSuccess = true
    }
}

private struct OptionCard: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 8) {
                Image(systemName: icon).font(.system(size: 28))
                Text(label).font(.footnote.weight(.medium)).multilineTextAlignment(.center)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 18)
            .background(RoundedRectangle(cornerRadius: 14).fill(.thinMaterial))
        }
        .buttonStyle(.plain)
    }
}

private struct DocumentPreviewSheet: View {
    let document: Document
    @EnvironmentObject private var store: DocumentStore

    var body: some View {
        if let image = store.image(for: document) {
            Image(uiImage: image).resizable().scaledToFit().padding()
        } else {
            Text(document.fileName).padding()
        }
    }
}
