import Foundation
import Capacitor

public func simdToArray4x4(_ t:simd_float4x4) -> Array<Float> {
    let c = t.columns
    return [
        c.0.x, c.0.y, c.0.z, c.0.w,
        c.1.x, c.1.y, c.1.z, c.1.w,
        c.2.x, c.2.y, c.2.z, c.2.w,
        c.3.x, c.3.y, c.3.z, c.3.w,
    ]
}

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(XRPlugin)
public class XRPlugin: CAPPlugin {
    private let implementation = WebXRNative()
    
    public override func load() {
        implementation.onUpdate = {
            guard let frame = self.implementation.session?.currentFrame else {
                return
            }
            let interfaceOrientation = UIApplication.shared.statusBarOrientation
            let transform = frame.camera.viewMatrix(for: interfaceOrientation).inverse
            let position = transform.columns.3
            let rotation = simd_quatf(transform) * simd_quatf(ix: 0, iy: 0, iz: 1, r: -1).normalized
            let projectionMatrix = frame.camera.projectionMatrix(
                for: interfaceOrientation,
                viewportSize: self.bridge.getWebView()!.frame.size,
                // TODO: z near/far should be configurabe (as described in the WebXR spec)
                zNear: 0.001,
                zFar: 1000
            )
            self.notifyListeners("poseDataReceived", data: [
                "cameraPositionX": position.x,
                "cameraPositionY": position.y,
                "cameraPositionZ": position.z,
                "cameraRotationX": rotation.imag.x,
                "cameraRotationY": rotation.imag.y,
                "cameraRotationZ": rotation.imag.z,
                "cameraRotationW": rotation.real,
                "cameraProjectionMatrix": simdToArray4x4(projectionMatrix)
            ])
        }
        // Add video view as a subview of the webview
        let videoView = implementation.videoView
        videoView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        videoView.frame = webView.bounds
        let webView = bridge!.getWebView()! as WKWebView
        webView.addSubview(videoView)
        webView.sendSubviewToBack(videoView)
        // NOTE: isOpaque MUST be set here (not in the subsequent async callback),
        // because Capacitor will remember this value after this method returns,
        // and stubbornly reapply it to the webview when the page loads
        webView.isOpaque = false
        // NOTE: Capacitor will override any background color we set on this webview
        // after this method returns (based on capacitor.config.json),
        // so we'll schedule an async task to override the capacitor config.
        // Presumably, anyone using this plugin always wants the following behavior.
        DispatchQueue.main.async {
            webView.scrollView.backgroundColor = .clear
            webView.backgroundColor = .clear
        }
    }

    @objc func initialize(_ call: CAPPluginCall) {
        call.resolve(["status": "ios"])
    }
    
    @objc func start(_ call: CAPPluginCall) {
        self.implementation.start() // TODO: handle various config options
        call.resolve(["status": "ok"])
    }
    
    @objc func stop(_ call: CAPPluginCall) {
        self.implementation.stop()
        call.resolve(["status": "ok"])
    }
    
    @objc func handleTap(_ call: CAPPluginCall) {
        call.unimplemented()
    }
}
