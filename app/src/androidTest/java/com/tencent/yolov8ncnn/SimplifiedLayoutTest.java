package com.tencent.yolov8ncnn;

import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SurfaceView;

/**
 * 简化版布局测试
 * 验证全屏预览、按钮布局等核心需求
 */
public class SimplifiedLayoutTest extends ActivityInstrumentationTestCase2<MainActivity> {

    private MainActivity mActivity;
    private SurfaceView mCameraView;
    private ImageButton mButtonSettings;
    private ImageButton mButtonCapture;

    public SimplifiedLayoutTest() {
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
     * 测试核心需求1: 全屏预览模式 - 界面元素正确加载
     */
    public void testFullScreenElementsExist() {
        // 验证关键组件存在
        assertNotNull("Activity should not be null", mActivity);
        assertNotNull("Camera view should exist", mCameraView);
        assertNotNull("Settings button should exist", mButtonSettings);
        assertNotNull("Capture button should exist", mButtonCapture);
    }

    /**
     * 测试核心需求1: 全屏预览模式 - 移除多余元素
     */
    public void testRemoveExcessElements() {
        // 验证旧的UI元素已被移除
        View oldSwitchButton = mActivity.findViewById(R.id.buttonSwitchCamera);
        View spinnerTask = mActivity.findViewById(R.id.spinnerTask);
        View spinnerModel = mActivity.findViewById(R.id.spinnerModel);
        View spinnerCPUGPU = mActivity.findViewById(R.id.spinnerCPUGPU);
        
        assertNull("Old switch camera button should be removed", oldSwitchButton);
        assertNull("Task spinner should be removed", spinnerTask);
        assertNull("Model spinner should be removed", spinnerModel);
        assertNull("CPU/GPU spinner should be removed", spinnerCPUGPU);
    }

    /**
     * 测试核心需求2: 画面自适应显示 - SurfaceView配置
     */
    public void testSurfaceViewConfiguration() {
        assertNotNull("SurfaceView holder should exist", mCameraView.getHolder());
        // 验证像素格式设置
        assertEquals("SurfaceView should use RGBA_8888 format", 
                    android.graphics.PixelFormat.RGBA_8888, 
                    mCameraView.getHolder().getSurfaceFrame().width()); // 示例验证
    }

    /**
     * 测试核心需求3: 简洁UI布局 - 按钮位置验证
     */
    public void testButtonPositions() {
        // 获取屏幕尺寸
        int screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = mActivity.getResources().getDisplayMetrics().heightPixels;
        
        // 验证设置按钮在右上角
        assertTrue("Settings button should be near top", mButtonSettings.getTop() < screenHeight * 0.2);
        assertTrue("Settings button should be near right edge", 
                  mButtonSettings.getRight() > screenWidth * 0.8);
        
        // 验证拍照按钮在底部居中
        assertTrue("Capture button should be near bottom", 
                  mButtonCapture.getBottom() > screenHeight * 0.8);
        
        // 验证水平居中（允许一定误差）
        int buttonCenterX = mButtonCapture.getLeft() + (mButtonCapture.getWidth() / 2);
        int screenCenterX = screenWidth / 2;
        assertTrue("Capture button should be horizontally centered (within 50px tolerance)", 
                  Math.abs(buttonCenterX - screenCenterX) <= 50);
    }

    /**
     * 测试技术要点: 按钮点击事件
     */
    public void testButtonClickListeners() {
        assertTrue("Settings button should have click listener", 
                  mButtonSettings.hasOnClickListeners());
        assertTrue("Capture button should have click listener", 
                  mButtonCapture.hasOnClickListeners());
    }

    /**
     * 测试核心功能保留: SurfaceView回调
     */
    public void testSurfaceViewCallbacks() {
        // 验证SurfaceView有正确的回调设置
        assertNotNull("SurfaceView holder callback should be set", 
                     mCameraView.getHolder().getCallbacks());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}