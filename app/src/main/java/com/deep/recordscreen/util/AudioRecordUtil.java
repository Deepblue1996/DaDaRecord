package com.deep.recordscreen.util;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.deep.dpwork.util.Lag;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class AudioRecordUtil {

    private AudioRecord audioRecord;
    private int bufferSize;
    private volatile boolean isRecording = true;

    private static AudioRecordUtil audioRecordUtil;

    public static AudioRecordUtil getInstance() {
        if(audioRecordUtil == null) {
            audioRecordUtil = new AudioRecordUtil();
        }
        return audioRecordUtil;
    }

    private void init() {
        // 获取每一帧大小
        bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        //麦克风 采样率 单声道 音频格式, 缓存大小
        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
    }

    //和视频编码一样 也是循环
    public void startRecord(File pcmFile, File muFile) {
        init();
        if (!pcmFile.exists()) {
            pcmFile.getParentFile().mkdirs();
            try {
                pcmFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        if (!muFile.exists()) {
            muFile.getParentFile().mkdirs();
            try {
                muFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        isRecording = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                audioRecord.startRecording();//开始录制
                FileOutputStream fileOutputStream = null;
                try {
                    fileOutputStream = new FileOutputStream(pcmFile);
                    byte[] bytes = new byte[bufferSize];
                    Lag.e("录音开始");
                    while (isRecording) {
                        Lag.e("录音中");
                        audioRecord.read(bytes, 0, bytes.length);//读取流
                        fileOutputStream.write(bytes);
                        fileOutputStream.flush();

                    }
                    Lag.e("录音结束");
                    audioRecord.stop();//停止录制
                    audioRecord.release();
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    addHeadData(pcmFile, muFile);//添加音频头部信息并且转成wav格式
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    audioRecord.stop();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }

    public boolean getRecordingState() {
        return isRecording;
    }

    public void stopRecord() {
        isRecording = false;
    }

    /**
     * 转换
     * @param handlerFile 源文件
     * @param pcmFile 目标文件
     */
    private void addHeadData(File handlerFile, File pcmFile) {
        if (!pcmFile.exists()) {
            pcmFile.getParentFile().mkdirs();
            try {
                pcmFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
        PcmToWavUtil pcmToWavUtil = new PcmToWavUtil(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        pcmToWavUtil.pcmToWav(handlerFile.toString(), pcmFile.toString());
    }
}
