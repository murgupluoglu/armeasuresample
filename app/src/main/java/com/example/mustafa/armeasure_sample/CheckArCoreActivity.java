package com.example.mustafa.armeasure_sample;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.blankj.utilcode.constant.PermissionConstants;
import com.blankj.utilcode.util.PermissionUtils;
import com.example.mustafa.armeasure_sample.helpers.SnackbarHelper;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;

public class CheckArCoreActivity extends AppCompatActivity {

    private final SnackbarHelper messageSnackbarHelper = new SnackbarHelper();
    private boolean installRequested;
    private Session session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkarcore);

        PermissionUtils.permission(PermissionConstants.CAMERA).callback(new PermissionUtils.SimpleCallback() {
            @Override
            public void onGranted() {
                checkArCore();
            }

            @Override
            public void onDenied() {

            }
        }).request();
    }

    private void checkArCore(){
        Exception exception = null;
        String message = null;
        try {
            switch (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                case INSTALL_REQUESTED:
                    installRequested = true;
                case INSTALLED:
                    break;
            }

            session = new Session(this);

        } catch (UnavailableArcoreNotInstalledException
                | UnavailableUserDeclinedInstallationException e) {
            message = "Please install ARCore";
            exception = e;
        } catch (UnavailableApkTooOldException e) {
            message = "Please update ARCore";
            exception = e;
        } catch (UnavailableSdkTooOldException e) {
            message = "Please update this app";
            exception = e;
        } catch (UnavailableDeviceNotCompatibleException e) {
            message = "This device does not support AR";
            exception = e;
        } catch (Exception e) {
            message = "Failed to create AR session";
            exception = e;
        }

        if (message != null) {
            messageSnackbarHelper.showError(this, message);
            Log.e("TAG", "Exception creating session", exception);
        }else{
            goToArCoreActivity();
        }
    }

    private void goToArCoreActivity(){
        Intent myIntent = new Intent(this, MainActivity.class);
        this.startActivity(myIntent);
        this.finish();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (session != null) {
            session.pause();
        }
    }
}
