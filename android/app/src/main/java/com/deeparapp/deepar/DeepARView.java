package com.deeparapp.deepar;

import ai.deepar.ar.ARErrorType;
import ai.deepar.ar.AREventListener;
import ai.deepar.ar.DeepAR;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.os.Build;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.uimanager.ThemedReactContext;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.deeparapp.MainActivity;
import com.deeparapp.R;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Date;

import ai.deepar.ar.CameraResolutionPreset;
import androidx.core.content.ContextCompat;

import android.util.Size;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.lifecycle.LifecycleOwner;

import static android.Manifest.permission.*;
// https://github.dev/DeepARSDK/quickstart-android-java
public class DeepARView extends FrameLayout {
  private final String TAG = "DeepARView";
  private final String licenseKey = "fd577cb00a6843d19307bc58060fe32b76badc7bb528a0d462ee5e44fa3456d55e103a9f7e93c204";

  private DeepAR deepAr;
  private final SurfaceHolder.Callback surfaceCallback;
  private final AREventListener arEventListener;

  private SurfaceView surface;

  private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
  private Camera camera;
  private CameraService cameraService;
  private ARSurfaceProvider surfaceProvider = null;
  private final int defaultLensFacing = CameraSelector.LENS_FACING_FRONT;
  private int lensFacing = defaultLensFacing;

  public DeepARView(@NonNull Context context) {
    super(context);

    arEventListener = new AREventListener() {
      @Override
      public void screenshotTaken(Bitmap bitmap) {}

      @Override
      public void videoRecordingStarted() {}

      @Override
      public void videoRecordingFinished() {}

      @Override
      public void videoRecordingFailed() {}

      @Override
      public void videoRecordingPrepared() {

      }

      @Override
      public void shutdownFinished() {
        sendEvent("shutdownFinished", "true", "true");
      }

      @Override
      public void initialized() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
          setupCamera();
        } else {
          setupCameraService();
        }

        sendEvent("initialized", "true", "true");
      }

      @Override
      public void faceVisibilityChanged(boolean b) {

      }

      @Override
      public void imageVisibilityChanged(String s, boolean b) {

      }

      @Override
      public void frameAvailable(Image image) {

      }

      @Override
      public void error(ARErrorType arErrorType, String s) {
        sendEvent("error", s, "true");
      }

