package com.deep.recordscreen.screen

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.view.MotionEvent
import android.view.View
import com.deep.dpwork.core.kotlin.BaseScreenKt
import com.deep.dpwork.util.CountDownTimeTextUtil
import com.deep.dpwork.util.DisplayUtil
import com.deep.dpwork.util.ImageUtil
import com.deep.recordscreen.R
import com.deep.recordscreen.core.InitApp
import com.deep.recordscreen.databinding.RecordScreenLayoutBinding
import com.deep.recordscreen.util.RecordManagerUtil
import com.github.florent37.viewanimator.ViewAnimator
import java.io.File
import java.text.DecimalFormat
import java.util.*

class RecordScreen : BaseScreenKt<RecordScreenLayoutBinding>() {

    private lateinit var timerTime: Timer
    private var batteryLevel = 0
    private var batteryScale = 0
    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        @SuppressLint("SetTextI18n")
        override fun onReceive(context: Context?, intent: Intent) {
            //获取当前电量，如未获取具体数值，则默认为0
            batteryLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
            //获取最大电量，如未获取到具体数值，则默认为100
            batteryScale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            //显示电量
            here.dianYuanTv.text = (batteryLevel * 100 / batteryScale).toString() + " % 电量"
        }
    }
    private val noSwitch = 0
    private var startTime: Long = 0

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    override fun mainInit() {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        //注册接收器以获取电量信息
        dpActivity.registerReceiver(broadcastReceiver, intentFilter)

        here.run {

            if (InitApp.appData.photoPath != "") {
                ImageUtil.show(
                    dpActivity,
                    File(InitApp.appData.photoPath),
                    here.videoTouch
                )
            }

            val layParams = mainSurfaceView.layoutParams;
            layParams.width = (DisplayUtil.getMobileHeight(context) * (1.5)).toInt()
            mainSurfaceView.layoutParams = layParams

            ViewAnimator.animate(recordImg).alpha(0f, 1f, 0f).repeatCount(-1).duration(1000).start()
            ViewAnimator.animate(fenTv).alpha(0f, 1f, 0f).repeatCount(-1).duration(2000).start()

            RecordManagerUtil.getInstance().init(dpActivity, mainSurfaceView)

            takeTouch.setOnTouchListener { _: View?, event: MotionEvent ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        takeTouch.alpha = 0.5f
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        takeTouch.alpha = 1.0f
                    }
                }
                true
            }

            recordTouch.setOnClickListener {
                if (RecordManagerUtil.getInstance().isRecording) {
                    recordTouch.setImageResource(R.mipmap.ic_re_start)
                    RecordManagerUtil.getInstance().stopRecord()
                    toast("停止录制")
                } else {
                    recordTouch.setImageResource(R.mipmap.ic_re_stop)
                    startTime = System.currentTimeMillis()
                    RecordManagerUtil.getInstance().startRecord(10)
                    toast("开始录制")
                }
            }

            timerTime = Timer()
            timerTime.schedule(object : TimerTask() {
                override fun run() {
                    runUi {
                        val number =
                            getSDFreeSize().toFloat() / (6 * 1024 * 60 * 24).toFloat() * 24
                        val decimalFormat = DecimalFormat("###.##")
                        val d = decimalFormat.format(number)
                        topStateTv.text =
                            "容量: ${getSDFreeSize()}/${getSDAllSize()} Kb 可录制${d}小时\n${CountDownTimeTextUtil.nowTime()}"
                        val date = Date()
                        val ap = date.hours > 12
                        val hour = if (date.hours > 12) {
                            date.hours - 12
                        } else {
                            date.hours
                        }
                        if (hour < 10) {
                            hourTv.text = "0$hour"
                        } else {
                            hourTv.text = hour.toString()
                        }
                        if (date.minutes < 10) {
                            minuteTv.text = "0" + date.minutes.toString()
                        } else {
                            minuteTv.text = date.minutes.toString()
                        }
                        if (RecordManagerUtil.getInstance().isRecording) {
                            luZhiTv.text = "录制中"
                            timeTv.text =
                                CountDownTimeTextUtil.getTimerString(System.currentTimeMillis() - startTime)
                                    .toString()
                        } else {
                            luZhiTv.text = "录制已停止"
                            timeTv.text = "00:00:00"
                        }
                    }
                }

            }, 1000, 1000)
        }
    }

    private fun toast(msg: String?) {
        here.connectToastText.text = msg
        ViewAnimator.animate(here.connectToastLin)
            .alpha(0f, 1f, 1f, 1f, 1f, 0f).duration(2000).start()
        //TextToastDialogScreen.prepare(TextToastDialogScreen.class, new TextBaseBean(msg)).open(fragmentManager());
    }

    override fun onBack() {

    }

    override fun onDestroy() {
        super.onDestroy()
        dpActivity.unregisterReceiver(broadcastReceiver)
    }

    /**
     * 获取剩余容量
     */
    private fun getSDFreeSize(): Long {
        //取得SD卡文件路径
        val path: File = Environment.getExternalStorageDirectory()
        val sf = StatFs(path.path)
        //获取单个数据块的大小(Byte)
        val blockSize = sf.blockSize.toLong()
        //空闲的数据块的数量
        val freeBlocks = sf.availableBlocks.toLong()
        //返回SD卡空闲大小
        //return freeBlocks * blockSize;  //单位Byte
        //return (freeBlocks * blockSize)/1024;   //单位KB
        return freeBlocks * blockSize / 1024 //单位MB
    }

    /**
     * 获取总容量
     */
    private fun getSDAllSize(): Long {
        //取得SD卡文件路径
        val path = Environment.getExternalStorageDirectory()
        val sf = StatFs(path.path)
        //获取单个数据块的大小(Byte)
        val blockSize = sf.blockSize.toLong()
        //获取所有数据块数
        val allBlocks = sf.blockCount.toLong()
        //返回SD卡大小
        //return allBlocks * blockSize; //单位Byte
        //return (allBlocks * blockSize)/1024; //单位KB
        return allBlocks * blockSize / 1024 //单位MB
    }
}