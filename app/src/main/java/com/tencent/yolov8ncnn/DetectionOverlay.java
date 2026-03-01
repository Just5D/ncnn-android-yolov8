package com.tencent.yolov8ncnn;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import java.util.ArrayList;
import java.util.List;

/**
 * 检测点覆盖层视图
 * 用于显示和交互预设图表中的检测点
 */
public class DetectionOverlay extends View {
    
    // 检测点列表
    private List<DetectionPoint> points;
    
    // 当前选中的点
    private DetectionPoint selectedPoint;
    
    // 编辑模式状态
    private boolean isEditMode = false;
    
    // 动画相关
    private ValueAnimator pulseAnimator;
    private float pulseScale = 1.0f;
    private static final long PULSE_DURATION = 1000; // 脉冲动画持续时间
    
    // 图标样式枚举
    public enum IconStyle {
        NORMAL,      // 普通模式
        EDIT,        // 编辑模式
        SELECTED,    // 选中状态
        NEW_POINT,   // 新添加的点
        DELETING     // 删除中的点
    }
    
    // 当前图标样式
    private IconStyle currentIconStyle = IconStyle.NORMAL;
    
    // 双击检测相关
    private static final long DOUBLE_TAP_TIMEOUT = 300; // 双击超时时间(ms)
    private long lastTapTime = 0;
    private float lastTapX = 0f;
    private float lastTapY = 0f;
    private static final float DOUBLE_TAP_SLOP = 20f; // 双击位置容差
    
    // 命中检测半径
    private static final float HIT_RADIUS = 30f;
    
    // 图标尺寸常量
    private static final float ICON_RADIUS = 20f;
    private static final float INNER_RADIUS = 18f;
    private static final float CROSS_SIZE = 6f;
    private static final float TEXT_OFFSET = 25f;
    private static final float SHADOW_OFFSET = 2f;
    
    // 绘制画笔
    private Paint normalPaint;
    private Paint selectedPaint;
    private Paint textPaint;
    private Paint crossPaint;
    
    // 回调接口
    public interface OnPointChangeListener {
        void onPointAdded(DetectionPoint point);
        void onPointRemoved(DetectionPoint point);
        void onPointSelected(DetectionPoint point);
        void onPointCountChanged(int count);
    }
    
    private OnPointChangeListener pointChangeListener;
    
    public DetectionOverlay(Context context) {
        super(context);
        init();
    }
    
    public DetectionOverlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    
    public DetectionOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    
    /**
     * 初始化方法
     */
    private void init() {
        points = new ArrayList<>();
        selectedPoint = null;
        
        // 初始化画笔
        initPaints();
        
        // 启用触摸焦点
        setFocusable(true);
        setFocusableInTouchMode(true);
    }
    