      @Override
      public void effectSwitched(String s) {
        sendEvent("effectSwitched", s, "true");
      }
    };

    surfaceCallback = new SurfaceHolder.Callback() {
      @Override
      public void surfaceCreated(SurfaceHolder surfaceHolder) {}

      @Override
      public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        if (deepAr != null) {
          deepAr.setRenderSurface(surfaceHolder.getSurface(), width, height);
        }
      }

      @Override
      public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        if (deepAr != null) {
          deepAr.setRenderSurface(null, 0, 0);
        }
      }
    };
  }

  @Override
  protected void onAttachedToWindow() {
    super.onAttachedToWindow();

    View view = inflate(getContext(), R.layout.deeparview, null);
    addView(view);

    init();

    if (getActivity() instanceof MainActivity) {
      MainActivity ma = (MainActivity) getActivity();
      ActivityCompat.requestPermissions(ma, new String[]{READ_EXTERNAL_STORAGE, WRITE_EXTERNAL_STORAGE, CAMERA}, 1);
    }
  }

  private void setupCameraService() {
    Activity application = getActivity();

    if (application == null || cameraService != null || deepAr == null) {
      return;
    }

    CameraManager mCameraManager = (CameraManager) application.getSystemService(Context.CAMERA_SERVICE);
    cameraService = new CameraService(mCameraManager, true, getContext(), getActivity());
    cameraService.setFrameReceiver(deepAr);
    cameraService.openCamera();

    surface.setVisibility(GONE);
    surface.setVisibility(VISIBLE);
  }

  private void setupCamera() {
    cameraProviderFuture = ProcessCameraProvider.getInstance(getContext());
    cameraProviderFuture.addListener(() -> {
      try {
        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
        bindImageAnalysis(cameraProvider);
      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
    }, ContextCompat.getMainExecutor(getContext()));
  }

  private void bindImageAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
    CameraResolutionPreset cameraResolutionPreset = CameraResolutionPreset.P1920x1080;
    int width = cameraResolutionPreset.getWidth();
    int height = cameraResolutionPreset.getHeight();

    Size cameraResolution = new Size(width, height);
    CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();

    Preview preview = new Preview.Builder()
            .setTargetResolution(cameraResolution)
            .build();

    cameraProvider.unbindAll();
    camera = cameraProvider.bindToLifecycle((LifecycleOwner) getCurrentActivity(), cameraSelector, preview);

    if(surfaceProvider == null) {
      surfaceProvider = new ARSurfaceProvider(getContext(), deepAr);
    }

    preview.setSurfaceProvider(surfaceProvider);

    surfaceProvider.setMirror(lensFacing == CameraSelector.LENS_FACING_FRONT);
  }

  @Override
  protected void onDetachedFromWindow() {
    super.onDetachedFromWindow();

    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
      ProcessCameraProvider cameraProvider = null;

      try {
        cameraProvider = cameraProviderFuture.get();
        cameraProvider.unbindAll();
      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }
    }

    if (cameraService != null) {
      cameraService.closeCamera();
      cameraService = null;
    }

    if(surfaceProvider != null) {
      surfaceProvider.stop();
      surfaceProvider = null;
    }
    deepAr.release();
    deepAr = null;
  }

  private void init() {
    if (deepAr == null) {
      deepAr = new DeepAR(getContext());

      surface = findViewById(R.id.surface);
      surface.getHolder().addCallback(surfaceCallback);

      deepAr.setLicenseKey(licenseKey);
      deepAr.setAntialiasingLevel(0);
      deepAr.changeLiveMode(true);

      deepAr.initialize(getContext(), arEventListener);
    }
  }

  public void stop() {
    if (deepAr == null) {
      return;
    }

    deepAr.setAREventListener(null);
    deepAr.release();
    deepAr = null;
  }

  public void getFlashInfoAvailable() {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
      boolean isAvailable = camera.getCameraInfo().hasFlashUnit();
      sendEvent("flashInfo", isAvailable ? "true" : "false", null);
    } else {
      boolean isAvailable = cameraService.getFlashInfoAvailable();
      sendEvent("flashInfo", isAvailable ? "true" : "false", null);
    }
  }

  public void start() {
    if (deepAr == null) {
      init();
    }

    resume();
  }

  public void flashOn() {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
      camera.getCameraControl().enableTorch(true);
    } else {
      cameraService.setFlashOn();
    }
  }

  public void flashOff() {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
      camera.getCameraControl().enableTorch(false);
    } else {
      cameraService.setFlashOff();
    }
  }

  public void pause() {
    if (deepAr == null) {
      return;
    }

    deepAr.setPaused(true);
  }

  public void resume() {
    if (deepAr == null) {
      return;
    }

    deepAr.setPaused(false);
  }

  public void switchCamera() {
    if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
      lensFacing = lensFacing ==  CameraSelector.LENS_FACING_FRONT ?  CameraSelector.LENS_FACING_BACK :  CameraSelector.LENS_FACING_FRONT;
      ProcessCameraProvider cameraProvider = null;

      try {
        cameraProvider = cameraProviderFuture.get();
        cameraProvider.unbindAll();
      } catch (ExecutionException | InterruptedException e) {
        e.printStackTrace();
      }

      sendEvent("cameraSwitch", "true", null);
      setupCamera();
    } else {
      pause();
      cameraService.switchCamera();
      sendEvent("cameraSwitch", "true", null);
      resume();
    }
  }

  public void switchEffect(String path, String slot) {
    if (deepAr == null) {
      return;
    }

    if (slot == null || slot.isEmpty()) {
      slot = "effect";
    }

    if (!path.isEmpty()) {
      deepAr.switchEffect(slot, path);
    }
  }

  public void startRecording() {
  }

  public void takeScreenshot() {
    if (deepAr == null) {
      return;
    }

    new android.os.Handler().postDelayed(() -> deepAr.takeScreenshot(), 100);
  }

  private File createTempFile(String name) {
    File cacheDir = getCacheDir();
    File tempPath = new File(cacheDir, name);

    if (tempPath.exists()) {
      tempPath.delete();
    }

    try {
      tempPath.createNewFile();
    } catch (IOException e) {
      e.printStackTrace();
    }

    return tempPath;
  }

  private File getCacheDir() {
    ContextWrapper cw = new ContextWrapper(getContext().getApplicationContext());
    File cacheDir;

    if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED))
      cacheDir = new File(cw.getCacheDir(), "LazyList");
    else
      cacheDir = cw.getCacheDir();

    if (!cacheDir.exists()) {
      cacheDir.mkdirs();
    }

    return cacheDir;
  }

  public void finishRecording() {
  }

  @Nullable
  private Activity getActivity() {
    Context context = getContext();

    while (context instanceof ContextWrapper) {

      if (context instanceof Activity) {
        return (Activity) context;
      }

      context = ((ContextWrapper) context).getBaseContext();
    }

    return null;
  }

  private Activity getCurrentActivity() {
    return ((ThemedReactContext) getContext()).getReactApplicationContext().getCurrentActivity();
  }

  private void sendEvent(String key, String value, String value2) {
    final Context context = getContext();
    if (context instanceof ReactContext) {
      WritableMap event = new WritableNativeMap();
      event.putString("type", key);
      event.putString("value", value);
      if (value2 != null) {
        event.putString("value2", value2);
      }
      ((ReactContext) context).getJSModule(RCTEventEmitter.class)
              .receiveEvent(getId(),
                      "onEventSent", event);
    }
  }
}
