package com.example.customcamera;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.MediaSpec;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Consumer;

import android.Manifest;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    ImageButton capture, toggleflash, flipCamera;
    ExecutorService service;
    Recording recording = null;
    VideoCapture<Recorder> videoCapture = null;
    PreviewView previewView;
    int cameraFacing = CameraSelector.LENS_FACING_BACK;

    private final ActivityResultLauncher<String> activityResultLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    new ActivityResultCallback<Boolean>() {
                        @Override
                        public void onActivityResult(Boolean o) {
                            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                startCamera(cameraFacing);
                            }
                        }
                    }
            );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        capture = findViewById(R.id.capture);
        flipCamera = findViewById(R.id.flip);
        toggleflash = findViewById(R.id.toggleFlash);
        previewView = findViewById(R.id.preview);

        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    activityResultLauncher.launch(Manifest.permission.CAMERA);
                } else if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    activityResultLauncher.launch(Manifest.permission.RECORD_AUDIO);
                } else if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    activityResultLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                } else {
                    captureVideo();
                }
            }
        });

        // flip camera btn
        flipCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cameraFacing == CameraSelector.LENS_FACING_BACK)
                    cameraFacing = CameraSelector.LENS_FACING_FRONT;
                else cameraFacing = CameraSelector.LENS_FACING_BACK;

                startCamera(cameraFacing);
            }
        });

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(Manifest.permission.CAMERA);
        } else {
            startCamera(cameraFacing);
        }


        // service
        service = Executors.newSingleThreadExecutor();
    }

    private void captureVideo() {
        capture.setImageResource(R.drawable.pause_recording_icon);
        Recording recording1 = recording;
        if (recording1 != null) {
            recording1.stop();
            recording1 = null;
            return;
        }

        String name = new SimpleDateFormat("yyy-mm-dd-HH-MM-ss-SSS", Locale.getDefault()).format(System.currentTimeMillis());
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
        contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX_recorder");

        MediaStoreOutputOptions options = new MediaStoreOutputOptions.Builder(getContentResolver(), MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                .setContentValues(contentValues).build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        recording = videoCapture.getOutput().prepareRecording(MainActivity.this, options).withAudioEnabled().start(ContextCompat.getMainExecutor(MainActivity.this),
                new Consumer<VideoRecordEvent>() {
                    @Override
                    public void accept(VideoRecordEvent videoRecordEvent) {
                        if (videoRecordEvent instanceof VideoRecordEvent.Start) {
                            capture.setImageResource(R.drawable.pause_recording_icon);
                        } else if (videoRecordEvent instanceof VideoRecordEvent.Finalize) {
                            if (! ((VideoRecordEvent.Finalize) videoRecordEvent).hasError()) {
                                String msg = "Video Capture Successful";
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                            } else {
                                recording.stop();
                                recording = null;
                                String msg = "Error : " + ((VideoRecordEvent.Finalize) videoRecordEvent).getError();
                                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                            }

                            capture.setImageResource(R.drawable.record_icon);
                        }
                    }
                });
    }


    private void startCamera(int cameraFacing) {
        ListenableFuture<ProcessCameraProvider> processCameraProviderListenableFuture= ProcessCameraProvider.getInstance(MainActivity.this);
        processCameraProviderListenableFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider provider =  processCameraProviderListenableFuture.get();
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    Recorder recorder = new Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                            .build();
                    videoCapture = VideoCapture.withOutput(recorder);

                    provider.unbindAll();

                    CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(cameraFacing).build();

                    Camera camera = provider.bindToLifecycle(MainActivity.this,cameraSelector,preview, videoCapture);

                    toggleflash.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            toggleFlash(camera);
                        }
                    });
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(MainActivity.this));
    }

    private void toggleFlash(Camera camera){
        if (camera.getCameraInfo().hasFlashUnit()){
            if(camera.getCameraInfo().getTorchState().getValue() == 0){
                camera.getCameraControl().enableTorch(true);
                toggleflash.setImageResource(R.drawable.flashlight_off_icon);
            }else{
                camera.getCameraControl().enableTorch(false);
                toggleflash.setImageResource(R.drawable.flash_icon);
            }
        }else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this,"Flash is not Availble ", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        service.shutdown();
    }
}