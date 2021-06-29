//
//  Renderer.swift
//  WebXRNative
//

import MetalKit
import ARKit

protocol RenderDestinationProvider {
    var currentRenderPassDescriptor: MTLRenderPassDescriptor? { get }
    var currentDrawable: CAMetalDrawable? { get }
    var colorPixelFormat: MTLPixelFormat { get set }
    var depthStencilPixelFormat: MTLPixelFormat { get set }
    var sampleCount: Int { get set }
}

let maxBuffersInFlight: Int = 1

public class Renderer {
    
    private let session: ARSession
    private let device: MTLDevice
    private let inFlightSemaphore = DispatchSemaphore(value: maxBuffersInFlight)
    private var renderDestination: RenderDestinationProvider
    private let initialOrientation: UIInterfaceOrientation
    
    // Metal objects
    private var commandQueue: MTLCommandQueue!
    private var sharedUniformBuffer: MTLBuffer!
    private var imagePlaneVertexBuffer: MTLBuffer!
    private var capturedImagePipelineState: MTLRenderPipelineState!
    private var capturedImageDepthState: MTLDepthStencilState!
    private var capturedImageTextureY: MTLTexture!
    private var capturedImageTextureCbCr: MTLTexture!
    private var capturedImageTextureCache: CVMetalTextureCache!
    private var uniformBufferIndex: Int = 0
    private var sharedUniformBufferOffset: Int = 0
    private var sharedUniformBufferAddress: UnsafeMutableRawPointer!
    
    // Viewport configuration
    private var viewportSize: CGSize = CGSize()
    private var viewportSizeDidChange: Bool = true
    private var viewportOrientation: UIInterfaceOrientation = .landscapeRight
    
    private struct SharedUniforms {
        var projectionMatrix: matrix_float4x4
        var viewMatrix: matrix_float4x4
        var ambientLightColor: vector_float3
        var directionalLightDirection: vector_float3
        var directionalLightColor: vector_float3
        var materialShininess: Float
    }
    
    private struct InstanceUniforms {
        var modelMatrix: matrix_float4x4
    }
    
    private let alignedSharedUniformSize = (MemoryLayout<SharedUniforms>.size & ~0xFF) + 0x100
    private let planeVertexData: [Float] = [-1, -1,  0,  1,
                                     1, -1,  1,  1,
                                    -1,  1,  0,  0,
                                     1,  1,  1,  0]
    
    public var lockRotation = false
    public var onUpdate: (() -> Void)?
    
    init(session: ARSession, metalDevice device: MTLDevice, renderDestination: RenderDestinationProvider, initialOrientation:UIInterfaceOrientation) {
        self.session = session
        self.device = device
        self.renderDestination = renderDestination
        self.initialOrientation = initialOrientation
        do { try setupPipeline() }
        catch let error { (print("Failed to setup Metal pipeline, error \(error)") ) }
    }
    
