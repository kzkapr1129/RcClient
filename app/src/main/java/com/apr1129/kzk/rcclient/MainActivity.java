package com.apr1129.kzk.rcclient;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    private static final String SHARED_PREF_KEY = "rc_client.shared_pref_key";
    private static final String MJPEG_URL_KEY = "mjpeg_url.key";
    private static final String RC_ADDR_KEY = "rc_addr.key";
    private static final String RC_PORT_KEY = "rc_port.key";
    private static final String IMG_SIZE_X_KEY = "img_size_x.key";
    private static final String IMG_SIZE_Y_KEY = "img_size_y.key";
    private static final String TAG = "RcClient_main";

    private RcClient2.Listener mListener = new RcClient2.Listener() {
            @Override
            public void onError(Exception e) {
                Toast.makeText(getApplicationContext(),"Error in RcClient: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onConnected() {
                Toast.makeText(getApplicationContext(), "Connected", Toast.LENGTH_SHORT).show();
            }
        };

    private WebView mCameraView;
    private String mLastUrl;
    private boolean mIsStopping = true;
    private Point mScreenSize = new Point();
    private RcClient2 mClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get screen size
        WindowManager wm = (WindowManager)getSystemService(WINDOW_SERVICE);
        Display disp = wm.getDefaultDisplay();
        disp.getRealSize(mScreenSize);

        // Initialize fields
        mCameraView = findViewById(R.id.camera_view);
        mClient = new RcClient2(getApplicationContext(), mListener);

        // Show Settings dialog
        showSettingsDialog();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        mClient.touchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onResume() {
        super.onResume();
        mCameraView.onResume();
        if (mIsStopping && mLastUrl != null && mLastUrl != "") {
            mCameraView.loadUrl(mLastUrl);
            mIsStopping = false;
        }

        mClient.restart();

        // Setup SystemUI
        stickyImmersiveMode();
    }

    @Override
    public void onPause() {
        mCameraView.stopLoading();
        mCameraView.onPause();
        mIsStopping = true;
        mClient.stop();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mCameraView.destroy();
        super.onDestroy();
    }

    private void stickyImmersiveMode(){
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

        decorView.setOnSystemUiVisibilityChangeListener
                (new View.OnSystemUiVisibilityChangeListener() {
                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        // Note that system bars will only be "visible" if none of the
                        // LOW_PROFILE, HIDE_NAVIGATION, or FULLSCREEN flags are set.
                        if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                            Log.d("debug","The system bars are visible");
                        } else {
                            Log.d("debug","The system bars are NOT visible");
                        }
                    }
                });
    }

    private void showSettingsDialog() {
        final DisplayMetrics dm = getResources().getDisplayMetrics();

        // load unorganized values
        final int padding_lr_value = getResources().getInteger(R.integer.settings_dlg_padding_left_right);
        final int padding_tb_value = getResources().getInteger(R.integer.settings_dlg_padding_top_bottom);
        int paddingLeftRight = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, padding_lr_value, dm);
        int paddingTopBottom = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, padding_tb_value, dm);
        int settings_dlg_title_text_size = getResources().getInteger(R.integer.settings_dlg_title_text_size_sp);

        // setup the custom title
        TextView titleView = new TextView(this);
        titleView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
        titleView.setTextColor(Color.WHITE);
        titleView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleView.setPadding(paddingLeftRight, paddingTopBottom, paddingLeftRight, paddingTopBottom);
        titleView.setText(getResources().getText(R.string.settins));
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, settings_dlg_title_text_size);

        // setup Alert dialog
        LayoutInflater inflater = (LayoutInflater)getSystemService(LAYOUT_INFLATER_SERVICE);
        final View layout = inflater.inflate(R.layout.settings_dialog_layout, null);

        final EditText mjpgUrlEdit = layout.findViewById(R.id.mjpg_url_edit);
        final EditText rcAddrEdit = layout.findViewById(R.id.rc_addr_edit);
        final EditText rcPortEdit = layout.findViewById(R.id.rc_port_edit);
        final EditText imgXSizeEdit = layout.findViewById(R.id.img_size_x_edit);
        final EditText imgYSizeEdit = layout.findViewById(R.id.img_size_y_edit);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCustomTitle(titleView);
        builder.setCancelable(false);
        builder.setPositiveButton(getText(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Get URLs
                String mjpgurl = mjpgUrlEdit.getText().toString();
                String rcaddr = rcAddrEdit.getText().toString();
                int rcport = Integer.valueOf(rcPortEdit.getText().toString());

                // Get size of image
                int img_size_x = Integer.valueOf(imgXSizeEdit.getText().toString());
                int img_size_y = Integer.valueOf(imgYSizeEdit.getText().toString());

                // Update the last value
                final SharedPreferences pref = getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
                SharedPreferences.Editor edit = pref.edit();
                edit.putString(MJPEG_URL_KEY, mjpgurl);
                edit.putString(RC_ADDR_KEY, rcaddr);
                edit.putInt(RC_PORT_KEY, rcport);
                edit.putInt(IMG_SIZE_X_KEY, img_size_x);
                edit.putInt(IMG_SIZE_Y_KEY, img_size_y);
                if (!edit.commit()) {
                    Log.e(TAG, "[showSettingsDialog] failed to Editor#commit");
                }

                // Start camera
                startCameraView(mjpgurl, img_size_x, img_size_y);

                // Start RcClient
                mClient.start(rcaddr, rcport);
            }
        });

        // load last values
        final SharedPreferences pref = getSharedPreferences(SHARED_PREF_KEY, Context.MODE_PRIVATE);
        final String mjpeg_url_ref_val = pref.getString(MJPEG_URL_KEY, "");
        final String rc_addr_ref_val = pref.getString(RC_ADDR_KEY, "");
        final int rc_port_ref_val = pref.getInt(RC_PORT_KEY, 9024);
        final int img_size_x_ref_val = pref.getInt(IMG_SIZE_X_KEY, 640);
        final int img_size_y_ref_val = pref.getInt(IMG_SIZE_Y_KEY, 480);

        // Setup default values
        mjpgUrlEdit.setText(mjpeg_url_ref_val);
        rcAddrEdit.setText(rc_addr_ref_val);
        rcPortEdit.setText(String.valueOf(rc_port_ref_val));
        imgXSizeEdit.setText(String.valueOf(img_size_x_ref_val));
        imgYSizeEdit.setText(String.valueOf(img_size_y_ref_val));
        builder.setView(layout);

        // Show Alert dialog
        builder.create().show();
    }

    private void startCameraView(String url, int img_size_x, int _img_size_y) {
        // load camera view
        WebView cameraView = (WebView)findViewById(R.id.camera_view);
        cameraView.setInitialScale(50);
        cameraView.getSettings().setUseWideViewPort(true);
        cameraView.getSettings().setLoadWithOverviewMode(true);
        cameraView.loadUrl(url);
        mLastUrl = url;
        mIsStopping = false;

        adjCameraView(cameraView, img_size_x, _img_size_y);
    }

    public void adjCameraView(View camView, int img_size_x, int img_size_y) {
        if (img_size_x <= 0 || img_size_y <= 0) {
            Log.e(TAG, "[adjCameraView] invalid param: img_size_x="+img_size_x+
            "img_size_y="+img_size_y);
            return;
        }

        // calc aspect with each size
        float realScreenAspect = (float)mScreenSize.x / (float)mScreenSize.y;
        float imgAspect = (float)img_size_x / (float)img_size_y;

        // init camera view size
        int camViewWidth = mScreenSize.x;
        int camViewHeight = mScreenSize.y;

        // adjust size of camera view
        if (realScreenAspect < imgAspect) {
            camViewHeight = Math.min(
                    mScreenSize.y,
                    (int)Math.floor((double)camViewWidth / (double)imgAspect));
        } else {
            camViewWidth = Math.min(
                    mScreenSize.x,
                    (int)Math.floor((double)camViewHeight * (double)imgAspect));
        }

        // Apply ajusted size to camera view
        LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(camViewWidth, camViewHeight);
        camView.setLayoutParams(param);
    }
}
