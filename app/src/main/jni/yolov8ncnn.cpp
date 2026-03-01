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

#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <fstream>
#include <sstream>
#include <iomanip>
#include <ctime>

#include <platform.h>
#include <benchmark.h>

#include "yolov8.h"

#include "ndkcamera.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

// JSON 相关辅助函数
static std::string getCurrentTimeString()
{
    time_t now = time(nullptr);
    tm* tm_now = localtime(&now);
    
    char buffer[64];
    strftime(buffer, sizeof(buffer), "%Y%m%d_%H%M%S", tm_now);
    return std::string(buffer);
}

static void saveObjectsToJson(const std::vector<Object>& objects, int width, int height, const std::string& filename)
{
    std::ofstream file(filename);
    if (!file.is_open()) {
        __android_log_print(ANDROID_LOG_ERROR, "MainActivity", "Failed to open JSON file for writing: %s", filename.c_str());
        return;
    }
    
    file << "{\n";
    file << "  \"timestamp\": \"" << getCurrentTimeString() << "\",\n";
    file << "  \"image_width\": " << width << ",\n";
    file << "  \"image_height\": " << height << ",\n";
    file << "  \"detections\": [\n";
    
    for (size_t i = 0; i < objects.size(); i++) {
        const Object& obj = objects[i];
        
        file << "    {\n";
        file << "      \"class_id\": " << obj.label << ",\n";
        file << "      \"confidence\": " << obj.prob << ",\n";
        file << "      \"bbox\": {\n";
        file << "        \"x\": " << obj.rect.x << ",\n";
        file << "        \"y\": " << obj.rect.y << ",\n";
        file << "        \"width\": " << obj.rect.width << ",\n";
        file << "        \"height\": " << obj.rect.height << "\n";
        file << "      }";
        
        // 如果有 mask 数据（分割任务）
        if (!obj.mask.empty()) {
            file << ",\n      \"mask_size\": " << obj.mask.size();
        }
        
        // 如果有 keypoints（姿态检测）
        if (!obj.keypoints.empty()) {
            file << ",\n      \"keypoints\": [";
            for (size_t j = 0; j < obj.keypoints.size(); j++) {
                if (j > 0) file << ",";
                file << "\n        {";
                file << "\"x\": " << obj.keypoints[j].p.x << ", ";
                file << "\"y\": " << obj.keypoints[j].p.y << ", ";
                file << "\"visible\": " << (obj.keypoints[j].prob > 0 ? 1 : 0);
                file << "}";
            }
            file << "\n      ]";
        }
        
        file << "\n    }";
        if (i < objects.size() - 1) file << ",";
        file << "\n";
    }
    
    file << "  ]\n";
    file << "}\n";
    
    file.close();
    __android_log_print(ANDROID_LOG_DEBUG, "MainActivity", "JSON saved: %s with %zu objects", filename.c_str(), objects.size());
}

static int draw_unsupported(cv::Mat& rgb)
{
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat& rgb)
{
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f)
        {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--)
        {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f)
        {
            return 0;
        }

        for (int i = 0; i < 10; i++)
        {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y), cv::Size(label_size.width, label_size.height + baseLine)),
                    cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}

static YOLOv8* g_yolov8 = 0;
static ncnn::Mutex lock;

class MyNdkCamera : public NdkCameraWindow
{
public:
    virtual void on_image_render(cv::Mat& rgb) const;
};

void MyNdkCamera::on_image_render(cv::Mat& rgb) const
{
    // yolov8
    {
        ncnn::MutexLockGuard g(lock);

        if (g_yolov8)
        {
            std::vector<Object> objects;
            g_yolov8->detect(rgb, objects);

            g_yolov8->draw(rgb, objects);
        }
        else
        {
            draw_unsupported(rgb);
        }
    }

    draw_fps(rgb);
}

static MyNdkCamera* g_camera = 0;

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnLoad");

    g_camera = new MyNdkCamera;

    ncnn::create_gpu_instance();

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_yolov8;
        g_yolov8 = 0;
    }

    ncnn::destroy_gpu_instance();

    delete g_camera;
    g_camera = 0;
}

