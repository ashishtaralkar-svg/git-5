import SwiftUI
import UIKit
import VisionKit

/// Wraps VisionKit's VNDocumentCameraViewController — Apple's built-in
/// document scanner: live edge detection, auto-capture, perspective
/// correction and multi-page capture, all handled natively with no
/// third-party SDK. Equivalent to ML Kit's GmsDocumentScanning on Android.
struct DocumentScannerView: UIViewControllerRepresentable {
    var onFinish: ([UIImage]) -> Void
    var onCancel: () -> Void
    var onError: (Error) -> Void

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let controller = VNDocumentCameraViewController()
        controller.delegate = context.coordinator
        return controller
    }

    func updateUIViewController(_ uiViewController: VNDocumentCameraViewController, context: Context) {
        // No dynamic updates needed — VisionKit owns the entire capture UI.
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(onFinish: onFinish, onCancel: onCancel, onError: onError)
    }

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        private let onFinish: ([UIImage]) -> Void
        private let onCancel: () -> Void
        private let onError: (Error) -> Void

        init(
            onFinish: @escaping ([UIImage]) -> Void,
            onCancel: @escaping () -> Void,
            onError: @escaping (Error) -> Void
        ) {
            self.onFinish = onFinish
            self.onCancel = onCancel
            self.onError = onError
        }

        func documentCameraViewController(
            _ controller: VNDocumentCameraViewController,
            didFinishWith scan: VNDocumentCameraScan
        ) {
            var pages: [UIImage] = []
            pages.reserveCapacity(scan.pageCount)
            for pageIndex in 0..<scan.pageCount {
                pages.append(scan.imageOfPage(at: pageIndex))
            }
            controller.dismiss(animated: true) { [onFinish] in
                onFinish(pages)
            }
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            controller.dismiss(animated: true) { [onCancel] in
                onCancel()
            }
        }

        func documentCameraViewController(
            _ controller: VNDocumentCameraViewController,
            didFailWithError error: Error
        ) {
            controller.dismiss(animated: true) { [onError] in
                onError(error)
            }
        }
    }
}
