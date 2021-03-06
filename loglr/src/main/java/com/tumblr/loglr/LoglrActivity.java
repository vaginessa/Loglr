package com.tumblr.loglr;

import android.Manifest;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.webkit.WebView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.tumblr.loglr.Exceptions.LoglrAPIException;
import com.tumblr.loglr.Exceptions.LoglrCallbackException;
import com.tumblr.loglr.Exceptions.LoglrLoginCanceled;
import com.tumblr.loglr.Exceptions.LoglrLoginException;
import com.tumblr.loglr.Interfaces.DialogCallbackListener;

public class LoglrActivity extends AppCompatActivity implements DialogCallbackListener, DialogInterface.OnKeyListener {

    /**
     * A tag for logging
     */
    private static final String TAG = LoglrActivity.class.getSimpleName();

    /**
     * The AsyncTask that initiates the login process by loading up Tumblr on the webview
     */
    private TaskTumblrLogin taskTumblrLogin;

    /**
     * The OTP broadcast receiver that monitors for incoming SMS.
     */
    OTPBroadcastReceiver otpBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tumblr_login);

        //Instantiate object & set to Loglr class
        Loglr.getInstance().setFirebase(FirebaseAnalytics.getInstance(this));
        //Set a new firebase user property to act as app version
        if(Loglr.getInstance().getFirebase() != null)
            Loglr.getInstance().getFirebase().setUserProperty(getString(R.string.FireBase_Property_Version), getString(R.string.FireBase_Property_Version_Value));
        //Send event for login button tap
        if(Loglr.getInstance().getFirebase() != null)
            Loglr.getInstance().getFirebase().logEvent(getString(R.string.FireBase_Event_ButtonClick), null);

        //Send event for login via activity
        if(Loglr.getInstance().getFirebase() != null)
            Loglr.getInstance().getFirebase().logEvent(getString(R.string.FireBase_Event_ActivityLogin), null);

        //Test if consumer key was received
        if(TextUtils.isEmpty(Loglr.getInstance().getConsumerKey()))
            throw new LoglrAPIException();

        //Test if Secret Key was received
        if(TextUtils.isEmpty(Loglr.getInstance().getConsumerSecretKey()))
            throw new LoglrAPIException();

        //Test if URL Call back was received
        if(TextUtils.isEmpty(Loglr.getInstance().getUrlCallBack()))
            throw new LoglrCallbackException();

        if(Loglr.getInstance().is2FAEnabled()) {
            //If the developer allowed the 2FA authentication feature, continue with execution and
            //send a shoutout to Firebase
            if(Loglr.getInstance().getFirebase() != null)
                Loglr.getInstance().getFirebase().logEvent(getString(R.string.FireBase_Event_OTP_DevGranted_True), null);

            //Test if permissions will need to be sought for OTP
            if (Utils.isMarshmallowAbove() && !Utils.isSMSReadPermissionGranted(this)) {
                SeekPermissionDialog seekPermissionDialog = new SeekPermissionDialog(LoglrActivity.this);
                seekPermissionDialog.setCanceledOnTouchOutside(false);
                seekPermissionDialog.setCancelable(false);
                seekPermissionDialog.setCallback(this);
                seekPermissionDialog.show();
            } else
                onButtonOkay();
        } else {
            //If the developer explicitly disabled the 2FA authentication feature, continue with
            //execution of the program but also, send an intimation to firebase
            if(Loglr.getInstance().getFirebase() != null)
                Loglr.getInstance().getFirebase().logEvent(getString(R.string.FireBase_Event_OTP_DevGranted_False), null);
            onButtonOkay();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            //Run case if permissions were requested for SMS read / write services
            case OTPBroadcastReceiver.REQUEST_OTP_PERMISSION:
                boolean isGranted = false;
                //Run only when grant result and permissions have data in them.
                //Used to avoid a certain scenario when this method is auto executed even before the user accepts or denies permission request
                if(permissions.length > 0 && grantResults.length > 0) {
                    for (int intGrantResult : grantResults)
                        //if result is granted, set is granted to true
                        isGranted = intGrantResult == PackageManager.PERMISSION_GRANTED;
                    //Test final grant status
                    if(isGranted) {
                        //Register the SMS receiver
                        registerReceiver();
                        if(Loglr.getInstance().getFirebase() != null)
                            Loglr.getInstance().getFirebase().logEvent(getString(R.string.FireBase_Event_OTP_UserGranted_True), null);
                    } else {
                        if(Loglr.getInstance().getFirebase() != null)
                            Loglr.getInstance().getFirebase().logEvent(getString(R.string.FireBase_Event_OTP_UserGranted_False), null);
                    }
                    initiateLoginProcess();
                }
                break;
        }
    }

    /**
     * A method to continue with the login process.
     */
    private void initiateLoginProcess() {
        //Generate the loading dialog passed by the developer
        Dialog dialog = Utils.getLoadingDialog(LoglrActivity.this);
        //Set a key listener on the dialog to keep a track of back buttons pressed in case the flow malfunctions
        dialog.setOnKeyListener(LoglrActivity.this);
        //Initiate an AsyncTask to begin TumblrLogin
        if(taskTumblrLogin == null)
            taskTumblrLogin = new TaskTumblrLogin();
        //Pass context to AsyncTask
        taskTumblrLogin.setContext(LoglrActivity.this);
        //Pass Resources reference
        taskTumblrLogin.setResources(getResources());
        //Pass LoadingDialog as passed on by developer
        taskTumblrLogin.setLoadingDialog(dialog);
        //Pass reference of WebView
        taskTumblrLogin.setWebView(findViewById(R.id.activity_tumblr_webview));
        //Execute AsyncTask
        taskTumblrLogin.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    /**
     * Register the SMS OTP broadcast receiver
     */
    private void registerReceiver() {
        //Initiate an AsyncTask to begin TumblrLogin
        taskTumblrLogin = new TaskTumblrLogin();
        //Depending on whether device is running Kitkat or above,
        //Register broadcast receiver
        if(Utils.isKitkatAbove()) {
            otpBroadcastReceiver = new OTPBroadcastReceiver();
            otpBroadcastReceiver.setCallback(taskTumblrLogin);
            otpBroadcastReceiver.setWebView((WebView) findViewById(R.id.activity_tumblr_webview));
            registerReceiver(otpBroadcastReceiver, new IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION));
        } else {
            otpBroadcastReceiver = new OTPBroadcastReceiver();
            otpBroadcastReceiver.setCallback(taskTumblrLogin);
            registerReceiver(otpBroadcastReceiver, new IntentFilter(
                    getResources().getString(R.string.tumblr_otp_provider)
            ));
        }
    }

    @Override
    public void onButtonOkay() {
        //Test if 2FA has been enabled by the user at all
        if(Loglr.getInstance().is2FAEnabled())
            //Check if SMS read permissions have been granted to the application
            if(Utils.isSMSReadPermissionGranted(LoglrActivity.this)) {
                //Register the SMS receiver
                registerReceiver();
                //test if LoginListener was registered
                if(Loglr.getInstance().getLoginListener() != null) {
                    if(Loglr.getInstance().getExceptionHandler() == null)
                        Log.w(TAG, "Continuing execution without ExceptionHandler. No Exception call backs will be sent. It is recommended to set one.");
                    initiateLoginProcess();
                } else {
                    //If Exception handler was registered by the dev, use it to return a call back.
                    //Otherwise, just throw the exception and make the application crash
                    if (Loglr.getInstance().getExceptionHandler() != null) {
                        LoglrLoginException ex = new LoglrLoginException();
                        if(Loglr.getInstance().getFirebase() != null)
                            Loglr.getInstance().getFirebase().logEvent(ex.getEvent(), null);
                        Loglr.getInstance().getExceptionHandler().onLoginFailed(ex);
                    } else
                        throw new LoglrLoginException();
                }
                //If not, Check if Its an android device that runs Marshmallow.
                //if it is, request user to grant permission
            } else if(Utils.isMarshmallowAbove()) {
                //Request user for permission.
                //Once granted or denied, callback will be on onRequestPermissionsResult
                ActivityCompat.requestPermissions(LoglrActivity.this,
                        new String[]{
                                Manifest.permission.READ_SMS,
                                Manifest.permission.RECEIVE_SMS
                        },
                        OTPBroadcastReceiver.REQUEST_OTP_PERMISSION);
            } else
                //If permissions are not granted and its not Marshmallow, get on with login
                initiateLoginProcess();
        else
            //If permissions are not granted and its not Marshmallow, get on with login
            initiateLoginProcess();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        //Pass reason for closing loglr
        if (Loglr.getInstance().getExceptionHandler() != null) {
            LoglrLoginCanceled ex = new LoglrLoginCanceled();
            if(Loglr.getInstance().getFirebase() != null)
                Loglr.getInstance().getFirebase().logEvent(ex.getEvent(), null);
            Loglr.getInstance().getExceptionHandler().onLoginFailed(ex);
        }
    }

    @Override
    public void finish() {
        super.finish();
        try {
            if(otpBroadcastReceiver != null)
                unregisterReceiver(otpBroadcastReceiver);
        } catch (Exception e) {
            e.printStackTrace();
            //caught exceptions just in case
        }
    }

    @Override
    public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
        //If the button tapped was back, exit the activity
        if(event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
            onBackPressed();
            return true;
        } else
            return false;
    }
}