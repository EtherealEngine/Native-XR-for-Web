/*
 * Copyright 2017 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.xr3ngine.xr;

import android.app.Fragment;
import android.content.Intent;
import android.media.Image;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.PopupWindow;
import android.widget.Toast;
import android.widget.VideoView;


import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Config.InstantPlacementMode;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.InstantPlacementPoint;
import com.google.ar.core.LightEstimate;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.Point.OrientationMode;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingFailureReason;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.xr3ngine.xr.arcore.helpers.CameraPermissionHelper;
import com.xr3ngine.xr.arcore.helpers.DepthSettings;
import com.xr3ngine.xr.arcore.helpers.DisplayRotationHelper;
import com.xr3ngine.xr.arcore.helpers.InstantPlacementSettings;
import com.xr3ngine.xr.arcore.samplerender.Framebuffer;
import com.xr3ngine.xr.arcore.samplerender.Mesh;
import com.xr3ngine.xr.arcore.samplerender.SampleRender;
import com.xr3ngine.xr.arcore.samplerender.Shader;
import com.xr3ngine.xr.arcore.samplerender.Texture;
import com.xr3ngine.xr.arcore.samplerender.VertexBuffer;
import com.xr3ngine.xr.arcore.samplerender.arcore.BackgroundRenderer;
import com.xr3ngine.xr.arcore.samplerender.arcore.PlaneRenderer;
import com.xr3ngine.xr.watermark.WatermarkManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL;
import static com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore API. The application will display any detected planes and will allow the user to tap on a
 * plane to place a 3D model.
 */
public class ARActivity extends Fragment implements SampleRender.Renderer, View.OnClickListener, WatermarkManager.WMStatus
{

    private static final String TAG = ARActivity.class.getSimpleName();

    private static final String SEARCHING_PLANE_MESSAGE = "Searching for surfaces...";
    private static final String WAITING_FOR_TAP_MESSAGE = "Tap on a surface to place an object.";

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100f;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView surfaceView;

    private boolean installRequested;
    private boolean pauseARSession=false;

    private Session session;
    private DisplayRotationHelper displayRotationHelper;
    private SampleRender render;

    private PlaneRenderer planeRenderer;
    private BackgroundRenderer backgroundRenderer;
    private Framebuffer virtualSceneFramebuffer;
    private boolean hasSetTextureNames = false;

    private PopupWindow popupWindow;

    public int width;
    public int height;
    public int x;
    public int y;

