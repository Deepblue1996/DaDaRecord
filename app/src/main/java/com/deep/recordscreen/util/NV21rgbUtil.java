package com.deep.recordscreen.util;

public class NV21rgbUtil {
    public static byte[] b2t(byte[] data, int imageWidth, int imageHeight, int bright) {
        byte[] yuv = new byte[imageWidth * imageHeight * 3 / 2];
        // 6*8*3/2=48*3/2=144/2=72
        // 48
        for (int i = 0; i < imageWidth * imageHeight; i++) {

            byte cs = data[i];
            int sx = cs & 0xff;
            //yuv[i] = (byte) sx;
            int byteX = (int) (((float) (sx)) / 100 * bright);
            if (byteX > 255) {
                yuv[i] = (byte) 255;
            } else {
                yuv[i] = (byte) byteX;
            }
        }
        // 24 = 6 * 8 / 2
        int wh = imageWidth * imageHeight - 1;
        for (int i = 0; i < imageWidth * imageHeight / 2; i++) {
            yuv[wh + i] = data[wh + i];
        }
        return yuv;
    }
}
