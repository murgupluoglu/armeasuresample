package com.example.mustafa.armeasure_sample;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.blankj.utilcode.constant.PermissionConstants;
import com.blankj.utilcode.util.PermissionUtils;
import com.example.mustafa.armeasure_sample.helpers.DisplayRotationHelper;
import com.example.mustafa.armeasure_sample.helpers.SnackbarHelper;
import com.example.mustafa.armeasure_sample.rendering.BackgroundRenderer;
import com.example.mustafa.armeasure_sample.rendering.PlaneRenderer;
import com.example.mustafa.armeasure_sample.rendering.PointCloudRenderer;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

import java.io.IOException;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();

    private GLSurfaceView surfaceView;
    private DisplayRotationHelper displayRotationHelper;

    private Session session;
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();


    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] anchorMatrix = new float[16];


    // Anchors created from taps used for object placing with a given color.
    private static class ColoredAnchor {
        public final Anchor anchor;
        public final float[] color;

        public ColoredAnchor(Anchor a, float[] color4f) {
            this.anchor = a;
            this.color = color4f;
        }
    }
    private final ArrayList<ColoredAnchor> anchors = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);


        // Set up renderer.
        surfaceView.setPreserveEGLContextOnPause(true);
        surfaceView.setEGLContextClientVersion(2);
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        surfaceView.setRenderer(new GLSurfaceView.Renderer() {
            @Override
            public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
                GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

                // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
                try {
                    // Create the texture and pass it to ARCore session to be filled during update().
                    backgroundRenderer.createOnGlThread(MainActivity.this);
                    planeRenderer.createOnGlThread(MainActivity.this, "models/trigrid.png");
                    pointCloudRenderer.createOnGlThread(MainActivity.this);

                    //virtualObject.createOnGlThread(/*context=*/ this, "models/andy.obj", "models/andy.png");
                    //virtualObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);

                    //virtualObjectShadow.createOnGlThread(this, "models/andy_shadow.obj", "models/andy_shadow.png");
                    //virtualObjectShadow.setBlendMode(BlendMode.Shadow);
                    //virtualObjectShadow.setMaterialProperties(1.0f, 0.0f, 0.0f, 1.0f);

                } catch (IOException e) {
                    Log.e(TAG, "Failed to read an asset file", e);
                }
            }

            @Override
            public void onSurfaceChanged(GL10 unused, int width, int height) {
                displayRotationHelper.onSurfaceChanged(width, height);
                GLES20.glViewport(0, 0, width, height);
            }

            @Override
            public void onDrawFrame(GL10 gl10) {
                // Clear screen to notify driver it should not load any pixels from previous frame.
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

                if (session == null) {
                    return;
                }
                // Notify ARCore session that the view size changed so that the perspective matrix and
                // the video background can be properly adjusted.
                displayRotationHelper.updateSessionIfNeeded(session);

                try {
                    session.setCameraTextureName(backgroundRenderer.getTextureId());

                    // Obtain the current frame from ARSession. When the configuration is set to
                    // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
                    // camera framerate.
                    Frame frame = session.update();
                    Camera camera = frame.getCamera();

                    // Handle one tap per frame.
                    //handleTap(frame, camera);

                    // Draw background.
                    backgroundRenderer.draw(frame);

                    // If not tracking, don't draw 3d objects.
                    if (camera.getTrackingState() == TrackingState.PAUSED) {
                        return;
                    }

                    // Get projection matrix.
                    float[] projmtx = new float[16];
                    camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

                    // Get camera matrix and draw.
                    float[] viewmtx = new float[16];
                    camera.getViewMatrix(viewmtx, 0);

                    // Compute lighting from average intensity of the image.
                    // The first three components are color scaling factors.
                    // The last one is the average pixel intensity in gamma space.
                    final float[] colorCorrectionRgba = new float[4];
                    frame.getLightEstimate().getColorCorrection(colorCorrectionRgba, 0);

                    // Visualize tracked points.
                    PointCloud pointCloud = frame.acquirePointCloud();
                    pointCloudRenderer.update(pointCloud);
                    pointCloudRenderer.draw(viewmtx, projmtx);

                    // Application is responsible for releasing the point cloud resources after
                    // using it.
                    //pointCloud.release();

                    // Check if we detected at least one plane. If so, hide the loading message.
                    if (messageSnackbarHelper.isShowing()) {
                        for (Plane plane : session.getAllTrackables(Plane.class)) {
                            if (plane.getTrackingState() == TrackingState.TRACKING) {
                                messageSnackbarHelper.hide(MainActivity.this);
                                break;
                            }
                        }
                    }

                    // Visualize planes.
                    planeRenderer.drawPlanes(session.getAllTrackables(Plane.class), camera.getDisplayOrientedPose(), projmtx);

                    // Visualize anchors created by touch.
                    float scaleFactor = 1.0f;
                    for (ColoredAnchor coloredAnchor : anchors) {
                        if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
                            continue;
                        }
                        // Get the current pose of an Anchor in world space. The Anchor pose is updated
                        // during calls to session.update() as ARCore refines its estimate of the world.
                        coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);

                        // Update and draw the model and its shadow.
                        //virtualObject.updateModelMatrix(anchorMatrix, scaleFactor);
                        //virtualObjectShadow.updateModelMatrix(anchorMatrix, scaleFactor);
                        //virtualObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
                        //virtualObjectShadow.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);
                    }

                } catch (Throwable t) {
                    // Avoid crashing the application due to unhandled exceptions.
                    Log.e(TAG, "Exception on the OpenGL thread", t);
                }
            }
        });
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }






















    @Override
    protected void onResume() {
        super.onResume();

        if (session == null) {
            Exception exception = null;
            String message = null;
            try {
                // Create the session.
                session = new Session(/* context= */ this);

            } catch (UnavailableArcoreNotInstalledException e) {
                message = "Please install ARCore";
                exception = e;
            } catch (UnavailableApkTooOldException e) {
                message = "Please update ARCore";
                exception = e;
            } catch (UnavailableSdkTooOldException e) {
                message = "Please update this app";
                exception = e;
            }catch (Exception e) {
                message = "Failed to create AR session";
                exception = e;
            }

            if (message != null) {
                messageSnackbarHelper.showError(this, message);
                Log.e(TAG, "Exception creating session", exception);
                return;
            }
        }

        // Note that order matters - see the note in onPause(), the reverse applies here.
        try {
            session.resume();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.");
            session = null;
            return;
        }

        surfaceView.onResume();
        displayRotationHelper.onResume();

        messageSnackbarHelper.showMessage(this, "Searching for surfaces...");
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
}