    private func setupPipeline() throws {
        renderDestination.depthStencilPixelFormat = .depth32Float_stencil8
        renderDestination.colorPixelFormat = .bgra8Unorm
        renderDestination.sampleCount = 1
        let sharedUniformBufferSize = alignedSharedUniformSize * maxBuffersInFlight
        sharedUniformBuffer = device.makeBuffer(length: sharedUniformBufferSize, options: .storageModeShared)
        let imagePlaneVertexDataCount = planeVertexData.count * MemoryLayout<Float>.size
        imagePlaneVertexBuffer = device.makeBuffer(bytes: planeVertexData, length: imagePlaneVertexDataCount, options: [])
        let defaultLibrary = try device.makeDefaultLibrary(bundle: Bundle.init(for: Renderer.self))
        let capturedImageVertexFunction = defaultLibrary.makeFunction(name: "capturedImageVertexTransform")!
        let capturedImageFragmentFunction = defaultLibrary.makeFunction(name: "capturedImageFragmentShader")!
        let imagePlaneVertexDescriptor = MTLVertexDescriptor()
        imagePlaneVertexDescriptor.attributes[0].format = .float2
        imagePlaneVertexDescriptor.attributes[0].offset = 0
        imagePlaneVertexDescriptor.attributes[0].bufferIndex = 0
        imagePlaneVertexDescriptor.attributes[1].format = .float2
        imagePlaneVertexDescriptor.attributes[1].offset = 8
        imagePlaneVertexDescriptor.attributes[1].bufferIndex = 0
        imagePlaneVertexDescriptor.layouts[0].stride = 16
        imagePlaneVertexDescriptor.layouts[0].stepRate = 1
        imagePlaneVertexDescriptor.layouts[0].stepFunction = .perVertex
        let capturedImagePipelineStateDescriptor = MTLRenderPipelineDescriptor()
        capturedImagePipelineStateDescriptor.label = "MyCapturedImagePipeline"
        capturedImagePipelineStateDescriptor.sampleCount = renderDestination.sampleCount
        capturedImagePipelineStateDescriptor.vertexFunction = capturedImageVertexFunction
        capturedImagePipelineStateDescriptor.fragmentFunction = capturedImageFragmentFunction
        capturedImagePipelineStateDescriptor.vertexDescriptor = imagePlaneVertexDescriptor
        capturedImagePipelineStateDescriptor.colorAttachments[0].pixelFormat = renderDestination.colorPixelFormat
        capturedImagePipelineStateDescriptor.depthAttachmentPixelFormat = renderDestination.depthStencilPixelFormat
        capturedImagePipelineStateDescriptor.stencilAttachmentPixelFormat = renderDestination.depthStencilPixelFormat
        do { try capturedImagePipelineState = device.makeRenderPipelineState(descriptor: capturedImagePipelineStateDescriptor) }
        catch let error { print("Failed to created captured image pipeline state, error \(error)") }
        let capturedImageDepthStateDescriptor = MTLDepthStencilDescriptor()
        capturedImageDepthStateDescriptor.depthCompareFunction = .always
        capturedImageDepthStateDescriptor.isDepthWriteEnabled = false
        capturedImageDepthState = device.makeDepthStencilState(descriptor: capturedImageDepthStateDescriptor)
        var textureCache: CVMetalTextureCache?
        CVMetalTextureCacheCreate(nil, nil, device, nil, &textureCache)
        capturedImageTextureCache = textureCache
        commandQueue = device.makeCommandQueue()
    }
    
    public func resizeViewport(size: CGSize) {
        DispatchQueue.main.async {
            self.viewportSizeDidChange = true
            self.viewportSize = size
            self.viewportOrientation = self.lockRotation ?
                self.initialOrientation : UIApplication.shared.statusBarOrientation
        }
    }
    
    public func update() {
        let _ = inFlightSemaphore.wait(timeout: DispatchTime.distantFuture)
        self.onUpdate?()
        guard let commandBuffer = commandQueue.makeCommandBuffer() else { return }
        commandBuffer.addCompletedHandler{ [weak self] commandBuffer in
            if let strongSelf = self {
                strongSelf.inFlightSemaphore.signal()
            }
            return
        }
        updateBufferStates()
        updateFrameState()
        guard let passDescriptor = renderDestination.currentRenderPassDescriptor,
            let drawable = renderDestination.currentDrawable else { return }
        guard let renderEncoder = commandBuffer.makeRenderCommandEncoder(descriptor: passDescriptor) else { return }
        drawCapturedImage(renderEncoder: renderEncoder)
        renderEncoder.endEncoding()
        commandBuffer.present(drawable)
        commandBuffer.commit()
    }
    
    private func updateBufferStates() {
        uniformBufferIndex = (uniformBufferIndex + 1) % maxBuffersInFlight
        sharedUniformBufferOffset = alignedSharedUniformSize * uniformBufferIndex
        sharedUniformBufferAddress = sharedUniformBuffer.contents().advanced(by: sharedUniformBufferOffset)
    }
    
    private func updateFrameState() {
        guard let currentFrame = session.currentFrame else { return }
        updateSharedUniforms(frame: currentFrame)
        updateCapturedImageTextures(frame: currentFrame)
        if viewportSizeDidChange {
            viewportSizeDidChange = false
            updateImagePlane(frame: currentFrame)
        }
    }
    