    public void setRect(int x, int y, int width, int height){
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    // Assumed distance from the device camera to the surface on which user will try to place objects.
    // This value affects the apparent scale of objects while the tracking method of the
    // Instant Placement point is SCREENSPACE_WITH_APPROXIMATE_DISTANCE.
    // Values in the [0.2, 2.0] meter range are a good choice for most AR experiences. Use lower
    // values for AR experiences where users are expected to place objects on surfaces close to the
    // camera. Use larger values for experiences where the user will likely be standing and trying to
    // place an object on the ground or floor in front of them.
    private static final float APPROXIMATE_DISTANCE_METERS = 2.0f;

    // Point Cloud
    private VertexBuffer pointCloudVertexBuffer;
    private Mesh pointCloudMesh;
    private Shader pointCloudShader;
    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    private long lastPointCloudTimestamp = 0;

    private String videoFilePath="";
    private String imgFilePath ="";
    private String watermarkedFilePath = "";

    // Virtual object (ARCore pawn)
    private Mesh virtualObjectMesh;
    private Shader virtualObjectShader;
    private final ArrayList<Anchor> anchors = new ArrayList<>();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] modelMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] projectionMatrix = new float[16];
    private final float[] modelViewMatrix = new float[16]; // view x model
    private final float[] modelViewProjectionMatrix = new float[16]; // projection x view x model
    private final float[] viewInverseMatrix = new float[16];
    private final float[] worldLightDirection = {0.0f, 0.0f, 0.0f, 0.0f};
    private final float[] viewLightDirection = new float[4]; // view x world light direction

    private static final String INSUFFICIENT_FEATURES_MESSAGE =
            "Can't find anything. Aim device at a surface with more texture or color.";
    private static final String EXCESSIVE_MOTION_MESSAGE = "Moving too fast. Slow down.";
    private static final String INSUFFICIENT_LIGHT_MESSAGE =
            "Too dark. Try moving to a well-lit area.";
    private static final String BAD_STATE_MESSAGE =
            "Tracking lost due to bad internal state. Please try restarting the AR experience.";
    private static final String CAMERA_UNAVAILABLE_MESSAGE =
            "Another app is using the camera. Tap on this app or try closing the other one.";

    public static String getTrackingFailureReasonString(Camera camera) {
        TrackingFailureReason reason = camera.getTrackingFailureReason();
        switch (reason) {
            case NONE:
                return "";
            case BAD_STATE:
                return BAD_STATE_MESSAGE;
            case INSUFFICIENT_LIGHT:
                return INSUFFICIENT_LIGHT_MESSAGE;
            case EXCESSIVE_MOTION:
                return EXCESSIVE_MOTION_MESSAGE;
            case INSUFFICIENT_FEATURES:
                return INSUFFICIENT_FEATURES_MESSAGE;
            case CAMERA_UNAVAILABLE:
                return CAMERA_UNAVAILABLE_MESSAGE;
        }
        return "Unknown tracking failure reason: " + reason;
    }

    private View view;
    private String appResourcesPackage;


    private final DepthSettings depthSettings = new DepthSettings();
    private boolean[] depthSettingsMenuDialogCheckboxes = new boolean[2];

    private final InstantPlacementSettings instantPlacementSettings = new InstantPlacementSettings();
    private boolean[] instantPlacementSettingsMenuDialogCheckboxes = new boolean[1];

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appResourcesPackage = getActivity().getPackageName();

        // Inflate the layout for this fragment
        view = inflater.inflate(getResources().getIdentifier("xr_activity", "layout", appResourcesPackage), container, false);



        surfaceView = view.findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ getContext());

        // Set up renderer.
        render = new SampleRender(surfaceView, this, getActivity().getAssets());

        installRequested = false;
        return view;
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            // Explicitly close ARCore Session to release native resources.
            // Review the API reference for important considerations before calling close() in apps with
            // more complicated lifecycle requirements:
            // https://developers.google.com/ar/reference/java/arcore/reference/com/google/ar/core/Session#close()
            session.close();
            session = null;
        }

        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                switch (ArCoreApk.getInstance().requestInstall(getActivity(), !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // ARCore requires camera permissions to operate. If we did not yet obtain runtime
                // permission on Android M and above, now is a good time to ask the user for it.
                if (!CameraPermissionHelper.hasCameraPermission(getActivity())) {
                    CameraPermissionHelper.requestCameraPermission(getActivity());
                    return;
                }

                // Create the session.
                session = new Session(/* context= */ getContext());
            } catch (UnavailableArcoreNotInstalledException
                    | UnavailableUserDeclinedInstallationException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            } catch (UnavailableDeviceNotCompatibleException e) {
                message = "This device does not support AR";
                exception = e;
            } catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                // Throw event here
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            configureSession();
            // To record a live camera session for later playback, call
            // `session.startRecording(recorderConfig)` at anytime. To playback a previously recorded AR
            // session instead of using the live camera feed, call
            // `session.setPlaybackDataset(playbackDatasetPath)` before calling `session.resume()`. To
            // learn more about recording and playback, see:
            // https://developers.google.com/ar/develop/java/recording-and-playback
            session.resume();
        } catch (CameraNotAvailableException e) {
            // Throw event here
            Log.e(TAG, "Camera not available. Try restarting the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            surfaceView.onPause();
            session.pause();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(getActivity())) {
            // Use toast instead of snackbar here since the activity will exit.
            Toast.makeText(getContext(), "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(getActivity())) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(getActivity());
            }
            getActivity().finish();
        }
    }

    @Override
    public void onSurfaceCreated(SampleRender render) {
        // Prepare the rendering objects. This involves reading shaders and 3D model files, so may throw
        // an IOException.
        try {
            planeRenderer = new PlaneRenderer(render);
            backgroundRenderer = new BackgroundRenderer(render);
            virtualSceneFramebuffer = new Framebuffer(render, /*width=*/ 1, /*height=*/ 1);

            // Point cloud
            pointCloudShader =
                    Shader.createFromAssets(
                            render, "shaders/point_cloud.vert", "shaders/point_cloud.frag", /*defines=*/ null)
                            .setVec4(
                                    "u_Color", new float[] {31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f})
                            .setFloat("u_PointSize", 5.0f);
            // four entries per vertex: X, Y, Z, confidence
            pointCloudVertexBuffer =
                    new VertexBuffer(render, /*numberOfEntriesPerVertex=*/ 4, /*entries=*/ null);
            final VertexBuffer[] pointCloudVertexBuffers = {pointCloudVertexBuffer};
            pointCloudMesh =
                    new Mesh(
                            render, Mesh.PrimitiveMode.POINTS, /*indexBuffer=*/ null, pointCloudVertexBuffers);

            // Virtual object to render (ARCore pawn)
            Texture virtualObjectAlbedoTexture =
                    Texture.createFromAsset(
                            render,
                            "models/pawn_albedo.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.SRGB);
            Texture virtualObjectPbrTexture =
                    Texture.createFromAsset(
                            render,
                            "models/pawn_roughness_metallic_ao.png",
                            Texture.WrapMode.CLAMP_TO_EDGE,
                            Texture.ColorFormat.LINEAR);
            virtualObjectMesh = Mesh.createFromAsset(render, "models/pawn.obj");
            virtualObjectShader =
                    Shader.createFromAssets(
                            render,
                            "shaders/environmental_hdr.vert",
                            "shaders/environmental_hdr.frag",
                            /*defines=*/ new HashMap<String, String>() {
                            })
                            .setTexture("u_AlbedoTexture", virtualObjectAlbedoTexture)
                            .setTexture("u_RoughnessMetallicAmbientOcclusionTexture", virtualObjectPbrTexture);
        } catch (IOException e) {
            // Put event here
            Log.e(TAG, "Failed to read a required asset file", e);
        }
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        displayRotationHelper.onSurfaceChanged(width, height);
        virtualSceneFramebuffer.resize(width, height);
    }

    Camera camera = null;
    Frame frame = null;
    Pose cameraPose;
    Pose anchorPose;

    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null||pauseARSession)
        {
            return;
        }


        // Texture names should only be set once on a GL thread unless they change. This is done during
        // onDrawFrame rather than onSurfaceCreated since the session is not guaranteed to have been
        // initialized during the execution of onSurfaceCreated.
        if (!hasSetTextureNames) {
            session.setCameraTextureNames(
                    new int[] {backgroundRenderer.getCameraColorTexture().getTextureId()});
            hasSetTextureNames = true;
        }

        // -- Update per-frame state

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        displayRotationHelper.updateSessionIfNeeded(session);

        // Obtain the current frame from ARSession. When the configuration is set to
        // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
        // camera framerate.
        try {
            frame = session.update();
        } catch (CameraNotAvailableException e) {
            Log.e(TAG, "Camera not available during onDrawFrame", e);
            return;
        }
        camera = frame.getCamera();

        // Update BackgroundRenderer state to match the depth settings.
        try {
            backgroundRenderer.setUseDepthVisualization(
                    render, depthSettings.depthColorVisualizationEnabled());
            backgroundRenderer.setUseOcclusion(render, depthSettings.useDepthForOcclusion());
        } catch (IOException e) {
            Log.e(TAG, "Failed to read a required asset file", e);
            return;
        }
        // BackgroundRenderer.updateDisplayGeometry must be called every frame to update the coordinates
        // used to draw the background camera image.
        backgroundRenderer.updateDisplayGeometry(frame);

        if (camera.getTrackingState() == TrackingState.TRACKING
                && (depthSettings.useDepthForOcclusion()
                || depthSettings.depthColorVisualizationEnabled())) {
            try (Image depthImage = frame.acquireDepthImage()) {
                backgroundRenderer.updateCameraDepthTexture(depthImage);
            } catch (NotYetAvailableException e) {
                // This normally means that depth data is not available yet. This is normal so we will not
                // spam the logcat with this.
            }
        }


        // Show a message based on whether tracking has failed, if planes are detected, and if the user
        // has placed any objects.
        String message = null;
        if (camera.getTrackingState() == TrackingState.PAUSED) {
            if (camera.getTrackingFailureReason() == TrackingFailureReason.NONE) {
                message = SEARCHING_PLANE_MESSAGE;
            } else {
                message = getTrackingFailureReasonString(camera);
            }
        } else if (hasTrackingPlane()) {
            if (anchors.isEmpty()) {
                message = WAITING_FOR_TAP_MESSAGE;
            }
        } else {
            message = SEARCHING_PLANE_MESSAGE;
        }
        if (message == null) {

        } else {
//            Log.d(TAG, message);
        }

        // -- Draw background

        if (frame.getTimestamp() != 0) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            backgroundRenderer.drawBackground(render);
        }

        // If not tracking, don't draw 3D objects.
        if (camera.getTrackingState() == TrackingState.PAUSED) {
            return;
        }

        // -- Draw non-occluded virtual objects (planes, point cloud)

        // Get projection matrix.
        camera.getProjectionMatrix(projectionMatrix, 0, Z_NEAR, Z_FAR);

        // Get camera matrix and draw.
        camera.getViewMatrix(viewMatrix, 0);

        cameraPose = camera.getPose();

        // Visualize tracked points.
        // Use try-with-resources to automatically release the point cloud.
        try (PointCloud pointCloud = frame.acquirePointCloud()) {
            if (pointCloud.getTimestamp() > lastPointCloudTimestamp) {
                pointCloudVertexBuffer.set(pointCloud.getPoints());
                lastPointCloudTimestamp = pointCloud.getTimestamp();
            }
            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
            pointCloudShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
            render.draw(pointCloudMesh, pointCloudShader);
        }

        // Visualize planes.
        planeRenderer.drawPlanes(
                render,
                session.getAllTrackables(Plane.class),
                camera.getDisplayOrientedPose(),
                projectionMatrix);

        // -- Draw occluded virtual objects

        // Update lighting parameters in the shader
        updateLightEstimation(frame.getLightEstimate(), viewMatrix);

        // Visualize anchors created by touch.
        render.clear(virtualSceneFramebuffer, 0f, 0f, 0f, 0f);
        for (Anchor anchor : anchors) {
            if (anchor.getTrackingState() != TrackingState.TRACKING) {
                continue;
            }

            anchorPose = anchor.getPose();

//            // Get the current pose of an Anchor in world space. The Anchor pose is updated
//            // during calls to session.update() as ARCore refines its estimate of the world.
//            anchor.getPose().toMatrix(modelMatrix, 0);

//            // Calculate model/view/projection matrices
//            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0);
//            Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0);
//
//            // Update shader properties and draw
//            virtualObjectShader.setMat4("u_ModelView", modelViewMatrix);
//            virtualObjectShader.setMat4("u_ModelViewProjection", modelViewProjectionMatrix);
//
//            render.draw(virtualObjectMesh, virtualObjectShader, virtualSceneFramebuffer);
        }

        // Compose the virtual scene with the background.
        backgroundRenderer.drawVirtualScene(render, virtualSceneFramebuffer, Z_NEAR, Z_FAR);


        if(activity != null){
            if(cameraPose == null){
                Log.e(TAG, "************* Camera pose is null!");
                return;
            }

            Log.d(TAG, "Sending pose data");
            float[] cameraPoseData = new float[7];
            cameraPoseData[0] = cameraPose.tx();
            cameraPoseData[1] = cameraPose.ty();
            cameraPoseData[2] = cameraPose.tz();
            cameraPoseData[3] = cameraPose.qw();
            cameraPoseData[4] = cameraPose.qx();
            cameraPoseData[5] = cameraPose.qy();
            cameraPoseData[6] = cameraPose.qz();

            float[] anchorPoseData = new float[7];

            if(anchorPose != null) {
                anchorPoseData[0] = anchorPose.tx();
                anchorPoseData[1] = anchorPose.ty();
                anchorPoseData[2] = anchorPose.tz();
                anchorPoseData[3] = anchorPose.qw();
                anchorPoseData[4] = anchorPose.qx();
                anchorPoseData[5] = anchorPose.qy();
                anchorPoseData[6] = anchorPose.qz();
            }
            activity.sendPoseData(cameraPoseData, anchorPoseData);
        }
    }

    private XRPlugin activity = null;

    public void clearAnchors(){
        anchors.get(0).detach();
        anchors.remove(0);
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    public void handleTap(XRPlugin activity) {
        this.activity = activity;
        if(camera == null){
            Log.e(TAG, "Camera object is null, couldn't handle tap");
            return;
        }

        CameraIntrinsics ci = camera.getImageIntrinsics();
        float[] focalLength = ci.getFocalLength();
        float[] principalPoint = ci.getPrincipalPoint();
        int[] imageDimensions = ci.getImageDimensions();


        activity.receiveCameraIntrinsics(focalLength, principalPoint, imageDimensions);

        if (camera.getTrackingState() == TrackingState.TRACKING) {
            List<HitResult> hitResultList;
//            if (instantPlacementSettings.isInstantPlacementEnabled()) {
                hitResultList = frame.hitTestInstantPlacement(0, 0, APPROXIMATE_DISTANCE_METERS);
//            } else {
//                hitResultList = frame.hitTest(tap);
//            }
            for (HitResult hit : hitResultList) {
                // If any plane, Oriented Point, or Instant Placement Point was hit, create an anchor.
                Trackable trackable = hit.getTrackable();
                if(trackable.getTrackingState() == TrackingState.TRACKING &&
                        trackable.getTrackingState() != TrackingState.STOPPED &&
                        trackable.getTrackingState() != TrackingState.PAUSED) {
                // If a plane was hit, check that it was hit inside the plane polygon.
                if ((trackable instanceof Plane
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof Point
                        && ((Point) trackable).getOrientationMode()
                        == OrientationMode.ESTIMATED_SURFACE_NORMAL)
                        || (trackable instanceof InstantPlacementPoint)) {
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    if (anchors.size() >= 1) {

                        return;
                    }


                        // Adding an Anchor tells ARCore that it should track this position in
                        // space. This anchor is created on the Plane to place the 3D model
                        // in the correct position relative both to the world and to the plane.

                        anchors.add(hit.createAnchor());
                    }
                    // For devices that support the Depth API, shows a dialog to suggest enabling
                    // depth-based occlusion. This dialog needs to be spawned on the UI thread.
//                    getActivity().runOnUiThread(this::showOcclusionDialogIfNeeded);

                    // Hits are sorted by depth. Consider only closest hit on a plane, Oriented Point, or
                    // Instant Placement Point.
                    break;
                }
            }
        }
    }


    /*
    Pauses the current ARSession and opens a popwindow and play the recorded video
    * */
    void showVideo(String filePathVideo,String filePathImg)
    {
        videoFilePath = filePathVideo;
        imgFilePath = filePathImg;

        String[] tokens = videoFilePath.split("\\.");
        int ind = (int)(Math.random()*100000);
        if(tokens.length>1)
        {
            watermarkedFilePath= tokens[0]+"_"+ind+"."+tokens[1];
        }

        pauseARSession = true;
        session.pause();

        View popupView = LayoutInflater.from(getActivity()).inflate(R.layout.video_viewer_layout, null);
        VideoView videoView = popupView.findViewById(R.id.videoview1);
        Button btnClose =popupView.findViewById(R.id.btn_close_view);
        Button btnShare =popupView.findViewById(R.id.btn_share_view);
        Button btnTrim =popupView.findViewById(R.id.btn_trim_view);



        //Onclicking the close button popwindow will be closed resuming the ARCore session
       /* button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                popupWindow.dismiss();
                pauseARSession =false;
                try
                {
                    session.resume();
                } catch (CameraNotAvailableException e)
                {
                    e.printStackTrace();
                }
            }
        });*/
        btnClose.setOnClickListener(this);
        btnShare.setOnClickListener(this);
        btnTrim.setOnClickListener(this);

        popupWindow = new PopupWindow(popupView, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT);

        popupWindow.showAtLocation(view, Gravity.CENTER,0,0);

        MediaController mediaController= new MediaController(getContext());
        mediaController.setAnchorView(videoView);

        String appName = "Example";
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), appName);
        String uriPath = mediaStorageDir.getPath()+"/test.mp4";
        //specify the location of media file
        Uri uri= Uri.parse(uriPath);

        //Setting MediaController and URI, then starting the videoView
        videoView.setMediaController(mediaController);
        videoView.setVideoURI(uri);
        videoView.requestFocus();
        videoView.start();
    }
