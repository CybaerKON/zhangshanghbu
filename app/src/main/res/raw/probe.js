// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

/*
 * 登录态探针：只回答一个问题 —— 这个页面上还认不认我。
 *
 * 判定以「有没有可见密码框」为主，配合地址与页面特征；姓名 / 学号只做展示用，
 * 读不到就算了，绝不能因为猜错名字让界面出错。
 */
(function () {
  try {
    var d = document, u = location.href;
    var pwd = 0, ins = d.querySelectorAll('input[type=password]');
    for (var i = 0; i < ins.length; i++) {
      var r = ins[i].getBoundingClientRect();
      if (r.width > 2 && r.height > 2) pwd++;
    }
    var body = d.body ? (d.body.innerText || d.body.textContent || '') : '';
    var name = '';
    var m = body.match(/姓名[：:\s]*([\u4e00-\u9fa5]{2,4})/);
    if (!m) m = body.match(/(?:欢迎|你好)[，,：:\s]+([\u4e00-\u9fa5]{2,3})(?:同学|老师)?/);
    if (m && !/^(使用|登录|访问|来到|您|大家)$/.test(m[1])) name = m[1];

    var user = '';
    m = body.match(/(?:学号|用户名|帐号|账号)[：:\s]*([A-Za-z0-9]{6,20})/);
    if (m) user = m[1];

    /* 登录入口页上那个「统一认证（CAS登录业务系统）」按钮：
       有就直接帮用户点掉，省得他自己找。 */
    var cas = d.getElementById('cas-login') ? 1 : 0;

    HBU_P.onProbe(JSON.stringify({
      url: u, title: d.title || '', pwd: pwd, name: name, user: user, cas: cas,
      logout: /退出|注销|安全退出/.test(body) ? 1 : 0
    }));
  } catch (e) {
    try { HBU_P.onProbe(JSON.stringify({ url: location.href, pwd: 0, err: '' + e })); } catch (e2) {}
  }
})();
