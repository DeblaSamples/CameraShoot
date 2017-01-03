package com.cocoonshu.example.surfaceviewshoot;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Camera helper
 * @Auther Cocoonshu
 * @Date   2017-01-01 10:59:11
 *
 * @Notice in android.hardware.camera2.CameraCaptureSession
 *   **
 *    * Temporary for migrating to Callback naming
 *    * @hide
 *    *
 *   public static abstract class StateListener extends StateCallback {}
 */

public class CameraHelper {

    private static final String   NAME_CAMERA_HELPER_HANDLER       = "CameraHelperHandler";
    private static final String[] CameraPermissions                = new String[] {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};

    private static final int      REQUEST_CAMERA_PERMISSION        = 0xA000;
    public  static final int      ERR_NO_ERROR                     = 0xB000;
    public  static final int      ERR_NO_INITIALIZE                = 0xB001;
    public  static final int      ERR_BAD_SURFACE                  = 0xB002;
    public  static final int      ERR_NO_ACCESS_TO_OPEN_CAMERA     = 0xB003;
    public  static final int      ERR_CONFIG_CAMERA_SESSION_FAILED = 0xB004;
    public  static final int      ERR_WRONG_STATE                  = 0xB005;
    public  static final int      STATE_OPENING                    = 1;
    public  static final int      STATE_OPEN                       = 2;
    public  static final int      STATE_CLOSING                    = 3;
    public  static final int      STATE_CLOSED                     = 4;

    private HandlerThread                       mCameraStreamHandlerThread = null;
    private Handler                             mCameraStreamHandler       = null;
    private Handler                             mUiHandler                 = null;
    private CameraManager                       mCameraManager             = null;
    private CameraDevice                        mCamera                    = null;
    private CameraCaptureSession                mCameraSession             = null;
    private CaptureRequest.Builder              mPreviewRequest            = null;
    private CaptureRequest.Builder              mCaptureRequest            = null;
    private SurfaceHolder                       mPreviewSurfaceHolder      = null;
    private CameraDevice.StateCallback          mCameraStateListener       = null;
    private CaptureCallback                     mCameraCaptureListener     = null;
    private CameraCaptureSession.StateCallback  mSessionStateListener      = null;
    private OnImageAvailableListener            mImageReaderListener       = null;
    private OnErrorListener                     mOnErrorListener           = null;
    private OnCapturedListener                  mOnCapturedListener        = null;
    private RequestPermissionCallback           mRequestPermissionCallback = null;
    private Size                                mOutputSize                = null;
    private Size                                mSuggestPreviewSize        = null;
    private String                              mCameraId                  = null;
    private int                                 mOperationState            = STATE_CLOSED;

    public interface RequestPermissionCallback {
        boolean onRequestCameraPermission(String[] permission, int requestID);
    }

    public interface OnErrorListener {
        void onErrorOccurred(int error, String errorMessage);
    }

    public interface OnCapturedListener {
        void onCaptured(Bitmap bitmap);
    }

    public CameraHelper(Context context) {
        mCameraManager = (CameraManager) context.getSystemService(Service.CAMERA_SERVICE);
        mUiHandler     = new Handler(Looper.getMainLooper());
        initializeComponentFlows();
    }

    private void startCameraStreamHandler() {
        if (mCameraStreamHandlerThread != null && mCameraStreamHandlerThread.isAlive()) {
            if (mCameraStreamHandler == null) {
                mCameraStreamHandler = new Handler(mCameraStreamHandlerThread.getLooper());
            }
            return;
        }
        mCameraStreamHandlerThread = new HandlerThread(NAME_CAMERA_HELPER_HANDLER);
        mCameraStreamHandlerThread.start();
        mCameraStreamHandler = new Handler(mCameraStreamHandlerThread.getLooper());
    }

    private void stopCameraStreamHandler() {
        if (mCameraStreamHandlerThread != null) {
            mCameraStreamHandlerThread.quitSafely();
            mCameraStreamHandlerThread = null;
        }
        mCameraStreamHandler = null;
    }

    public Size getSuggestPreviewSize() {
        return mSuggestPreviewSize;
    }

