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
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.BufferedReader;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

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
    private Button btnViewJson;
    
    // 相机状态管理
    private boolean isCameraFrozen = false;
    private Bitmap frozenFrame = null;
    
    // 盘点流程管理
    private boolean isInventoryMode = false;
    private List<DetectionPoint> inventoryPoints = new ArrayList<>();
    private int currentPointIndex = 0;
    
    // UI组件引用
    private DetectionOverlay detectionOverlay;
    private Button btnStartInventory;
    private Button btnConfirmSave;

    /**
     * 开始盘点流程
     */
    private void startInventoryProcess() {
        Log.d("MainActivity", "开始盘点流程");
        
        try {
            // 1. 冻结相机
            freezeCamera();
            
            // 2. 切换到盘点模式
            isInventoryMode = true;
            
            // 3. 获取检测点数据
            float[][] detectionPoints = yolov8ncnn.getDetectionPoints();
            if (detectionPoints != null && detectionPoints.length > 0) {
                inventoryPoints.clear();
                for (int i = 0; i < detectionPoints.length; i++) {
                    if (detectionPoints[i].length >= 2) {
                        DetectionPoint point = new DetectionPoint(
                            detectionPoints[i][0], // x坐标
                            detectionPoints[i][1], // y坐标
                            i
                        );
                        inventoryPoints.add(point);
                    }
                }
                
                Log.d("MainActivity", "获取到 " + inventoryPoints.size() + " 个检测点");
                
                // 4. 显示检测点覆盖层
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        detectionOverlay.setPoints(inventoryPoints);
                        detectionOverlay.setVisibility(View.VISIBLE);
                        detectionOverlay.setEditMode(true);
                        
                        // 隐藏拍照按钮，显示确认保存按钮
                        btnCapture.setVisibility(View.GONE);
                        btnViewJson.setVisibility(View.GONE);
                        btnStartInventory.setVisibility(View.GONE);
                        btnConfirmSave.setVisibility(View.VISIBLE);
                        
                        Toast.makeText(MainActivity.this, 
                            "已进入盘点模式，共 " + inventoryPoints.size() + " 个目标", 
                            Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, 
                            "未检测到目标，请确保相机已正确加载模型", 
                            Toast.LENGTH_LONG).show();
                        // 恢复相机
                        resumeCamera();
                        isInventoryMode = false;
                    }
                });
            }
            
        } catch (Exception e) {
            Log.e("MainActivity", "开始盘点流程失败: " + e.getMessage(), e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, 
                        "开始盘点失败: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    
    /**
     * 确认并保存盘点结果
     */
    private void confirmAndSaveInventory() {
        Log.d("MainActivity", "确认并保存盘点结果");
        
        try {
            // 1. 获取最终的检测点数据
            List<DetectionPoint> finalPoints = detectionOverlay.getPoints();
            
            // 2. 保存盘点数据
            saveInventoryData(finalPoints);
            
            // 3. 恢复正常模式
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 隐藏覆盖层
                    detectionOverlay.setVisibility(View.GONE);
                    detectionOverlay.setEditMode(false);
                    
                    // 恢复按钮显示
                    btnCapture.setVisibility(View.VISIBLE);
                    btnViewJson.setVisibility(View.VISIBLE);
                    btnStartInventory.setVisibility(View.VISIBLE);
                    btnConfirmSave.setVisibility(View.GONE);
                    
                    Toast.makeText(MainActivity.this, 
                        "盘点完成，已保存 " + finalPoints.size() + " 个目标数据", 
                        Toast.LENGTH_LONG).show();
                }
            });
            
            // 4. 恢复相机
            resumeCamera();
            isInventoryMode = false;
            inventoryPoints.clear();
            currentPointIndex = 0;
            
        } catch (Exception e) {
            Log.e("MainActivity", "保存盘点数据失败: " + e.getMessage(), e);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, 
                        "保存失败: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    
    /**
     * 保存盘点数据到JSON文件
     */
    private void saveInventoryData(List<DetectionPoint> points) {
        try {
            // 创建盘点数据对象
            InventoryData inventoryData = new InventoryData();
            inventoryData.setTimestamp(System.currentTimeMillis());
            inventoryData.setPointCount(points.size());
            
            List<PointData> pointList = new ArrayList<>();
            for (int i = 0; i < points.size(); i++) {
                DetectionPoint point = points.get(i);
                PointData pointData = new PointData();
                pointData.setId(i);
                pointData.setX(point.getX());
                pointData.setY(point.getY());
                pointData.setSelected(point.isSelected());
                pointList.add(pointData);
            }
            inventoryData.setPoints(pointList);
            
            // 保存到JSON文件
            String jsonString = inventoryData.toJson();
            String fileName = "inventory_" + System.currentTimeMillis() + ".json";
            File jsonDir = new File(getFilesDir(), "inventory");
            if (!jsonDir.exists()) {
                jsonDir.mkdirs();
            }
            
            File jsonFile = new File(jsonDir, fileName);
            java.io.FileWriter writer = new java.io.FileWriter(jsonFile);
            writer.write(jsonString);
            writer.close();
            
            Log.d("MainActivity", "盘点数据已保存到: " + jsonFile.getAbsolutePath());
            
        } catch (Exception e) {
            Log.e("MainActivity", "保存盘点数据异常: " + e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
    private String getAppExternalPath() {
        File externalDir = getExternalFilesDir(null);
        if (externalDir != null) {
            return externalDir.getAbsolutePath();
        }
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" + getPackageName() + "/files";
    }
    
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
        
        // 初始化检测点覆盖层
        detectionOverlay = (DetectionOverlay) findViewById(R.id.detection_overlay);

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
        
        // 初始化开始盘点按钮
        btnStartInventory = new Button(this);
        btnStartInventory.setText("开始盘点");
        btnStartInventory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startInventoryProcess();
            }
        });
        
        // 初始化确认保存按钮
        btnConfirmSave = new Button(this);
        btnConfirmSave.setText("确认保存");
        btnConfirmSave.setVisibility(View.GONE);
        btnConfirmSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmAndSaveInventory();
            }
        });
        
        // 将按钮添加到布局中
        LinearLayout buttonContainer = (LinearLayout) findViewById(R.id.button_container);
        if (buttonContainer != null) {
            buttonContainer.addView(btnStartInventory);
            buttonContainer.addView(btnConfirmSave);
        }
        
        // 初始化查看JSON按钮
        btnViewJson = (Button) findViewById(R.id.btn_view_json);
        btnViewJson.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewJsonFiles();
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
                        
                        // **新增：保存检测结果为 JSON**
                        saveDetectionResults(width, height);
                        
                        Toast.makeText(MainActivity.this, 
                                      "已保存原始图像、检测结果和JSON文件", 
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
     * 查看JSON文件
     */
    private void viewJsonFiles() {
        try {
            // 获取JSON文件目录
            File jsonDir = new File(getFilesDir(), "detections");
            
            if (!jsonDir.exists()) {
                Toast.makeText(this, "JSON文件夹不存在，请先拍照生成检测结果", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 获取所有JSON文件
            File[] jsonFiles = jsonDir.listFiles((dir, name) -> name.endsWith(".json"));
            
            if (jsonFiles == null || jsonFiles.length == 0) {
                Toast.makeText(this, "没有找到JSON文件，请先拍照生成检测结果", Toast.LENGTH_LONG).show();
                return;
            }
            
            // 按修改时间排序（最新的在前面）
            Arrays.sort(jsonFiles, (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
            
            // 创建文件列表适配器
            List<String> fileList = new ArrayList<>();
            for (File file : jsonFiles) {
                String fileInfo = file.getName() + " (" + 
                    android.text.format.Formatter.formatFileSize(this, file.length()) + ")";
                fileList.add(fileInfo);
            }
            
            // 显示文件选择对话框
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle("选择要查看的JSON文件")
                   .setItems(fileList.toArray(new String[0]), new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialog, int which) {
                           // 查看选中的文件
                           viewJsonFile(jsonFiles[which]);
                       }
                   })
                   .setNegativeButton("取消", null)
                   .show();
                    
        } catch (Exception e) {
            Log.e("MainActivity", "查看JSON文件失败", e);
            Toast.makeText(this, "查看失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 查看单个JSON文件内容
     */
    private void viewJsonFile(File jsonFile) {
        try {
            // 读取文件内容
            StringBuilder content = new StringBuilder();
            BufferedReader reader = new BufferedReader(new FileReader(jsonFile));
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            
            // 显示文件内容
            android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
            builder.setTitle(jsonFile.getName())
                   .setMessage(content.toString())
                   .setPositiveButton("确定", null)
                   .setNeutralButton("分享", new DialogInterface.OnClickListener() {
                       @Override
                       public void onClick(DialogInterface dialog, int which) {
                           shareJsonFile(jsonFile);
                       }
                   })
                   .show();
                    
        } catch (Exception e) {
            Log.e("MainActivity", "读取JSON文件失败", e);
            Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 分享JSON文件
     */
    private void shareJsonFile(File jsonFile) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            
            Uri fileUri = Uri.fromFile(jsonFile);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "YOLOv8检测结果");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "这是YOLOv8目标检测的结果文件");
            
            startActivity(Intent.createChooser(shareIntent, "分享JSON文件"));
            
        } catch (Exception e) {
            Log.e("MainActivity", "分享JSON文件失败", e);
            Toast.makeText(this, "分享失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 保存检测结果到 JSON 文件
     */
    private void saveDetectionResults(int width, int height) {
        try {
            // 创建 JSON 对象
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("timestamp", System.currentTimeMillis());
            json.put("image_width", width);
            json.put("image_height", height);
            
            // 创建检测结果数组
            org.json.JSONArray detections = new org.json.JSONArray();
            
            // TODO: 这里需要从 native 层获取实际的检测结果
            // 目前先创建示例数据
            org.json.JSONObject detection = new org.json.JSONObject();
            detection.put("class_id", 0);
            detection.put("class_name", "person");
            detection.put("confidence", 0.95);
            
            org.json.JSONObject bbox = new org.json.JSONObject();
            bbox.put("x", 100);
            bbox.put("y", 100);
            bbox.put("width", 200);
            bbox.put("height", 300);
            detection.put("bbox", bbox);
            
            detections.put(detection);
            
            json.put("detections", detections);
            
            // 保存到应用内部存储目录
            String fileName = "detection_" + System.currentTimeMillis() + ".json";
            File jsonDir = new File(getFilesDir(), "detections");
            if (!jsonDir.exists()) {
                jsonDir.mkdirs();
            }
            File jsonFile = new File(jsonDir, fileName);
            
            FileOutputStream fos = new FileOutputStream(jsonFile);
            fos.write(json.toString(2).getBytes()); // 格式化输出，缩进为2个空格
            fos.close();
            
            Log.d("MainActivity", "JSON saved to internal storage: " + jsonFile.getAbsolutePath());
            Log.d("MainActivity", "File size: " + jsonFile.length() + " bytes");
            
        } catch (Exception e) {
            Log.e("MainActivity", "Failed to save JSON", e);
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

    /**
     * 冻结相机预览
     * 暂停相机并保存当前帧
     */
    public void freezeCamera() {
        if (isCameraFrozen) return;
        
        Log.d("MainActivity", "freezeCamera called");
        
        try {
            // 关闭相机
            yolov8ncnn.closeCamera();
            isCameraFrozen = true;
            
            // 获取当前帧并保存
            byte[] frameData = yolov8ncnn.getCurrentFrame();
            if (frameData != null && frameData.length > 0) {
                // 将字节数组转换为Bitmap
                int width = 640; // 默认宽度
                int height = 480; // 默认高度
                int expectedSize = width * height * 3;
                
                // 根据数据大小推算实际尺寸
                if (frameData.length == 640 * 480 * 3) {
                    width = 640;
                    height = 480;
                } else if (frameData.length == 1280 * 720 * 3) {
                    width = 1280;
                    height = 720;
                } else if (frameData.length == 1920 * 1080 * 3) {
                    width = 1920;
                    height = 1080;
                } else {
                    // 根据数据大小计算尺寸
                    int pixels = frameData.length / 3;
                    width = (int) Math.sqrt(pixels * 4.0 / 3.0);
                    height = pixels / width;
                }
                
                // 创建Bitmap
                frozenFrame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                int[] argbData = new int[width * height];
                
                // 转换BGR到ARGB
                for (int i = 0; i < width * height; i++) {
                    int dataIndex = i * 3;
                    if (dataIndex + 2 < frameData.length) {
                        int b = frameData[dataIndex] & 0xFF;
                        int g = frameData[dataIndex + 1] & 0xFF;
                        int r = frameData[dataIndex + 2] & 0xFF;
                        argbData[i] = (0xFF << 24) | (r << 16) | (g << 8) | b;
                    }
                }
                
                frozenFrame.setPixels(argbData, 0, width, 0, 0, width, height);
                Log.d("MainActivity", "Camera frozen, frame saved: " + width + "x" + height);
            }
            
        } catch (Exception e) {
            Log.e("MainActivity", "freezeCamera error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 恢复相机预览
     * 重新打开相机并清理冻结帧
     */
    public void resumeCamera() {
        if (!isCameraFrozen) return;
        
        Log.d("MainActivity", "resumeCamera called");
        
        try {
            // 清理冻结的帧
            if (frozenFrame != null && !frozenFrame.isRecycled()) {
                frozenFrame.recycle();
                frozenFrame = null;
            }
            
            // 重新打开相机
            yolov8ncnn.openCamera(facing);
            isCameraFrozen = false;
            
            Log.d("MainActivity", "Camera resumed");
            
        } catch (Exception e) {
            Log.e("MainActivity", "resumeCamera error: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取冻结的帧
     * @return 冻结的Bitmap帧，如果相机未冻结则返回null
     */
    public Bitmap getFrozenFrame() {
        return frozenFrame;
    }
    
    /**
     * 检查相机是否处于冻结状态
     * @return true表示相机已冻结，false表示正常运行
     */
    public boolean isCameraFrozen() {
        return isCameraFrozen;
    }
    
    /**
     * 检查是否处于盘点模式
     * @return true表示正在盘点，false表示正常模式
     */
    public boolean isInInventoryMode() {
        return isInventoryMode;
    }
    
    /**
     * 获取当前盘点点位列表
     * @return 当前的检测点列表
     */
    public List<DetectionPoint> getCurrentInventoryPoints() {
        return new ArrayList<>(inventoryPoints);
    }
    
    /**
     * 取消盘点流程
     */
    public void cancelInventoryProcess() {
        Log.d("MainActivity", "取消盘点流程");
        
        try {
            // 恢复相机
            resumeCamera();
            
            // 重置状态
            isInventoryMode = false;
            inventoryPoints.clear();
            currentPointIndex = 0;
            
            // 更新UI
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // 隐藏覆盖层
                    if (detectionOverlay != null) {
                        detectionOverlay.setVisibility(View.GONE);
                        detectionOverlay.setEditMode(false);
                    }
                    
                    // 恢复按钮显示
                    if (btnCapture != null) btnCapture.setVisibility(View.VISIBLE);
                    if (btnViewJson != null) btnViewJson.setVisibility(View.VISIBLE);
                    if (btnStartInventory != null) btnStartInventory.setVisibility(View.VISIBLE);
                    if (btnConfirmSave != null) btnConfirmSave.setVisibility(View.GONE);
                    
                    Toast.makeText(MainActivity.this, "已取消盘点", Toast.LENGTH_SHORT).show();
                }
            });
            
        } catch (Exception e) {
            Log.e("MainActivity", "取消盘点流程失败: " + e.getMessage(), e);
        }
    }
    
    public void onPause()
    {
        super.onPause();

        // 如果正在盘点，先取消盘点流程
        if (isInventoryMode) {
            cancelInventoryProcess();
        }
        
        // 关闭相机
        yolov8ncnn.closeCamera();
        
        // 清理冻结帧资源
        if (frozenFrame != null && !frozenFrame.isRecycled()) {
            frozenFrame.recycle();
            frozenFrame = null;
        }
        isCameraFrozen = false;
        isInventoryMode = false;
    }
}
