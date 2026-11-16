// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

/*
 * 河大 URP（强智）取数脚本。
 *
 * 这个系统有两种数据来源，不能用同一套办法：
 *   1. 课表 / 成绩：页面自己 ajax 一个带随机令牌的 callback 接口，令牌写在页面内联 JS 里；
 *   2. 考试安排：数据是服务端直出的表格（两套表：按课程、按日期），没有 JSON 接口；
 *   3. 空闲教室：要 POST /freeClassroom/today/<节次> 才返回 JSON。
 * 所以这里按 KIND 分支处理，全部在同源执行，Cookie 由 WebView 自动带上。
 *
 * 协议：HBU.onData(JSON.stringify({ok, kind, data|headers+rows, msg}))
 *   kind = courses / grades / exams / free / profile
 */
(function () {
  var K = "__KIND__";
  var EXTRA = __EXTRA__;
  var sent = false;

  function post(o) {
    if (sent) return;
    sent = true;
    try { window.HBU.onData(JSON.stringify(o)); } catch (e) {}
  }

  function textOf(el) { return (el && el.textContent || '').replace(/\s+/g, ' ').trim(); }

  function bodyText() {
    var b = document.body;
    if (!b) return '';
    return b.innerText || b.textContent || '';
  }

  function visiblePasswords() {
    var n = 0;
    var list = document.querySelectorAll('input[type=password]');
    for (var i = 0; i < list.length; i++) {
      var b = list[i].getBoundingClientRect();
      if (b.width > 2 && b.height > 2) n++;
    }
    return n;
  }

  /* 找出本页的数据接口。
     优先用 performance 里已经发生过的请求（最准），再退回内联 JS 的 url:。 */
  function apiUrl() {
    try {
      var list = performance.getEntriesByType('resource') || [];
      for (var i = list.length - 1; i >= 0; i--) {
        var n = list[i].name || '';
        if (n.indexOf('/callback') >= 0 || n.indexOf('callback?') >= 0) return n;
      }
    } catch (e) {}
    var html = document.documentElement.innerHTML;
    var m = html.match(/url\s*:\s*["']([^"']*callback[^"']*)["']/);
    if (m) {
      try { return new URL(m[1], location.href).toString(); } catch (e2) { return ''; }
    }
    return '';
  }

  function req(url, body) {
    return fetch(url, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'X-Requested-With': 'XMLHttpRequest',
        'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
      },
      body: body || ''
    }).then(function (r) { return r.text(); });
  }

  /* ---------------- 课表 / 成绩：接口 JSON ---------------- */

  function apiKinds() {
    var u = apiUrl();
    if (!u) {
      post({ ok: false, msg: '这页没找到数据接口（可能还没登录，或教务改版了）' });
      return;
    }
    req(u).then(function (body) {
      if (!body || body.charAt(0) !== '{') {
        if (body && body.indexOf('<') === 0) {
          post({ ok: false, need: 'login', msg: '需要重新登录' });
        } else {
          post({ ok: false, msg: '接口返回异常' });
        }
        return;
      }
      if (K !== 'courses') {
        post({ ok: true, kind: 'urp', data: body });
        return;
      }
      /* 课表额外把作息表取回来：这是学校真实的每节起止时间，
         比让用户自己猜默认值靠谱。 */
      req('/ajax/getSectionAndTime')
        .then(function (sec) { post({ ok: true, kind: 'urp', data: body, sections: sec }); })
        .catch(function () { post({ ok: true, kind: 'urp', data: body }); });
    }).catch(function (e) {
      post({ ok: false, msg: '读取失败：' + (e && e.message ? e.message : e) });
    });
  }

  /* ---------------- 考试安排：服务端渲染的两张表格 ---------------- */

  function cellText(c) { return (c && c.textContent || '').replace(/\s+/g, ' ').trim(); }

  function exams() {
    var cols = ['课程名', '考试名称', '考试时间', '校区', '教学楼', '教室', '座位'];
    var rows = [];
    var list = docs();
    var sawTable = false;

    for (var d = 0; d < list.length; d++) {
      var tables = list[d].doc.querySelectorAll('table');
      for (var i = 0; i < tables.length; i++) {
        var trs = tables[i].querySelectorAll('tr');
        if (!trs.length) continue;
        var head = [];
        var first = trs[0].querySelectorAll('th,td');
        for (var h = 0; h < first.length; h++) head.push(cellText(first[h]));
        var headLine = head.join(' ');
        if (!/考试|考场/.test(headLine)) continue;
        sawTable = true;

        for (var r = 1; r < trs.length; r++) {
          var cells = trs[r].querySelectorAll('td');
          if (!cells.length) continue;
          var map = {};
          for (var c = 0; c < cells.length && c < head.length; c++) map[head[c]] = cellText(cells[c]);

          var name = map['课程名'] || map['课程名称'] || map['课程'] || '';
          if (!name) continue;
          var examName = map['考试名称'] || '';
          var time = map['考试时间'] || '';
          if (!time) {
            var day = map['考试日期'] || map['日期'] || '';
            var start = map['开始时间'] || map['时间'] || '';
            time = (day + ' ' + start).trim();
          }
          var room = [map['校区'] || '', map['教学楼'] || '', map['教室'] || map['考场'] || '']
            .filter(function (x) { return !!x; }).join(' ');

          rows.push([
            name,
            examName,
            time,
            map['校区'] || '',
            map['教学楼'] || '',
            room,
            map['座位'] || map['座号'] || ''
          ]);
        }
      }
    }

    if (!sawTable) {
      post({ ok: false, msg: '这页没有考试安排表（可能还没登录，或教务改版了）' });
      return;
    }
    post({ ok: true, kind: 'exams', headers: cols, rows: rows, msg: '' });
  }

  /* ---------------- 空闲教室：POST 才返回 JSON ---------------- */

  function free() {
    var E = EXTRA || {};
    var sections = String(E.sections || '1,2');
    var dayplus = String(E.dayplus == null ? 0 : E.dayplus);
    var campusNo = String(E.campus == null ? '' : E.campus);

    /* 注意：清单用 campusList，当前选中的校区号用 campus。
       之前两个都叫 campus，对象字面量里后者覆盖前者，
       结果 out.campus 是字符串、.push 直接报错。 */
    var out = { campusList: [], list: [], curxqm: '', sections: sections,
                dayplus: dayplus, campus: campusNo };

    function parse(txt) {
      try { return JSON.parse(txt); } catch (e) { return null; }
    }

    function pickCampus() {
      /* 不指定就用第一个正常校区（清单里带「请选择校区」这种占位项，要跳过） */
      if (campusNo) {
        for (var i = 0; i < out.campusList.length; i++) {
          if (out.campusList[i].n === campusNo) return out.campusList[i];
        }
      }
      for (var j = 0; j < out.campusList.length; j++) {
        if (out.campusList[j].name && out.campusList[j].name.indexOf('请选择') < 0) return out.campusList[j];
      }
      return null;
    }

    req('/student/teachingResources/freeClassroom/queryCodeCampusList', '')
      .then(function (txt) {
        var j = parse(txt);
        if (j && j.length) {
          for (var i = 0; i < j.length; i++) {
            out.campusList.push({ n: String(j[i].campusNumber || ''), name: j[i].campusName || '' });
          }
        }
        var c = pickCampus();
        if (!c) return null;
        out.curxqm = c.name;
        out.campus = c.n;
        /* 关键一步：先把查询范围设成「这个校区的全部教学楼」。
           不设的话，服务端会沿用上一次在这个会话里选的某个教学楼，
           结果就只剩那一栋楼 —— 看起来像「查不到别的楼」。 */
        return req('/student/teachingResources/freeClassroom/today',
          'position=' + encodeURIComponent(c.n + '_n') + '&xqm=' + encodeURIComponent(c.name));
      })
      .then(function () {
        if (!sections) {
          post({ ok: true, kind: 'free', data: out });
          return null;
        }
        return req('/student/teachingResources/freeClassroom/today/' + sections, 'dayplus=' + dayplus);
      })
      .then(function (txt) {
        if (!txt) return;
        var j = parse(txt);
        if (!j) {
          post({ ok: false, need: 'login', msg: '查不到空闲教室，可能需要重新登录' });
          return;
        }
        out.curxqm = j.curxqm || out.curxqm;
        var arr = j.spareroomObjList || [];
        for (var i = 0; i < arr.length; i++) {
          var b = arr[i];
          var rooms = [];
          var cl = b.claroom || [];
          for (var k = 0; k < cl.length; k++) {
            rooms.push({ room: cl[k].classroom || '', seats: cl[k].classNumberOfSeats || '' });
          }
          out.list.push({ building: b.acmcBuildingName || '', code: String(b.acmcBuilding || ''), rooms: rooms });
        }
        post({ ok: true, kind: 'free', data: out });
      })
      .catch(function (e) {
        post({ ok: false, msg: '查询失败：' + (e && e.message ? e.message : e) });
      });
  }

  /* ---------------- 个人信息：学号 / 姓名 / 院系 + 头像 ---------------- */

  function firstMatch(text, re) {
    var m = text.match(re);
    return m ? String(m[1]).trim() : '';
  }

  /* 学籍页是一张「标签 | 值」的表，按单元格配对取最准；
     取不到再退回正则（不同学期的模板会变）。 */
  function kvPairs() {
    var out = {};
    var list = docs();
    var WANT = /^(学号|姓名|院系|专业|班级|年级|专业方向)$/;

    for (var d = 0; d < list.length; d++) {
      /* 学籍页的实际结构是 <div class="profile-info-name">班级</div>
         紧跟 <div class="profile-info-value">24示例2班</div>，不是表格。 */
      var names = list[d].doc.querySelectorAll('.profile-info-name');
      for (var n = 0; n < names.length; n++) {
        var label = (names[n].textContent || '').replace(/\s+/g, '');
        if (!WANT.test(label)) continue;
        var sib = names[n].nextElementSibling;
        if (!sib) continue;
        var v = (sib.textContent || '').replace(/\s+/g, ' ').trim();
        if (v && !out[label]) out[label] = v;
      }

      var rows = list[d].doc.querySelectorAll('tr');
      for (var r = 0; r < rows.length; r++) {
        var cells = rows[r].querySelectorAll('td,th');
        for (var c = 0; c + 1 < cells.length; c++) {
          var label = (cells[c].textContent || '').replace(/\s+/g, '');
          if (!label || label.length > 6) continue;
          if (!WANT.test(label)) continue;
          var val = (cells[c + 1].textContent || '').replace(/\s+/g, ' ').trim();
          if (val && !out[label]) out[label] = val;
        }
      }
    }
    return out;
  }

  function profile() {
    var text = bodyText().replace(/\s+/g, ' ');
    var kv = kvPairs();

    /* 这一页里「学号」「班级」这些字样出现过好几次（奖惩表、异动表、
       导航条都有），所以按单元格抓到的值必须过一遍校验，
       不合格就退回正则再取一次。 */
    function pick(v, re, ok) {
      if (v && ok(v)) return v;
      var alt = firstMatch(text, re);
      return (alt && ok(alt)) ? alt : '';
    }
    var isNo = function (v) { return /^\d{6,}$/.test(v); };
    var isName = function (v) { return /^[\u4e00-\u9fa5·]{2,8}$/.test(v); };
    var isWord = function (v) { return v && v.length >= 2 && v.length <= 20 && !/^[\d\s]+$/.test(v); };
    var isCls = function (v) { return v && v.length >= 2 && v.length <= 14 && /\d/.test(v); };

    var no = pick(kv['学号'], /学号[：:\s]*(\d{8,})/, isNo);
    var name = pick(kv['姓名'], /姓名[：:\s]*([\u4e00-\u9fa5·]{2,8})/, isName);
    if (!name) name = firstMatch(text, /欢迎您[，,]\s*([\u4e00-\u9fa5·]{2,8})/);
    var dept = pick(kv['院系'], /院系[：:\s]*([^\s]{2,20})/, isWord);
    var major = pick(kv['专业'], /(?:^|\s)专业[：:\s]*([^\s]{2,20})/, isWord);
    if (major.indexOf('方向') === 0) major = '';
    var clazz = pick(kv['班级'], /班级[：:\s]*([^\s]{2,20})/, isCls);
    var grade = pick(kv['年级'], /年级[：:\s]*([^\s]{2,8})/, isWord);

    var data = {
      no: no || '', name: name || '', dept: dept || '', major: major || '',
      cls: clazz || '', grade: grade || '', avatar: ''
    };

    /* 头像：学籍页那张大图优先 */
    var img = null;
    var imgs = document.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      var src = String(imgs[i].src || '');
      if (src.indexOf('/rollInfo/img') >= 0) { img = imgs[i]; break; }
    }
    if (!img) {
      for (var j = 0; j < imgs.length; j++) {
        var s2 = String(imgs[j].src || '');
        if (s2.indexOf('queryStudent/img') >= 0) { img = imgs[j]; break; }
      }
    }

    function done() { post({ ok: true, kind: 'profile', data: data }); }
    if (!img) { done(); return; }

    /* 用 arrayBuffer + btoa 而不是 FileReader：
       教务返回的 Content-Type 是 "img/*"，FileReader 会拼出
       "data:img/*;base64,…" 这种浏览器不认的 MIME，
       自己编码反而干净，也不用关心 MIME。 */
    fetch(img.src, { credentials: 'include' })
      .then(function (r) { return r.arrayBuffer(); })
      .then(function (buf) {
        var bytes = new Uint8Array(buf);
        var bin = '';
        var CH = 0x8000;
        for (var k = 0; k < bytes.length; k += CH) {
          bin += String.fromCharCode.apply(null, bytes.subarray(k, k + CH));
        }
        data.avatar = btoa(bin);
        done();
      })
      .catch(function () { done(); });
  }

  /* ---------------- 入口 ---------------- */

  function docs() {
    var list = [{ doc: document, url: location.href }];
    try {
      var fs = document.querySelectorAll('iframe,frame');
      for (var i = 0; i < fs.length; i++) {
        try {
          var d = fs[i].contentDocument;
          if (d) list.push({ doc: d, url: (fs[i].contentWindow || {}).location ? fs[i].contentWindow.location.href : '' });
        } catch (e) {}
      }
    } catch (e2) {}
    return list;
  }

  function start() {
    if (visiblePasswords() > 0) {
      post({ ok: false, need: 'login', msg: '需要重新登录' });
      return;
    }
    if (K === 'exams') { exams(); return; }
    if (K === 'free') { free(); return; }
    if (K === 'profile') { profile(); return; }
    apiKinds();
  }

  // 页面自己的 ajax 可能还没发完，等一拍再抓
  setTimeout(start, 800);
})();
