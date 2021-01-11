package com.deep.recordscreen.weight;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.deep.dpwork.util.Lag;
import com.deep.recordscreen.R;
import com.deep.recordscreen.databinding.CameraOurLayoutBinding;
import com.deep.recordscreen.util.ImageUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static android.content.Context.CAMERA_SERVICE;

/**
 * 相机 Kotlin版移植
 * Created by Deep on 2018/5/10 0010.
 */

public class Camera2View extends LinearLayout {

    private Context context;
    /**
     * 相机设备类
     * The CameraDevice class is a representation of a single camera connected to an
     * Android device, allowing for fine-grain control of image capture and
     * post-processing at high frame rates.
     */
    private CameraDevice cameraDevice;

    /**
     * <p>A system service manager for detecting, characterizing, and connecting to
     * {@link CameraDevice CameraDevices}.</p>
     **/
    private CameraManager cameraManager;

    /**
     * 调用相机设备id
     */
    private String mCameraID = "0";

    /**
     * 最佳尺寸
     */
    private Size mPreviewSize;

    /**
     * 配置
     * An immutable package of settings and outputs needed to capture a single
     * image from the camera device.
     */
    private CaptureRequest.Builder mPreviewBuilder;

    /**
     * 允许应用程序直接访问呈现表面的图像数据
     * The ImageReader class allows direct application access to image data
     * rendered into a {@link Surface}
     */
    private ImageReader mImageReader;

    /**
     * 消息机制
     */
    private Handler mHandler;

    private static boolean takePhoto = false;

    /**
     * 视图
     */
    private TextureView mPreviewView;

    private CameraCaptureSession cameraCaptureSession;

    private OrientationEventListener mScreenOrientationEventListener;

    private int mScreenExifOrientation = 0;

    private static final SparseIntArray ORIENTATION = new SparseIntArray();

    static {
        ORIENTATION.append(Surface.ROTATION_0, 90);
        ORIENTATION.append(Surface.ROTATION_90, 0);
        ORIENTATION.append(Surface.ROTATION_180, 270);
        ORIENTATION.append(Surface.ROTATION_270, 180);
    }

    public interface PreviewCallback {
        // preview size callback when created
        void onGetPreviewOptimalSize(int optimalWidth, int optimalHeight, int cameraOrientation, int deviecAutoRotateAngle);

        // preview callback
        void onPreviewFrame(byte[] data, int width, int height, int cameraOrientation);

        void onPreviewFrame(Bitmap data, int width, int height, int cameraOrientation);
    }

    private PreviewCallback previewCallback;

    public Camera2View(Context context) {
        super(context);
        init(context);
    }

