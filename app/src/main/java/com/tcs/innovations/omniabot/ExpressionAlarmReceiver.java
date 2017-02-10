package com.tcs.innovations.omniabot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Created by 1115394 on 2/6/2017.
 */
public class ExpressionAlarmReceiver extends BroadcastReceiver {

    private static final String TAG = ExpressionAlarmReceiver.class.getSimpleName();
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "in onReceive\nHappiness Factor: " + MainActivity.happinessFactor);
    }
}