    private func updateSharedUniforms(frame: ARFrame) {
        let uniforms = sharedUniformBufferAddress.assumingMemoryBound(to: SharedUniforms.self)
        uniforms.pointee.viewMatrix = frame.camera.transform.inverse
        uniforms.pointee.projectionMatrix = frame.camera.projectionMatrix(for: viewportOrientation, viewportSize: viewportSize, zNear: 0.001, zFar: 1000)
        var ambientIntensity: Float = 1.0
        if let lightEstimate = frame.lightEstimate {
            ambientIntensity = Float(lightEstimate.ambientIntensity) / 1000.0
        }
        let ambientLightColor: vector_float3 = vector3(0.5, 0.5, 0.5)
        uniforms.pointee.ambientLightColor = ambientLightColor * ambientIntensity
        var directionalLightDirection : vector_float3 = vector3(0.0, 0.0, -1.0)
        directionalLightDirection = simd_normalize(directionalLightDirection)
        uniforms.pointee.directionalLightDirection = directionalLightDirection
        let directionalLightColor: vector_float3 = vector3(0.6, 0.6, 0.6)
        uniforms.pointee.directionalLightColor = directionalLightColor * ambientIntensity
        uniforms.pointee.materialShininess = 30
    }
    
    private func updateCapturedImageTextures(frame: ARFrame) {
        let pixelBuffer = frame.capturedImage
        if (CVPixelBufferGetPlaneCount(pixelBuffer) < 2) { return }
        capturedImageTextureY = createTexture(fromPixelBuffer: pixelBuffer, pixelFormat:.r8Unorm, planeIndex:0)!
        capturedImageTextureCbCr = createTexture(fromPixelBuffer: pixelBuffer, pixelFormat:.rg8Unorm, planeIndex:1)!
    }
    
    private func createTexture(fromPixelBuffer pixelBuffer: CVPixelBuffer, pixelFormat: MTLPixelFormat, planeIndex: Int) -> MTLTexture? {
        var mtlTexture: MTLTexture? = nil
        let width = CVPixelBufferGetWidthOfPlane(pixelBuffer, planeIndex)
        let height = CVPixelBufferGetHeightOfPlane(pixelBuffer, planeIndex)
        var texture: CVMetalTexture? = nil
        let status = CVMetalTextureCacheCreateTextureFromImage(nil, capturedImageTextureCache, pixelBuffer, nil, pixelFormat, width, height, planeIndex, &texture)
        if status == kCVReturnSuccess { mtlTexture = CVMetalTextureGetTexture(texture!) }
        return mtlTexture
    }
    
    private func updateImagePlane(frame: ARFrame) {
        let displayToCameraTransform = frame.displayTransform(for: viewportOrientation, viewportSize: viewportSize).inverted()
        let vertexData = imagePlaneVertexBuffer.contents().assumingMemoryBound(to: Float.self)
        for index in 0...3 {
            let textureCoordIndex = 4 * index + 2
            let textureCoord = CGPoint(x: CGFloat(planeVertexData[textureCoordIndex]), y: CGFloat(planeVertexData[textureCoordIndex + 1]))
            let transformedCoord = textureCoord.applying(displayToCameraTransform)
            vertexData[textureCoordIndex] = Float(transformedCoord.x)
            vertexData[textureCoordIndex + 1] = Float(transformedCoord.y)
        }
    }
    
    private func drawCapturedImage(renderEncoder: MTLRenderCommandEncoder) {
        guard capturedImageTextureY != nil && capturedImageTextureCbCr != nil else { return }
        renderEncoder.pushDebugGroup("DrawCapturedImage")
        renderEncoder.setCullMode(.none)
        renderEncoder.setRenderPipelineState(capturedImagePipelineState)
        renderEncoder.setDepthStencilState(capturedImageDepthState)
        renderEncoder.setVertexBuffer(imagePlaneVertexBuffer, offset: 0, index: 0)
        renderEncoder.setFragmentTexture(capturedImageTextureY, index: Int(kTextureIndexY.rawValue))
        renderEncoder.setFragmentTexture(capturedImageTextureCbCr, index: Int(kTextureIndexCbCr.rawValue))
        renderEncoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4)
        renderEncoder.popDebugGroup()
    }
    
}
