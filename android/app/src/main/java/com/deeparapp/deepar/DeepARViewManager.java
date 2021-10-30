package com.deeparapp.deepar;

import android.util.Log;
import androidx.annotation.NonNull;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.common.MapBuilder;
import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.Map;

public class DeepARViewManager extends SimpleViewManager<DeepARView> {
    public static final String REACT_CLASS = "DeepARView";
    ReactApplicationContext mCallerContext;

    public DeepARViewManager(ReactApplicationContext reactContext) {
        mCallerContext = reactContext;
    }

    @NonNull
    @Override
    public String getName() {
        return REACT_CLASS;
    }

    @NonNull
    @Override
    protected DeepARView createViewInstance(@NonNull ThemedReactContext reactContext) {
        return new DeepARView(reactContext);
    }

    @Override
    public void receiveCommand(@NotNull DeepARView view, String commandId, @Nullable ReadableArray args) {
        Log.i(REACT_CLASS, "receiveCommand: " + commandId);

        switch (commandId) {
            case "takeScreenshot": {
                view.takeScreenshot();
                break;
            }
            case "startRecording": {
                view.startRecording();
                break;
            }
            case "stopRecording": {
                view.finishRecording();
                break;
            }
            case "resume": {
                view.resume();
                break;
            }
            case "pause": {
                view.pause();
                break;
            }
            case "switchEffect": {
                if (args != null) {
                    view.switchEffect(args.getString(0), "effect");
                }

                break;
            }
            case "switchCamera": {
                view.switchCamera();
                break;
            }
            case "flashOn": {
                view.flashOn();
                break;
            }
            case "flashOff": {
                view.flashOff();
                break;
            }
            case "stop": {
                view.stop();
                break;
            }
            case "start": {
                view.start();
                break;
            }
            case "flashInfo": {
                view.getFlashInfoAvailable();
                break;
            }
        }

        Assertions.assertNotNull(view);
    }

    @Nullable
    @Override
    public Map<String, Object> getExportedCustomDirectEventTypeConstants() {
        return MapBuilder.<String, Object>builder()
                .put("onEventSent", MapBuilder.of("registrationName", "onEventSent"))
                .build();
    }
}
