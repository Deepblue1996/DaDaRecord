package com.deep.recordscreen.weight;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.deep.dpwork.util.CountDownTimeTextUtil;
import com.deep.dpwork.util.Lag;
import com.deep.recordscreen.util.AtWavUtil;
import com.deep.recordscreen.util.AudioRecordUtil;
import com.deep.recordscreen.util.AvcEncoder;
import com.deep.recordscreen.util.FileUtil;
import com.deep.recordscreen.util.NV21RotateUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;

import ffmpeglib.utils.FFmpegKit;

public class CameraView extends SurfaceView implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private static final int MINIMUM_PREVIEW_SIZE = 320;

    private Camera mCamera;
    private static int mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    private Camera.Parameters mParams;
    private Camera.Size mPreviewSize;
    private PreviewCallback mPreviewCallback;
    private int mOrientationAngle;

    public int previewCallbackFrequence;// frame callback frequence
    private int previewCallbackCount;

    private int minPreviewWidth;
    private int minPreviewHeight;

    private int mDeviecAutoRotateAngle;

    private boolean isRecordState = false;

    public static void resetCameraId() {
        mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
    }

    public CameraView(Context context) {
        this(context, null);
    }

    public CameraView(Context context, AttributeSet attrs) {
        super(context, attrs);

//        mCameraId = Camera.CameraInfo.CAMERA_FACING_FRONT;
        previewCallbackFrequence = 1;

        minPreviewWidth = 1280;
        minPreviewHeight = 720;

        SurfaceHolder holder = getHolder();
        holder.addCallback(this);

    }

    public void setPreviewCallback(PreviewCallback previewCallback) {
        mPreviewCallback = previewCallback;
    }

    public void setMinPreviewSize(int minWidth, int minHeight) {

        minPreviewWidth = minWidth;
        minPreviewHeight = minHeight;
    }

    public Camera.Size getPreviewSize() {
        return mPreviewSize;
    }


    /**
     * 通过对比得到与宽高比最接近的预览尺寸（如果有相同尺寸，优先选择）
     *
     * @param isPortrait    是否竖屏
     * @param surfaceWidth  需要被进行对比的原宽
     * @param surfaceHeight 需要被进行对比的原高
     * @param preSizeList   需要对比的预览尺寸列表
     * @return 得到与原宽高比例最接近的尺寸
     */
    private Camera.Size getCloselyPreSize(boolean isPortrait, int surfaceWidth, int surfaceHeight, List<Camera.Size> preSizeList) {
        int reqTmpWidth;
        int reqTmpHeight;
        // 当屏幕为垂直的时候需要把宽高值进行调换，保证宽大于高
        if (isPortrait) {
            reqTmpWidth = surfaceHeight;
            reqTmpHeight = surfaceWidth;
        } else {
            reqTmpWidth = surfaceWidth;
            reqTmpHeight = surfaceHeight;
        }
        //先查找preview中是否存在与surfaceview相同宽高的尺寸
        for (Camera.Size size : preSizeList) {
            if ((size.width == reqTmpWidth) && (size.height == reqTmpHeight)) {
                return size;
            }
        }

        // 得到与传入的宽高比最接近的size
        float reqRatio = ((float) reqTmpWidth) / reqTmpHeight;
        float curRatio, deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        Camera.Size retSize = null;
        for (Camera.Size size : preSizeList) {
            curRatio = ((float) size.width) / size.height;
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }

        return retSize;
    }

    private void openCamera(SurfaceHolder holder) {
        try {
            // release Camera, if not release camera before call camera, it will be locked
            releaseCamera();

            mCamera = Camera.open(mCameraId);

            // set camera front/back
            setCameraDisplayOrientation((Activity) getContext(), mCameraId, mCamera);

            // set preview size
            mParams = mCamera.getParameters();
            mPreviewSize = getPropPreviewSize(mParams.getSupportedPreviewSizes(), minPreviewWidth, minPreviewHeight);

            //mPreviewSize = getCloselyPreSize(true, DisplayUtil.getMobileWidth(getContext()), DisplayUtil.getMobileHeight(getContext()), mParams.getSupportedPreviewSizes());

            Log.i("Camera", "mPreviewSize width:" + mPreviewSize.width + " height:" + mPreviewSize.height);

            mParams.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

            // set format
            mParams.setPreviewFormat(ImageFormat.NV21);
            if (mCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mParams.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }

            mCamera.setParameters(mParams);

            if (mPreviewCallback != null) {
                mPreviewCallback.onGetPreviewOptimalSize(mPreviewSize.width, mPreviewSize.height, mOrientationAngle, mDeviecAutoRotateAngle);
            }

            try {
                mCamera.setPreviewDisplay(holder);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.setPreviewCallback(this);
            mCamera.startPreview();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean isRecord() {
        return isRecordState;
    }

    private ArrayBlockingQueue<byte[]> arrayBlockingQueue;

    private Timer timer;

    private long startTimeLong = 0;
    private long stopTimeLong = 0;

    private static String avFilePath = FileUtil.getSDPath() + "/DCIM/Camera/";
    private static String avFileName = "r_2020-1-1 12-26";

    public String tempPathName = "";

    public void starRecordVideo(FFmpegKit.KitInterface kitInterface) {
        if (!isRecordState) {
            startTimeLong = System.currentTimeMillis();
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (cameraViewRecorderListener != null) {
                        cameraViewRecorderListener.update(System.currentTimeMillis() - startTimeLong);
                    }
                }
            }, 1000, 1000);
            isRecordState = true;
            arrayBlockingQueue = new ArrayBlockingQueue<byte[]>(10);
            avFileName = "r_" + CountDownTimeTextUtil.nowTime().replace(':', '-').replace(' ', '-') + "-" + System.currentTimeMillis();
            //AvcEncoder.getInstance().init(minPreviewWidth, minPreviewHeight, 24, avFilePath + avFileName + ".h264");
            AvcEncoder.getInstance().init(minPreviewHeight, minPreviewWidth, 24, avFilePath + avFileName + ".h264");
            AvcEncoder.getInstance().startEncoderThread(arrayBlockingQueue);

            if (cameraViewRecorderListener != null) {
                cameraViewRecorderListener.start();
            }
            AudioRecordUtil.getInstance().startRecord(new File(avFilePath + avFileName + ".pcm"), new File(avFilePath + avFileName + ".wav"));

            Lag.i("音视频开始");
        } else {
            Lag.i("音视频结束:" + avFilePath + avFileName + ".h264/.pcm/.wav");
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            stopTimeLong = System.currentTimeMillis();
            if (cameraViewRecorderListener != null) {
                cameraViewRecorderListener.stop(stopTimeLong - startTimeLong);
            }
            AvcEncoder.getInstance().stopThread();
            AudioRecordUtil.getInstance().stopRecord();
            isRecordState = false;

            AtWavUtil.getInstance().compressVideo(avFilePath + avFileName + ".h264",
                    avFilePath + avFileName + ".wav", avFilePath + avFileName + ".mp4", kitInterface);

            tempPathName = avFilePath + avFileName + ".mp4";
        }
    }

    private CameraViewRecorderListener cameraViewRecorderListener;

    public void setCameraViewRecorderListener(CameraViewRecorderListener cameraViewRecorderListener) {
        this.cameraViewRecorderListener = cameraViewRecorderListener;
    }

    public interface CameraViewRecorderListener {
        void start();

        void update(long time);

        void stop(long time);
    }

    public void flashOn(boolean onOff) {
        Camera.Parameters parameters = mCamera.getParameters();
        if (onOff) {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        } else {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
        mCamera.setParameters(parameters);
    }

    private synchronized void releaseCamera() {
        if (mCamera != null) {
            try {
                mCamera.setPreviewCallback(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                mCamera.stopPreview();
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                mCamera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
            mCamera = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.i("Camera", "surfaceCreated");

        openCamera(surfaceHolder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        Log.i("Camera", "surfaceChanged");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.i("Camera", "surfaceDestroyed");
    }

    public void switchCamera() {
        mCameraId = mCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT ? Camera.CameraInfo.CAMERA_FACING_BACK : Camera.CameraInfo.CAMERA_FACING_FRONT;
        openCamera(getHolder());
    }

    public boolean isFrontCamera() {
        return Camera.CameraInfo.CAMERA_FACING_FRONT == mCameraId;
    }

    public void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info =
                new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
            default:
                degrees = 0;
                break;
        }

        mDeviecAutoRotateAngle = degrees;

        mOrientationAngle = info.orientation;

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    public void onResume() {
        openCamera(getHolder());
    }

    public void onPause() {
        // 在暂停事件中立即释放摄像头
        releaseCamera();
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {

        previewCallbackCount++;
        if (previewCallbackCount % previewCallbackFrequence != 0) {
            return;
        }
        previewCallbackCount = 0;

//        Lag.i("转换亮度 时间:" + System.currentTimeMillis());
//
//        //byte[] bytes = NV21rgbUtil.b2t(bytesEx, mPreviewSize.width, mPreviewSize.height, 150);
////        byte[] bytes = new byte[mPreviewSize.width * mPreviewSize.height * 3 / 2];
////        YuvUtil.yuvLight(bytesEx, bytes, mPreviewSize.width, mPreviewSize.height, 150);
//
//        Lag.i("转换亮度完毕 时间:" + System.currentTimeMillis());

        if (mPreviewCallback != null) {
            mPreviewCallback.onPreviewFrame(bytes, mPreviewSize.width, mPreviewSize.height, mOrientationAngle);
        }

        if (hasCake) {
            hasCake = false;
            savePic(bytes);
            Lag.i("拍照");
        }

        if (isRecordState) {
            byte[] newByte;
            if (isFrontCamera()) {
                newByte = NV21RotateUtil.rotateYUVDegree270AndMirror(bytes, mPreviewSize.width, mPreviewSize.height);
            } else {
                newByte = NV21RotateUtil.rotateYUVDegree90(bytes, mPreviewSize.width, mPreviewSize.height);
            }
            putYUVData(newByte);
        }
    }

    public void putYUVData(byte[] buffer) {
        if (arrayBlockingQueue.size() >= 10) {
            arrayBlockingQueue.poll();
        }
        arrayBlockingQueue.add(buffer);
        Lag.i("音视频中");
    }

    private boolean hasCake = false;

    public interface ListenPhoto {
        void cakePath(String path);
    }

    private ListenPhoto listenPhoto;

    public void cake(ListenPhoto listenPhoto) {
        hasCake = true;
        this.listenPhoto = listenPhoto;
    }

    private void savePic(byte[] data) {
        new Thread(() -> {
            Lag.i("提取照片");
            Camera.Size previewSize = mCamera.getParameters().getPreviewSize();//获取尺寸,格式转换的时候要用到
            BitmapFactory.Options newOpts = new BitmapFactory.Options();
            newOpts.inJustDecodeBounds = true;
            YuvImage yuvimage = new YuvImage(
                    data,
                    ImageFormat.NV21,
                    previewSize.width,
                    previewSize.height,
                    null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            yuvimage.compressToJpeg(new Rect(0, 0, previewSize.width, previewSize.height), 100, baos);// 80--JPG图片的质量[0-100],100最高
            byte[] rawImage = baos.toByteArray();
            //将rawImage转换成bitmap
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length, options);

            Lag.i("拍照角度:" + mOrientationAngle);

            Bitmap temp;
            if (isFrontCamera()) {
//                if (MainScreen.rotateDegree == 270) {
                temp = rotateBitmap(bitmap, 90);
//                } else if (MainScreen.rotateDegree == 90) {
//                    temp = rotateBitmap(bitmap, 90);
//                } else {
//                    temp = rotateBitmap(bitmap, -90);
//                }
                temp = convert(temp);
            } else {
                temp = rotateBitmap(bitmap, 90);
            }
            Lag.i("开始保存照片");
            try {
                String path = saveMyBitmap(temp, "r_" + CountDownTimeTextUtil.nowTime().replace(':', '-').replace(' ', '-') + "-" + System.currentTimeMillis() + "");
                Lag.i("保存照片:" + path);
                if (listenPhoto != null) {
                    listenPhoto.cakePath(path);
                }
            } catch (IOException e) {
                e.printStackTrace();
                Lag.i("保存照片异常");
            }

            bitmap.recycle();
            temp.recycle();
        }).start();
    }

    private Bitmap convert(Bitmap origin) {
        if (origin == null) {
            return null;
        }
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(-1, 1);   //镜像水平翻转
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        return newBM;
    }

    /**
     * 选择变换
     *
     * @param origin 原图
     * @param alpha  旋转角度，可正可负
     * @return 旋转后的图片
     */
    private Bitmap rotateBitmap(Bitmap origin, float alpha) {
        if (origin == null) {
            return null;
        }
        int width = origin.getWidth();
        int height = origin.getHeight();
        Matrix matrix = new Matrix();
        matrix.setRotate(alpha);
        // 围绕原地进行旋转
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (newBM.equals(origin)) {
            return newBM;
        }
        origin.recycle();
        return newBM;
    }

    private String saveMyBitmap(Bitmap bmp, String bitName) throws IOException {
        File dirFile = new File(FileUtil.getSDPath() + "/DCIM/Camera/");
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }
        File f = new File(FileUtil.getSDPath() + "/DCIM/Camera/" + bitName + ".jpg");
        boolean flag = false;
        Lag.i("路径" + f.getPath());
        f.createNewFile();
        FileOutputStream fOut = null;
        try {
            fOut = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
            flag = true;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            fOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            fOut.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 通知图库更新
        getContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.parse("file://" + dirFile.getAbsolutePath())));
        return f.getPath();
    }

    /**
     * Given choices supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the minimum of both, or an exact match if possible.
     *
     * @param choices   The list of sizes that the camera supports for the intended output class
     * @param minWidth  The minimum desired width
     * @param minHeight The minimum desired height
     * @return The optimal size, or an arbitrary one if none were big enough
     */
    private Camera.Size getPropPreviewSize(List<Camera.Size> choices, int minWidth, int minHeight) {
        final int minSize = Math.max(Math.min(minWidth, minHeight), MINIMUM_PREVIEW_SIZE);

        final List<Camera.Size> bigEnough = new ArrayList<Camera.Size>();
        final List<Camera.Size> tooSmall = new ArrayList<Camera.Size>();

        for (Camera.Size option : choices) {
            if (option.width == minWidth && option.height == minHeight) {
                return option;
            }

            if (option.height >= minSize && option.width >= minSize) {
                bigEnough.add(option);
            } else {
                tooSmall.add(option);
            }
        }

        if (bigEnough.size() > 0) {
            Camera.Size chosenSize = Collections.min(bigEnough, new CompareSizesByArea());
            return chosenSize;
        } else {
            return choices.get(0);
        }
    }


    public interface PreviewCallback {
        // preview size callback when created
        void onGetPreviewOptimalSize(int optimalWidth, int optimalHeight, int cameraOrientation, int deviecAutoRotateAngle);

        // preview callback
        void onPreviewFrame(byte[] data, int width, int height, int cameraOrientation);
    }

    // Compares two size based on their areas.
    static class CompareSizesByArea implements Comparator<Camera.Size> {
        @Override
        public int compare(final Camera.Size lhs, final Camera.Size rhs) {
            return Long.signum(
                    (long) lhs.width * lhs.height - (long) rhs.width * rhs.height);
        }
    }

}
