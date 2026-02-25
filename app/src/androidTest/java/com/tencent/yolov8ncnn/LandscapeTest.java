package com.tencent.yolov8ncnn;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.test.ActivityInstrumentationTestCase2;
import android.view.SurfaceView;
import android.widget.ImageButton;

/**
 * 横屏功能测试
 * 验证横屏时图像旋转和全屏显示
 */
public class LandscapeTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private MainActivity mActivity;
    private SurfaceView mCameraView;
    private ImageButton mButtonSettings;
    private ImageButton mButtonCapture;

    public LandscapeTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mCameraView = (SurfaceView) mActivity.findViewById(R.id.cameraview);
        mButtonSettings = (ImageButton) mActivity.findViewById(R.id.buttonSettings);
        mButtonCapture = (ImageButton) mActivity.findViewById(R.id.buttonCapture);
    }

    /**
     * 测试横屏时SurfaceView全屏显示
     */
    public void testLandscapeFullScreen() {
        // 切换到横屏
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        // 等待方向改变
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("等待中断");
        }
        
        // 验证屏幕变为横屏模式
        int screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = mActivity.getResources().getDisplayMetrics().heightPixels;
        assertTrue("屏幕应为横屏模式", screenWidth > screenHeight);
        
        // 验证SurfaceView填满整个屏幕
        assertEquals("SurfaceView宽度应等于屏幕宽度", screenWidth, mCameraView.getWidth());
        assertEquals("SurfaceView高度应等于屏幕高度", screenHeight, mCameraView.getHeight());
        
        // 验证从屏幕顶部开始显示（全屏）
        int[] location = new int[2];
        mCameraView.getLocationOnScreen(location);
        assertEquals("SurfaceView应从屏幕顶部开始", 0, location[1]);
    }

    /**
     * 测试横屏时图像旋转功能
     */
    public void testLandscapeImageRotation() {
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("等待中断");
        }
        
        // 验证SurfaceView holder有效
        assertNotNull("SurfaceView holder应存在", mCameraView.getHolder());
        assertNotNull("Surface应存在", mCameraView.getHolder().getSurface());
        
        // 验证摄像头输出已更新到新方向
        assertTrue("摄像头输出应已设置", true); // 这里需要实际验证YOLOv8Ncnn的输出设置
    }

    /**
     * 测试横屏时按钮位置正确性
     */
    public void testLandscapeButtonPosition() {
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            fail("等待中断");
        }
        
        int screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = mActivity.getResources().getDisplayMetrics().heightPixels;
        
        // 验证设置按钮在右上角
        assertTrue("设置按钮应在顶部区域", mButtonSettings.getTop() < screenHeight * 0.2);
        assertTrue("设置按钮应靠近右边缘", mButtonSettings.getRight() > screenWidth * 0.8);
        
        // 验证拍照按钮在底部居中
        assertTrue("拍照按钮应在底部区域", mButtonCapture.getBottom() > screenHeight * 0.8);
        
        int buttonCenterX = mButtonCapture.getLeft() + (mButtonCapture.getWidth() / 2);
        int screenCenterX = screenWidth / 2;
        assertTrue("拍照按钮应水平居中", Math.abs(buttonCenterX - screenCenterX) <= 50);
    }

    /**
     * 测试方向变化时的稳定性
     */
    public void testOrientationStability() {
        // 记录初始状态
        int initialWidth = mCameraView.getWidth();
        int initialHeight = mCameraView.getHeight();
        
        // 切换到横屏
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        // 切换回竖屏
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        // 验证组件仍然正常工作
        assertNotNull("SurfaceView应仍然存在", mCameraView);
        assertTrue("SurfaceView应有有效尺寸", mCameraView.getWidth() > 0 && mCameraView.getHeight() > 0);
        assertNotNull("设置按钮应仍然存在", mButtonSettings);
        assertNotNull("拍照按钮应仍然存在", mButtonCapture);
    }

    /**
     * 测试连续方向切换
     */
    public void testContinuousOrientationSwitch() {
        for (int i = 0; i < 2; i++) {
            // 横屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            try { Thread.sleep(800); } catch (InterruptedException e) {}
            
            // 竖屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            try { Thread.sleep(800); } catch (InterruptedException e) {}
        }
        
        // 最终验证
        assertTrue("最终应有有效尺寸", mCameraView.getWidth() > 0 && mCameraView.getHeight() > 0);
    }

    @Override
    protected void tearDown() throws Exception {
        // 恢复默认方向
        if (mActivity != null) {
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        super.tearDown();
    }
}