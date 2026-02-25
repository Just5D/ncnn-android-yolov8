# 颜色修复测试说明

## 修改内容

已在 `ndkcamera.cpp` 的 `get_current_frame()` 方法中添加了RGB到BGR的颜色转换逻辑：

```cpp
// **关键修复：转换 RGB 到 BGR**
// OpenCV 默认是 BGR，而 ncnn::yuv420sp2rgb 输出的是 RGB
// 所以需要交换 R 和 B 通道
for (int i = 0; i < frame_copy.rows; i++) {
    unsigned char* row = frame_copy.ptr<unsigned char>(i);
    for (int j = 0; j < frame_copy.cols; j++) {
        // 交换 R(0) 和 B(2)
        unsigned char temp = row[0];
        row[0] = row[2];
        row[2] = temp;
        row += 3;
    }
}
```

## 测试步骤

1. 安装编译好的APK到Android设备
2. 打开应用，确保相机正常预览
3. 点击拍照按钮
4. 检查保存的图片颜色是否正常（不应出现偏色问题）

## 预期效果

- 拍照保存的图片颜色应该与相机预览一致
- 解决了之前可能出现的红色和蓝色颠倒的问题
- 图片应该显示正常的色彩，而不是绿色通道为主的异常颜色

## 技术说明

这个修复解决了OpenCV中RGB/BGR颜色空间不匹配的问题：
- `ncnn::yuv420sp2rgb` 输出RGB格式
- OpenCV默认使用BGR格式
- Java层期望接收BGR格式的数据进行正确显示

通过在获取帧数据时进行颜色通道交换，确保了整个处理链的颜色一致性。