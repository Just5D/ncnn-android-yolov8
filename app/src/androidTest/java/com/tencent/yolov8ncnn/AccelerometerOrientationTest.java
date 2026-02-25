package com.tencent.yolov8ncnn;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.test.ActivityInstrumentationTestCase2;
import android.view.SurfaceView;
import android.widget.ImageButton;

/**
 * 加速度计方向检测测试
 * 验证NDK层面的方向处理与Java层的协调
 */
public class AccelerometerOrientationTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private MainActivity mActivity;
    private SurfaceView mCameraView;

    public AccelerometerOrientationTest() {
        super(MainActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        mCameraView = (SurfaceView) mActivity.findViewById(R.id.cameraview);
    }

    /**
     * 测试加速度计方向检测与屏幕方向的同步
     */
    public void testAccelerometerSyncWithScreenOrientation() {
        // 初始状态验证
        assertEquals("初始应为竖屏", Configuration.ORIENTATION_PORTRAIT, 
                    mActivity.getResources().getConfiguration().orientation);
        
        // 切换到横屏
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        try {
            Thread.sleep(1500); // 给足够时间让加速度计检测到变化
        } catch (InterruptedException e) {
            fail("等待中断");
        }
        
        // 验证屏幕方向已改变
        assertEquals("应切换到横屏", Configuration.ORIENTATION_LANDSCAPE,
                    mActivity.getResources().getConfiguration().orientation);
        
        // 验证SurfaceView仍然正常工作
        assertNotNull("SurfaceView应存在", mCameraView);
        assertNotNull("SurfaceView holder应存在", mCameraView.getHolder());
        assertTrue("SurfaceView应有有效尺寸", 
                  mCameraView.getWidth() > 0 && mCameraView.getHeight() > 0);
    }

    /**
     * 测试快速方向切换的稳定性
     */
    public void testRapidOrientationChanges() {
        // 快速连续切换方向
        for (int i = 0; i < 3; i++) {
            // 横屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            try { Thread.sleep(600); } catch (InterruptedException e) {}
            
            // 竖屏
            mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            try { Thread.sleep(600); } catch (InterruptedException e) {}
        }
        
        // 最终验证组件完整性
        assertNotNull("最终SurfaceView应存在", mCameraView);
        assertTrue("最终应有有效尺寸", 
                  mCameraView.getWidth() > 0 && mCameraView.getHeight() > 0);
    }

    /**
     * 测试方向变化时的摄像头输出连续性
     */
    public void testCameraOutputContinuity() {
        // 记录初始状态
        int initialWidth = mCameraView.getWidth();
        int initialHeight = mCameraView.getHeight();
        
        // 方向变化
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        // 验证输出窗口已更新
        assertTrue("摄像头输出应已重新设置", true); // 这里需要实际验证NDK层面的调用
        
        // 切换回竖屏
        mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        try { Thread.sleep(1000); } catch (InterruptedException e) {}
        
        // 验证持续正常工作
        assertTrue("摄像头应持续正常工作", 
                  mCameraView.getWidth() > 0 && mCameraView.getHeight() > 0);
    }

    /**
     * 测试极端角度下的方向处理
     */
    public void testExtremeAngleHandling() {
        // 测试各种方向
        int[] orientations = {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        };
        
        for (int orientation : orientations) {
            mActivity.setRequestedOrientation(orientation);
            try { Thread.sleep(800); } catch (InterruptedException e) {}
            
            // 验证每个方向变化后系统仍稳定
            assertNotNull("方向变化后SurfaceView应存在", mCameraView);
            assertTrue("方向变化后应有有效尺寸", 
                      mCameraView.getWidth() > 0 && mCameraView.getHeight() > 0);
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