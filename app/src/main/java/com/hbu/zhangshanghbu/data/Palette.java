// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 课表配色：8 组主色/浅底色，按课程顺序分配，尽量让相邻课程不撞色。
 * 颜色值写死在这里，XML 里也留一份（widget 用），两边保持同样顺序。
 */
public final class Palette {

    private Palette() {}

    public static final int SIZE = 8;

    public static final int[] MAIN = {
            0xFF3B7BE8, 0xFFE8734A, 0xFF3FA37A, 0xFF9B6BDA,
            0xFFD9A21B, 0xFF3F9FB8, 0xFFC9527A, 0xFF6E7FA8
    };

    public static final int[] SOFT_LIGHT = {
            0xFFE8EFFC, 0xFFFCEEE8, 0xFFE8F5EF, 0xFFF3EBFB,
            0xFFFBF3DE, 0xFFE8F4F8, 0xFFFAEAEF, 0xFFEEF1F7
    };

    public static final int[] SOFT_DARK = {
            0xFF1B2A44, 0xFF3A241C, 0xFF17332A, 0xFF2E1F3D,
            0xFF3A2F14, 0xFF152E38, 0xFF3A1F29, 0xFF232A38
    };

    public static int main(int i) {
        return MAIN[((i % SIZE) + SIZE) % SIZE];
    }

    public static int soft(int i, boolean night) {
        int k = ((i % SIZE) + SIZE) % SIZE;
        return night ? SOFT_DARK[k] : SOFT_LIGHT[k];
    }

    /** 给整张表排色：先按 key 的哈希占位，撞了就往后找空位。 */
    public static Map<String, Integer> assign(Table t) {
        Map<String, Integer> out = new LinkedHashMap<>();
        boolean[] used = new boolean[SIZE];
        for (Table.Course c : t.courses) {
            int idx = Math.abs((c.key == null ? c.name : c.key).hashCode()) % SIZE;
            int k = 0;
            while (used[idx] && k < SIZE) {
                idx = (idx + 1) % SIZE;
                k++;
            }
            used[idx] = true;
            c.color = idx;
            out.put(c.key, idx);
        }
        return out;
    }
}
