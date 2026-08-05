import SwiftUI
import UIKit

struct UploadedView: View {
    let applicationNumber: String
    @EnvironmentObject private var store: DocumentStore
    @State private var previewDocument: Document?

    private var documents: [Document] {
        store.uploaded(for: applicationNumber)
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if documents.isEmpty {
                    EmptyDocumentsView()
                } else {
                    ForEach(documents) { document in
                        DocumentRow(
                            document: document,
                            thumbnail: store.image(for: document),
                            showDate: true,
                            onPreview: { previewDocument = document },
                            onDelete: { store.delete(document) }
                        )
                        Divider()
                    }
                }
            }
            .padding()
        }
        .navigationTitle("Uploaded Documents")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $previewDocument) { document in
            if let image = store.image(for: document) {
                Image(uiImage: image).resizable().scaledToFit().padding()
            } else {
                Text(document.fileName).padding()
            }
        }
    }
}
