// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

package com.hbu.zhangshanghbu.campus;

import android.app.Activity;

import com.hbu.zhangshanghbu.data.Store;

import org.json.JSONObject;

/**
 * 登录后把「学生本人」的信息补齐：姓名、学号、院系、专业、班级、头像。
 *
 * 这些都不让用户填 —— 教务里本来就有，登录完顺手读回来，
 * 以后「我的」直接显示真名和头像，而不是一串学号。
 *
 * 只读了就存本地，不上传；头像解码后落成文件（几十 KB 的 base64 不适合塞 SharedPreferences）。
 */
public final class Profile {

    public interface Done {
        void onDone(boolean ok);
    }

    private Profile() {}

    /** 有缓存且不超过 6 小时，就不再打一次教务。 */
    public static boolean fresh(Store store) {
        return store.profileAt() > 0 && !store.studentNo().isEmpty()
                && System.currentTimeMillis() - store.profileAt() < 6 * 3600_000L;
    }

    public static void refresh(Activity host, Store store, Done done) {
        Engine.fetchProfile(host, (ok, json, msg) -> {
            boolean saved = false;
            if (ok) {
                try {
                    JSONObject env = new JSONObject(json);
                    JSONObject d = env.optJSONObject("data");
                    if (d != null) {
                        String avatar = d.optString("avatar", "");
                        if (!avatar.isEmpty()) store.saveAvatar(avatar);
                        store.setProfile(d.optString("name", ""), d.optString("no", ""),
                                d.optString("dept", ""), d.optString("major", ""),
                                d.optString("cls", ""));
                        saved = true;
                    }
                } catch (Exception ignored) {
                }
            }
            if (done != null) done.onDone(saved);
        });
    }
}
