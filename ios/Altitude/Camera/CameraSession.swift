import AVFoundation
import SwiftUI
import UIKit

struct LensCapabilities {
    var autofocus: Bool
    var tapFlags: Int?
    var zoomMin: Float
    var zoomMax: Float
    var exposureSupported: Bool
    var exposureMin: Float
    var exposureMax: Float
}

enum LensProbe {
    static func read(_ device: AVCaptureDevice) -> LensCapabilities {
        var modes: [Int] = []
        if device.isFocusModeSupported(.autoFocus) { modes.append(Autofocus.modeAuto) }
        if device.isFocusModeSupported(.continuousAutoFocus) { modes.append(Autofocus.modeContinuousPicture) }
        let distance: Float
        if modes.isEmpty {
            distance = 0
        } else {
            let reported = device.minimumFocusDistance
            distance = reported > 0 ? reported : 0.1
        }
        let autofocus = Autofocus.supported(
            afModes: modes,
            minimumFocusDistance: distance,
            meteringSupportsAf: device.isFocusPointOfInterestSupported
        )
        let flags = Autofocus.tapFlags(autofocus: autofocus) { mask in
            let wantsAF = (mask & FocusMeteringChoice.af) != 0
            let wantsAE = (mask & FocusMeteringChoice.ae) != 0
            if wantsAF && !device.isFocusPointOfInterestSupported { return false }
            if wantsAE && !device.isExposurePointOfInterestSupported { return false }
            return wantsAF || wantsAE
        }
        let minZoom = Float(device.minAvailableVideoZoomFactor)
        let maxZoom = Float(device.maxAvailableVideoZoomFactor)
        let exposureSupported = device.minExposureTargetBias < device.maxExposureTargetBias
        return LensCapabilities(
            autofocus: autofocus,
            tapFlags: flags,
            zoomMin: minZoom.isFinite ? minZoom : 1,
            zoomMax: maxZoom.isFinite ? maxZoom : 1,
            exposureSupported: exposureSupported,
            exposureMin: device.minExposureTargetBias,
            exposureMax: device.maxExposureTargetBias
        )
    }

    static func device(front: Bool) -> AVCaptureDevice? {
        let position: AVCaptureDevice.Position = front ? .front : .back
        let types: [AVCaptureDevice.DeviceType] = front
            ? [.builtInWideAngleCamera]
            : [.builtInTripleCamera, .builtInDualWideCamera, .builtInDualCamera, .builtInWideAngleCamera]
        let discovery = AVCaptureDevice.DiscoverySession(deviceTypes: types, mediaType: .video, position: position)
        return discovery.devices.first
    }

    static func availability() -> (back: Bool, front: Bool) {
        (device(front: false) != nil, device(front: true) != nil)
    }
}

final class CameraSession: NSObject, AVCapturePhotoCaptureDelegate {
    let session = AVCaptureSession()
    private let output = AVCapturePhotoOutput()
    private let queue = DispatchQueue(label: "app.altitude.camera")
    private var device: AVCaptureDevice?
    private var configured = false
    private var photoHandler: ((Result<UIImage, Error>) -> Void)?

