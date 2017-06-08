package com.example.prades.watchout;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by prades on 2017-06-08.
 */

public class MainService extends Service {
    private static final String TAG = "HelloService";
    int count = 0;
    private boolean isRunning  = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Start", "onStartCommand");
        new Thread(new Runnable() {
            @Override
            public void run() {

                while(isRunning) {
                    Log.d(TAG,String.valueOf(count));
                    count++;
                    try {
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                }

                stopSelf();
            }
        }).start();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;

        Log.d("Destroy", "onDestroy");
    }

}
