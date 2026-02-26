// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

package com.tencent.yolov8ncnn;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

public class MainActivity extends Activity implements SurfaceHolder.Callback
{
    // 添加native方法声明
    public native void nativeCapture();
    public static final int REQUEST_CAMERA = 100;

    private YOLOv8Ncnn yolov8ncnn = new YOLOv8Ncnn();
    private int facing = 0;

    private Spinner spinnerTask;
    private Spinner spinnerModel;
    private Spinner spinnerCPUGPU;
    private int current_task = 0;
    private int current_model = 0;
    private int current_cpugpu = 0;

    private SurfaceView cameraView;
    private Button btnCapture;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        
        // 强制竖屏模式，屏蔽横屏消息
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        
        setContentView(R.layout.main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        cameraView = (SurfaceView) findViewById(R.id.cameraview);

        cameraView.getHolder().setFormat(PixelFormat.RGBA_8888);
        cameraView.getHolder().addCallback(this);

        Button buttonSwitchCamera = (Button) findViewById(R.id.buttonSwitchCamera);

        // 初始化拍照按钮
        btnCapture = (Button) findViewById(R.id.btn_capture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 检查存储权限
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                        != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
                        return;
                    }
                }
                
                // 调用 nativeCapture
                nativeCapture();
                
                // 显示拍照提示
                Toast.makeText(MainActivity.this, "拍照中...", Toast.LENGTH_SHORT).show();
            }
        });
        buttonSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {

                int new_facing = 1 - facing;

                yolov8ncnn.closeCamera();

                yolov8ncnn.openCamera(new_facing);

                facing = new_facing;
            }
        });

        spinnerTask = (Spinner) findViewById(R.id.spinnerTask);
        spinnerTask.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_task)
                {
                    current_task = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        spinnerModel = (Spinner) findViewById(R.id.spinnerModel);
        spinnerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_model)
                {
                    current_model = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        spinnerCPUGPU = (Spinner) findViewById(R.id.spinnerCPUGPU);
        spinnerCPUGPU.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long id)
            {
                if (position != current_cpugpu)
                {
                    current_cpugpu = position;
                    reload();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0)
            {
            }
        });

        reload();
    }

    private void reload()
    {
        boolean ret_init = yolov8ncnn.loadModel(getAssets(), current_task, current_model, current_cpugpu);
        if (!ret_init)
        {
            Log.e("MainActivity", "yolov8ncnn loadModel failed");
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        yolov8ncnn.setOutputWindow(holder.getSurface());
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder)
    {
    }

    @Override
    public void onResume()
    {
        super.onResume();
        
        // 每次恢复时都强制竖屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        if (ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }

        yolov8ncnn.openCamera(facing);
    }
    
    // 屏蔽所有配置变化，包括方向变化
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        // 不调用super.onConfigurationChanged()来完全屏蔽配置变化
        // 强制保持竖屏状态
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    /**
     * JNI回调方法 - 处理nativeCapture的错误
     */
    private void onCaptureError(String errorMessage) {
        Log.e("MainActivity", "Capture error: " + errorMessage);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, 
                              "拍照失败: " + errorMessage, 
                              Toast.LENGTH_LONG).show();
            }
        });
    }
    
    /**
     * JNI回调方法 - 处理nativeCapture的结果
     */
    private void onCaptureComplete(byte[] boxedFrame, byte[] originalFrame, int width, int height) {
        try {
            Log.d("MainActivity", "onCaptureComplete: " + width + "x" + height);
            
            // 添加参数验证
            if (boxedFrame == null || originalFrame == null) {
                throw new IllegalArgumentException("Frame data is null");
            }
            
            int expectedRgbaSize = width * height * 4;
            int expectedRgbSize = width * height * 3;
            
            if (boxedFrame.length != expectedRgbaSize) {
                throw new IllegalArgumentException("RGBA frame size mismatch: expected " + 
                    expectedRgbaSize + ", got " + boxedFrame.length);
            }
            
            if (originalFrame.length != expectedRgbSize) {
                throw new IllegalArgumentException("RGB frame size mismatch: expected " + 
                    expectedRgbSize + ", got " + originalFrame.length);
            }
            
            // 1. 从RGBA数据创建带检测框的Bitmap（用于显示）
            final Bitmap displayBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            displayBitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(boxedFrame));
            
            // 2. 从RGB数据创建原始Bitmap（用于保存）
            final Bitmap originalBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            
            // **RGB 转 ARGB**
            // JNI层保持RGB格式，这里将RGB转换为ARGB用于显示和保存
            int[] pixels = new int[width * height];
            for (int i = 0; i < width * height; i++) {
                int r = originalFrame[i * 3] & 0xFF;      // R通道
                int g = originalFrame[i * 3 + 1] & 0xFF;  // G通道
                int b = originalFrame[i * 3 + 2] & 0xFF;  // B通道
                pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;  // ARGB格式
            }
            originalBitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        // 显示带检测框的照片
                        showCapturedImage(displayBitmap);
                        
                        // 保存原始图像
                        saveBitmapToGallery(originalBitmap, "original_");
                        
                        // 保存带检测框的图像
                        saveBitmapToGallery(displayBitmap, "detected_");
                        
                        Toast.makeText(MainActivity.this, 
                                      "已保存原始图像和检测结果", 
                                      Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("MainActivity", "UI操作异常", e);
                        Toast.makeText(MainActivity.this, 
                                      "显示或保存失败: " + e.getMessage(), 
                                      Toast.LENGTH_SHORT).show();
                    } finally {
                        // 延迟回收Bitmap，确保UI操作完成
                        cameraView.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (displayBitmap != null && !displayBitmap.isRecycled()) {
                                        displayBitmap.recycle();
                                    }
                                    if (originalBitmap != null && !originalBitmap.isRecycled()) {
                                        originalBitmap.recycle();
                                    }
                                } catch (Exception e) {
                                    Log.w("MainActivity", "Bitmap回收异常", e);
                                }
                            }
                        }, 1000); // 1秒后回收
                    }
                }
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("MainActivity", "onCaptureComplete error: " + e.getMessage());
            
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, 
                                  "拍照失败: " + e.getMessage(), 
                                  Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
    
    /**
     * 拍照并保存图像
     */
    private void captureAndSaveImage() {
        Log.d("MainActivity", "开始拍照");
        
        // 检查存储权限
        if (!checkStoragePermission()) {
            requestStoragePermission();
            return;
        }
        
        // 在UI线程中延迟执行拍照，确保SurfaceView完全渲染
        cameraView.postDelayed(new Runnable() {
            @Override
            public void run() {
                // 获取当前帧并保存
                Bitmap bitmap = getCurrentFrameBitmap();
                if (bitmap != null) {
                    saveBitmapToGallery(bitmap);
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "获取图像失败", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }, 200); // 延迟200ms确保渲染完成
    }

    /**
     * 检查存储权限
     */
    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) 
                   == PackageManager.PERMISSION_GRANTED;
        }
        return true; // 低版本默认有权限
    }

    /**
     * 请求存储权限
     */
    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 101);
        }
    }

    /**
     * 获取当前帧的Bitmap
     * 从NDK层获取真实的相机帧数据
     */
    private Bitmap getCurrentFrameBitmap() {
        try {
            Log.d("MainActivity", "开始获取相机帧数据");
            
            // 调用NDK方法获取当前帧数据
            byte[] rgbData = yolov8ncnn.getCurrentFrame();
            
            Log.d("MainActivity", "NDK返回数据长度: " + (rgbData != null ? rgbData.length : 0));
            
            if (rgbData == null || rgbData.length == 0) {
                Log.e("MainActivity", "获取相机帧数据失败");
                return createFallbackBitmap();
            }
            
            // 默认假设是640x480的图像（可以根据实际情况调整）
            int width = 640;
            int height = 480;
            int expectedSize = width * height * 3;
            
            Log.d("MainActivity", "原始数据大小: " + rgbData.length + ", 期望大小: " + expectedSize);
            
            // 如果数据大小不符合预期，尝试其他常见分辨率
            if (rgbData.length == 480 * 640 * 3) {
                width = 640;
                height = 480;
                Log.d("MainActivity", "检测到640x480分辨率");
            } else if (rgbData.length == 640 * 480 * 3) {
                width = 640;
                height = 480;
                Log.d("MainActivity", "检测到640x480分辨率(转置)");
            } else if (rgbData.length == 1280 * 720 * 3) {
                width = 1280;
                height = 720;
                Log.d("MainActivity", "检测到1280x720分辨率");
            } else if (rgbData.length == 720 * 1280 * 3) {
                width = 1280;
                height = 720;
                Log.d("MainActivity", "检测到1280x720分辨率(转置)");
            } else if (rgbData.length == 1920 * 1080 * 3) {
                width = 1920;
                height = 1080;
                Log.d("MainActivity", "检测到1920x1080分辨率");
            } else if (rgbData.length == 1080 * 1920 * 3) {
                width = 1920;
                height = 1080;
                Log.d("MainActivity", "检测到1920x1080分辨率(转置)");
            } else {
                Log.w("MainActivity", "未知的图像尺寸: " + rgbData.length + " bytes");
                // 使用数据大小推算尺寸
                int pixels = rgbData.length / 3;
                width = (int) Math.sqrt(pixels * 4.0 / 3.0); // 假设16:9比例
                height = pixels / width;
                Log.d("MainActivity", "推算尺寸: " + width + "x" + height);
            }
            
            // 创建Bitmap
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            
            // 彻底修复：重新构建图像数据，确保正确的像素排列
            int[] argbData = new int[width * height];
            
            // 确保数据长度正确
            int expectedLength = width * height * 3;
            if (rgbData.length != expectedLength) {
                Log.e("MainActivity", "数据长度不匹配: 期望=" + expectedLength + ", 实际=" + rgbData.length);
                return createFallbackBitmap();
            }
            
            // 正确处理BGR格式数据到ARGB转换
            // NDK层返回的是BGR格式(cv::Mat默认BGR)，所以索引是B-G-R
            for (int i = 0; i < width * height; i++) {
                int dataIndex = i * 3;
                if (dataIndex + 2 < rgbData.length) {
                    int b = rgbData[dataIndex] & 0xFF;        // B通道 (索引0)
                    int g = rgbData[dataIndex + 1] & 0xFF;    // G通道 (索引1)
                    int r = rgbData[dataIndex + 2] & 0xFF;    // R通道 (索引2)
                    argbData[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                }
            }
            
            bitmap.setPixels(argbData, 0, width, 0, 0, width, height);
            
            // 验证图像数据质量
            int blackPixels = 0;
            int whitePixels = 0;
            for (int i = 0; i < argbData.length && i < 1000; i++) { // 只检查前1000个像素
                int pixel = argbData[i];
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                
                if (r < 10 && g < 10 && b < 10) blackPixels++;
                if (r > 245 && g > 245 && b > 245) whitePixels++;
            }
            
            Log.d("MainActivity", "图像质量检查 - 黑像素: " + blackPixels + ", 白像素: " + whitePixels + 
                  ", 总检查像素: " + Math.min(1000, argbData.length));
            
            if (blackPixels > 800) {
                Log.w("MainActivity", "警告: 图像可能全是黑色");
            }
            if (whitePixels > 800) {
                Log.w("MainActivity", "警告: 图像可能全是白色");
            }
            
            Log.d("MainActivity", "成功获取相机帧: " + width + "x" + height + ", 数据长度: " + rgbData.length);
            return bitmap;
            
        } catch (Exception e) {
            Log.e("MainActivity", "获取相机帧异常", e);
            return createFallbackBitmap();
        }
    }
    
    /**
     * 创建备用的测试Bitmap（当无法获取真实帧时使用）
     */
    private Bitmap createFallbackBitmap() {
        try {
            // 获取SurfaceView尺寸
            int width = cameraView.getWidth();
            int height = cameraView.getHeight();
            
            // 如果尺寸无效，使用默认值
            if (width <= 0 || height <= 0) {
                width = 640;
                height = 480;
            }
            
            // 创建Bitmap
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            
            // 填充背景色
            canvas.drawColor(0xFF4CAF50); // 绿色背景
            
            // 绘制简单的文本提示
            android.graphics.Paint textPaint = new android.graphics.Paint();
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setTextSize(48);
            textPaint.setTextAlign(android.graphics.Paint.Align.CENTER);
            
            String text = "相机帧获取失败";
            float x = width / 2f;
            float y = height / 2f;
            
            canvas.drawText(text, x, y, textPaint);
            
            // 绘制时间戳
            textPaint.setTextSize(24);
            String timestamp = "Fallback at: " + java.text.SimpleDateFormat.getInstance().format(new java.util.Date());
            canvas.drawText(timestamp, x, y + 60, textPaint);
            
            Log.d("MainActivity", "创建备用Bitmap成功: " + width + "x" + height);
            return bitmap;
            
        } catch (Exception e) {
            Log.e("MainActivity", "创建备用Bitmap失败", e);
            return null;
        }
    }

    /**
     * 显示拍照的照片
     */
    private void showCapturedImage(Bitmap bitmap) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        
        android.widget.ImageView imageView = new android.widget.ImageView(this);
        imageView.setImageBitmap(bitmap);
        
        // 计算合适的显示尺寸
        int maxWidth = getResources().getDisplayMetrics().widthPixels;
        int maxHeight = getResources().getDisplayMetrics().heightPixels / 2;
        
        imageView.setLayoutParams(new android.view.ViewGroup.LayoutParams(maxWidth, maxHeight));
        imageView.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        
        builder.setTitle("检测结果预览")
               .setView(imageView)
               .setPositiveButton("确定", null)
               .show();
    }

    /**
     * 保存Bitmap到相册
     */
    private void saveBitmapToGallery(Bitmap bitmap) {
        saveBitmapToGallery(bitmap, "yolov8_capture_");
    }
    
    /**
     * 保存Bitmap到相册（带前缀）
     */
    private void saveBitmapToGallery(Bitmap bitmap, String prefix) {
        try {
            String fileName = prefix + System.currentTimeMillis() + ".jpg";
            String folderName = "yolov8ncnn";
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10及以上使用MediaStore
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + folderName);
                
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    android.graphics.Bitmap.CompressFormat compressFormat = android.graphics.Bitmap.CompressFormat.JPEG;
                    bitmap.compress(compressFormat, 90, getContentResolver().openOutputStream(uri));
                    
                    // 通知媒体扫描器
                    MediaScannerConnection.scanFile(this, 
                        new String[]{uri.toString()}, null, null);
                    
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "已保存到相册", Toast.LENGTH_SHORT).show();
                        }
                    });
                    
                    Log.d("MainActivity", "图片已保存到: " + uri.toString());
                }
            } else {
                // Android 10以下使用传统方式
                File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                File appDir = new File(picturesDir, folderName);
                if (!appDir.exists()) {
                    appDir.mkdirs();
                }
                
                File imageFile = new File(appDir, fileName);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, new java.io.FileOutputStream(imageFile));
                
                // 通知媒体扫描器
                MediaScannerConnection.scanFile(this, 
                    new String[]{imageFile.getAbsolutePath()}, null, null);
                
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "已保存到相册", Toast.LENGTH_SHORT).show();
                    }
                });
                
                Log.d("MainActivity", "图片已保存到: " + imageFile.getAbsolutePath());
            }
            
            // 不在这里回收Bitmap，由调用方负责回收
            // 这样可以避免在UI操作中回收导致的闪退
            
        } catch (Exception e) {
            Log.e("MainActivity", "保存图片失败", e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限获取成功，重新执行拍照
                captureAndSaveImage();
            } else {
                Toast.makeText(this, "需要存储权限才能保存图片", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();

        yolov8ncnn.closeCamera();
    }
}