// public native boolean loadModel(AssetManager mgr, int taskid, int modelid, int cpugpu);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_loadModel(JNIEnv* env, jobject thiz, jobject assetManager, jint taskid, jint modelid, jint cpugpu)
{
    if (taskid < 0 || taskid > 5 || modelid < 0 || modelid > 8 || cpugpu < 0 || cpugpu > 2)
    {
        return JNI_FALSE;
    }

    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    const char* tasknames[6] =
    {
        "",
        "_oiv7",
        "_seg",
        "_pose",
        "_cls",
        "_obb"
    };

    const char* modeltypes[9] =
    {
        "n",
        "s",
        "m",
        "n",
        "s",
        "m",
        "n",
        "s",
        "m"
    };

    std::string parampath = std::string("yolov8") + modeltypes[(int)modelid] + tasknames[(int)taskid] + ".ncnn.param";
    std::string modelpath = std::string("yolov8") + modeltypes[(int)modelid] + tasknames[(int)taskid] + ".ncnn.bin";
    bool use_gpu = (int)cpugpu == 1;
    bool use_turnip = (int)cpugpu == 2;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        {
            static int old_taskid = 0;
            static int old_modelid = 0;
            static int old_cpugpu = 0;
            if (taskid != old_taskid || (modelid % 3) != old_modelid || cpugpu != old_cpugpu)
            {
                // taskid or model or cpugpu changed
                delete g_yolov8;
                g_yolov8 = 0;
            }
            old_taskid = taskid;
            old_modelid = modelid % 3;
            old_cpugpu = cpugpu;

            ncnn::destroy_gpu_instance();

            if (use_turnip)
            {
                ncnn::create_gpu_instance("libvulkan_freedreno.so");
            }
            else if (use_gpu)
            {
                ncnn::create_gpu_instance();
            }

            if (!g_yolov8)
            {
                if (taskid == 0) g_yolov8 = new YOLOv8_det_coco;
                if (taskid == 1) g_yolov8 = new YOLOv8_det_oiv7;
                if (taskid == 2) g_yolov8 = new YOLOv8_seg;
                if (taskid == 3) g_yolov8 = new YOLOv8_pose;
                if (taskid == 4) g_yolov8 = new YOLOv8_cls;
                if (taskid == 5) g_yolov8 = new YOLOv8_obb;

                g_yolov8->load(mgr, parampath.c_str(), modelpath.c_str(), use_gpu || use_turnip);
            }
            int target_size = 320;
            if ((int)modelid >= 3)
                target_size = 480;
            if ((int)modelid >= 6)
                target_size = 640;
            g_yolov8->set_det_target_size(target_size);
        }
    }

    return JNI_TRUE;
}

// public native boolean openCamera(int facing);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_openCamera(JNIEnv* env, jobject thiz, jint facing)
{
    if (facing < 0 || facing > 1)
        return JNI_FALSE;

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "openCamera %d", facing);

    g_camera->open((int)facing);

    return JNI_TRUE;
}

// public native boolean closeCamera();
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_closeCamera(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "closeCamera");

    g_camera->close();

    return JNI_TRUE;
}

// public native boolean setOutputWindow(Surface surface);
JNIEXPORT jboolean JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_setOutputWindow(JNIEnv* env, jobject thiz, jobject surface)
{
    ANativeWindow* win = ANativeWindow_fromSurface(env, surface);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "setOutputWindow %p", win);

    g_camera->set_window(win);

    return JNI_TRUE;
}

