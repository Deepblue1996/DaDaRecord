package com.deep.recordscreen.util;

import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileUtil {
    /**
     * 获取SD path
     *
     * @return
     */
    public static String getSDPath() {
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(Environment.MEDIA_MOUNTED); // 判断sd卡是否存在
        if (sdCardExist) {
            sdDir = Environment.getExternalStorageDirectory();// 获取跟目录
            return sdDir.toString();
        }

        return null;
    }

    public static List<File> GetFileName(String fileAbsolutePath) {
        List<File> vecFile = new ArrayList<>();
        File file = new File(fileAbsolutePath);
        File[] subFile = file.listFiles();

        if (subFile != null) {
            for (int i = 0; i < subFile.length - 1; i++) {
                for (int j = 0; j < subFile.length - i - 1; j++) {   // 这里说明为什么需要-1
                    if (subFile[j].lastModified() < subFile[j + 1].lastModified()) {
                        File temp = subFile[j];
                        subFile[j] = subFile[j + 1];
                        subFile[j + 1] = temp;
                    }
                }
            }

            for (int iFileLength = 0; iFileLength < subFile.length; iFileLength++) {
                // 判断是否为文件夹
                if (!subFile[iFileLength].isDirectory()) {
                    String filename = subFile[iFileLength].getName();
                    // 判断是否为MP4结尾
                    if (filename.trim().toLowerCase().endsWith(".jpg") || filename.trim().toLowerCase().endsWith(".mp4")) {
                        vecFile.add(subFile[iFileLength]);
                    }
                }
            }
        }
        return vecFile;
    }

    /**
     * 删除单个文件
     * @param   filePath    被删除文件的文件名
     * @return 文件删除成功返回true，否则返回false
     */
    public static boolean deleteFile(String filePath) {
        File file = new File(filePath);
        if (file.isFile() && file.exists()) {
            return file.delete();
        }
        return false;
    }
}
