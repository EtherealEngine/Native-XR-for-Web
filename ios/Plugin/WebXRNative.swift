import Foundation
import UIKit
import MetalKit
import ARKit
import Capacitor

let LOCK_VIDEO_ROTATION = true

extension CAPBridgeViewController {
    override public func viewWillTransition(to size: CGSize,
                            with coordinator: UIViewControllerTransitionCoordinator) {
        if !LOCK_VIDEO_ROTATION {return}
        super.viewWillTransition(to: size, with: coordinator)
        let webView = self.bridge?.getWebView()
        let videoView = webView!.viewWithTag(42)! as! MTKView
        coordinator.animate { _ in
            let deltaTransform = coordinator.targetTransform
            let deltaAngle = atan2f(Float(deltaTransform.b), Float(deltaTransform.a))
            var currentRotation = atan2f(Float(videoView.transform.b), Float(videoView.transform.a))
            // Adding a small value to the rotation angle forces the animation to occur in a the desired direction, preventing an issue where the view would appear to rotate 2PI radians during a rotation from LandscapeRight -> LandscapeLeft.
            currentRotation += -1 * deltaAngle + 0.0001
            videoView.layer.transform = CATransform3DMakeRotation(CGFloat(currentRotation), 0, 0, 1)
            videoView.frame = CGRect(x: 0,y: 0,width: size.width, height: size.height)
        } completion: { _ in
            // Integralize the transform to undo the extra 0.0001 added to the rotation angle.
            var currentTransform = videoView.transform
            currentTransform.a = round(currentTransform.a)
            currentTransform.b = round(currentTransform.b)
            currentTransform.c = round(currentTransform.c)
            currentTransform.d = round(currentTransform.d)
            videoView.transform = currentTransform
            videoView.frame = CGRect(x: 0,y: 0,width: size.width,height: size.height)
        }
    }
}

extension MTKView : RenderDestinationProvider {}

@available(iOS 13.0, *)
@objc public class WebXRNative: NSObject, MTKViewDelegate, ARSessionDelegate {
    
    private let initialOrientation = UIApplication.shared.statusBarOrientation
    
    public let videoView = MTKView(frame: CGRect(x: 0, y: 0, width: 100, height: 100))
    public var session:ARSession?
    public var renderer:Renderer?
    
    public var placementAnchor:ARAnchor?
    
    public var onUpdate: (() -> Void)?
    
    public override init() {
        super.init()
        videoView.device = MTLCreateSystemDefaultDevice()
        videoView.isOpaque = false
        videoView.backgroundColor = .clear
        videoView.delegate = self
        videoView.tag = 42
    }
    
    public func start() {
        if (session == nil) {
            session = ARSession()
        }
        if (renderer == nil) {
            DispatchQueue.main.sync {
                renderer = Renderer(session: session!, metalDevice: videoView.device!, renderDestination: videoView, initialOrientation: self.initialOrientation)
                renderer!.resizeViewport(size: videoView.bounds.size)
                renderer!.lockRotation = LOCK_VIDEO_ROTATION
                renderer!.onUpdate = {
                    self.onUpdate?()
                }
            }
        }
        let configuration = ARWorldTrackingConfiguration()
        configuration.planeDetection = [.horizontal, .vertical]
        session?.run(configuration, options:[])
    }
    
    public func stop() {
        session?.pause()
    }
    
    public func handleTap(point:CGPoint) {
        guard let query = session?.currentFrame?.raycastQuery(from: point, allowing: .estimatedPlane, alignment: .horizontal)
        else { return }
        guard let result = session?.raycast(query).first
        else { return }
        if (self.placementAnchor != nil) {
            session?.remove(anchor:self.placementAnchor!)
            self.placementAnchor = nil
        }
        let anchor = ARAnchor(transform: result.worldTransform)
        session?.add(anchor: anchor)
        self.placementAnchor = anchor
    }
    
    public func getXRDataForFrame() {
        
    }
    
    // MARK: MTKVIewDelegate methods
    
    public func mtkView(_ view: MTKView, drawableSizeWillChange size: CGSize) {
        DispatchQueue.main.async {
            self.renderer?.resizeViewport(size: size)
        }
    }
    
    public func draw(in view: MTKView) {
        renderer?.update()
    }
    
    // MARK: ARSessionDelegate methods
    
    public func session(_ session: ARSession, didAdd anchors: [ARAnchor]) {
        print(anchors)
    }
    
    public func session(_ session: ARSession, didFailWithError error: Error) {
        self.session = nil
    }
    
    public func sessionWasInterrupted(_ session: ARSession) {}
    
    public func sessionInterruptionEnded(_ session: ARSession) {}
    
}
