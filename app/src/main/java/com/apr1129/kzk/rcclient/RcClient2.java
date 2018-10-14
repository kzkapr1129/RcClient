package com.apr1129.kzk.rcclient;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class RcClient2 {

    private static final String TAG = "RcClient2";
    private static final int POLLING_INTERVAL = 100; // ms
    private static final int WHAT_ERROR = 0;
    private static final int WHAT_CONNECTED = 1;

    public interface Listener {
        void onError(Exception e);
        void onConnected();
    }

    private final float SCREEN_HEIGHT;
    private final float SCREEN_WIDTH;
    private final float SCREEN_X_CENTER;
    private final float SCREEN_Y_CENTER;

    private Listener mListener;
    private volatile PointF mLeftTouchPos = new PointF(-1, -1);
    private volatile PointF mRightTouchPos = new PointF(-1, -1);
    private HandlerThread mPoolingHandlerThread;
    private Handler mPoolingHandler;
    private Handler mMainHandler = new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                switch (msg.what) {
                    case WHAT_ERROR:
                        if (mListener != null) mListener.onError((Exception)msg.obj);
                        break;
                    case WHAT_CONNECTED:
                        if (mListener != null) mListener.onConnected();
                        break;
                }
            }
        };

    private Socket mSocket;
    private String mAddress;
    private int mPort;
    private Runnable mPoolingRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    onUpdate();

                    // Post next if didn't occur exception
                    mPoolingHandler.postDelayed(mPoolingRunnable, POLLING_INTERVAL);
                } catch (IOException e) {

                    // inform exception to listener
                    Message errmsg = mMainHandler.obtainMessage(WHAT_ERROR, e);
                    mMainHandler.sendMessage(errmsg);
                }
            }
        };

    public RcClient2(Context context, Listener listener) {
        mListener = listener;

        // Get screen size
        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        Point screenSize = new Point();
        disp.getRealSize(screenSize);

        // Initialize constant values
        SCREEN_WIDTH = screenSize.x;
        SCREEN_HEIGHT = screenSize.y;
        SCREEN_X_CENTER = SCREEN_WIDTH / 2;
        SCREEN_Y_CENTER = SCREEN_HEIGHT / 2;
    }

    public void restart() {
        if (mAddress != null && mAddress != "" && mPort > 0) {
            start(mAddress, mPort);
        }
    }

    public void start(String address, int port) {
        mAddress = address;
        mPort = port;

        mPoolingHandlerThread = new HandlerThread("RcClient2");
        mPoolingHandlerThread.start();

        mPoolingHandler = new Handler(mPoolingHandlerThread.getLooper());
        mPoolingHandler.post(mPoolingRunnable);
    }

    public void stop() {
        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Exception in Socket#close", e);
            }
        }

        if (mPoolingHandlerThread != null) {
            mPoolingHandlerThread.quit();
            mPoolingHandler.removeCallbacks(mPoolingRunnable);
        }
    }

    public void touchEvent(MotionEvent event) {
        int action = event.getAction();
        if (action == MotionEvent.ACTION_UP) {
            mLeftTouchPos.x = -1;
            mLeftTouchPos.y = -1;
            mRightTouchPos.x = -1;
            mRightTouchPos.y = -1;
        } else {

            int count = event.getPointerCount();
            boolean touched_left = false;
            boolean touched_right = false;
            for (int i = 0; i < count; i++) {
                float x = event.getX(i);
                float y = event.getY(i);

                if (x < SCREEN_X_CENTER) {
                    // Left side
                    mLeftTouchPos.x = x;
                    mLeftTouchPos.y = y;
                    touched_left = true;
                } else {
                    // Right side
                    mRightTouchPos.x = x;
                    mRightTouchPos.y = y;
                    touched_right = true;
                }
            }

            if (!touched_left) {
                mLeftTouchPos.x = -1;
                mLeftTouchPos.y = -1;
            }

            if (!touched_right) {
                mRightTouchPos.x = -1;
                mRightTouchPos.y = -1;
            }

        }
    }

    private void onUpdate() throws IOException {
        // Open socket if disconnected
        if (mSocket == null || mSocket.isClosed()) {
            mSocket = new Socket(mAddress, mPort);
            Message msg = mMainHandler.obtainMessage(WHAT_CONNECTED);
            mMainHandler.sendMessage(msg);
        }

        int left_power = 0;
        int right_power = 0;

        if (mLeftTouchPos.x >= 0 && mLeftTouchPos.y >= 0) {
            float dist_left = -(((mLeftTouchPos.y - SCREEN_Y_CENTER) / SCREEN_Y_CENTER) * 1024.f);
            left_power = (int)dist_left;
        }

        if (mRightTouchPos.x >= 0 && mRightTouchPos.y >= 0) {
            float dist_right = -(((mRightTouchPos.y - SCREEN_Y_CENTER) / SCREEN_Y_CENTER) * 1024.f);
            right_power = (int)dist_right;
        }

        OutputStream out = mSocket.getOutputStream();

        String request = String.format("MV %d %d\n", left_power, right_power);
        byte[] bytes = request.getBytes();
        out.write(bytes);
        out.flush();
    }
}
