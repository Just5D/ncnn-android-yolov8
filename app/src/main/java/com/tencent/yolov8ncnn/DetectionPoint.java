package com.tencent.yolov8ncnn;

/**
 * 检测点数据模型类
 * 用于表示预设图表中的可交互检测点
 */
public class DetectionPoint {
    // 坐标属性
    private float x;
    private float y;
    
    // 状态属性
    private boolean isSelected;
    private int index;
    
    // 构造函数
    public DetectionPoint(float x, float y) {
        this.x = x;
        this.y = y;
        this.isSelected = false;
        this.index = -1; // 默认未分配序号
    }
    
    public DetectionPoint(float x, float y, int index) {
        this.x = x;
        this.y = y;
        this.isSelected = false;
        this.index = index;
    }
    
    // Getter方法
    public float getX() {
        return x;
    }
    
    public float getY() {
        return y;
    }
    
    public boolean isSelected() {
        return isSelected;
    }
    
    public int getIndex() {
        return index;
    }
    
    // Setter方法
    public void setX(float x) {
        this.x = x;
    }
    
    public void setY(float y) {
        this.y = y;
    }
    
    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }
    
    public void setIndex(int index) {
        this.index = index;
    }
    
    /**
     * 计算到另一个点的距离
     * @param other 另一个检测点
     * @return 距离值
     */
    public float distanceTo(DetectionPoint other) {
        float dx = this.x - other.x;
        float dy = this.y - other.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 计算到指定坐标的距离
     * @param targetX 目标X坐标
     * @param targetY 目标Y坐标
     * @return 距离值
     */
    public float distanceTo(float targetX, float targetY) {
        float dx = this.x - targetX;
        float dy = this.y - targetY;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    /**
     * 判断是否在指定范围内
     * @param targetX 目标X坐标
     * @param targetY 目标Y坐标
     * @param radius 判定半径
     * @return 是否在范围内
     */
    public boolean isInRadius(float targetX, float targetY, float radius) {
        return distanceTo(targetX, targetY) <= radius;
    }
    
    @Override
    public String toString() {
        return "DetectionPoint{" +
                "x=" + x +
                ", y=" + y +
                ", isSelected=" + isSelected +
                ", index=" + index +
                '}';
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        DetectionPoint that = (DetectionPoint) obj;
        return Float.compare(that.x, x) == 0 &&
               Float.compare(that.y, y) == 0 &&
               index == that.index;
    }
    
    @Override
    public int hashCode() {
        int result = (x != 0.0f ? Float.floatToIntBits(x) : 0);
        result = 31 * result + (y != 0.0f ? Float.floatToIntBits(y) : 0);
        result = 31 * result + index;
        return result;
    }
}