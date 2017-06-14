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
import android.os.Environment;
import android.os.IBinder;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Calendar;

/**
 * Created by prades on 2017-06-08.
 */

public class MainService extends Service {
    private static final String TAG = "MainService";

    int count = 0;
    private boolean isRunning  = false;
    int mWidth = 1280;
    int mHeight = 720;
    int mYSize = mWidth*mHeight;
    int mUVSize = mYSize/4;
    int mFrameSize = mYSize+(mUVSize*2);

    private String cId = "";
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession session;
    protected ImageReader imageReader;

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
            if (img != null) {
                long time= System.currentTimeMillis();
                if(time%2000 < 100) {
                    Image.Plane Y = img.getPlanes()[0];
                    Image.Plane U = img.getPlanes()[1];
                    Image.Plane V = img.getPlanes()[2];
                    int yRowStride = Y.getRowStride();
                    int yPixelStride = Y.getPixelStride();
                    int uRowStride = U.getRowStride();
                    int uPixelStride = U.getPixelStride();
                    int vRowStride = V.getRowStride();
                    int vPixelStride = V.getPixelStride();

                    ByteBuffer YBuffer = Y.getBuffer();
                    ByteBuffer UBuffer = U.getBuffer();
                    ByteBuffer VBuffer = V.getBuffer();
                    YBuffer.rewind();
                    UBuffer.rewind();
                    VBuffer.rewind();

                    byte[] ybb;
                    byte[] ubb;
                    byte[] vbb;
                    ybb = new byte[YBuffer.capacity()];
                    ubb = new byte[UBuffer.capacity()];
                    vbb = new byte[VBuffer.capacity()];

                    YBuffer.get(ybb);
                    UBuffer.get(ubb);
                    VBuffer.get(vbb);
                    Log.d("YBBBB", String.valueOf(YBuffer.capacity()));
                    Log.d("UBBBB", String.valueOf(UBuffer.capacity()));
                    Log.d("VBBBB", String.valueOf(VBuffer.capacity()));
                }
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
                imageReader = ImageReader.newInstance(mWidth, mHeight, ImageFormat.YUV_420_888, 2);
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
                if (cOrientation == CameraCharacteristics.LENS_FACING_BACK) {
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
