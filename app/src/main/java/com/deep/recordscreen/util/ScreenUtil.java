package com.deep.recordscreen.util;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.view.WindowManager;

public class ScreenUtil {
    /**
     * 切换全屏,取消全屏
     *
     * @param isChecked
     */
    public static void switchFullScreen(Activity activity, boolean isChecked) {
        if (isChecked) {
            //切换到全屏模式
            //添加一个全屏的标记
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            //请求横屏
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        } else {
            //切换到默认模式
            //清除全屏标记
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            //请求纵屏
            activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
    }
}
