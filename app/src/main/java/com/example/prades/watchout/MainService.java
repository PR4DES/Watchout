package com.example.prades.watchout;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.StrictMode;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Created by prades on 2017-06-08.
 */

public class MainService extends Service {
    private static final String TAG = "MainService";

    int mWidth = 1280;
    int mHeight = 720;
    int mYSize = mWidth*mHeight;
    int mUVSize = mYSize/4;
    int mFrameSize = mYSize+(mUVSize*2);

    protected CameraDevice cameraDevice;
    protected CameraCaptureSession session;
    protected ImageReader imageReader;

    private int[] Ywindows = new int[10];
    private int[] Uwindows = new int[10];
    private int[] Vwindows = new int[10];

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

    private void insertWindow(int insert, String area) {
        switch (area) {
            case "Y":
                for(int i=0; i<9; i++) Ywindows[i+1] = Ywindows[i];
                Ywindows[0] = insert;
                break;
            case "U":
                for(int i=0; i<9; i++) Uwindows[i+1] = Uwindows[i];
                Uwindows[0] = insert;
                break;
            case "V":
                for(int i=0; i<9; i++) Vwindows[i+1] = Vwindows[i];
                Vwindows[0] = insert;
                break;
        }
    }

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

                    ByteBuffer YBuffer = Y.getBuffer();
                    ByteBuffer UBuffer = U.getBuffer();
                    ByteBuffer VBuffer = V.getBuffer();
                    YBuffer.rewind();
                    UBuffer.rewind();
                    VBuffer.rewind();

                    byte[] ybb = new byte[YBuffer.capacity()];
                    byte[] ubb = new byte[UBuffer.capacity()];
                    byte[] vbb = new byte[VBuffer.capacity()];

                    YBuffer.get(ybb);
                    UBuffer.get(ubb);
                    VBuffer.get(vbb);

                    int Yavgcolor = 0;
                    int Uavgcolor = 0;
                    int Vavgcolor = 0;
                    for(int i=0; i<100; i++) {
                        for(int j=0; j<100; j++) {
                            Yavgcolor += Integer.parseInt(String.valueOf(ybb[mWidth*(1/2 + j) + i]));
                        }
                    }
                    Yavgcolor = Yavgcolor/10000;
                    insertWindow(Yavgcolor, "Y");
                    for(int i=0; i<100; i++) {
                        for(int j=0; j<100; j++) {
                            Uavgcolor += Integer.parseInt(String.valueOf(ubb[mWidth*(1/2 + j) + i]));
                        }
                    }
                    Uavgcolor = Uavgcolor/10000;
                    insertWindow(Uavgcolor,"U");
                    for(int i=0; i<200; i++) {
                        for(int j=0; j<100; j++) {
                            Vavgcolor += Integer.parseInt(String.valueOf(vbb[mWidth*(1/2 + j) + i]));
                        }
                    }
                    Vavgcolor = Vavgcolor/20000;
                    insertWindow(Vavgcolor,"V");

                    if(Math.abs(Ywindows[0]-Ywindows[1]) > 50 && Math.abs(Ywindows[0]-Ywindows[2]) > 50) {
                        Toast.makeText(getApplicationContext(), "different Y?", Toast.LENGTH_SHORT).show();
                    } else if (Math.abs(Uwindows[0]-Uwindows[1]) > 50 && Math.abs(Uwindows[0]-Uwindows[2]) > 50) {
                        Toast.makeText(getApplicationContext(), "different U?", Toast.LENGTH_SHORT).show();
                    } else if (Math.abs(Vwindows[0]-Vwindows[1]) > 50 && Math.abs(Vwindows[0]-Vwindows[2]) > 50) {
                        Toast.makeText(getApplicationContext(), "different V?", Toast.LENGTH_SHORT).show();
                    }

                    Log.d("YBBBB", String.valueOf(Yavgcolor));
                    Log.d("UBBBB", String.valueOf(Uavgcolor));
                    Log.d("VBBBB", String.valueOf(Vavgcolor));
                    Log.d("ABBBB"," ");
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

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Start", "onStartCommand");
        for(int i=0; i<10; i++) Ywindows[i] = 0;
        for(int i=0; i<10; i++) Uwindows[i] = 0;
        for(int i=0; i<10; i++) Vwindows[i] = 0;

        readyCamera();
        return super.onStartCommand(intent, flags, startId);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraDevice.close();
        Toast.makeText(getApplicationContext(), "service now destroyed", Toast.LENGTH_SHORT).show();
        Log.d("Destroy", "onDestroy");
    }
}
