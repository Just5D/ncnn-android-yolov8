package com.tencent.yolov8ncnn;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.test.ActivityInstrumentationTestCase2;
import android.view.SurfaceView;
import android.widget.ImageButton;

/**
 * 完整方向处理测试
 * 验证Java层与NDK层方向处理的完整协调
 */
public class CompleteOrientationHandlingTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private MainActivity mActivity;
    private SurfaceView mCameraView;
    private ImageButton mButtonSettings;
    private ImageButton mButtonCapture;

    public CompleteOrientationHandlingTest() {
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
     * 测试完整的方向处理流程
     */
    public void testCompleteOrientationHandlingFlow() {
        // 1. 验证初始状态
        assertEquals("初始应为竖屏", Configuration.ORIENTATION_PORTRAIT, 
                    mActivity.getResources().getConfiguration().orientation);
        
        // 2. 切换到横屏并验证
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        waitForOrientationChange();
        
        assertEquals("应成功切换到横屏", Configuration.ORIENTATION_LANDSCAPE,
                    mActivity.getResources().getConfiguration().orientation);
        
        // 3. 验证UI元素正确布局
        validateLandscapeLayout();
        
        // 4. 验证摄像头功能正常
        validateCameraFunctionality();
        
        // 5. 切换回竖屏并验证
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        waitForOrientationChange();
        
        assertEquals("应成功切换回竖屏", Configuration.ORIENTATION_PORTRAIT,
                    mActivity.getResources().getConfiguration().orientation);
        
        // 6. 最终验证
        validatePortraitLayout();
        validateCameraFunctionality();
    }

    /**
     * 测试NDK与Java层的协调性
     */
    public void testNDKJavaCoordination() {
        // 模拟多次方向变化，测试系统的稳定性
        for (int cycle = 0; cycle < 2; cycle++) {
            // 横屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            waitForOrientationChange();
            assertTrue("横屏状态下应保持功能正常", validateSystemIntegrity());
            
            // 竖屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            waitForOrientationChange();
            assertTrue("竖屏状态下应保持功能正常", validateSystemIntegrity());
        }
    }

    /**
     * 测试边界条件和异常情况
     */
    public void testBoundaryConditions() {
        // 测试快速连续方向切换
        for (int i = 0; i < 4; i++) {
            int orientation = (i % 2 == 0) ? 
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : 
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            mActivity.setRequestedOrientation(orientation);
            try { Thread.sleep(400); } catch (InterruptedException e) {}
        }
        
        waitForOrientationChange();
        assertTrue("快速切换后系统应保持稳定", validateSystemIntegrity());
        
        // 测试极端方向
        int[] extremeOrientations = {
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        };
        
        for (int orientation : extremeOrientations) {
            mActivity.setRequestedOrientation(orientation);
            waitForOrientationChange();
            assertTrue("极端方向下应保持稳定", validateSystemIntegrity());
        }
    }

    /**
     * 测试用户交互在方向变化中的表现
     */
    public void testUserInteractionDuringOrientationChanges() {
        // 在方向变化过程中模拟用户操作
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        // 在变化过程中点击按钮
        try {
            Thread.sleep(300);
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mButtonSettings != null) {
                        mButtonSettings.performClick();
                    }
                }
            });
            
            Thread.sleep(700); // 等待方向变化完成
        } catch (InterruptedException e) {
            fail("测试过程中断");
        }
        
        assertTrue("方向变化期间的用户操作不应导致崩溃", validateSystemIntegrity());
    }

    // 辅助方法
    private void waitForOrientationChange() {
        try {
            Thread.sleep(1200); // 给足够时间完成方向变化
        } catch (InterruptedException e) {
            fail("等待方向变化时被中断");
        }
    }

    private void validateLandscapeLayout() {
        int screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = mActivity.getResources().getDisplayMetrics().heightPixels;
        
        // 验证屏幕确实是横屏
        assertTrue("屏幕应为横屏", screenWidth > screenHeight);
        
        // 验证SurfaceView填满屏幕
        assertEquals("SurfaceView宽度应匹配屏幕", screenWidth, mCameraView.getWidth());
        assertEquals("SurfaceView高度应匹配屏幕", screenHeight, mCameraView.getHeight());
        
        // 验证按钮位置正确
        assertTrue("设置按钮应在右上角区域", 
                  mButtonSettings.getTop() < screenHeight * 0.2 && 
                  mButtonSettings.getRight() > screenWidth * 0.8);
        
        assertTrue("拍照按钮应在底部居中区域",
                  mButtonCapture.getBottom() > screenHeight * 0.8);
    }

    private void validatePortraitLayout() {
        int screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = mActivity.getResources().getDisplayMetrics().heightPixels;
        
        // 验证屏幕确实是竖屏
        assertTrue("屏幕应为竖屏", screenHeight > screenWidth);
        
        // 验证SurfaceView填满屏幕
        assertEquals("SurfaceView宽度应匹配屏幕", screenWidth, mCameraView.getWidth());
        assertEquals("SurfaceView高度应匹配屏幕", screenHeight, mCameraView.getHeight());
    }

    private void validateCameraFunctionality() {
        assertNotNull("SurfaceView应存在", mCameraView);
        assertNotNull("SurfaceView holder应存在", mCameraView.getHolder());
        assertNotNull("Surface应存在", mCameraView.getHolder().getSurface());
        assertTrue("应有有效的显示尺寸", 
                  mCameraView.getWidth() > 0 && mCameraView.getHeight() > 0);
    }

    private boolean validateSystemIntegrity() {
        try {
            // 检查所有关键组件
            assertNotNull("Activity应存在", mActivity);
            assertNotNull("SurfaceView应存在", mCameraView);
            assertNotNull("设置按钮应存在", mButtonSettings);
            assertNotNull("拍照按钮应存在", mButtonCapture);
            
            // 检查尺寸有效性
            assertTrue("SurfaceView应有有效尺寸", 
                      mCameraView.getWidth() > 0 && mCameraView.getHeight() > 0);
            
            return true;
        } catch (Exception e) {
            return false;
        }
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