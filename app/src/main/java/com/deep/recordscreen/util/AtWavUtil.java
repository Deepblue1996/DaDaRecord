package com.deep.recordscreen.util;

import com.deep.dpwork.util.Lag;

import java.io.File;

import ffmpeglib.utils.FFmpegKit;
import ffmpeglib.utils.ThreadPoolUtils;

public class AtWavUtil {

    private static AtWavUtil atWavUtil;

    public static AtWavUtil getInstance() {
        if (atWavUtil == null) {
            atWavUtil = new AtWavUtil();
        }
        return atWavUtil;
    }

    public void compressVideo(final String inVideoFile, final String inWavFile, final String outFile, FFmpegKit.KitInterface kitInterface) {
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                String[] commands = new String[16];
                // ffmpeg -i video.mp4 -i audio.wav -c:v copy -c:a aac -strict experimental output.mp4
                commands[0] = "ffmpeg";
                commands[1] = "-i";
                commands[2] = inVideoFile;
                commands[3] = "-i";
                commands[4] = inWavFile;
                commands[5] = "-c:v";
                commands[6] = "copy";
                commands[7] = "-c:a";        //这个
                commands[8] = "aac";
                commands[9] = "-strict";
                commands[10] = "experimental";
                commands[11] = "-s";
                commands[12] = "640x480";
                commands[13] = "-b";
                commands[14] = "64k";
                commands[15] = outFile;
                FFmpegKit.execute(commands, new FFmpegKit.KitInterface() {
                    @Override
                    public void onStart() {
                        kitInterface.onStart();
                    }

                    @Override
                    public void onProgress(int progress) {
                        kitInterface.onProgress(progress);
                    }

                    @Override
                    public void onEnd(int result) {
                        kitInterface.onEnd(result);
                        File dir = new File(inVideoFile);
                        Lag.i("合并视频完成");
                        Lag.i("目标目录:" + dir.getParent());
                        Lag.i("目标文件名:" + dir.getName());
                        int index = dir.getName().indexOf('.');
                        String name = dir.getName().substring(0, index);
                        Lag.i("目标文件集:" + name + ".h264/.pcm/.wav");
                        File h264 = new File(dir.getParent() + "/" + name + ".h264");
                        File pcm = new File(dir.getParent() + "/" + name + ".pcm");
                        File wav = new File(dir.getParent() + "/" + name + ".wav");
                        if (h264.exists()) {
                            boolean t = h264.delete();
                            if (t) {
                                Lag.i("h264，删除完成");
                            } else {
                                Lag.i("h264，删除失败");
                            }
                        }
                        if (pcm.exists()) {
                            boolean t = pcm.delete();
                            if (t) {
                                Lag.i("pcm，删除完成");
                            } else {
                                Lag.i("pcm，删除失败");
                            }
                        }
                        if (wav.exists()) {
                            boolean t = wav.delete();
                            if (t) {
                                Lag.i("wav，删除完成");
                            } else {
                                Lag.i("wav，删除失败");
                            }
                        }
                    }
                });
            }
        };
        ThreadPoolUtils.execute(runnable);
    }

}
