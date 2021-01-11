package com.deep.recordscreen.core

import android.Manifest
import android.view.WindowManager
import com.deep.dpwork.annotation.DpFullScreen
import com.deep.dpwork.annotation.DpMainScreenKt
import com.deep.dpwork.annotation.DpPermission
import com.deep.dpwork.annotation.DpScreen
import com.deep.dpwork.core.kotlin.DpInitCoreKt
import com.deep.recordscreen.screen.RecordScreen

/**
 * Class - 框架入口
 *
 * Created by Deepblue on 2018/9/29 0029.
 */
@DpPermission(
    Manifest.permission.READ_EXTERNAL_STORAGE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE,
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.INTERNET,
    Manifest.permission.ACCESS_NETWORK_STATE
)
@DpMainScreenKt(RecordScreen::class)
@DpScreen(vertical = false)
@DpFullScreen
class InitCore : DpInitCoreKt() {
    override fun mainInit() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}