//    /**
//     * Shows a pop-up dialog on the first call, determining whether the user wants to enable
//     * depth-based occlusion. The result of this dialog can be retrieved with useDepthForOcclusion().
//     */
//    private void showOcclusionDialogIfNeeded() {
//        boolean isDepthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
//        if (!depthSettings.shouldShowDepthEnableDialog() || !isDepthSupported) {
//            return; // Don't need to show dialog.
//        }
//
//        // Asks the user whether they want to use depth-based occlusion.
//        new AlertDialog.Builder(getContext())
//                .setTitle(R.string.options_title_with_depth)
//                .setMessage(R.string.depth_use_explanation)
//                .setPositiveButton(
//                        R.string.button_text_enable_depth,
//                        (DialogInterface dialog, int which) -> {
//                            depthSettings.setUseDepthForOcclusion(true);
//                        })
//                .setNegativeButton(
//                        R.string.button_text_disable_depth,
//                        (DialogInterface dialog, int which) -> {
//                            depthSettings.setUseDepthForOcclusion(false);
//                        })
//                .show();
//    }


    /** Checks if we detected at least one plane. */
    private boolean hasTrackingPlane() {
        for (Plane plane : session.getAllTrackables(Plane.class)) {
            if (plane.getTrackingState() == TrackingState.TRACKING) {
                return true;
            }
        }
        return false;
    }

    /** Update state based on the current frame's light estimation. */
    private void updateLightEstimation(LightEstimate lightEstimate, float[] viewMatrix) {
        if (lightEstimate.getState() != LightEstimate.State.VALID) {
            virtualObjectShader.setBool("u_LightEstimateIsValid", false);
            return;
        }
        virtualObjectShader.setBool("u_LightEstimateIsValid", true);

        Matrix.invertM(viewInverseMatrix, 0, viewMatrix, 0);
        virtualObjectShader.setMat4("u_ViewInverse", viewInverseMatrix);

        updateMainLight(
                lightEstimate.getEnvironmentalHdrMainLightDirection(),
                lightEstimate.getEnvironmentalHdrMainLightIntensity(),
                viewMatrix);
        updateSphericalHarmonicsCoefficients(
                lightEstimate.getEnvironmentalHdrAmbientSphericalHarmonics());
    }

    private void updateMainLight(float[] direction, float[] intensity, float[] viewMatrix) {
        // We need the direction in a vec4 with 0.0 as the final component to transform it to view space
        worldLightDirection[0] = direction[0];
        worldLightDirection[1] = direction[1];
        worldLightDirection[2] = direction[2];
        Matrix.multiplyMV(viewLightDirection, 0, viewMatrix, 0, worldLightDirection, 0);
        virtualObjectShader.setVec4("u_ViewLightDirection", viewLightDirection);
        virtualObjectShader.setVec3("u_LightIntensity", intensity);
    }

    private void updateSphericalHarmonicsCoefficients(float[] coefficients) {
        // Pre-multiply the spherical harmonics coefficients before passing them to the shader. The
        // constants in sphericalHarmonicFactors were derived from three terms:
        //
        // 1. The normalized spherical harmonics basis functions (y_lm)
        //
        // 2. The lambertian diffuse BRDF factor (1/pi)
        //
        // 3. A <cos> convolution. This is done to so that the resulting function outputs the irradiance
        // of all incoming light over a hemisphere for a given surface normal, which is what the shader
        // (environmental_hdr.frag) expects.
        //
        // You can read more details about the math here:
        // https://google.github.io/filament/Filament.html#annex/sphericalharmonics

        if (coefficients.length != 9 * 3) {
            throw new IllegalArgumentException(
                    "The given coefficients array must be of length 27 (3 components per 9 coefficients");
        }
    }

    /** Configures the session with feature settings. */
    private void configureSession() {
        Config config = session.getConfig();
        config.setLightEstimationMode(Config.LightEstimationMode.ENVIRONMENTAL_HDR);
        if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
            config.setDepthMode(Config.DepthMode.AUTOMATIC);
        } else {
            config.setDepthMode(Config.DepthMode.DISABLED);
        }