// public native void nativeCapture();
JNIEXPORT void JNICALL Java_com_tencent_yolov8ncnn_MainActivity_nativeCapture(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "MainActivity", "nativeCapture called");
    
    // 添加全面的安全检查
    if (!g_camera) {
        __android_log_print(ANDROID_LOG_ERROR, "MainActivity", "g_camera is null");
        // 调用Java层的错误回调
        jclass clazz = env->GetObjectClass(thiz);
        jmethodID errorCallback = env->GetMethodID(clazz, "onCaptureError", "(Ljava/lang/String;)V");
        if (errorCallback) {
            jstring errorMsg = env->NewStringUTF("相机未初始化");
            env->CallVoidMethod(thiz, errorCallback, errorMsg);
            env->DeleteLocalRef(errorMsg);
        }
        return;
    }
    
    // 获取带检测框的帧
    cv::Mat frame_with_boxes = g_camera->getFrameWithBoxes();
    // 获取原始帧
    cv::Mat frame_original = g_camera->getOriginalFrame();
    
    __android_log_print(ANDROID_LOG_DEBUG, "MainActivity", 
                       "Frame sizes - with_boxes: %dx%d, original: %dx%d", 
                       frame_with_boxes.cols, frame_with_boxes.rows,
                       frame_original.cols, frame_original.rows);
    
    if (frame_with_boxes.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "MainActivity", "frame_with_boxes is empty");
        return;
    }
    
    if (frame_original.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, "MainActivity", "frame_original is empty");
        return;
    }
    
    // 验证帧尺寸一致性
    if (frame_with_boxes.cols != frame_original.cols || 
        frame_with_boxes.rows != frame_original.rows) {
        __android_log_print(ANDROID_LOG_ERROR, "MainActivity", 
                           "Frame dimensions mismatch: boxes(%dx%d) vs original(%dx%d)",
                           frame_with_boxes.cols, frame_with_boxes.rows,
                           frame_original.cols, frame_original.rows);
        return;
    }
    
    int width = frame_with_boxes.cols;
    int height = frame_with_boxes.rows;
    
    __android_log_print(ANDROID_LOG_DEBUG, "MainActivity", 
                       "Captured frames: %dx%d", width, height);
    __android_log_print(ANDROID_LOG_DEBUG, "MainActivity", 
                       "Frame data pointers - with_boxes: %p, original: %p", 
                       frame_with_boxes.data, frame_original.data);
    
    // **新增：保存检测结果为 JSON**
    if (g_yolov8) {
        std::vector<Object> objects = g_yolov8->getLastObjects();
        
        // 获取当前时间作为文件名
        std::string timeStr = getCurrentTimeString();
        
        // 使用应用内部存储路径
        std::string jsonPath = "/data/data/com.tencent.yolov8ncnn/files/detections/detection_" + timeStr + ".json";
        
        // 确保目录存在
        system("mkdir -p /data/data/com.tencent.yolov8ncnn/files/detections");
        
        saveObjectsToJson(objects, width, height, jsonPath);
    }
    
    // 1. 转换带检测框的帧为 RGBA（用于显示）
    int rgbaSize = width * height * 4;
    jbyteArray boxedArray = env->NewByteArray(rgbaSize);
    jbyte* boxedData = env->GetByteArrayElements(boxedArray, NULL);
    
    const unsigned char* bgrData = frame_with_boxes.data;  // OpenCV默认是BGR
    unsigned char* rgbaData = (unsigned char*)boxedData;
    
    // **保持RGB格式**
    // 帧数据已经是RGB格式，直接转换为RGBA用于显示
    for (int i = 0; i < width * height; i++) {
        rgbaData[0] = bgrData[0];  // R <- RGB的红色通道
        rgbaData[1] = bgrData[1];  // G <- RGB的绿色通道
        rgbaData[2] = bgrData[2];  // B <- RGB的蓝色通道
        rgbaData[3] = 255;         // A (不透明)
        bgrData += 3;
        rgbaData += 4;
    }
    
    env->ReleaseByteArrayElements(boxedArray, boxedData, 0);
    
    // 2. 转换原始帧为 RGB 数组
    int rgbSize = width * height * 3;
    jbyteArray originalArray = env->NewByteArray(rgbSize);
    jbyte* originalData = env->GetByteArrayElements(originalArray, NULL);
    
    const unsigned char* origBgrData = frame_original.data;  // OpenCV的BGR数据
    unsigned char* rgbOutData = (unsigned char*)originalData;
    
    // **保持RGB格式不变**
    // 原始帧已经是RGB格式，直接复制
    for (int i = 0; i < width * height; i++) {
        rgbOutData[0] = origBgrData[0];  // R <- RGB的红色通道
        rgbOutData[1] = origBgrData[1];  // G <- RGB的绿色通道
        rgbOutData[2] = origBgrData[2];  // B <- RGB的蓝色通道
        origBgrData += 3;
        rgbOutData += 3;
    }
    
    env->ReleaseByteArrayElements(originalArray, originalData, 0);
    
    // 调用 Java 回调
    jclass clazz = env->GetObjectClass(thiz);
    jmethodID callbackMethod = env->GetMethodID(clazz, 
                                               "onCaptureComplete", 
                                               "([B[BII)V");
    if (callbackMethod) {
        env->CallVoidMethod(thiz, callbackMethod, 
                           boxedArray,      // 带检测框的帧 (RGBA)
                           originalArray,   // 原始帧 (RGB)
                           width, height);
    }
    
    env->DeleteLocalRef(boxedArray);
    env->DeleteLocalRef(originalArray);
    
    __android_log_print(ANDROID_LOG_DEBUG, "MainActivity", "nativeCapture completed");
}

