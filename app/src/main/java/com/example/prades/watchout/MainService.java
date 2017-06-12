package com.example.prades.watchout;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.IBinder;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.nio.Buffer;
import java.util.Arrays;

/**
 * Created by prades on 2017-06-08.
 */

public class MainService extends Service {
    private static final String TAG = "MainService";
    protected static int camchoice;

    int count = 0;
    private boolean isRunning  = false;

    private String cId = "";
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession session;
    protected ImageReader imageReader;
    private static final int REQUEST_CODE_CAMERA = 999;

    @TargetApi(Build.VERSION_CODES.M)
    private void processImage(Image image){
    }

    @TargetApi(Build.VERSION_CODES.M)
    class scb extends CameraDevice.StateCallback {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "CameraDevice.StateCallback onOpened");
            cameraDevice = camera;
            actOnReadyCameraDevice();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.w(TAG, "CameraDevice.StateCallback onDisconnected");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "CameraDevice.StateCallback onError " + error);
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    class ssc extends CameraCaptureSession.StateCallback {

        @Override
        public void onReady(CameraCaptureSession session) {
            MainService.this.session = session;
            try {
                session.setRepeatingRequest(createCaptureRequest(), null, null);
            } catch (CameraAccessException e) {
                Log.e(TAG, e.getMessage());
            }
        }

        @Override
        public void onConfigured(CameraCaptureSession session) {
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
        }
    };

    @TargetApi(Build.VERSION_CODES.M)
     class ial implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireLatestImage();
            Bitmap bitmap = null;
            if (img != null) {
                Image.Plane[] p = img.getPlanes();
                if (p[0].getBuffer() == null) {
                    return;
                }
                int width = img.getWidth();
                int height = img.getHeight();
                int pixelStride = p[0].getPixelStride();
                int rowStride = p[0].getRowStride();
                int rowPadding = rowStride - pixelStride * width;
                byte[] newData = new byte[width * height * 4];

                int offset = 0;
                Buffer buffer = p[0].getBuffer();
                buffer.rewind();
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                //bitmap.copyPixelsFromBuffer(buffer);

                processImage(img);
                img.close();
            }
        }
    };

    protected CameraDevice.StateCallback cameraStateCallback = new scb();
    protected CameraCaptureSession.StateCallback sessionStateCallback = new ssc();
    protected ImageReader.OnImageAvailableListener onImageAvailableListener = new ial();

    @TargetApi(Build.VERSION_CODES.M)
    public void readyCamera() {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            String pickedCamera = getCamera(manager);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(getApplicationContext(), "ready permission denied", Toast.LENGTH_SHORT).show();
                return;
            }
            else {
                manager.openCamera(pickedCamera, cameraStateCallback, null);

                Toast.makeText(getApplicationContext(), "service now start", Toast.LENGTH_SHORT).show();
                imageReader = ImageReader.newInstance(1920, 1088, ImageFormat.JPEG, 2 /* images buffered */);
                imageReader.setOnImageAvailableListener(onImageAvailableListener, null);
            }
        } catch (CameraAccessException e){
            Log.e("READY exception", e.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    public String getCamera(CameraManager manager){
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                int cOrientation = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cOrientation != camchoice) {
                    return cameraId;
                }
            }
        } catch (CameraAccessException e){
            e.printStackTrace();
        }
        return null;
    }

    @TargetApi(Build.VERSION_CODES.M)
    public void actOnReadyCameraDevice()
    {
        try {
            cameraDevice.createCaptureSession(Arrays.asList(imageReader.getSurface()), sessionStateCallback, null);
        } catch (CameraAccessException e){
            Log.e(TAG, e.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    protected CaptureRequest createCaptureRequest() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            builder.addTarget(imageReader.getSurface());
            return builder.build();
        } catch (CameraAccessException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        camchoice = CameraCharacteristics.LENS_FACING_BACK;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Start", "onStartCommand");
        readyCamera();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        Toast.makeText(getApplicationContext(), "service now destroyed", Toast.LENGTH_SHORT).show();
        Log.d("Destroy", "onDestroy");
    }
}
