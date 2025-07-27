package com.github.catvod.utils;

import android.util.Log;

public class LogUtils {

    private static final String TAG = "TVBox";

    public static void e(String msg) {
        Log.e(TAG, msg);
    }

    public static void d(String msg) {
        Log.d(TAG, msg);
    }

    public static void i(String msg) {
        Log.i(TAG, msg);
    }

    public static void w(String msg) {
        Log.w(TAG, msg);
    }

    public static void v(String msg) {
        Log.v(TAG, msg);
    }
}