// public native byte[] getCurrentFrame();
JNIEXPORT jbyteArray JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_getCurrentFrame(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getCurrentFrame");
    
    // 获取当前帧
    cv::Mat frame = g_camera->get_current_frame();
    
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getCurrentFrame: frame empty=%d size=%dx%d", 
                       frame.empty() ? 1 : 0, frame.cols, frame.rows);
    
    if (frame.empty())
    {
        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getCurrentFrame: empty frame");
        return NULL;
    }
    
    // 确保返回正确的BGR格式数据
    int frame_size = frame.rows * frame.cols * 3; // 3 channels
    
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getCurrentFrame: frame_size=%d rows=%d cols=%d", 
                       frame_size, frame.rows, frame.cols);
    
    // 创建新的连续内存数据以确保正确传输
    cv::Mat continuous_frame;
    if (frame.isContinuous()) {
        continuous_frame = frame.clone();
    } else {
        frame.copyTo(continuous_frame);
    }
    
    // 创建Java字节数组
    jbyteArray result = env->NewByteArray(frame_size);
    if (result == NULL)
    {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "getCurrentFrame: NewByteArray failed");
        return NULL;
    }
    
    // 安全地复制数据
    env->SetByteArrayRegion(result, 0, frame_size, (jbyte*)continuous_frame.data);
    
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getCurrentFrame: success, size=%dx%d, bytes=%d", 
                       frame.cols, frame.rows, frame_size);
    
    return result;
}

// public native float[][] getDetectionPoints();
JNIEXPORT jobjectArray JNICALL Java_com_tencent_yolov8ncnn_YOLOv8Ncnn_getDetectionPoints(JNIEnv* env, jobject thiz)
{
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getDetectionPoints");
    
    // 检查YOLOv8实例是否存在
    if (!g_yolov8) {
        __android_log_print(ANDROID_LOG_WARN, "ncnn", "getDetectionPoints: g_yolov8 is null");
        return NULL;
    }
    
    // 获取最后一次检测结果
    std::vector<Object> objects = g_yolov8->getLastObjects();
    
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getDetectionPoints: found %zu objects", objects.size());
    
    if (objects.empty()) {
        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getDetectionPoints: no detection objects");
        return NULL;
    }
    
    // 获取Float数组类
    jclass floatArrayClass = env->FindClass("[F");
    if (floatArrayClass == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "getDetectionPoints: Failed to find [F class");
        return NULL;
    }
    
    // 创建二维数组对象
    jobjectArray result = env->NewObjectArray(objects.size(), floatArrayClass, NULL);
    if (result == NULL) {
        __android_log_print(ANDROID_LOG_ERROR, "ncnn", "getDetectionPoints: NewObjectArray failed");
        return NULL;
    }
    
    // 为每个检测对象创建坐标数组
    for (size_t i = 0; i < objects.size(); i++) {
        const Object& obj = objects[i];
        
        // 计算检测框中心点坐标
        float centerX = obj.rect.x + obj.rect.width / 2.0f;
        float centerY = obj.rect.y + obj.rect.height / 2.0f;
        
        // 创建包含x,y坐标的float数组
        jfloatArray pointArray = env->NewFloatArray(2);
        if (pointArray == NULL) {
            __android_log_print(ANDROID_LOG_ERROR, "ncnn", "getDetectionPoints: NewFloatArray failed for point %zu", i);
            continue;
        }
        
        jfloat pointData[2] = {centerX, centerY};
        env->SetFloatArrayRegion(pointArray, 0, 2, pointData);
        
        // 将float数组放入结果数组
        env->SetObjectArrayElement(result, i, pointArray);
        
        // 释放局部引用
        env->DeleteLocalRef(pointArray);
        
        __android_log_print(ANDROID_LOG_DEBUG, "ncnn", 
                           "getDetectionPoints: Point %zu - x=%.2f, y=%.2f, label=%d, confidence=%.3f", 
                           i, centerX, centerY, obj.label, obj.prob);
    }
    
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "getDetectionPoints: success, returned %zu points", objects.size());
    
    return result;
}

}