    /**
     * 初始化绘制画笔
     */
    private void initPaints() {
        // 普通模式画笔 - 蓝色半透明圆
        normalPaint = new Paint();
        normalPaint.setAntiAlias(true);
        normalPaint.setColor(0x800000FF); // 半透明蓝色
        normalPaint.setStyle(Paint.Style.FILL);
        
        // 选中模式画笔 - 黄色外圈
        selectedPaint = new Paint();
        selectedPaint.setAntiAlias(true);
        selectedPaint.setColor(0x80FFFF00); // 半透明黄色
        selectedPaint.setStyle(Paint.Style.FILL);
        
        // 文字画笔
        textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setColor(0xFFFFFFFF); // 白色文字
        textPaint.setTextSize(20f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        
        // 十字线画笔
        crossPaint = new Paint();
        crossPaint.setAntiAlias(true);
        crossPaint.setColor(0xFFFFFFFF); // 白色十字
        crossPaint.setStrokeWidth(2f);
        crossPaint.setStyle(Paint.Style.STROKE);
        
        // 初始化动画
        initAnimations();
    }
    
    /**
     * 初始化动画效果
     */
    private void initAnimations() {
        pulseAnimator = ValueAnimator.ofFloat(1.0f, 1.3f, 1.0f);
        pulseAnimator.setDuration(PULSE_DURATION);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                pulseScale = (Float) animation.getAnimatedValue();
                invalidate();
            }
        });
    }
    
    /**
     * 设置检测点列表
     */
    public void setPoints(List<DetectionPoint> newPoints) {
        this.points.clear();
        if (newPoints != null) {
            this.points.addAll(newPoints);
        }
        // 更新序号
        updatePointIndices();
        invalidate();
    }
    
    /**
     * 获取检测点列表
     */
    public List<DetectionPoint> getPoints() {
        return new ArrayList<>(points);
    }
    
    /**
     * 添加检测点
     */
    public void addPoint(DetectionPoint point) {
        points.add(point);
        updatePointIndices();
        invalidate();
        if (pointChangeListener != null) {
            pointChangeListener.onPointAdded(point);
            pointChangeListener.onPointCountChanged(points.size());
        }
    }
    
    /**
     * 移除检测点
     */
    public void removePoint(DetectionPoint point) {
        if (points.remove(point)) {
            if (selectedPoint == point) {
                selectedPoint = null;
            }
            updatePointIndices();
            invalidate();
            if (pointChangeListener != null) {
                pointChangeListener.onPointRemoved(point);
                pointChangeListener.onPointCountChanged(points.size());
            }
        }
    }
    
    /**
     * 更新检测点序号
     */
    private void updatePointIndices() {
        for (int i = 0; i < points.size(); i++) {
            points.get(i).setIndex(i + 1);
        }
    }
    
    /**
     * 设置编辑模式
     */
    public void setEditMode(boolean editMode) {
        this.isEditMode = editMode;
        this.currentIconStyle = editMode ? IconStyle.EDIT : IconStyle.NORMAL;
        
        if (!editMode && selectedPoint != null) {
            selectedPoint.setSelected(false);
            selectedPoint = null;
        }
        
        // 控制脉冲动画
        if (editMode && !pulseAnimator.isRunning()) {
            pulseAnimator.start();
        } else if (!editMode && pulseAnimator.isRunning()) {
            pulseAnimator.cancel();
            pulseScale = 1.0f;
        }
        
        invalidate();
    }
    
    /**
     * 获取编辑模式状态
     */
    public boolean isEditMode() {
        return isEditMode;
    }
    
    /**
     * 清空所有检测点
     */
    public void clearPoints() {
        points.clear();
        selectedPoint = null;
        invalidate();
        if (pointChangeListener != null) {
            pointChangeListener.onPointCountChanged(0);
        }
    }
    
    /**
     * 获取检测点数量
     */
    public int getPointCount() {
        return points.size();
    }
    
    /**
     * 设置点变化监听器
     */
    public void setOnPointChangeListener(OnPointChangeListener listener) {
        this.pointChangeListener = listener;
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        
        // 绘制所有检测点
        for (DetectionPoint point : points) {
            drawDetectionPoint(canvas, point);
        }
    }
    
    /**
     * 绘制单个检测点
     */
    private void drawDetectionPoint(Canvas canvas, DetectionPoint point) {
        float x = point.getX();
        float y = point.getY();
        boolean isSelected = point.isSelected();
        int index = point.getIndex();
        
        // 确定当前点的图标样式
        IconStyle style = determineIconStyle(point, isSelected);
        
        // 应用动画缩放
        float animatedRadius = ICON_RADIUS * pulseScale;
        float animatedInnerRadius = INNER_RADIUS * pulseScale;
        
        switch (style) {
            case NORMAL:
                drawNormalPoint(canvas, x, y, animatedRadius, animatedInnerRadius);
                break;
            case EDIT:
                drawEditPoint(canvas, x, y, animatedRadius, animatedInnerRadius);
                break;
            case SELECTED:
                drawSelectedPoint(canvas, x, y, animatedRadius, animatedInnerRadius);
                break;
            case NEW_POINT:
                drawNewPoint(canvas, x, y, animatedRadius, animatedInnerRadius);
                break;
            case DELETING:
                drawDeletingPoint(canvas, x, y, animatedRadius, animatedInnerRadius);
                break;
        }
        
        // 绘制中心十字
        drawCross(canvas, x, y, style);
        
        // 绘制序号
        if (index > 0) {
            drawPointNumber(canvas, x, y, index, style);
        }
    }
    
    /**
     * 确定图标样式
     */
    private IconStyle determineIconStyle(DetectionPoint point, boolean isSelected) {
        if (isSelected) {
            return IconStyle.SELECTED;
        } else if (currentIconStyle == IconStyle.EDIT) {
            return IconStyle.EDIT;
        } else {
            return IconStyle.NORMAL;
        }
    }
    
    /**
     * 绘制普通模式点
     */
    private void drawNormalPoint(Canvas canvas, float x, float y, float radius, float innerRadius) {
        // 绘制阴影效果
        Paint shadowPaint = new Paint();
        shadowPaint.setAntiAlias(true);
        shadowPaint.setColor(0x40000000);
        shadowPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x + SHADOW_OFFSET, y + SHADOW_OFFSET, radius, shadowPaint);
        
        // 绘制主圆
        canvas.drawCircle(x, y, radius, normalPaint);
        
        // 绘制边框
        Paint borderPaint = new Paint();
        borderPaint.setAntiAlias(true);
        borderPaint.setColor(0xFF0000FF);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(2f);
        canvas.drawCircle(x, y, innerRadius, borderPaint);
    }
    
    /**
     * 绘制编辑模式点
     */
    private void drawEditPoint(Canvas canvas, float x, float y, float radius, float innerRadius) {
        // 绘制渐变背景
        Paint gradientPaint = new Paint();
        gradientPaint.setAntiAlias(true);
        gradientPaint.setColor(0x6000FFFF);
        canvas.drawCircle(x, y, radius * 1.2f, gradientPaint);
        
        // 绘制主圆
        canvas.drawCircle(x, y, radius, normalPaint);
        
        // 绘制闪烁边框
        Paint flashBorder = new Paint();
        flashBorder.setAntiAlias(true);
        flashBorder.setColor(0xFFFF00FF);
        flashBorder.setStyle(Paint.Style.STROKE);
        flashBorder.setStrokeWidth(3f);
        canvas.drawCircle(x, y, innerRadius, flashBorder);
    }
    
    /**
     * 绘制选中点
     */
    private void drawSelectedPoint(Canvas canvas, float x, float y, float radius, float innerRadius) {
        // 绘制选中外发光
        Paint glowPaint = new Paint();
        glowPaint.setAntiAlias(true);
        glowPaint.setColor(0x80FFFF00);
        canvas.drawCircle(x, y, radius * 1.5f, glowPaint);
        
        // 绘制选中背景
        canvas.drawCircle(x, y, radius, selectedPaint);
        
        // 绘制金色边框
        Paint goldBorder = new Paint();
        goldBorder.setAntiAlias(true);
        goldBorder.setColor(0xFFFFFF00);
        goldBorder.setStyle(Paint.Style.STROKE);
        goldBorder.setStrokeWidth(4f);
        canvas.drawCircle(x, y, innerRadius, goldBorder);
    }
    
    /**
     * 绘制新添加的点
     */
    private void drawNewPoint(Canvas canvas, float x, float y, float radius, float innerRadius) {
        // 绘制绿色背景
        Paint greenPaint = new Paint();
        greenPaint.setAntiAlias(true);
        greenPaint.setColor(0x8000FF00);
        canvas.drawCircle(x, y, radius, greenPaint);
        
        // 绘制绿色边框
        Paint greenBorder = new Paint();
        greenBorder.setAntiAlias(true);
        greenBorder.setColor(0xFF00FF00);
        greenBorder.setStyle(Paint.Style.STROKE);
        greenBorder.setStrokeWidth(3f);
        canvas.drawCircle(x, y, innerRadius, greenBorder);
    }
    
    /**
     * 绘制删除中的点
     */
    private void drawDeletingPoint(Canvas canvas, float x, float y, float radius, float innerRadius) {
        // 绘制红色警告背景
        Paint redPaint = new Paint();
        redPaint.setAntiAlias(true);
        redPaint.setColor(0x80FF0000);
        canvas.drawCircle(x, y, radius, redPaint);
        
        // 绘制红色边框
        Paint redBorder = new Paint();
        redBorder.setAntiAlias(true);
        redBorder.setColor(0xFFFF0000);
        redBorder.setStyle(Paint.Style.STROKE);
        redBorder.setStrokeWidth(3f);
        canvas.drawCircle(x, y, innerRadius, redBorder);
    }
    
    /**
     * 绘制中心十字
     */
    private void drawCross(Canvas canvas, float x, float y, IconStyle style) {
        Paint crossPaintStyle = new Paint(crossPaint);
        
        // 根据样式调整十字颜色
        switch (style) {
            case SELECTED:
                crossPaintStyle.setColor(0xFF000000); // 黑色十字在金色背景上更明显
                break;
            case NEW_POINT:
                crossPaintStyle.setColor(0xFF000000); // 黑色十字
                break;
            case DELETING:
                crossPaintStyle.setColor(0xFFFFFFFF); // 白色十字在红色背景上
                break;
            default:
                crossPaintStyle.setColor(0xFFFFFFFF); // 白色十字
                break;
        }
        
        float crossSize = CROSS_SIZE * pulseScale;
        canvas.drawLine(x - crossSize, y, x + crossSize, y, crossPaintStyle);
        canvas.drawLine(x, y - crossSize, x, y + crossSize, crossPaintStyle);
    }
    
    /**
     * 绘制点序号
     */
    private void drawPointNumber(Canvas canvas, float x, float y, int index, IconStyle style) {
        Paint numberPaint = new Paint(textPaint);
        
        // 根据样式调整数字颜色和大小
        switch (style) {
            case SELECTED:
                numberPaint.setColor(0xFF000000); // 黑色数字
                numberPaint.setTextSize(24f * pulseScale);
                break;
            case NEW_POINT:
                numberPaint.setColor(0xFF000000); // 黑色数字
                numberPaint.setTextSize(22f * pulseScale);
                break;
            case DELETING:
                numberPaint.setColor(0xFFFF0000); // 红色数字
                numberPaint.setTextSize(20f * pulseScale);
                break;
            default:
                numberPaint.setColor(0xFFFFFFFF); // 白色数字
                numberPaint.setTextSize(20f * pulseScale);
                break;
        }
        
        // 添加数字背景框（仅对重要状态）
        if (style == IconStyle.SELECTED || style == IconStyle.DELETING) {
            String numberText = String.valueOf(index);
            float textWidth = numberPaint.measureText(numberText);
            float textHeight = numberPaint.getTextSize();
            
            Paint bgPaint = new Paint();
            bgPaint.setColor(style == IconStyle.SELECTED ? 0xFFFFFF00 : 0xFFFF0000);
            bgPaint.setStyle(Paint.Style.FILL);
            
            canvas.drawRoundRect(
                x - textWidth/2 - 4,
                y - TEXT_OFFSET - textHeight/2 - 2,
                x + textWidth/2 + 4,
                y - TEXT_OFFSET + textHeight/2 + 2,
                4f, 4f, bgPaint
            );
        }
        
        canvas.drawText(String.valueOf(index), x, y - TEXT_OFFSET, numberPaint);
    }
    
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEditMode) {
            return false;
        }
        
        float x = event.getX();
        float y = event.getY();
        
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return handleTouchDown(x, y);
                
            case MotionEvent.ACTION_MOVE:
                return handleTouchMove(x, y);
                
            case MotionEvent.ACTION_UP:
                return handleTouchUp(x, y);
                
            case MotionEvent.ACTION_CANCEL:
                return handleTouchCancel();
        }
        
        return super.onTouchEvent(event);
    }
    
    /**
     * 处理触摸按下事件
     */
    private boolean handleTouchDown(float x, float y) {
        long currentTime = System.currentTimeMillis();
        
        // 双击检测
        if (isDoubleTap(currentTime, x, y)) {
            handleDoubleTap(x, y);
            lastTapTime = 0; // 重置防止连续触发
            return true;
        }
        
        // 更新上次点击信息
        lastTapTime = currentTime;
        lastTapX = x;
        lastTapY = y;
        
        // 命中检测
        DetectionPoint hitPoint = findHitPoint(x, y);
        
        if (hitPoint != null) {
            // 选中已存在的点
            if (selectedPoint != null && selectedPoint != hitPoint) {
                selectedPoint.setSelected(false);
            }
            selectedPoint = hitPoint;
            hitPoint.setSelected(true);
            invalidate();
            
            if (pointChangeListener != null) {
                pointChangeListener.onPointSelected(hitPoint);
            }
            return true;
        } else {
            // 取消当前选中
            if (selectedPoint != null) {
                selectedPoint.setSelected(false);
                selectedPoint = null;
                invalidate();
            }
        }
        
        return true;
    }
    
    /**
     * 处理触摸移动事件
     */
    private boolean handleTouchMove(float x, float y) {
        if (selectedPoint != null) {
            // 移动选中的点
            selectedPoint.setX(x);
            selectedPoint.setY(y);
            invalidate();
            return true;
        }
        return false;
    }
    
    /**
     * 处理触摸抬起事件
     */
    private boolean handleTouchUp(float x, float y) {
        return true;
    }
    
    /**
     * 处理触摸取消事件
     */
    private boolean handleTouchCancel() {
        if (selectedPoint != null) {
            selectedPoint.setSelected(false);
            selectedPoint = null;
            invalidate();
        }
        return true;
    }
    
    /**
     * 改进的命中检测算法
     * 使用距离权重和优先级排序来提高准确性
     */
    private DetectionPoint findHitPoint(float x, float y) {
        DetectionPoint closestPoint = null;
        float minDistance = Float.MAX_VALUE;
        
        // 遍历所有点，找到最近的点
        for (DetectionPoint point : points) {
            float distance = point.distanceTo(x, y);
            
            // 如果在命中半径内且距离更近
            if (distance <= HIT_RADIUS && distance < minDistance) {
                minDistance = distance;
                closestPoint = point;
            }
        }
        
        return closestPoint;
    }
    
    /**
     * 判断是否为双击
     */
    private boolean isDoubleTap(long currentTime, float x, float y) {
        if (lastTapTime == 0) return false;
        
        long timeDelta = currentTime - lastTapTime;
        float distance = (float) Math.sqrt((x - lastTapX) * (x - lastTapX) + 
                                          (y - lastTapY) * (y - lastTapY));
        
        return timeDelta <= DOUBLE_TAP_TIMEOUT && distance <= DOUBLE_TAP_SLOP;
    }
    
    /**
     * 处理双击事件
     */
    private void handleDoubleTap(float x, float y) {
        DetectionPoint hitPoint = findHitPoint(x, y);
        
        if (hitPoint != null) {
            // 双击已有点：删除该点
            removePoint(hitPoint);
        } else {
            // 双击空白区域：添加新点
            addNewPoint(x, y);
        }
    }
    
    /**
     * 添加新的检测点（供外部调用）
     */
    public void addNewPoint(float x, float y) {
        DetectionPoint newPoint = new DetectionPoint(x, y);
        addPoint(newPoint);
    }
    
    /**
     * 移动选中的点到新位置
     */
    public void moveSelectedPoint(float newX, float newY) {
        if (selectedPoint != null) {
            selectedPoint.setX(newX);
            selectedPoint.setY(newY);
            invalidate();
        }
    }
    
    /**
     * 取消选中状态
     */
    public void deselectPoint() {
        if (selectedPoint != null) {
            selectedPoint.setSelected(false);
            selectedPoint = null;
            invalidate();
        }
    }
    
    /**
     * 获取命中检测半径
     */
    public float getHitRadius() {
        return HIT_RADIUS;
    }
    
    /**
     * 检查坐标是否在任何检测点的命中范围内
     */
    public boolean isPointInHitArea(float x, float y) {
        return findHitPoint(x, y) != null;
    }
    
    /**
     * 获取选中的检测点
     */
    public DetectionPoint getSelectedPoint() {
        return selectedPoint;
    }
    
    /**
     * 设置特定点的样式
     */
    public void setPointStyle(DetectionPoint point, IconStyle style) {
        // 可以在这里添加特殊的点样式逻辑
        invalidate();
    }
    
    /**
     * 触发特殊动画效果
     */
    public void triggerSpecialAnimation(IconStyle style) {
        switch (style) {
            case NEW_POINT:
                // 触发添加点动画
                startAddAnimation();
                break;
            case DELETING:
                // 触发删除点动画
                startDeleteAnimation();
                break;
        }
    }
    
    /**
     * 开始添加动画
     */
    private void startAddAnimation() {
        // 可以实现缩放弹跳效果
        ValueAnimator addAnimator = ValueAnimator.ofFloat(0f, 1f);
        addAnimator.setDuration(300);
        addAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // 实现添加动画效果
                invalidate();
            }
        });
        addAnimator.start();
    }
    
    /**
     * 开始删除动画
     */
    private void startDeleteAnimation() {
        // 可以实现淡出或收缩效果
        ValueAnimator deleteAnimator = ValueAnimator.ofFloat(1f, 0f);
        deleteAnimator.setDuration(200);
        deleteAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                // 实现删除动画效果
                invalidate();
            }
        });
        deleteAnimator.start();
    }
    
    /**
     * 生命周期管理 - 视图附着到窗口时
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 可以在这里启动必要的动画
    }
    
    /**
     * 生命周期管理 - 视图从窗口分离时
     */
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        // 清理动画资源
        if (pulseAnimator != null && pulseAnimator.isRunning()) {
            pulseAnimator.cancel();
        }
    }
    
    /**
     * 获取当前图标样式
     */
    public IconStyle getCurrentIconStyle() {
        return currentIconStyle;
    }
    
    /**
     * 批量更新点样式
     */
    public void updateAllPointsStyle(IconStyle style) {
        this.currentIconStyle = style;
        invalidate();
    }
}