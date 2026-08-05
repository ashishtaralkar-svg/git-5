import SwiftUI
import UIKit

/// One row in a document list — thumbnail, name, size, status — shared by
/// UploadView (pending) and UploadedView (uploaded, with date + preview).
struct DocumentRow: View {
    let document: Document
    let thumbnail: UIImage?
    var showDate: Bool = false
    var onPreview: (() -> Void)?
    var onDelete: (() -> Void)?

    var body: some View {
        HStack(spacing: 12) {
            thumbnailView
            VStack(alignment: .leading, spacing: 4) {
                Text(document.fileName)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Text(subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                statusBadge
            }
            Spacer()
            if let onPreview {
                Button(action: onPreview) {
                    Image(systemName: "eye")
                }
                .buttonStyle(.plain)
            }
            if let onDelete {
                Button(role: .destructive, action: onDelete) {
                    Image(systemName: "trash")
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 6)
    }

    private var subtitle: String {
        var parts = [Formatters.readableSize(document.sizeBytes)]
        if showDate { parts.append(Formatters.dateTime(document.createdAt)) }
        return parts.joined(separator: " • ")
    }

    @ViewBuilder
    private var thumbnailView: some View {
        if let thumbnail {
            Image(uiImage: thumbnail)
                .resizable()
                .scaledToFill()
                .frame(width: 52, height: 52)
                .clipShape(RoundedRectangle(cornerRadius: 10))
        } else {
            RoundedRectangle(cornerRadius: 10)
                .fill(.quaternary)
                .frame(width: 52, height: 52)
                .overlay(Image(systemName: "doc.fill").foregroundStyle(.secondary))
        }
    }

    private var statusBadge: some View {
        Text(document.status == .uploaded ? "Uploaded" : "Pending")
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(
                Capsule().fill(document.status == .uploaded ? Color.green.opacity(0.15) : Color.orange.opacity(0.15))
            )
            .foregroundStyle(document.status == .uploaded ? Color.green : Color.orange)
    }
}

struct EmptyDocumentsView: View {
    var body: some View {
        VStack(spacing: 8) {
            Image(systemName: "tray")
                .font(.system(size: 40))
                .foregroundStyle(.secondary)
            Text("No documents yet.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 40)
    }
}
