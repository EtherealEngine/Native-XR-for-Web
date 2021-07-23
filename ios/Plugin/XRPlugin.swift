import Foundation
import Capacitor
import Photos



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
@available(iOS 14.0, *)
@objc(XRPlugin)
public class XRPlugin: CAPPlugin {
    private let implementation = WebXRNative()
    private let videoRec = ScreenRecorder()
    public var destinationUrl: URL?
    var sound1: AudioPlayer?
    var sound2: AudioPlayer?
    var soundStatus: Bool?
    
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
                   viewportSize: self.bridge!.getWebView()!.frame.size,
                // TODO: z near/far should be configurabe (as described in the WebXR spec)
                zNear: 0.001,
                zFar: 1000
            )
            
            let anchor = self.implementation.placementAnchor
            var anchorPosition = simd_float4(x:0,y:0,z:0,w:0)
            var anchorRotation = simd_quatf(ix:0,iy:0,iz:0,r:1)
            
            if (anchor?.transform != nil) {
                anchorPosition = anchor!.transform.columns.3
                anchorRotation = simd_quatf(anchor!.transform)
            }
            
            self.notifyListeners("poseDataReceived", data: [
                "cameraPositionX": position.x,
                "cameraPositionY": position.y,
                "cameraPositionZ": position.z,
                "cameraRotationX": rotation.imag.x,
                "cameraRotationY": rotation.imag.y,
                "cameraRotationZ": rotation.imag.z,
                "cameraRotationW": rotation.real,
                "cameraProjectionMatrix": simdToArray4x4(projectionMatrix),
                "placed": anchor !== nil,
                "anchorPositionX": anchorPosition.x,
                "anchorPositionY": anchorPosition.y,
                "anchorPositionZ": anchorPosition.z,
                "anchorRotationX": anchorRotation.imag.x,
                "anchorRotationY": anchorRotation.imag.y,
                "anchorRotationZ": anchorRotation.imag.z,
                "anchorRotationW": anchorRotation.real,
            ])
        }
        // Add video view as a subview of the webview
        let videoView = implementation.videoView
        videoView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        videoView.frame = webView!.bounds
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
            DispatchQueue.main.async {
                let pixelDensity = UIScreen.main.scale
                guard let bounds = self.webView?.bounds
                else { return }
                let x = CGFloat(call.getFloat("x")!) / (bounds.width * pixelDensity)
                let y = CGFloat(call.getFloat("y")!) / (bounds.height * pixelDensity)
                self.implementation.handleTap(point: CGPoint(x:y,y:1-x))
            }
            call.resolve(["status": "ok"])
        }
    
    @objc func accessPermission(_ call : CAPPluginCall) {
        call.resolve(["status": "ok"])
    }
    
    @objc func uploadFiles(_ call : CAPPluginCall) {
        let audioPath = call.getString("audioPath") ?? "_"
        if (audioPath == "https://dev-resources.arcmedia.us/23abe470-cad3-11eb-83c9-676c9a4a5b60/aya_something_low.mp4")
        {
            soundStatus = true
        } else {
            soundStatus = false
        }
        
        call.resolve(["status": "ok"])
        
    }
    
    @objc func startRecording(_ call : CAPPluginCall) -> URL? {
        do {
            try
                AVAudioSession.sharedInstance().setCategory(AVAudioSession.Category.playback, options: .mixWithOthers)
            try AVAudioSession.sharedInstance().setActive(true);
            sound1 = try AudioPlayer(fileName: "ohno_jugo.mp3")
            sound2 = try AudioPlayer(fileName: "aya_something.mp3")
            
        } catch {
                print("Sound initialization failed")
            }
        if (soundStatus == true) {
            sound2?.play()
        }else {
            sound1?.play()
        }
       
        let docsUrl = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!

        destinationUrl = docsUrl.appendingPathComponent("ARCvideo.mp4");
        print(destinationUrl)
        videoRec.startRecording(to: destinationUrl,
                                      saveToCameraRoll: false,
                                      errorHandler: { error in
                                        debugPrint("Error when recording \(error)")
                                      })
       
        call.resolve(["status": "ok"])
        return destinationUrl
    }
    
    @objc func stopRecording(_ call : CAPPluginCall) {
        print(destinationUrl)
        videoRec.stoprecording(errorHandler: { error in
          debugPrint("Error when stop recording \(error)")
        })
        if(soundStatus == true) {
            sound2?.fadeOut()
        }else {
            sound1?.fadeOut()
        }
        
        var urlString: String = destinationUrl?.absoluteString ?? "_"
        
        call.resolve(["status": "ok",
                      "filePath": urlString
        ])
        
    }

    @objc func saveVideoTo(_ call : CAPPluginCall) {
        self.saveVideoRequest(errorHandler: { error in
            debugPrint("Error when save Video \(error)")
          })
        call.resolve(["status": "ok"])
    }

    @objc func shareMedia(_ call : CAPPluginCall) {
       
        call.resolve(["status": "ok"])
    }
    
    private func saveVideoRequest(errorHandler: @escaping (Error) -> Void) {
      if PHPhotoLibrary.authorizationStatus() == .authorized {
        self.saveVideo()
      } else {
          PHPhotoLibrary.requestAuthorization({ (status) in
              if status == .authorized {
                self.saveVideo()
              } else {
                errorHandler(WylerError.photoLibraryAccessNotGranted)
            }
          })
      }
    }
    
    private func saveVideo() {
        PHPhotoLibrary.shared().performChanges({
        PHAssetChangeRequest.creationRequestForAssetFromVideo(atFileURL: self.destinationUrl!)
            }) { saved, error in
            if saved {
                    let fetchOptions = PHFetchOptions()
                    fetchOptions.sortDescriptors = [NSSortDescriptor(key: "creationDate", ascending: false)]
                    let fetchResult = PHAsset.fetchAssets(with: .video, options: fetchOptions).firstObject
                }
            }
        
    }
    
}
         
