package com.example.mustafa.armeasure_sample.helpers;

import android.app.Activity;
import android.support.design.widget.BaseTransientBottomBar;
import android.support.design.widget.Snackbar;
import android.view.View;
import android.widget.TextView;

/**
 * Helper to manage the sample snackbar. Hides the Android boilerplate code, and exposes simpler
 * methods.
 */
public final class SnackbarHelper {
    private static final int BACKGROUND_COLOR = 0xbf323232;
    private Snackbar messageSnackbar;
    private enum DismissBehavior { HIDE, SHOW, FINISH };
    private int maxLines = 2;

    public boolean isShowing() {
        return messageSnackbar != null;
    }

    /** Shows a snackbar with a given message. */
    public void showMessage(Activity activity, String message) {
        show(activity, message, DismissBehavior.HIDE);
    }

    /** Shows a snackbar with a given message, and a dismiss button. */
    public void showMessageWithDismiss(Activity activity, String message) {
        show(activity, message, DismissBehavior.SHOW);
    }

    /**
     * Shows a snackbar with a given error message. When dismissed, will finish the activity. Useful
     * for notifying errors, where no further interaction with the activity is possible.
     */
    public void showError(Activity activity, String errorMessage) {
        show(activity, errorMessage, DismissBehavior.FINISH);
    }

    /**
     * Hides the currently showing snackbar, if there is one. Safe to call from any thread. Safe to
     * call even if snackbar is not shown.
     */
    public void hide(Activity activity) {
        activity.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        if (messageSnackbar != null) {
                            messageSnackbar.dismiss();
                        }
                        messageSnackbar = null;
                    }
                });
    }

    public void setMaxLines(int lines) {
        maxLines = lines;
    }

    private void show(
            final Activity activity, final String message, final DismissBehavior dismissBehavior) {
        activity.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        messageSnackbar =
                                Snackbar.make(
                                        activity.findViewById(android.R.id.content),
                                        message,
                                        Snackbar.LENGTH_INDEFINITE);
                        messageSnackbar.getView().setBackgroundColor(BACKGROUND_COLOR);
                        if (dismissBehavior != DismissBehavior.HIDE) {
                            messageSnackbar.setAction(
                                    "Dismiss",
                                    new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            messageSnackbar.dismiss();
                                        }
                                    });
                            if (dismissBehavior == DismissBehavior.FINISH) {
                                messageSnackbar.addCallback(
                                        new BaseTransientBottomBar.BaseCallback<Snackbar>() {
                                            @Override
                                            public void onDismissed(Snackbar transientBottomBar, int event) {
                                                super.onDismissed(transientBottomBar, event);
                                                activity.finish();
                                            }
                                        });
                            }
                        }
                        ((TextView)
                                messageSnackbar
                                        .getView()
                                        .findViewById(android.support.design.R.id.snackbar_text))
                                .setMaxLines(maxLines);
                        messageSnackbar.show();
                    }
                });
    }
}