    public Camera2View(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);


    }

    public Camera2View(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public boolean isFrontCamera() {
        return true;
    }

    public Size getPreviewSize() {
        return mPreviewSize;
    }

    //用于水平翻转镜像
    private void horizontalMirrorData() {
        Matrix matrix = mPreviewView.getTransform(new Matrix());
        matrix.setScale(-1, 1);
        int width = mPreviewView.getWidth();
        matrix.postTranslate(width, 0);
        mPreviewView.setTransform(matrix);
    }

    public void show(Activity activity) {

        mScreenOrientationEventListener = new OrientationEventListener(activity) {
            @Override
            public void onOrientationChanged(int i) {
                // i的范围是0～359
                // 屏幕左边在顶部的时候 i = 90;
                // 屏幕顶部在底部的时候 i = 180;
                // 屏幕右边在底部的时候 i = 270;
                // 正常情况默认i = 0;

                if (45 <= i && i < 135) {
                    mScreenExifOrientation = ExifInterface.ORIENTATION_ROTATE_180;
                } else if (135 <= i && i < 225) {
                    mScreenExifOrientation = ExifInterface.ORIENTATION_ROTATE_270;
                } else if (225 <= i && i < 315) {
                    mScreenExifOrientation = ExifInterface.ORIENTATION_NORMAL;
                } else {
                    mScreenExifOrientation = ExifInterface.ORIENTATION_ROTATE_90;
                }
            }
        };

        mScreenOrientationEventListener.enable();

        initLooper();

        initView();
    }

    /**
     * 初始化
     *
     * @param context
     */
    private void init(Context context) {
        this.context = context;

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.camera_our_layout, null);
        mPreviewView = view.findViewById(R.id.mPreviewView);

        addView(view);
    }

    /**
     * 很多过程都变成了异步的了，所以这里需要一个子线程的looper
     */
    private void initLooper() {
        /**
         * Google封装的
         * Handy class for starting a new thread that has a looper. The looper can then be
         * used to create handler classes. Note that start() must still be called.
         */
        HandlerThread mThreadHandler = new HandlerThread("CAMERA2");
        mThreadHandler.start();
        mHandler = new Handler(mThreadHandler.getLooper());
    }

    private int getPreviewOrientation(Context mContext, String camera2Id) {
        CameraManager mCameraManager = (CameraManager) mContext.getSystemService(CAMERA_SERVICE);
        CameraCharacteristics characteristics = null;
        try {
            if (mCameraManager != null) {
                characteristics = mCameraManager.getCameraCharacteristics(camera2Id);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        int result = 0;
        if (characteristics != null) {
            Integer mCameraOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            WindowManager wm = ((WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE));
            if (wm != null) {
                int rotation = wm.getDefaultDisplay().getRotation();

                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraMetadata.LENS_FACING_FRONT) {
                    result = (mCameraOrientation + ORIENTATION.get(rotation)) % 360;
                    result = (360 - result) % 360;
                } else {
                    result = (mCameraOrientation - ORIENTATION.get(rotation) + 180) % 360;
                }
            }

        }
        return result;
    }

    /**
     * 可以通过TextureView或者SurfaceView
     */
    private void initView() {

        //mPreviewView.setRotation(getPreviewOrientation(context, mCameraID));
        /**
         * This listener can be used to be notified when the surface texture
         * associated with this texture view is available.
         * 当与此纹理视图关联的表面纹理可用时，可以使用此侦听器来通知
         */

        mPreviewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {

            /**
             * Invoked when a {@link TextureView}'s SurfaceTexture is ready for use.
             *
             * @param surface The surface returned by
             *                {@link TextureView#getSurfaceTexture()}
             * @param width The width of the surface
             * @param height The height of the surface
             */
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                try {
                    /**
                     * 获得所有摄像头的管理者CameraManager
                     */
                    cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
                    /**
                     * 获得某个摄像头的特征，支持的参数
                     */
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(mCameraID);
                    /**
                     * 支持的STREAM CONFIGURATION
                     */
                    StreamConfigurationMap map = characteristics
                            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    /**
                     * 摄像头支持的预览Size数组
                     */
                    Size[] sizes = map != null ? map.getOutputSizes(SurfaceTexture.class) : new Size[0];
                    /**
                     * 获取全屏像素 坚果pro2 高度获取不准确
                     */
                    DisplayMetrics dm = new DisplayMetrics();
                    WindowManager windowManager = (WindowManager) context
                            .getSystemService(Context.WINDOW_SERVICE);
                    windowManager.getDefaultDisplay().getMetrics(dm);

                    /**
                     * 获取最佳预览尺寸
                     */
                    mPreviewSize = getCloselyPreSize(dm.heightPixels, dm.widthPixels, sizes);

                    Lag.i("预览 宽度:" + mPreviewSize.getWidth() + " 高度:" + mPreviewSize.getHeight());

//                    if (previewCallback != null) {
//                        previewCallback.onGetPreviewOptimalSize(mPreviewSize.getWidth(), mPreviewSize.getHeight(), mOrientationAngle, mDeviecAutoRotateAngle);
//                    }
                    /**
                     * 打开相机
                     */
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        return;
                    }
                    cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mHandler);

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            /**
             * Invoked when the {@link SurfaceTexture}'s buffers size changed.
             *
             * @param surface The surface returned by
             *                {@link TextureView#getSurfaceTexture()}
             * @param width The new width of the surface
             * @param height The new height of the surface
             */
            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            /**
             * Invoked when the specified {@link SurfaceTexture} is about to be destroyed.
             * If returns true, no rendering should happen inside the surface texture after this method
             * is invoked. If returns false, the client needs to call {@link SurfaceTexture#release()}.
             * Most applications should return true.
             *
             * @param surface The surface about to be destroyed
             */
            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            /**
             * 这个方法要注意一下，因为每有一帧画面，都会回调一次此方法
             * Invoked when the specified {@link SurfaceTexture} is updated through
             * {@link SurfaceTexture#updateTexImage()}.
             *
             * @param surface The surface just updated
             */
            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * 通过对比得到与宽高比最接近的尺寸（如果有相同尺寸，优先选择）
     *
     * @param surfaceWidth  需要被进行对比的原宽
     * @param surfaceHeight 需要被进行对比的原高
     * @param preSizeList   需要对比的预览尺寸列表
     * @return 得到与原宽高比例最接近的尺寸
     */
    private Size getCloselyPreSize(int surfaceWidth, int surfaceHeight, Size[] preSizeList) {

        // 当屏幕为垂直的时候需要把宽高值进行调换，保证宽大于高
        //        if (mIsPortrait) {
        //            ReqTmpWidth = surfaceHeight;
        //            ReqTmpHeight = surfaceWidth;
        //        } else {
        //        }
        /**
         * 先查找preview中是否存在与surfaceView相同宽高的尺寸
         */
        for (Size size : preSizeList) {
            if (size.getWidth() == surfaceWidth && size.getHeight() == surfaceHeight) {
                return size;
            }
        }

        /**
         * 得到与传入的宽高比最接近的size
         */
        float reqRatio = (float) surfaceWidth / surfaceHeight;
        float curRatio;
        float deltaRatio;
        float deltaRatioMin = Float.MAX_VALUE;
        Size retSize = null;
        for (Size size : preSizeList) {
            curRatio = (float) size.getWidth() / size.getHeight();
            deltaRatio = Math.abs(reqRatio - curRatio);
            if (deltaRatio < deltaRatioMin) {
                deltaRatioMin = deltaRatio;
                retSize = size;
            }
        }

        return retSize;
    }

    /**
     * 设备监听
     */
    private CameraDevice.StateCallback mCameraDeviceStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview(cameraDevice);
            //Log.i("相机", "打开");
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            //Log.i("相机", "断开");
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            //Log.i("相机", "错误");
        }

        @Override
        public void onClosed(CameraDevice camera) {
            //Log.i("相机", "关闭");
        }

    };

    /**
     * 开始预览，主要是camera.createCaptureSession这段代码很重要，创建会话
     */
    private void startPreview(CameraDevice camera) {

        SurfaceTexture texture = mPreviewView.getSurfaceTexture();

        // 这里设置的就是预览大小
        texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        Surface surface = new Surface(texture);
        try {
            // 设置捕获请求为预览，这里还有拍照啊，录像等
            mPreviewBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        // 就是在这里，通过这个set(key,value)方法，设置曝光啊，自动聚焦等参数！！ 如下举例：
        // mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

        mImageReader = ImageReader.newInstance(mPreviewSize.getHeight(), mPreviewSize.getWidth(),
                ImageFormat.YUV_420_888/* 此处还有很多格式，比如我所用到YUV等 */, 2/*
                 * 最大的图片数，
                 * mImageReader里能获取到图片数
                 * ，
                 * 但是实际中是2+1张图片，就是多一张
                 */);

        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);
        // 这里一定分别add两个surface，一个Textureview的，一个ImageReader的，如果没add，会造成没摄像头预览，或者没有ImageReader的那个回调！！
        mPreviewBuilder.addTarget(surface);
        //设置拍照方向
        mPreviewBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(mScreenExifOrientation));

        mPreviewBuilder.addTarget(mImageReader.getSurface());
        try {
            camera.createCaptureSession(
                    Arrays.asList(surface, mImageReader.getSurface()),
                    mSessionStateCallback, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 会话监听
     */
    private CameraCaptureSession.StateCallback mSessionStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            cameraCaptureSession = session;
            updatePreview(session);
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };

    /**
     * 更新预览
     */
    private void updatePreview(CameraCaptureSession session) {
        try {
            session.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void setPreviewCallback(PreviewCallback previewCallback) {
        this.previewCallback = previewCallback;
    }

    private int[] getPicturePixel(Bitmap bitmap) {

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // 保存所有的像素的数组，图片宽×高
        int[] pixels = new int[width * height];

        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int clr = pixels[i];
            int red = (clr & 0x00ff0000) >> 16; // 取高两位
            int green = (clr & 0x0000ff00) >> 8; // 取中两位
            int blue = clr & 0x000000ff; // 取低两位
            Log.d("tag", "r=" + red + ",g=" + green + ",b=" + blue);
        }
        return pixels;
    }

    /**
     * Callback interface for being notified that a new image is available.
     * <p>
     * <p>
     * The onImageAvailable is called per image basis, that is, callback fires for every new frame
     * available from ImageReader.
     * </p>
     */
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            //获取最新的一帧的Image
            Image image = reader.acquireLatestImage();
            //因为是ImageFormat.JPEG格式，所以 image.getPlanes()返回的数组只有一个，也就是第0个。
            if (image == null) {
                return;
            }
            //转成NV21数据，可用于直播
            byte[] data = ImageUtil.getBytesFromImageAsType(image, ImageUtil.NV21);
            if (previewCallback != null) {
                previewCallback.onPreviewFrame(data, mPreviewSize.getWidth(), mPreviewSize.getHeight(), mScreenExifOrientation);
            }
//            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
//            byte[] bytes = new byte[byteBuffer.remaining()];
//            byteBuffer.get(bytes);
//            //ImageFormat.JPEG格式直接转化为Bitmap格式。
//            Bitmap temp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);//ImageFormat.JPEG格式直接转化为Bitmap格式。
//            //因为摄像机数据默认是横的，所以需要旋转90度。
//            Bitmap newBitmap = BitmapUtil.rotateBitmap(temp, 270);
//            if (previewCallback != null) {
//                previewCallback.onPreviewFrame(newBitmap, temp.getWidth(), temp.getHeight(), mScreenExifOrientation);
//            }
            //一定需要close，否则不会收到新的Image回调。
            image.close();
        }
    };

    /**
     * 暂停
     */
    public void onPause() {

        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }

    /**
     * 恢复
     */
    public void onResume() {

        if (cameraManager != null) {
            /**
             * 恢复打开相机
             */
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    // TODO: Consider calling
                    //    ActivityCompat#requestPermissions
                    // here to request the missing permissions, and then overriding
                    //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                    //                                          int[] grantResults)
                    // to handle the case where the user grants the permission. See the documentation
                    // for ActivityCompat#requestPermissions for more details.
                    return;
                }
                cameraManager.openCamera(mCameraID, mCameraDeviceStateCallback, mHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 保存线程
     */
    public static class imageSaver implements Runnable {
        private Image mImage;
        private File mImageFile;

        public imageSaver(Image image) {
            mImage = image;
        }

        @Override
        public void run() {
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            File f = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera2/");
            if (!f.exists()) {
                mImageFile.mkdirs();
            }
            mImageFile = new File(Environment.getExternalStorageDirectory() + "/DCIM/Camera2/" + System.currentTimeMillis() + ".jpg");
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(mImageFile);
                fos.write(data, 0, data.length);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImageFile = null;
                if (fos != null) {
                    try {
                        fos.close();
                        fos = null;
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            mImage.close();
            takePhoto = false;
        }
    }

    /**
     * 拍照
     */
    public void capture() {
        try {
            takePhoto = true;
            //首先我们创建请求拍照的CaptureRequest
            final CaptureRequest.Builder mCaptureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            //获取屏幕方向
            int rotation = mScreenExifOrientation;
            //设置CaptureRequest输出到mImageReader
            mCaptureBuilder.addTarget(mImageReader.getSurface());
            //设置拍照方向
            mCaptureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATION.get(rotation));
            //这个回调接口用于拍照结束时重启预览，因为拍照会导致预览停止
            CameraCaptureSession.CaptureCallback mImageSavedCallback = new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    Toast.makeText(context, "Image Saved!", Toast.LENGTH_SHORT).show();
                    //重启预览
                    restartPreview();
                }
            };
            //停止预览
            cameraCaptureSession.stopRepeating();
            //开始拍照，然后回调上面的接口重启预览，因为mCaptureBuilder设置ImageReader作为target，所以会自动回调ImageReader的onImageAvailable()方法保存图片
            cameraCaptureSession.capture(mCaptureBuilder.build(), mImageSavedCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void restartPreview() {
        try {
            //执行setRepeatingRequest方法就行了，注意mCaptureRequest是之前开启预览设置的请求
            cameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), null, mHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
}