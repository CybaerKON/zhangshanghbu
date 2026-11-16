// SPDX-License-Identifier: AGPL-3.0-only
// Copyright (C) 2026 二相穆者波形 <https://github.com/CybaerKON/zhangshanghbu>

/*
 * 页面解析脚本（只解析当前页，不做跳转）。
 *
 * 为什么不做跳转：跳转由原生层按候选地址逐个 loadUrl 完成，能明确知道「这一页是哪一步、
 * 失败在哪一页」；脚本自己用 sessionStorage 记步数会随 WebView 重建而丢失，
 * 表现就是来回空跳、永远解析不出来。
 *
 * 协议：HBU.onData(JSON.stringify({ok,kind,records|headers+rows,tabs,term,semesterText,msg}))
 *   kind = courses / grades / exams / calendar / links
 *
 * 所有页面都在同一个源里（WebVPN 编码前缀 / Cookie / Referer 自动正确），
 * URP 的 GB2312 页面由浏览器解码后再交给我们，不存在乱码问题。
 */
(function () {
  var KIND = "__KIND__";
  var DONE = false;

  function send(o) {
    if (DONE) return;
    DONE = true;
    try { HBU.onData(JSON.stringify(o)); } catch (e) {}
  }

  function txt(e) {
    return (e && e.textContent || '').replace(/\s+/g, ' ').trim();
  }

  /* URP 有 frameset，课表/成绩可能在子框架里；同源都能读 */
  function docs() {
    var out = [{ doc: document, url: location.href }];
    try {
      for (var i = 0; i < window.frames.length; i++) {
        try {
          var w = window.frames[i];
          if (w && w.document && w.document.body) {
            out.push({ doc: w.document, url: w.location.href });
            for (var j = 0; j < w.frames.length; j++) {
              try {
                var w2 = w.frames[j];
                if (w2 && w2.document && w2.document.body) {
                  out.push({ doc: w2.document, url: w2.location.href });
                }
              } catch (e2) {}
            }
          }
        } catch (e) {}
      }
    } catch (e) {}
    return out;
  }

  function bodyText(d) {
    var b = d.body;
    return b ? (b.innerText || b.textContent || '') : '';
  }

  function allText() {
    var parts = [], list = docs();
    for (var i = 0; i < list.length; i++) parts.push(bodyText(list[i].doc));
    return parts.join('\n');
  }

  /* ---------------- 周次 / 节次 ---------------- */

  function parseWeeks(expr) {
    var s = (expr || '').replace(/[（）()]/g, '');
    var odd = /单周|单/.test(s), even = /双周|双/.test(s);
    var out = [], m;
    var re = /(\d{1,2})\s*[-~—至]\s*(\d{1,2})/g;
    while ((m = re.exec(s))) {
      var a = +m[1], b = +m[2];
      if (b < a) { var t = a; a = b; b = t; }
      if (b > 40) continue;
      for (var w = a; w <= b; w++) {
        if (odd && w % 2 === 0) continue;
        if (even && w % 2 === 1) continue;
        out.push(w);
      }
    }
    if (!out.length) {
      var singles = s.match(/\d{1,2}/g) || [];
      for (var i = 0; i < singles.length; i++) out.push(+singles[i]);
    }
    var uniq = [];
    for (var k = 0; k < out.length; k++) {
      if (uniq.indexOf(out[k]) < 0) uniq.push(out[k]);
    }
    return uniq.sort(function (x, y) { return x - y; });
  }

  function parseSections(raw) {
    var s = raw || '';
    var m = s.match(/(\d{1,2})\s*[-~—至]\s*(\d{1,2})\s*节/);
    if (m) return [+m[1], +m[2]];
    m = s.match(/(\d{1,2})\s*节/);
    if (m) return [+m[1], +m[1]];
    return [0, 0];
  }

  var DAY_RE = [/(?:星期|周)\s*一/, /(?:星期|周)\s*二/, /(?:星期|周)\s*三/, /(?:星期|周)\s*四/,
    /(?:星期|周)\s*五/, /(?:星期|周)\s*六/, /(?:星期|周)\s*[日天]/];

  function dayOf(text) {
    var s = (text || '').replace(/\s+/g, '');
    for (var i = 0; i < 7; i++) if (DAY_RE[i].test(s)) return i + 1;
    return 0;
  }

  /* ---------------- 表格网格 ---------------- */

  function toGrid(tb) {
    var grid = [], trs = tb.rows, r, k;
    for (r = 0; r < trs.length; r++) {
      if (!grid[r]) grid[r] = [];
      var cells = trs[r].cells, c = 0;
      for (k = 0; k < cells.length; k++) {
        while (grid[r][c] !== undefined) c++;
        var cell = cells[k];
        var rs = cell.rowSpan || 1, cs = cell.colSpan || 1;
        for (var i = 0; i < rs; i++) {
          if (!grid[r + i]) grid[r + i] = [];
          for (var j = 0; j < cs; j++) {
            grid[r + i][c + j] = { cell: cell, top: r, left: c, rs: rs, cs: cs };
          }
        }
        c += cs;
      }
    }
    return grid;
  }

  function cellLines(cell) {
    var html = (cell.innerHTML || '')
      .replace(/<br\s*\/?>/gi, '\n')
      .replace(/<\/(p|div|li|tr)>/gi, '\n');
    var box = document.createElement('div');
    box.innerHTML = html;
    var raw = (box.textContent || '').replace(/\u00a0/g, ' ');
    return raw.split('\n')
      .map(function (s) { return s.replace(/\s+/g, ' ').trim(); })
      .filter(function (s) { return s.length > 0; });
  }

  /* 表格里的「课程名（课程号）」去掉课程号后缀，避免把编号当名字 */
  function cleanName(s) {
    var v = (s || '').replace(/\s+/g, ' ').trim();
    v = v.replace(/[_＿（(]\s*[A-Za-z0-9]{4,}\s*[)）]\s*$/, '');
    v = v.replace(/\s+[A-Z0-9]{6,}$/, '');
    return v.trim();
  }

  /* ---------------- 课表 ---------------- */

  function bestCourseTable() {
    var best = null, bestScore = -1;
    var list = docs();
    for (var d = 0; d < list.length; d++) {
      var tables = list[d].doc.querySelectorAll('table');
      for (var i = 0; i < tables.length; i++) {
        var tb = tables[i];
        var td = tb.querySelectorAll('td').length;
        if (td < 12) continue;
        var t = tb.textContent || '';
        if (!/(星期|周)[一二三四五六日天]/.test(t)) continue;
        /* 课表表格一定同时出现「节次」和若干周次 */
        var weeks = (t.match(/周/g) || []).length;
        if (weeks < 3) continue;
        var score = td + weeks * 3 - tb.querySelectorAll('table').length * 2;
        if (score > bestScore) { bestScore = score; best = tb; }
      }
    }
    return best;
  }

  function courses() {
    var tb = bestCourseTable();
    if (!tb) return { ok: false, msg: '这一页没有课表网格' };

    var grid = toGrid(tb);
    var headerRow = -1, dayCols = {}, r, c;
    for (r = 0; r < Math.min(grid.length, 8) && headerRow < 0; r++) {
      var map = {}, hits = 0;
      for (c = 0; c < (grid[r] || []).length; c++) {
        var v = grid[r][c] ? txt(grid[r][c].cell) : '';
        var d = dayOf(v);
        if (d) { map[c] = d; hits++; }
      }
      if (hits >= 3) { headerRow = r; dayCols = map; }
    }
    if (headerRow < 0) return { ok: false, msg: '课表没有「星期一…」表头' };

    var records = [], seen = {};
    for (r = headerRow + 1; r < grid.length; r++) {
      var row = grid[r];
      if (!row) continue;
      var label = row[0] ? txt(row[0].cell) : '';
      var sec = parseSections(label);
      var secStart = sec[0], secEnd = sec[1];

      for (var key in dayCols) {
        var ref = row[+key];
        if (!ref || !ref.cell) continue;
        if (ref.cell.__hbuSeen) continue;
        ref.cell.__hbuSeen = 1;

        var lines = cellLines(ref.cell);
        if (!lines.length) continue;
        var name = cleanName(lines[0]);
        if (!name || /^\d+$/.test(name)) continue;

        var teacher = '', room = '', weekRaw = '';
        for (var i = 1; i < lines.length; i++) {
          var ln = lines[i];
          if (!weekRaw && /\d{1,2}\s*[-~至,，]\s*\d{1,2}\s*周|\d{1,2}\s*周/.test(ln)) {
            weekRaw = ln;
            continue;
          }
          if (!room && !/周/.test(ln) && /楼|教室|室|校区|馆|场|机房|号/.test(ln)) {
            room = ln;
            continue;
          }
          if (!room && !/周/.test(ln) && /\d/.test(ln) && ln.length <= 16) {
            room = ln;
            continue;
          }
          if (!teacher && /^[\u4e00-\u9fa5]{2,4}(\s|\*|$)/.test(ln)) {
            teacher = ln.replace(/\*+$/, '');
          }
        }

        var weeks = parseWeeks(lines.join(' '));
        var start = secStart, end = secEnd;
        if (!start) {
          var inCell = parseSections(lines.join(' '));
          start = inCell[0];
          end = inCell[1];
        }
        if (!start) { start = r - headerRow; end = start; }
        if (ref.rs && ref.rs > 1 && end < start + ref.rs - 1) end = start + ref.rs - 1;

        records.push({
          name: name, teacher: teacher, room: room, day: dayCols[+key],
          start: start, end: end, weeks: weeks, raw: weekRaw
        });
        if (records.length > 400) break;
      }
    }
    if (!records.length) return { ok: false, msg: '课表网格是空的' };

    var term = termInfo();
    return { ok: true, kind: 'courses', records: records, term: term, msg: '' };
  }

  /* ---------------- 学期文本 ---------------- */

  function termInfo() {
    var t = allText();
    var m = t.match(/(\d{4})\s*[-—~]\s*(\d{4})\s*学年\s*[第]?\s*([一二12])\s*学期/);
    if (m) return m[1] + '-' + m[2] + '-' + (/[二2]/.test(m[3]) ? '2' : '1');
    m = t.match(/(\d{4}-\d{4})\s*学年\s*(秋|春)/);
    if (m) return m[1] + '-' + (m[2] === '春' ? '2' : '1');
    m = t.match(/(\d{4}-\d{4})\s*[-—]?\s*([12])\s*$/);
    if (m) return m[1] + '-' + m[2];
    return '';
  }

  function semesterText() {
    var t = allText();
    var m = t.match(/\d{4}\s*[-—~]\s*\d{4}\s*学年\s*[第]?\s*[一二12]\s*学期/);
    if (m) return m[0].replace(/\s+/g, ' ');
    m = t.match(/\d{4}-\d{4}\s*学年\s*[秋春]\s*季?/);
    return m ? m[0] : '';
  }

  /* ---------------- 通用表格 ---------------- */

  function tableOf(tb) {
    if (!tb) return null;
    var trs = tb.rows;
    if (!trs.length) return null;

    var headers = [];
    var first = trs[0];
    for (var i = 0; i < first.cells.length; i++) headers.push(txt(first.cells[i]));
    var headText = headers.join(' ');
    var hasHead = /课程|科目|学分|成绩|绩点|考试|时间|地点|周次|名称/.test(headText);

    /* 两行表头（URP 常见）：合并成「课程号」「课程名称」这种单行 */
    var start = 0;
    if (hasHead) {
      start = 1;
      if (trs.length > 1) {
        var second = [];
        for (var k = 0; k < trs[1].cells.length; k++) second.push(txt(trs[1].cells[k]));
        var secondText = second.join(' ');
        if (!/\d{2,}/.test(secondText) && /课程|成绩|学分|学期|属性|性质/.test(secondText)
            && second.length === headers.length) {
          headers = second;
          start = 2;
        }
      }
    }

    var rows = [];
    for (var r = start; r < trs.length && rows.length < 400; r++) {
      var cells = trs[r].cells;
      if (cells.length < 2) continue;
      var row = [];
      var empty = true;
      for (var c = 0; c < cells.length; c++) {
        var v = txt(cells[c]);
        if (v) empty = false;
        row.push(v);
      }
      if (empty) continue;
      /* 合计/备注行不进成绩列表 */
      if (/^(合计|总计|平均|备注)/.test(row.join(''))) continue;
      rows.push(row);
    }
    return { headers: headers, rows: rows };
  }

  function biggestTable(match) {
    var best = null, bestScore = -1, list = docs();
    for (var d = 0; d < list.length; d++) {
      var tables = list[d].doc.querySelectorAll('table');
      for (var i = 0; i < tables.length; i++) {
        var tb = tables[i];
        var td = tb.querySelectorAll('td').length;
        if (td < 6) continue;
        var t = tb.textContent || '';
        if (match && !match.test(t)) continue;
        var score = td;
        if (/课程名称|课程名/.test(t)) score += 20;
        if (score > bestScore) { bestScore = score; best = tb; }
      }
    }
    return best;
  }

  function tabs() {
    var out = [], seen = {};
    var list = docs();
    for (var d = 0; d < list.length; d++) {
      var as = list[d].doc.querySelectorAll('a[href]');
      for (var i = 0; i < as.length; i++) {
        var t = txt(as[i]).replace(/\s+/g, '');
        var h = as[i].href || '';
        if (!t || !h || h.indexOf('javascript:') === 0) continue;
        if (t.length > 16) continue;
        if (!/成绩|学期|学年|属性|方案|及格|学分/.test(t) && !/grade|cj/i.test(h)) continue;
        if (seen[h]) continue;
        seen[h] = 1;
        out.push({ t: t, h: h });
        if (out.length > 30) break;
      }
    }
    return out;
  }

  function grades() {
    var tb = biggestTable(/学分|成绩|绩点/);
    if (!tb) return { ok: false, msg: '这一页没有成绩表' };
    var data = tableOf(tb);
    if (!data || !data.rows.length) return { ok: false, msg: '成绩表是空的' };
    return {
      ok: true, kind: 'grades', headers: data.headers, rows: data.rows,
      term: termInfo(), semesterText: semesterText(), tabs: tabs(), msg: ''
    };
  }

  function exams() {
    var tb = biggestTable(/考试|考场|时间|地点/);
    if (!tb) return { ok: false, msg: '这一页没有考试安排表' };
    var data = tableOf(tb);
    if (!data || !data.rows.length) return { ok: false, msg: '考试安排表是空的' };
    return { ok: true, kind: 'exams', headers: data.headers, rows: data.rows, msg: '' };
  }

  function calendar() {
    var tb = biggestTable(/周次|教学周|日期|月份/);
    if (!tb) return { ok: false, msg: '这一页没有校历' };
    var data = tableOf(tb);
    if (!data || !data.rows.length) return { ok: false, msg: '校历表是空的' };
    return { ok: true, kind: 'calendar', headers: data.headers, rows: data.rows, msg: '' };
  }

  function links() {
    var out = [], seen = {};
    var list = docs();
    for (var d = 0; d < list.length; d++) {
      var as = list[d].doc.querySelectorAll('a[href]');
      for (var i = 0; i < as.length; i++) {
        var t = txt(as[i]).replace(/\s+/g, '');
        var h = as[i].href || '';
        if (!t || !h || h.indexOf('javascript:') === 0 || h === '#') continue;
        if (t.length > 24 || seen[h]) continue;
        seen[h] = 1;
        out.push({ t: t, h: h });
        if (out.length > 120) break;
      }
    }
    return { ok: true, kind: 'links', tabs: out, url: location.href, title: document.title || '' };
  }

  /* ---------------- 入口 ---------------- */

  function loginState() {
    var pwd = 0, ins = document.querySelectorAll('input[type=password]');
    for (var i = 0; i < ins.length; i++) {
      var box = ins[i].getBoundingClientRect();
      if (box.width > 2 && box.height > 2) pwd++;
    }
    return pwd;
  }

  function run() {
    var r;
    try {
      var pwd = loginState();
      if (pwd > 0 || /loginaction|\/login/i.test(location.href)) {
        send({ ok: false, need: 'login', url: location.href, pwd: pwd,
               title: document.title || '' });
        return;
      }
      if (KIND === 'courses') r = courses();
      else if (KIND === 'grades') r = grades();
      else if (KIND === 'exams') r = exams();
      else if (KIND === 'calendar') r = calendar();
      else if (KIND === 'links') r = links();
      else r = { ok: false, msg: '未知的解析类型：' + KIND };
    } catch (e) {
      r = { ok: false, msg: '页面解析出错：' + (e && e.message ? e.message : e) };
    }
    if (!r) r = { ok: false, msg: '页面解析没有结果' };
    r.url = location.href;
    r.title = document.title || '';
    send(r);
  }

  run();
})();
