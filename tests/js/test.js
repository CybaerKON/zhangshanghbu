/**
 * 页面解析脚本的离线测试：用 jsdom 造 URP 风格的课表页 / 成绩页，
 * 跑 app 里真正的 res/raw/page.js，断言解析出来的具体字段。
 *
 * 重点覆盖上一版踩过的坑：
 *   · 成绩表头「课程号 | 课程名称 …」时，不能把课程号当课名；
 *   · 课表 rowspan 合并的格子不能重复计入；
 *   · 单双周、连堂课（3-4 节）要还原成起止节次；
 *   · 登录页（有可见密码框）要返回 need=login。
 */
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const RAW = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'raw', 'page.js');

async function run(html, kind, opts = {}) {
  const dom = new JSDOM(html, {
    url: opts.url || 'https://v.hbu.cn/http/7772abcd/index.jsp',
    runScripts: 'outside-only',
  });
  const w = dom.window;
  const out = { data: null };
  w.HBU = { onData: (j) => { out.data = JSON.parse(j); } };

  // jsdom 没有布局，给密码框造一个可见尺寸，才能测「登录页」分支
  w.Element.prototype.getBoundingClientRect = function () {
    const type = this.getAttribute && this.getAttribute('type');
    const size = type === 'password' ? 120 : 0;
    return { width: size, height: size ? 30 : 0, top: 0, left: 0, right: size, bottom: 30 };
  };

  const code = fs.readFileSync(RAW, 'utf8').replace('"__KIND__"', JSON.stringify(kind));
  w.eval(code);
  for (let i = 0; i < 20 && !out.data; i++) await new Promise((r) => setTimeout(r, 5));
  return out.data;
}

let failed = 0;
function check(name, ok, detail) {
  console.log((ok ? '  ✓ ' : '  ✗ ') + name + (ok ? '' : ' — ' + detail));
  if (!ok) failed++;
}

async function main() {
  /* ---------- 1. 课表 ---------- */
  console.log('1. URP 课表网格');
  const courseHtml = `<!DOCTYPE html><html><head><title>学生课表</title></head><body>
  <div>2026-2027学年 第一学期</div>
  <table id="kbtable">
    <tr><th>节次</th><th>星期一</th><th>星期二</th><th>星期三</th><th>星期四</th><th>星期五</th><th>星期六</th><th>星期日</th></tr>
    <tr><td>第1-2节</td>
        <td>高等数学A(二)<br>王建国<br>1-16周<br>七教302</td>
        <td>数据结构<br>刘鹏<br>1-16周<br>计算机楼401</td>
        <td></td><td>操作系统<br>周颖<br>1-16周<br>计算机楼402</td>
        <td></td><td></td><td></td></tr>
    <tr><td>第3-4节</td>
        <td>大学物理<br>李红<br>1-8周<br>九教105</td>
        <td></td>
        <td>马克思主义基本原理<br>陈晓东<br>3-16周<br>文科楼101</td>
        <td></td>
        <td>软件工程<br>刘鹏<br>2-16周(双)<br>计算机楼401</td>
        <td rowspan="2">物理实验<br>李红<br>1-16周<br>实验楼201</td>
        <td></td></tr>
    <tr><td>第5-6节</td>
        <td></td><td></td><td></td><td></td><td></td><td></td></tr>
  </table></body></html>`;
  {
    const d = await run(courseHtml, 'courses');
    check('ok', d && d.ok === true, JSON.stringify(d && d.ok));
    const recs = (d && d.records) || [];
    check('抽出 7 条时段', recs.length === 7, recs.length);
    const lab = recs.find((r) => r.name.includes('物理实验'));
    check('rowspan 连堂课 3-4 节', lab && lab.start === 3 && lab.end === 4,
      lab && `${lab.start}-${lab.end}`);
    const math = recs.find((r) => r.name.includes('高等数学'));
    check('课程名', math && math.name === '高等数学A(二)', math && math.name);
    check('教师', math && math.teacher === '王建国', math && math.teacher);
    check('教室', math && math.room === '七教302', math && math.room);
    check('星期', math && math.day === 1, math && math.day);
    check('节次 1-2', math && math.start === 1 && math.end === 2, math && `${math.start}-${math.end}`);
    check('周次 16 周', math && math.weeks.length === 16, math && math.weeks.length);
    const se = recs.find((r) => r.name.includes('软件工程'));
    check('双周只留偶数周', se && se.weeks.every((w) => w % 2 === 0), se && se.weeks.join(','));
    check('学期识别 2026-2027-1', d && d.term === '2026-2027-1', d && d.term);
  }

  /* ---------- 2. 成绩 ---------- */
  console.log('2. URP 成绩表');
  const gradeHtml = `<!DOCTYPE html><html><head><title>成绩查询</title></head><body>
  <div>2026-2027学年 第一学期</div>
  <a href="/gradeLnAllAction.do?type=ln&oper=qbinfo">全部及格成绩</a>
  <a href="/bxqcjcxAction.do">本学期成绩</a>
  <table id="user">
    <tr><th>课程号</th><th>课程名称</th><th>课程属性</th><th>学分</th><th>成绩</th><th>绩点</th></tr>
    <tr><td>B1234567</td><td>高等数学A(二)</td><td>学科基础课</td><td>5.0</td><td>92</td><td>4.2</td></tr>
    <tr><td>B7654321</td><td>数据结构</td><td>专业必修</td><td>3.5</td><td>85</td><td>3.5</td></tr>
  </table></body></html>`;
  {
    const d = await run(gradeHtml, 'grades');
    check('ok', d && d.ok === true, JSON.stringify(d));
    check('表头 6 列', d && d.headers.length === 6, d && JSON.stringify(d.headers));
    check('数据 2 行', d && d.rows.length === 2, d && d.rows.length);
    check('第一行课程号在第 0 列', d && d.rows[0][0] === 'B1234567', d && d.rows[0][0]);
    check('第一行课程名在第 1 列', d && d.rows[0][1] === '高等数学A(二)', d && d.rows[0][1]);
    check('带回成绩页签', d && d.tabs && d.tabs.length >= 2, d && (d.tabs || []).length);
    check('学期文本', d && d.semesterText.includes('2026-2027'), d && d.semesterText);
  }

  /* ---------- 3. 登录页 ---------- */
  console.log('3. 登录页判定');
  {
    const d = await run('<html><body><form><input name="user"><input type="password"></form></body></html>',
      'grades', { url: 'https://v.hbu.cn/login' });
    check('返回 need=login', d && (d.need === 'login' || d.needLogin === true), JSON.stringify(d));
  }

  /* ---------- 4. 空白页不崩 ---------- */
  console.log('4. 空页面');
  {
    const d = await run('<html><body><p>没有表格</p></body></html>', 'grades');
    check('ok=false 且带原因', d && d.ok === false && !!d.msg, JSON.stringify(d));
  }

  console.log(failed === 0 ? '\n全部通过' : `\n${failed} 项失败`);
  process.exit(failed === 0 ? 0 : 1);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
