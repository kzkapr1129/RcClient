package com.apr1129.kzk.rcclient;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class RcClient {

    private static final String TAG = "RcClient";
    private static final int WHAT_MAIN_ERROR = 0;
    private static final int WHAT_MAIN_CONNECTED = 1;

    private static final int WHAT_SUB_CONNECT = 0;
    private static final int WHAT_SUB_TOUCH = 1;

    private volatile boolean mIsConnected = false;

    private HandlerThread mThread;
    private Handler mSubHandler;
    private Handler mMainHandler = new Handler() {
        @Override
        public void dispatchMessage(Message msg) {
            switch (msg.what) {
                case WHAT_MAIN_ERROR:
                    if (mListener != null) {
                        mListener.onError((Exception)msg.obj);
                    }
                    break;
                case WHAT_MAIN_CONNECTED:
                    if (mListener != null) {
                        mListener.onConnected();
                    }
                    mIsConnected = true;
                    break;
            }
        }
    };

    private String mSrvAddress;
    private int mSrvPort;
    private Socket mSocket;
    private Listener mListener;

    private Object LOCK = new Object();

    private volatile int mNumQueueItems = 0;
    private volatile long mLastDispatchedTime = 0;

    private final int DISPLAY_WIDTH;
    private final int DISPLAY_WIDTH_CENTER;
    private final int DISPLAY_HEIGHT;
    private final int DISPLAY_HEIGHT_CENTER;

    public interface Listener {
        void onError(Exception e);
        void onConnected();
    }

    public RcClient(Listener listener, int display_width, int display_height) {
        mListener = listener;
        DISPLAY_WIDTH = display_width;
        DISPLAY_WIDTH_CENTER = DISPLAY_WIDTH / 2;
        DISPLAY_HEIGHT = display_height;
        DISPLAY_HEIGHT_CENTER = DISPLAY_HEIGHT / 2;
    }

    public void start(String address, int port) {
        mSrvAddress = address;
        mSrvPort = port;

        if (mThread != null && mThread.isAlive()) {
            mThread.quit();
        }

        mThread = new HandlerThread("RcClient");
        mThread.start();

        mSubHandler = new Handler(mThread.getLooper()) {
            @Override
            public void dispatchMessage (Message msg) {
                switch (msg.what) {
                    case WHAT_SUB_CONNECT:
                        processConnect(msg);
                        break;

                    case WHAT_SUB_TOUCH:
                        processTouchEvent(msg);
                        break;
                }
            }
        };

        Message msg = mSubHandler.obtainMessage(WHAT_SUB_CONNECT);
        mSubHandler.sendMessage(msg);
    }

    void restart() {
        if (mSrvAddress != null && mSrvAddress != "" && mSrvPort > 0) {
            start(mSrvAddress, mSrvPort);
        }
    }

    public void stop() {
        if (mThread != null) {
            mThread.quit();
        }

        if (mSocket != null) {
            try {
                mSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to close socket", e);
            }

            mIsConnected = false;
        }
    }

    public void touchEvent(MotionEvent event) {
        if (mThread == null || mSubHandler == null || !mIsConnected) {
            return;
        }

        long now = SystemClock.elapsedRealtime();
        if (now - mLastDispatchedTime > 200 && numQueueItems() < 2) {
            incrementNumQueueItems();

            int left_power = 0;
            int right_power = 0;

            int count = event.getPointerCount();
            for (int i = 0; i < count; i++) {
                float x = event.getX(i);
                float y = event.getY(i);

                boolean inLeft = x < DISPLAY_WIDTH_CENTER;
                boolean inTop = y < DISPLAY_HEIGHT_CENTER;

                float dist = Math.abs(DISPLAY_HEIGHT_CENTER - y);
                float power_parcent = dist / (float)DISPLAY_HEIGHT_CENTER;

                if (inLeft) {
                    left_power = (int)Math.floor(1024 * power_parcent);
                } else {
                    right_power = (int)Math.floor(1024 * power_parcent);
                }

            }


            Message msg = mSubHandler.obtainMessage(WHAT_SUB_TOUCH, left_power, right_power);
            mSubHandler.sendMessage(msg);
        }

    }

    private void processTouchEvent(Message msg) {
        try {
            doProcessTouchEvent(msg);
        } catch (Exception e) {
            Message errmsg = mMainHandler.obtainMessage(WHAT_MAIN_ERROR, e);
            mMainHandler.sendMessage(errmsg);
        }

        decrementNumQueueItems();
    }

    private void doProcessTouchEvent(Message msg) throws IOException {
        int left_power = msg.arg1;
        int right_power = msg.arg2;
        Log.d(TAG, "("+left_power+", " + right_power + ")");

        String request = String.format("MV %d %d\n", left_power, right_power);
        byte[] bytes = request.getBytes();

        OutputStream out = mSocket.getOutputStream();
        out.write(bytes);
        out.flush();
    }

    private void processConnect(Message msg) {
        try {
            doProcessConnect(msg);
        } catch (Exception e) {
            Message errmsg = mMainHandler.obtainMessage(WHAT_MAIN_ERROR, e);
            mMainHandler.sendMessage(errmsg);
        }
    }

    private void doProcessConnect(Message msg) throws IOException {
        if (mSocket == null || mSocket.isClosed()) {
            mSocket = new Socket(mSrvAddress, mSrvPort);

            Message connected_msg = mMainHandler.obtainMessage(WHAT_MAIN_CONNECTED);
            mMainHandler.sendMessage(connected_msg);
        }
    }

    private int numQueueItems() {
        int ret = 0;
        synchronized (LOCK) {
            ret = mNumQueueItems;
        }
        return ret;
    }

    private void decrementNumQueueItems() {
        synchronized (LOCK) {
            mNumQueueItems--;
        }
    }

    private void incrementNumQueueItems() {
        synchronized (LOCK) {
            mNumQueueItems++;
        }
    }
}