    private void fireErrorEvent(final int error) {
        if (mOnErrorListener != null) {
            final String message;
            switch (error) {
                case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                    message = "Camera is in use already";
                    break;
                case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                    message = "Too many camera open";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                    message = "Camera device has been disabled";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                    message = "Camera device has a fatal error";
                    break;
                case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                    message = "Camera service has a fatal error";
                    break;

                case ERR_NO_ERROR:
                    message = "OK";
                    break;
                case ERR_NO_INITIALIZE:
                    message = "Camera helper isn't initialing";
                    break;
                case ERR_BAD_SURFACE:
                    message = "Preview surface is invalid";
                    break;
                case ERR_NO_ACCESS_TO_OPEN_CAMERA:
                    message = "Have no permission to access open";
                    break;
                case ERR_CONFIG_CAMERA_SESSION_FAILED:
                    message = "Config camera session failed, check camera supports";
                    break;
                case ERR_WRONG_STATE:
                    message = "Operation is requested under a wrong state";
                    break;

                default:
                    message = "Unknown error occurred";
                    break;
            }

            if (mUiHandler != null) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mOnErrorListener.onErrorOccurred(error, message);
                    }
                });
            }
        }
    }

    private void initializeComponentFlows() {
        mImageReaderListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                ByteBuffer jpegBuffer   = reader.acquireNextImage().getPlanes()[0].getBuffer();
                byte[]     copiedBuffer = new byte[jpegBuffer.remaining()];
                jpegBuffer.get(copiedBuffer);

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                options.inMutable         = true;

                Bitmap bitmap = BitmapFactory.decodeByteArray(copiedBuffer, 0, copiedBuffer.length, options);
                if (mOnCapturedListener != null) {
                    mOnCapturedListener.onCaptured(bitmap);
                }
            }
        };

        mCameraStateListener = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                mOperationState = STATE_OPEN;
                mCamera = camera;

                try {
                    // Preview surface
                    Surface previewSurface = mPreviewSurfaceHolder.getSurface();
                    mPreviewRequest = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    mPreviewRequest.addTarget(previewSurface);
                    mPreviewRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    startCameraStreamHandler(); // Make sure camera stream handler is alive

                    // Capture surface
                    ImageReader imageReader    = ImageReader.newInstance(mOutputSize.getWidth(), mOutputSize.getHeight(), ImageFormat.JPEG, 1);
                    Surface     captureSurface = imageReader.getSurface();
                    imageReader.setOnImageAvailableListener(mImageReaderListener, mCameraStreamHandler);
                    mCaptureRequest = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                    mCaptureRequest.addTarget(captureSurface);
                    mCaptureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                    // Create session
                    camera.createCaptureSession(Arrays.asList(previewSurface, captureSurface), mSessionStateListener, mCameraStreamHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    fireErrorEvent(ERR_NO_ACCESS_TO_OPEN_CAMERA);
                }
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                stopCameraStreamHandler();
                mOperationState = STATE_CLOSED;
                mCamera = null;

            }

            @Override
            public void onError(CameraDevice camera, int error) {
                mOperationState = STATE_CLOSED;
                fireErrorEvent(error);
            }
        };

        mSessionStateListener = new CameraCaptureSession.StateCallback() {

            @Override
            public void onConfigured(CameraCaptureSession session) {
                if (mCamera == null) {
                    return;
                }
                if (session == null) {
                    return;
                }
                try {
                    session.setRepeatingRequest(mPreviewRequest.build(), mCameraCaptureListener, mCameraStreamHandler);
                    mCameraSession = session;
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                    fireErrorEvent(ERR_NO_ACCESS_TO_OPEN_CAMERA);
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession session) {
                fireErrorEvent(ERR_CONFIG_CAMERA_SESSION_FAILED);
            }

        };

        mCameraCaptureListener = new CaptureCallback() {
            @Override
            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                super.onCaptureStarted(session, request, timestamp, frameNumber);
            }

            @Override
            public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
                super.onCaptureProgressed(session, request, partialResult);
            }

            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
            }

            @Override
            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                super.onCaptureFailed(session, request, failure);
            }

            @Override
            public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
                super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
            }

            @Override
            public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
                super.onCaptureSequenceAborted(session, sequenceId);
            }
        };
    }

    public void capturePictureSync() {
        try {
            if (mCamera == null) {
                return;
            }

            mCameraSession.stopRepeating();
            mCameraSession.capture(mCaptureRequest.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    try {
                        mCameraSession.setRepeatingRequest(mPreviewRequest.build(), mCameraCaptureListener, mCameraStreamHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                        fireErrorEvent(ERR_NO_ACCESS_TO_OPEN_CAMERA);
                    }
                }
            }, null);
            Log.e("CameraHelper", "[capturePictureSync]");
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Find a camera we want:
     *   > Back facing camera
     *   > Fit preview view size
     * @return
     */
    public int setupCamera(int previewWidth, int previewHeight) {
        String[] cameraIds   = null;
        Size     surfaceSize = new Size(previewWidth, previewHeight);
        try {
            cameraIds = mCameraManager.getCameraIdList();
            if (cameraIds != null) {
                for (int i = 0; i < cameraIds.length; i++) {
                    CameraCharacteristics identify = mCameraManager.getCameraCharacteristics(cameraIds[i]);
                    Integer facing = identify.get(CameraCharacteristics.LENS_FACING);
                    if (facing != CameraCharacteristics.LENS_FACING_BACK) {
                        continue;
                    }

                    StreamConfigurationMap configMap = identify.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    if (configMap == null) {
                        continue;
                    } else {
                        Size[] sizes = configMap.getOutputSizes(ImageFormat.JPEG);
                        if (sizes != null && sizes.length > 0) {
                            Size  outputSize = sizes[0];
                            float maxArea    = 0f;
                            for (int j = 0; j < sizes.length; j++) {
                                float area = sizes[j].getWidth() * sizes[j].getHeight();
                                if (maxArea < area) {
                                    maxArea  = area;
                                    outputSize = sizes[j];
                                }
                            }
                            mOutputSize = outputSize;
                        } else {
                            continue;
                        }
                    }

                    float suitableScale = ImageUtils.scaleImage(
                            mOutputSize.getWidth(), mOutputSize.getHeight(),
                            surfaceSize.getWidth(), surfaceSize.getHeight(),
                            ImageUtils.SCALE_MODE_INSIDE);
                    if (suitableScale != 1f) {
                        final int scaledWidth  = (int) (mOutputSize.getWidth() * suitableScale);
                        final int scaledHeight = (int) (mOutputSize.getHeight() * suitableScale);
                        mSuggestPreviewSize = new Size(scaledWidth, scaledHeight);
                    }

                    // If all matched, this code will be touched
                    mCameraId = cameraIds[i];
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            fireErrorEvent(ERR_NO_ACCESS_TO_OPEN_CAMERA);
            return ERR_NO_ACCESS_TO_OPEN_CAMERA;
        }

        return ERR_NO_ERROR;
    }

    public synchronized int startPreview(SurfaceHolder surfaceHolder) {
        if (mOperationState != STATE_CLOSED) {
            mPreviewSurfaceHolder = surfaceHolder;
            return ERR_NO_ERROR;
        }
        if (mCameraManager == null) {
            fireErrorEvent(ERR_NO_INITIALIZE);
            return ERR_NO_INITIALIZE;
        }
        if (surfaceHolder == null) {
            fireErrorEvent(ERR_BAD_SURFACE);
            return ERR_BAD_SURFACE;
        } else {
            mPreviewSurfaceHolder = surfaceHolder;
        }
        if (!requestCameraPermission()) {
            return ERR_NO_ACCESS_TO_OPEN_CAMERA;
        }

        mOperationState = STATE_OPENING;

        try {
            mCameraManager.openCamera(mCameraId, mCameraStateListener, mCameraStreamHandler);
        } catch (CameraAccessException exp) {
            exp.printStackTrace();
            mOperationState = STATE_CLOSED;
            fireErrorEvent(ERR_NO_ACCESS_TO_OPEN_CAMERA);
            return ERR_NO_ACCESS_TO_OPEN_CAMERA;
        } catch (SecurityException exp) {
            exp.printStackTrace();
            mOperationState = STATE_CLOSED;
            fireErrorEvent(ERR_NO_ACCESS_TO_OPEN_CAMERA);
            return ERR_NO_ACCESS_TO_OPEN_CAMERA;
        }

        return ERR_NO_ERROR;
    }

    public synchronized void stopPreview() {
        if (mOperationState == STATE_CLOSING || mOperationState == STATE_CLOSED) {
            return;
        } else {
            mPreviewRequest = null;
            if (mCameraSession != null) {
                mCameraSession.close();
                mCameraSession = null;
            }
            if (mCamera != null) {
                mCamera.close();
                mCamera = null;
            }
        }
    }

    public void setOnCapturedListener(OnCapturedListener listener) {
        mOnCapturedListener = listener;
    }

    public void setOnErrorListener(OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    public void setOnRequestCameraPermissionListener(RequestPermissionCallback listener) {
        mRequestPermissionCallback = listener;
    }

    private boolean requestCameraPermission() {
        if (mRequestPermissionCallback != null) {
            return mRequestPermissionCallback.onRequestCameraPermission(CameraPermissions, REQUEST_CAMERA_PERMISSION);
        } else {
            return false;
        }
    }

    public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        return requestCode == REQUEST_CAMERA_PERMISSION
            && grantResults.length > 0
            && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }
}
