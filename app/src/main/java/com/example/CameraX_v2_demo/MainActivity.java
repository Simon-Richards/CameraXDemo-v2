package com.example.CameraX_v2_demo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.core.impl.VideoCaptureConfig;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModel;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;

public class MainActivity extends AppCompatActivity {

    private static final int IMMERSIVE_FLAG_TIMEOUT = 500;
    private static final int PERMISSIONS_REQUEST_CODE = 10;
    private static final String[] PERMISSIONS_REQUIRED = new String[] {Manifest.permission.CAMERA};

    private static final double RATIO_4_3_VALUE = 4.0 / 3.0;
    private static final double RATIO_16_9_VALUE = 16.0 / 9.0;


    ConstraintLayout cameraContainer;
    PreviewView viewFinder;

    DisplayManager displayManager;
    DisplayManager.DisplayListener displayListener;
    ImageCapture.OnImageSavedCallback imageSavedListener;
    Executor executor;

    ImageCapture imageCapture;
    VideoCapture videoCapture;
    Preview preview;
    Camera camera;

    int displayId = -1;
    int lensFacing = CameraSelector.LENS_FACING_BACK;
    boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraContainer = findViewById(R.id.camera_container);
        viewFinder = findViewById(R.id.view_finder);

        displayManager = (DisplayManager) viewFinder.getContext().getSystemService(Context.DISPLAY_SERVICE);
        setDisplayListener();
        setImageCaptureListener();
        displayManager.registerDisplayListener(displayListener, null);

        executor = ContextCompat.getMainExecutor(getApplicationContext());

        viewFinder.post(() -> {
                    displayId = viewFinder.getDisplay().getDisplayId();
                    updateCameraUI();
                    bindCameraUseCases();
                }
        );
    }

    private void setDisplayListener() {
        displayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) { }

            @Override
            public void onDisplayRemoved(int displayId) { }

            @Override
            public void onDisplayChanged(int displayId) {
                if (displayId == MainActivity.this.displayId) {
                    imageCapture.setTargetRotation(viewFinder.getDisplay().getRotation());
                }
            }
        };
    }

    private void setImageCaptureListener() {
        imageSavedListener = new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                Toast.makeText(MainActivity.this, "Photo successfully saved", Toast.LENGTH_SHORT).show();
                // TODO Scan photo with MediaScanner
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(MainActivity.this, "An error occurred while saving photo", Toast.LENGTH_SHORT).show();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        viewFinder.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        viewFinder.setSystemUiVisibility(SYSTEM_UI_FLAG_FULLSCREEN);
                    }
                },
                IMMERSIVE_FLAG_TIMEOUT
        );
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS_REQUIRED, PERMISSIONS_REQUEST_CODE);
        } else {
            // Enable Camera
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        displayManager.unregisterDisplayListener(displayListener);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateCameraUI();
    }

    /* ======== PERMISSIONS ======== */

    private boolean hasPermissions() {
        boolean hasPermissions = false;
        for (String permission : PERMISSIONS_REQUIRED) {
            hasPermissions = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
            if (!hasPermissions) break;
        }
        return hasPermissions;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (PackageManager.PERMISSION_GRANTED == grantResults[0]) {

            }
        }
    }

    /* ======== /PERMISSIONS ======== */

    private int aspectRatio(int width, int height) {
        double previewRatio = (double) Math.max(width, height) / Math.min(width, height);
        if (Math.abs(previewRatio - RATIO_4_3_VALUE) <= Math.abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3;
        }
        return AspectRatio.RATIO_16_9;
    }

    private void updateCameraUI() {
        if (cameraContainer.findViewById(R.id.camera_ui_container) != null) {
            cameraContainer.removeView(findViewById(R.id.camera_ui_container));
        }

        View controls = View.inflate(this, R.layout.camera_ui_container, cameraContainer);

        controls.findViewById(R.id.camera_capture_button).setOnClickListener(v -> {
            if (imageCapture != null) {
                File photoFile = createFile();

                ImageCapture.Metadata metadata = new ImageCapture.Metadata();
                metadata.setReversedHorizontal(lensFacing == CameraSelector.LENS_FACING_FRONT);
                ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).setMetadata(metadata).build();
                imageCapture.takePicture(outputFileOptions, executor, imageSavedListener);
            }
        });

        controls.findViewById(R.id.camera_switch_button).setOnClickListener(v -> {
            lensFacing = CameraSelector.LENS_FACING_FRONT == lensFacing ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
            bindCameraUseCases();
        });

        controls.findViewById(R.id.video_record_button).setOnClickListener(v -> {
            //recordVideo();
        });

    }

    /*
    private void recordVideo() {
        File videoFile = createFile();
        if (!isRecording) {
            videoCapture.startRecording(
                    videoFile,
                    executor,
                    new VideoCapture.OnVideoSavedCallback() {
                        @Override
                        public void onVideoSaved(@NonNull File file) {
                            Toast.makeText(MainActivity.this, "Video saved in " + videoFile, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                            Toast.makeText(MainActivity.this, "Video failed to save: " + message, Toast.LENGTH_SHORT).show();
                        }
                    }
            );
        } else {
            videoCapture.stopRecording();
        }
        isRecording = !isRecording;
    }
    */

    private File createFile() {
        return new File(getOutputDirectory(), System.currentTimeMillis() + ".jpg");
    }

    private File getOutputDirectory() {
        File[] externalMediaDir = getApplicationContext().getExternalMediaDirs();
        File mediaDirectory = null;

        if (externalMediaDir != null) {
            mediaDirectory = new File(externalMediaDir[0], this.getResources().getString(R.string.app_name));
        }
        if (mediaDirectory.mkdirs()) {
            return mediaDirectory;
        } else {
            return getApplicationContext().getFilesDir();
        }
    }

    private void bindCameraUseCases() {
        DisplayMetrics metrics = new DisplayMetrics();
        viewFinder.getDisplay().getRealMetrics(metrics);
        int aspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels);

        int rotation = viewFinder.getDisplay().getRotation();

        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(lensFacing).build();
        ListenableFuture cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                ProcessCameraProvider cameraProvider = null;
                try {
                    cameraProvider = (ProcessCameraProvider) cameraProviderFuture.get();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                preview = new Preview.Builder().setTargetAspectRatio(aspectRatio).setTargetRotation(rotation).build();
                preview.setSurfaceProvider(viewFinder.getPreviewSurfaceProvider());

                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetAspectRatio(aspectRatio).setTargetRotation(rotation).build();

                //videoCapture = new VideoCaptureConfig.Builder().setTargetAspectRatio(aspectRatio).setVideoFrameRate(24).setTargetRotation(rotation).build();
                //videoCapture = new VideoCapture(null);

                //Toast.makeText(MainActivity.this, "Is videoCapture null: " + (videoCapture == null), Toast.LENGTH_SHORT).show();
                //Log.d("MainActivity", "Is videoCapture null: " + (videoCapture == null));

                cameraProvider.unbindAll();

                try {
                    camera = cameraProvider.bindToLifecycle((LifecycleOwner) MainActivity.this, cameraSelector, preview, imageCapture);
                } catch(Exception e) {
                    Toast.makeText(MainActivity.this, "ERROR: " + e, Toast.LENGTH_SHORT).show();
                    Log.d("Main Activity", e.toString());
                }

            }
        }, executor);

    }
}
