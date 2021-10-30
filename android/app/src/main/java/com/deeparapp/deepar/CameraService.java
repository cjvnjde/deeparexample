package com.deeparapp.deepar;

import ai.deepar.ar.DeepAR;
import ai.deepar.ar.DeepARImageFormat;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.Surface;
import androidx.core.app.ActivityCompat;

import static android.content.Context.CAMERA_SERVICE;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import android.util.Size;
import android.hardware.camera2.params.StreamConfigurationMap;

public class CameraService {
  public static final String CAMERA_FRONT = "1";
  public static final String CAMERA_BACK = "0";

  private final String TAG = "CameraService";
  private final CameraManager cameraManager;
  private CameraDevice cameraDevice;
  private Handler backgroundHandler;
  private String cameraID;
  private final Context context;
  private DeepAR frameReceiver;
  private CameraCaptureSession captureSession;
  private CamcorderProfile cameracoderProfile;
  private CaptureRequest.Builder builder;
  private HandlerThread backgroundThread;
  private ImageReader imageReader;
  private int cameraOrientation;
  private ByteBuffer buffer;
  private Activity activity;

  public CameraService(CameraManager mCameraManager, boolean isFront, Context mContext, Activity activity) {
    cameraManager = mCameraManager;
    this.activity = activity;

    if (isFront) {
      cameraID = CAMERA_FRONT;
    } else {
      cameraID = CAMERA_BACK;
    }

    setOrientation();

    if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_1080P)) {
      Log.d(TAG, "setCamcorderProfile: 1080");
      cameracoderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
    } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_720P)) {
      Log.d(TAG, "setCamcorderProfile: 720");
      cameracoderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
    } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_480P)) {
      Log.d(TAG, "setCamcorderProfile: 480");
      cameracoderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_480P);
    } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_HIGH)) {
      Log.d(TAG, "setCamcorderProfile: HIGH");
      cameracoderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
    } else if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_LOW)) {
      Log.d(TAG, "setCamcorderProfile: LOW");
      cameracoderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_LOW);
    }

    context = mContext;
  }

  private void setOrientation() {
    android.hardware.Camera.CameraInfo info =
            new android.hardware.Camera.CameraInfo();
    android.hardware.Camera.getCameraInfo(Integer.parseInt(cameraID), info);
    int rotation = activity.getWindowManager().getDefaultDisplay()
            .getRotation();
    int degrees = 0;
    switch (rotation) {
      case Surface.ROTATION_0: degrees = 0; break;
      case Surface.ROTATION_90: degrees = 90; break;
      case Surface.ROTATION_180: degrees = 180; break;
      case Surface.ROTATION_270: degrees = 270; break;
    }
    int result;

    if (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
      result = (info.orientation + degrees) % 360;
      result = (360 - result) % 360;  // compensate the mirror
    } else {  // back-facing
      result = (info.orientation - degrees + 360) % 360;
    }

    Log.d(TAG, "setOrientation: " + result);
    cameraOrientation = result;
  }

  public void startRecording() {
  }

  public void stopRecording() {
  }

  public boolean isOpen() {
    return cameraDevice != null;
  }

  public void setFrameReceiver(DeepAR frameReceiver) {
    this.frameReceiver = frameReceiver;
  }

  @SuppressLint("MissingPermission")
  public void openCamera() {
    try {
      closeCamera();
      startBackgroundThread();

      if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        return;
      }

      if (cameraManager != null) {
        cameraManager.openCamera(cameraID, cameraCallback, backgroundHandler);
      }
    } catch (CameraAccessException e) {
      Log.i(TAG, Objects.requireNonNull(e.getMessage()));
    }
  }

  public void closeCamera() {
    if (isOpen() && cameraDevice != null && captureSession != null) {
      cameraDevice.close();
      captureSession.close();
      imageReader.close();
      cameraDevice = null;
      backgroundHandler.removeCallbacksAndMessages(null);
      backgroundThread.getLooper().quit();
    }
  }

  public boolean getFlashInfoAvailable() {
    try {
      CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraID);
      return characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
    } catch (CameraAccessException e) {
      return false;
    }
  }

  public void setFlashOn() {
    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH);

    try {
      captureSession.setRepeatingRequest(builder.build(), null, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  public void setFlashOff() {
    builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
    builder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);

    try {
      captureSession.setRepeatingRequest(builder.build(), null, null);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }

  public void switchCamera() {
    if (cameraID.equals(CAMERA_FRONT)) {
      cameraID = CAMERA_BACK;

      setOrientation();
      openCamera();

    } else if (cameraID.equals(CAMERA_BACK)) {
      cameraID = CAMERA_FRONT;

      setOrientation();
      openCamera();
    }
  }


  private void startBackgroundThread() {
    backgroundThread = new HandlerThread("CameraBackground");
    backgroundThread.start();
    backgroundHandler = new Handler(backgroundThread.getLooper());
  }

  private final CameraDevice.StateCallback cameraCallback = new CameraDevice.StateCallback() {

    @Override
    public void onOpened(CameraDevice camera) {
      cameraDevice = camera;

      createCameraPreviewSession();
    }

    @Override
    public void onDisconnected(CameraDevice camera) {
      closeCamera();
    }

    @Override
    public void onError(CameraDevice camera, int error) {
      Log.i(TAG, "error! camera id:" + camera.getId() + " error:" + error);
    }
  };

  private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
          = new ImageReader.OnImageAvailableListener() {

    @Override
    public void onImageAvailable(ImageReader reader) {
      Image image;

      try {
        image = reader.acquireLatestImage();
        if (image != null && frameReceiver != null) {
          ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
          ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
          ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

          buffer.put(yBuffer);
          buffer.put(vBuffer);
          buffer.put(uBuffer);

          buffer.position(0);
          frameReceiver.receiveFrame(buffer, image.getWidth(), image.getHeight(), cameraOrientation, cameraID.equals(CAMERA_FRONT), DeepARImageFormat.YUV_420_888, image.getPlanes()[1].getPixelStride());

          image.close();
        }
      } catch (Exception e) {
        Log.w(TAG, Objects.requireNonNull(e.getMessage()));
      }
    }
  };

  private void setUpMediaRecorder() {

  }

  private void createCameraPreviewSession() {
    try {
      buffer = ByteBuffer.allocateDirect(cameracoderProfile.videoFrameWidth * cameracoderProfile.videoFrameHeight * 2);
      buffer.position(0);
      imageReader = ImageReader.newInstance(cameracoderProfile.videoFrameWidth, cameracoderProfile.videoFrameHeight, ImageFormat.YUV_420_888, 1);
      imageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);

      setUpMediaRecorder();

      builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

      Surface imageSurface = imageReader.getSurface();

      builder.addTarget(imageSurface);

      cameraDevice.createCaptureSession(Arrays.asList(imageSurface),
              new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(CameraCaptureSession session) {
                  captureSession = session;
                  try {
                    captureSession.setRepeatingRequest(builder.build(), null, backgroundHandler);
                  } catch (CameraAccessException e) {
                    e.printStackTrace();
                  }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                  Log.d(TAG, "onConfigureFailed");
                }
              }, backgroundHandler);
    } catch (CameraAccessException e) {
      e.printStackTrace();
    }
  }
}
