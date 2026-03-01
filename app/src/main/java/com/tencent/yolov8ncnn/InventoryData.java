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

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

/**
 * 盘点数据模型
 * 包含整个盘点会话的信息
 */
public class InventoryData {
    private long timestamp;
    private int pointCount;
    private List<PointData> points;
    
    public InventoryData() {}
    
    public InventoryData(long timestamp, int pointCount, List<PointData> points) {
        this.timestamp = timestamp;
        this.pointCount = pointCount;
        this.points = points;
    }
    
    // Getter和Setter方法
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public int getPointCount() {
        return pointCount;
    }
    
    public void setPointCount(int pointCount) {
        this.pointCount = pointCount;
    }
    
    public List<PointData> getPoints() {
        return points;
    }
    
    public void setPoints(List<PointData> points) {
        this.points = points;
    }
    
    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("timestamp", timestamp);
            jsonObject.put("pointCount", pointCount);
            
            JSONArray pointsArray = new JSONArray();
            if (points != null) {
                for (PointData point : points) {
                    JSONObject pointObj = new JSONObject();
                    pointObj.put("id", point.getId());
                    pointObj.put("x", point.getX());
                    pointObj.put("y", point.getY());
                    pointObj.put("selected", point.isSelected());
                    pointsArray.put(pointObj);
                }
            }
            jsonObject.put("points", pointsArray);
            
            return jsonObject.toString();
        } catch (Exception e) {
            throw new RuntimeException("JSON序列化失败", e);
        }
    }
    
    /**
     * 从JSON字符串解析
     */
    public static InventoryData fromJson(String jsonString) {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            InventoryData inventoryData = new InventoryData();
            inventoryData.setTimestamp(jsonObject.getLong("timestamp"));
            inventoryData.setPointCount(jsonObject.getInt("pointCount"));
            
            // 解析点位数据
            JSONArray pointsArray = jsonObject.getJSONArray("points");
            // 注意：这里需要在实际使用时配合Gson或其他JSON库来解析List<PointData>
            
            return inventoryData;
        } catch (Exception e) {
            throw new RuntimeException("JSON反序列化失败", e);
        }
    }
    
    @Override
    public String toString() {
        return "InventoryData{" +
                "timestamp=" + timestamp +
                ", pointCount=" + pointCount +
                ", points=" + (points != null ? points.size() : 0) + " items" +
                '}';
    }
}