//        if (instantPlacementSettings.isInstantPlacementEnabled()) {
            config.setInstantPlacementMode(InstantPlacementMode.LOCAL_Y_UP);
//        } else {
//            config.setInstantPlacementMode(InstantPlacementMode.DISABLED);
//        }
        session.configure(config);
    }


    @Override
    public void onClick(View v)
    {
        int id = v.getId();

        if (id == R.id.btn_share_view)
        {
            WatermarkManager.wmStatusInterface= this;
            WatermarkManager.addWatermark(videoFilePath,watermarkedFilePath,imgFilePath,true);

        }
        else if (id == R.id.btn_trim_view)
        {
            WatermarkManager.wmStatusInterface= this;
            String[] tokens = videoFilePath.split("\\.");
            int ind = (int)(Math.random()*100000);
            String trimmedFilePath="";
            if(tokens.length>1)
            {
                trimmedFilePath= tokens[0]+"_t_"+ind+"."+tokens[1];
            }
            WatermarkManager.trimVideo(videoFilePath,trimmedFilePath,0,0,0,2,true);
        }
        else if (id == R.id.btn_close_view)
        {
            popupWindow.dismiss();
            pauseARSession =false;
            try
            {
                session.resume();
            } catch (CameraNotAvailableException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void watermarkStatus(int code)
    {
        if(code == RETURN_CODE_SUCCESS)
        {
            // watermark addition succeeded
            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.setType("video/mp4");
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Video");
            sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(watermarkedFilePath));
            sendIntent.putExtra(Intent.EXTRA_TEXT, "Enjoy the Video");
            startActivity(Intent.createChooser(sendIntent, "Email:"));
        }
        else if(code == RETURN_CODE_CANCEL)
        {
            // watermark addition cancelled
        }
        else
        {
            // watermark addtion failed with code
        }
    }

    @Override
    public void trimStatus(int code)
    {

    }
}
