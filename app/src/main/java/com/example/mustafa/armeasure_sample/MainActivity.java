package com.example.mustafa.armeasure_sample;

import android.annotation.SuppressLint;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import com.example.mustafa.armeasure_sample.helpers.DisplayRotationHelper;
import com.example.mustafa.armeasure_sample.helpers.SnackbarHelper;
import com.example.mustafa.armeasure_sample.helpers.TapHelper;
import com.example.mustafa.armeasure_sample.rendering.BackgroundRenderer;
import com.example.mustafa.armeasure_sample.rendering.ObjectRenderer;
import com.example.mustafa.armeasure_sample.rendering.PlaneRenderer;
import com.example.mustafa.armeasure_sample.rendering.PointCloudRenderer;
import com.example.mustafa.armeasure_sample.rendering.ShaderUtil;
import com.google.ar.core.Anchor;
import com.google.ar.core.Camera;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Point;
import com.google.ar.core.PointCloud;
import com.google.ar.core.Session;
import com.google.ar.core.Trackable;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;

import java.io.IOException;
import java.util.ArrayList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private static final float[] DEFAULT_COLOR = ShaderUtil.hexToColor("#000000");
    private static final float[] BLUE_COLOR = new float[] {66.0f, 133.0f, 244.0f, 255.0f};
    private static final float[] GREEN_COLOR = new float[] {139.0f, 195.0f, 74.0f, 255.0f};


    private GLSurfaceView surfaceView;

    private DisplayRotationHelper displayRotationHelper;
    private TapHelper tapHelper;
    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();

    private Session session;
    private final BackgroundRenderer backgroundRenderer = new BackgroundRenderer();
    private final PlaneRenderer planeRenderer = new PlaneRenderer();
    private final PointCloudRenderer pointCloudRenderer = new PointCloudRenderer();

    private final ObjectRenderer touchPlaceObject = new ObjectRenderer();


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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.surfaceview);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);
        tapHelper = new TapHelper(/*context=*/ this);


        // Set up renderer.
        surfaceView.setOnTouchListener(tapHelper);
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

                    touchPlaceObject.createOnGlThread(MainActivity.this, "models/andy.obj", "models/andy.png");
                    //touchPlaceObject.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f);


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
                    handleTap(frame, camera);

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
                    float scaleFactor = 0.1f;
                    for (ColoredAnchor coloredAnchor : anchors) {
                        if (coloredAnchor.anchor.getTrackingState() != TrackingState.TRACKING) {
                            continue;
                        }
                        // Get the current pose of an Anchor in world space. The Anchor pose is updated
                        // during calls to session.update() as ARCore refines its estimate of the world.
                        coloredAnchor.anchor.getPose().toMatrix(anchorMatrix, 0);

                        // Update and draw the model and its shadow.
                        touchPlaceObject.updateModelMatrix(anchorMatrix, scaleFactor);
                        touchPlaceObject.draw(viewmtx, projmtx, colorCorrectionRgba, coloredAnchor.color);

                    }

                } catch (Throwable t) {
                    // Avoid crashing the application due to unhandled exceptions.
                    Log.e(TAG, "Exception on the OpenGL thread", t);
                }
            }
        });
        surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    // Handle only one tap per frame, as taps are usually low frequency compared to frame rate.
    private void handleTap(Frame frame, Camera camera) {
        MotionEvent tap = tapHelper.poll();
        if (tap != null && camera.getTrackingState() == TrackingState.TRACKING) {
            for (HitResult hit : frame.hitTest(tap)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon
                Trackable trackable = hit.getTrackable();
                // Creates an anchor if a plane or an oriented point was hit.
                if ((trackable instanceof Plane
                        && ((Plane) trackable).isPoseInPolygon(hit.getHitPose())
                        && (PlaneRenderer.calculateDistanceToPlane(hit.getHitPose(), camera.getPose()) > 0))
                        || (trackable instanceof Point
                        && ((Point) trackable).getOrientationMode()
                        == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL)
                        && anchors.size() <= 20) {//if points bigger than 20 we are not allow more points

                    // Hits are sorted by depth. Consider only closest hit on a plane or oriented point.
                    // Cap the number of objects created. This avoids overloading both the
                    // rendering system and ARCore.
                    //if (anchors.size() >= 20) {
                    //    anchors.get(0).anchor.detach();
                    //    anchors.remove(0);
                    //}

                    // Assign a color to the object for rendering based on the trackable type
                    // this anchor attached to. For AR_TRACKABLE_POINT, it's blue color, and
                    // for AR_TRACKABLE_PLANE, it's green color.
                    float[] objColor;
                    if (trackable instanceof Point) {
                        objColor = BLUE_COLOR;
                    } else if (trackable instanceof Plane) {
                        objColor = GREEN_COLOR;
                    } else {
                        objColor = DEFAULT_COLOR;
                    }

                    // Adding an Anchor tells ARCore that it should track this position in
                    // space. This anchor is created on the Plane to place the 3D model
                    // in the correct position relative both to the world and to the plane.
                    anchors.add(new ColoredAnchor(hit.createAnchor(), objColor));
                    break;
                }
            }
        }
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
