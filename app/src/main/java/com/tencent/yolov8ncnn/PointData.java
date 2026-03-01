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

/**
 * 盘点点位数据模型
 * 用于序列化检测点信息到JSON
 */
public class PointData {
    private int id;
    private float x;
    private float y;
    private boolean selected;
    
    public PointData() {}
    
    public PointData(int id, float x, float y, boolean selected) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.selected = selected;
    }
    
    // Getter和Setter方法
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public float getX() {
        return x;
    }
    
    public void setX(float x) {
        this.x = x;
    }
    
    public float getY() {
        return y;
    }
    
    public void setY(float y) {
        this.y = y;
    }
    
    public boolean isSelected() {
        return selected;
    }
    
    public void setSelected(boolean selected) {
        this.selected = selected;
    }
    
    @Override
    public String toString() {
        return "PointData{" +
                "id=" + id +
                ", x=" + x +
                ", y=" + y +
                ", selected=" + selected +
                '}';
    }
}