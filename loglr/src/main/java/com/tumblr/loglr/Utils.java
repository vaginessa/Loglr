package com.tumblr.loglr;

import android.Manifest;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;

import java.lang.reflect.InvocationTargetException;

public class Utils {

    /**
     * A method to check if the android version is Marshmallow or above
     * @return A boolean value
     */
    static boolean isMarshmallowAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    /**
     * A method to check if SMS read permissions have been granted to the application
     * @param context Context of the calling Activity
     * @return Returns a boolean value determining if SMS read permisisons have been granted or not
     */
    static boolean isSMSReadPermissionGranted(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * A method to check if the android version is Kitkat or above
     * @return a Boolean value
     */
    static boolean isKitkatAbove() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * A method to construct the loading dialog passed on by the developer to Loglr
     * @param context context of the calling activity
     * @return returns the Loading Dialog to display to the user when authentication tokens are being
     *         exchanged and the user is expected to wait.
     */
    static Dialog getLoadingDialog(Context context) {
        //Declare a LoadingDialog that will be passed to the AsyncTasks
        Dialog LoadingDialog = null;
        //Test if Loading Dialog was receiver
        if(Loglr.getInstance().getLoadingDialog() != null) {
            try {
                //Extract Loading Dialog class passed by the Activity
                Class<? extends Dialog> classDialog = Loglr.getInstance().getLoadingDialog();
                //get default constructor and create new instance for the Dialog
                LoadingDialog = classDialog.getConstructor(Context.class).newInstance(context);
                if(Loglr.getInstance().getFirebase() != null)
                    Loglr.getInstance().getFirebase().logEvent(context.getString(R.string.FireBase_Event_CustomDialog_Set), null);
                return LoadingDialog;
            } catch (InstantiationException | InvocationTargetException | NullPointerException | NoSuchMethodException | IllegalAccessException | ClassCastException e) {
                e.printStackTrace();
                LoadingDialog = new ProgressDialog(context);
                LoadingDialog.setTitle(context.getString(R.string.tumblrlogin_loading));
                if(Loglr.getInstance().getFirebase() != null)
                    Loglr.getInstance().getFirebase().logEvent(context.getString(R.string.FireBase_Event_CustomDialog_Fail), null);
                return LoadingDialog;
            }
        } else {
            ProgressDialog progressDialog = new ProgressDialog(context);
            progressDialog.setMessage(context.getString(R.string.tumblrlogin_loading));
            return progressDialog;
        }
    }
}