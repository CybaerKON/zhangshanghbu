// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

/*
 * 菜单读取脚本。
 *
 * 为什么需要它：强智 URP 的每个功能页都要 cookie selectionBar=<菜单 li 的 id>，
 * 而这个 id 是**每个账号、每次会话都不一样**的（它是数据库里的菜单行 id，
 * 换个人登录、甚至同一个人重新登录都可能变）。写死 id 在别人账号上必然 500。
 *
 * 所以这里的做法是：打开 /index，把「菜单名 + 当前 li id」原样交回原生层，
 * 由原生层按菜单名去匹配要进哪个页面。这样换账号只要菜单名不变就能用。
 */
(function () {
  function post(o) {
    try { HBU.onData(JSON.stringify(o)); } catch (e) {}
  }

  try {
    var pwd = 0;
    var ins = document.querySelectorAll('input[type=password]');
    for (var i = 0; i < ins.length; i++) {
      var b = ins[i].getBoundingClientRect();
      if (b.width > 2 && b.height > 2) pwd++;
    }
    if (pwd > 0) {
      post({ ok: false, need: 'login', msg: '需要重新登录' });
      return;
    }

    var menu = [];
    var lis = document.querySelectorAll('li[id]');
    for (var k = 0; k < lis.length; k++) {
      var li = lis[k];
      var a = li.querySelector('a');
      if (!a) continue;
      var label = (a.innerText || '').replace(/\s+/g, ' ').trim();
      if (!label) continue;
      menu.push({ id: li.id, label: label });
    }

    // 有些系统的 li id 不是数字（比如用 data-* 存），顺带把 path 也带上
    post({ ok: true, kind: 'menu', menu: menu, title: document.title || '' });
  } catch (e) {
    post({ ok: false, msg: '读取菜单失败：' + e });
  }
})();
