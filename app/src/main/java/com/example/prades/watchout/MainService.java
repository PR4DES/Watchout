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

    int distance = 0;
    int count = 0;
    boolean detection = false;
    int prevY = 0;
    int prevU = 0;
    int prevV = 0;

    class YUV {
        int Y;
        int U;
        int V;
        YUV() {
            Y=0; U=0; V=0;
        }
        YUV(int y, int u, int v) {
            Y=y; U=u; V=v;
        }
    }
    private YUV[] windows = new YUV[mHeight/10];

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

    private void insertWindow(int y, int u, int v) {
        for(int i=0; i<9; i++) windows[i+1] = windows[i];
        windows[0].Y = y;
        windows[0].U = u;
        windows[0].V = v;
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void imageprocess(Image image) {
        Image.Plane Y = image.getPlanes()[0];
        Image.Plane U = image.getPlanes()[1];
        Image.Plane V = image.getPlanes()[2];

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

        int threshold = 50;
        int cnt = 0;
        int prevdist = 0;
        for(int i=0; i<mHeight/10; i=i+10) {
            int Ysum = 0; int Usum = 0; int Vsum = 0;
            for(int j=0; j<10; j++) {
                Ysum += ybb[i+j] + ybb[i+j+mHeight] + ybb[i+j+2*mHeight];
                Usum += ubb[i+j] + ubb[i+j+mHeight] + ubb[i+j+2*mHeight];
                Vsum += vbb[i+j] + vbb[i+j+mHeight] + vbb[i+j+2*mHeight];
            }
            Ysum = Ysum/30;
            Usum = Usum/30;
            Vsum = Vsum/30;
            if(prevY == 0) {
                prevY = Ysum; prevU = Usum; prevV = Vsum;
            } else {
                if(Math.abs(prevY - Ysum) > threshold || Math.abs(prevU-Usum) > threshold || Math.abs(prevV-Vsum) > threshold) {
                    if(Math.abs((i-prevdist)/cnt - distance/count) < 100) {
                        if(detection)
                            Toast.makeText(getApplicationContext(), "Something is in front of you!", Toast.LENGTH_SHORT).show();
                        } else {
                        detection = true;
                    }
                    distance += (i-prevdist)/cnt;
                    count ++;
                    cnt = 0;
                    prevdist = i;
                }
                prevY = Ysum; prevU = Usum; prevV = Vsum;
                cnt++;
            }
        }
        distance = distance/count;

        Log.d("ABBBB"," ");
    }

    @TargetApi(Build.VERSION_CODES.M)
     class ial implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image img = reader.acquireLatestImage();
            if (img != null) {
                long time= System.currentTimeMillis();
                if(time%2000 < 100) {
                   imageprocess(img);
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