    func start(front: Bool, completion: @escaping (Result<LensCapabilities, Error>) -> Void) {
        queue.async {
            do {
                let capabilities = try self.configure(front: front)
                if !self.session.isRunning { self.session.startRunning() }
                DispatchQueue.main.async { completion(.success(capabilities)) }
            } catch {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }

    func stop() {
        queue.async {
            if self.session.isRunning { self.session.stopRunning() }
        }
    }

    func setZoom(_ ratio: Float) {
        queue.async {
            guard let device = self.device else { return }
            let minZoom = Float(device.minAvailableVideoZoomFactor)
            let maxZoom = Float(device.maxAvailableVideoZoomFactor)
            let clamped = CameraZoom.clamp(ratio, minZoom, maxZoom)
            do {
                try device.lockForConfiguration()
                device.videoZoomFactor = CGFloat(clamped)
                device.unlockForConfiguration()
            } catch {}
        }
    }

    func setExposure(_ bias: Float) {
        queue.async {
            guard let device = self.device else { return }
            let clamped = min(max(bias, device.minExposureTargetBias), device.maxExposureTargetBias)
            do {
                try device.lockForConfiguration()
                device.setExposureTargetBias(clamped, completionHandler: nil)
                device.unlockForConfiguration()
            } catch {}
        }
    }

    func focus(at devicePoint: CGPoint, lock: Bool) {
        queue.async {
            guard let device = self.device else { return }
            let caps = LensProbe.read(device)
            guard let flags = caps.tapFlags else { return }
            do {
                try device.lockForConfiguration()
                if (flags & FocusMeteringChoice.af) != 0, device.isFocusPointOfInterestSupported {
                    device.focusPointOfInterest = devicePoint
                    if lock, device.isFocusModeSupported(.locked) {
                        device.focusMode = .autoFocus
                    } else if device.isFocusModeSupported(.autoFocus) {
                        device.focusMode = .autoFocus
                    } else if device.isFocusModeSupported(.continuousAutoFocus) {
                        device.focusMode = .continuousAutoFocus
                    }
                }
                if (flags & FocusMeteringChoice.ae) != 0, device.isExposurePointOfInterestSupported {
                    device.exposurePointOfInterest = devicePoint
                    if device.isExposureModeSupported(.autoExpose) { device.exposureMode = .autoExpose }
                }
                device.unlockForConfiguration()
                if lock {
                    try device.lockForConfiguration()
                    if device.isFocusModeSupported(.locked) { device.focusMode = .locked }
                    device.unlockForConfiguration()
                }
            } catch {}
        }
    }

    func capture(completion: @escaping (Result<UIImage, Error>) -> Void) {
        queue.async {
            let settings = AVCapturePhotoSettings()
            settings.flashMode = .off
            if let connection = self.output.connection(with: .video) {
                let angle = Self.rotationAngle()
                if connection.isVideoRotationAngleSupported(angle) {
                    connection.videoRotationAngle = angle
                }
            }
            self.photoHandler = completion
            self.output.capturePhoto(with: settings, delegate: self)
        }
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        let handler = photoHandler
        photoHandler = nil
        if let error {
            DispatchQueue.main.async { handler?(.failure(error)) }
            return
        }
        guard let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else {
            DispatchQueue.main.async { handler?(.failure(PhotoStoreError.encode)) }
            return
        }
        DispatchQueue.main.async { handler?(.success(image)) }
    }

    private func configure(front: Bool) throws -> LensCapabilities {
        session.beginConfiguration()
        session.sessionPreset = .photo
        for input in session.inputs { session.removeInput(input) }
        guard let device = LensProbe.device(front: front) else { throw CameraSessionError.unavailable }
        let input = try AVCaptureDeviceInput(device: device)
        guard session.canAddInput(input) else { throw CameraSessionError.unavailable }
        session.addInput(input)
        if !session.outputs.contains(output) {
            guard session.canAddOutput(output) else { throw CameraSessionError.unavailable }
            session.addOutput(output)
        }
        output.maxPhotoQualityPrioritization = .quality
        self.device = device
        if device.isFocusModeSupported(.continuousAutoFocus) {
            try? device.lockForConfiguration()
            device.focusMode = .continuousAutoFocus
            device.unlockForConfiguration()
        }
        session.commitConfiguration()
        configured = true
        return LensProbe.read(device)
    }

    private static func rotationAngle() -> CGFloat {
        let orientation = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .first?.interfaceOrientation ?? .portrait
        switch orientation {
        case .portrait: return 90
        case .landscapeLeft: return 180
        case .landscapeRight: return 0
        case .portraitUpsideDown: return 270
        default: return 90
        }
    }
}

enum CameraSessionError: Error {
    case unavailable
}

struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    var onReady: (PreviewView) -> Void = { _ in }

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        DispatchQueue.main.async { onReady(view) }
        return view
    }

    func updateUIView(_ uiView: PreviewView, context: Context) {
        uiView.previewLayer.session = session
    }
}

final class PreviewView: UIView {
    override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
    var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }

    func devicePoint(from location: CGPoint) -> CGPoint {
        previewLayer.captureDevicePointConverted(fromLayerPoint: location)
    }
}
