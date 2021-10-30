package com.deeparapp.deepar;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;
import androidx.core.content.ContextCompat;

import java.util.Timer;
import java.util.TimerTask;

import ai.deepar.ar.DeepAR;

// https://github.deeparappdev/DeepARSDK/quickstart-android-java
@OptIn(markerClass = androidx.camera.core.ExperimentalUseCaseGroup.class)
public class ARSurfaceProvider implements Preview.SurfaceProvider {
  private static final String tag = ARSurfaceProvider.class.getSimpleName();

  ARSurfaceProvider(Context context, DeepAR deepAR) {
    this.context = context;
    this.deepAR = deepAR;
  }

  private void printEglState() {
    Log.d(tag, "display: " + EGL14.eglGetCurrentDisplay().getNativeHandle() + ", context: " + EGL14.eglGetCurrentContext().getNativeHandle());
  }

  @Override
  public void onSurfaceRequested(@NonNull SurfaceRequest request) {
    Log.d(tag, "Surface requested");
    printEglState();

    // request the external gl texture from deepar
    if(nativeGLTextureHandle == 0) {
      nativeGLTextureHandle = deepAR.getExternalGlTexture();
      Log.d(tag, "request new external GL texture");
      printEglState();
    }

    // if external gl texture could not be provided
    if(nativeGLTextureHandle == 0) {
      request.willNotProvideSurface();
      return;
    }

    // if external GL texture is provided create SurfaceTexture from it
    // and register onFrameAvailable listener to
    Size resolution = request.getResolution();
    if(surfaceTexture == null) {
      surfaceTexture = new SurfaceTexture(nativeGLTextureHandle);
      surfaceTexture.setOnFrameAvailableListener(__ -> {
        if(stop) {
          return;
        }
        surfaceTexture.updateTexImage();
        if(isNotifyDeepar) {
          deepAR.receiveFrameExternalTexture(resolution.getWidth(), resolution.getHeight(), orientation, mirror, nativeGLTextureHandle);
        }
      });
    }
    surfaceTexture.setDefaultBufferSize(resolution.getWidth(), resolution.getHeight());

    if(surface == null) {
      surface = new Surface(surfaceTexture);
    }

    request.setTransformationInfoListener(ContextCompat.getMainExecutor(context), transformationInfo -> orientation = transformationInfo.getRotationDegrees());

    request.provideSurface(surface, ContextCompat.getMainExecutor(context), result -> {
      switch (result.getResultCode()) {
        case SurfaceRequest.Result.RESULT_SURFACE_USED_SUCCESSFULLY:
          Log.i(tag, "RESULT_SURFACE_USED_SUCCESSFULLY");
          break;
        case SurfaceRequest.Result.RESULT_INVALID_SURFACE:
          Log.i(tag, "RESULT_INVALID_SURFACE");
          break;
        case SurfaceRequest.Result.RESULT_REQUEST_CANCELLED:
          Log.i(tag, "RESULT_REQUEST_CANCELLED");
          break;
        case  SurfaceRequest.Result.RESULT_SURFACE_ALREADY_PROVIDED:
          Log.i(tag, "RESULT_SURFACE_ALREADY_PROVIDED");
          break;
        case SurfaceRequest.Result.RESULT_WILL_NOT_PROVIDE_SURFACE:
          Log.i(tag, "RESULT_WILL_NOT_PROVIDE_SURFACE");
          break;
      }
    });
  }

  public boolean isMirror() {
    return mirror;
  }

  public void setMirror(boolean mirror) {
    this.mirror = mirror;
    if(surfaceTexture == null || surface == null) {
      return;
    }

    isNotifyDeepar = false;
    new Timer().schedule(new TimerTask() {
      @Override
      public void run() {
        isNotifyDeepar = true;
      }
    }, 1000);
  }

  public void stop() {
    stop = true;
  }

  public Surface getSurface() {
    return surface;
  }

  private boolean isNotifyDeepar = true;
  private boolean stop = false;
  private boolean mirror = true;
  private int orientation = 0;

  private SurfaceTexture surfaceTexture;
  private Surface surface;
  private int nativeGLTextureHandle = 0;

  private final DeepAR deepAR;
  private final Context context;
}
