// 用真实页面/真实响应验证 urp.js 的三个新分支：exams / free / profile
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const RAW = path.join(__dirname, '..', '..', 'app', 'src', 'main', 'res', 'raw', 'urp.js');
// 可选：把真机抓下来的页面放进 HBU_SAMPLES 目录，能额外验证真实 DOM
const SAMPLES = process.env.HBU_SAMPLES || '';
const code = fs.readFileSync(RAW, 'utf8');

let failed = 0;
function check(name, ok, extra) {
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra === undefined ? '' : ' -> ' + JSON.stringify(extra)}`);
  if (!ok) failed++;
}

async function run({ html, url, kind, extra, fetchImpl }) {
  const dom = new JSDOM(html, { url, runScripts: 'outside-only' });
  const w = dom.window;
  const out = { done: false };
  w.HBU = {
    onData: (json) => {
      out.done = true;
      try { out.data = JSON.parse(json); } catch (e) { out.error = 'bad json'; }
    },
  };
  w.__KIND__ = kind;
  w.fetch = fetchImpl || (() => Promise.resolve({ ok: true, text: () => Promise.resolve('') }));
  const src = code.replace('"__KIND__"', JSON.stringify(kind)).replace('__EXTRA__', JSON.stringify(extra || {}));
  w.eval(src);
  for (let i = 0; i < 160 && !out.done; i++) await new Promise((r) => setTimeout(r, 25));
  return out;
}

(async () => {
  // 1) 考试安排：真实页面（两张表，表头都在，数据为空）
  const examPath = SAMPLES ? path.join(SAMPLES, 'q-exam-live.html') : '';
  if (examPath && fs.existsSync(examPath)) {
    const r = await run({
      html: fs.readFileSync(examPath, 'utf8'),
      url: 'https://v.hbu.cn/https/77726476706e69737468656265737421eaff4b8b69386a45300b87/student/examinationManagement/examPlan/index',
      kind: 'exams',
    });
    check('考试页能识别到表', r.data && r.data.ok === true, r.data && r.data.msg);
    check('考试表头含课程名/考试时间/教室',
      r.data && r.data.headers && r.data.headers.join(',').includes('课程名')
      && r.data.headers.join(',').includes('考试时间') && r.data.headers.join(',').includes('教室'),
      r.data && r.data.headers);
    check('没排考时 rows 是空数组而不是报错', r.data && Array.isArray(r.data.rows) && r.data.rows.length === 0,
      r.data && r.data.rows && r.data.rows.length);
  } else {
    console.log('SKIP 考试页样本不存在');
  }

  // 2) 考试安排：造一条真实形状的数据，验证列合并（按日期那张表）
  const examHtml = `<!DOCTYPE html><html><body>
    <table><tr><th>序号</th><th>课程名</th><th>考试日期</th><th>开始时间</th><th>校区</th><th>教学楼</th><th>教室</th></tr>
    <tr><td>1</td><td>自动控制原理</td><td>2026-12-20</td><td>09:00</td><td>七一路校区</td><td>第七教学楼</td><td>302</td></tr></table>
    <table><tr><th>序号</th><th>课程号</th><th>课程名</th><th>考试名称</th><th>考试时间</th><th>校区</th><th>教学楼</th><th>教室</th></tr>
    <tr><td>1</td><td>0901001</td><td>高等数学A(二)</td><td>期末考试</td><td>2026-12-25 14:00-16:00</td><td>五四路校区</td><td>第九教学楼</td><td>105</td></tr></table>
  </body></html>`;
  const r2 = await run({ html: examHtml, url: 'https://v.hbu.cn/https/x/student/examinationManagement/examPlan/index', kind: 'exams' });
  const rows = (r2.data && r2.data.rows) || [];
  check('两张表合并成 2 条', rows.length === 2, rows.length);
  const a = rows[0] || [];
  const b = rows[1] || [];
  check('按日期那张表把日期+时间拼起来', a[2] === '2026-12-20 09:00', a[2]);
  check('教室列带校区与教学楼', (a[5] || '').includes('第七教学楼') && (a[5] || '').includes('302'), a[5]);
  check('按课程那张表保留考试名称与时间段', b[1] === '期末考试' && (b[2] || '').includes('14:00-16:00'), [b[1], b[2]]);

  // 3) 空闲教室：stub fetch，用真实响应结构
  const freeResp = {
    campus: [{ campusName: '五四路校区', campusNumber: '1' }, { campusName: '七一路校区', campusNumber: '2' }],
    rooms: {
      curxqm: '五四路校区',
      spareroomObjList: [
        { acmcBuilding: '7', acmcBuildingName: '第七教学楼',
          claroom: [{ classroom: '316', classNumberOfSeats: '110' }, { classroom: '314', classNumberOfSeats: '58' }] },
      ],
    },
  };
  const seen = [];
  const r3 = await run({
    html: '<html><body>空闲教室</body></html>',
    url: 'https://v.hbu.cn/https/x/student/teachingResources/freeClassroom/index',
    kind: 'free',
    extra: { sections: '1,2', dayplus: 0, campus: '' },
    fetchImpl: (u) => {
      seen.push(String(u));
      const body = String(u).includes('queryCodeCampusList') ? freeResp.campus : freeResp.rooms;
      return Promise.resolve({ ok: true, text: () => Promise.resolve(JSON.stringify(body)) });
    },
  });
  const d3 = (r3.data && r3.data.data) || {};
  check('空闲教室：ok', r3.data && r3.data.ok === true, r3.data && r3.data.msg);
  check('拿到校区列表', (d3.campusList || []).length === 2, (d3.campusList || []).length);
  check('默认选中第一个正常校区', d3.campus === '1' && d3.curxqm === '五四路校区', [d3.campus, d3.curxqm]);
  check('先设查询范围（position）再查节次', seen.some((u) => u.endsWith('/freeClassroom/today'))
    && seen.some((u) => u.indexOf('/today/1,2') >= 0), seen);
  check('拿到教室与座位数', (d3.list || []).length === 1 && d3.list[0].rooms.length === 2
    && d3.list[0].rooms[0].seats === '110', d3.list && d3.list[0]);

  // 4) 个人信息：合成页面 + 头像 fetch
  const profHtml = `<!DOCTYPE html><html><body>
    <div>欢迎您， 张同学</div>
    <table><tr><td>学号</td><td>20240000000</td><td>姓名</td><td>张同学</td></tr>
    <tr><td>院系</td><td>示例学院</td><td>专业</td><td>示例专业</td></tr>
    <tr><td>班级</td><td>24示例2班</td><td>年级</td><td>2024级</td></tr></table>
    <img src="https://zhjw.hbu.cn/student/rollInfo/img" width="97" height="118">
  </body></html>`;
  const fakeBase64 = Buffer.from('x'.repeat(400)).toString('base64');
  const r4 = await run({
    html: profHtml,
    url: 'https://v.hbu.cn/https/x/student/rollManagement/rollInfo/index',
    kind: 'profile',
    fetchImpl: () => Promise.resolve({
      arrayBuffer: () => Promise.resolve(new Uint8Array(Buffer.from('x'.repeat(400))).buffer),
    }),
  });
  const p = (r4.data && r4.data.data) || {};
  check('读到学号', p.no === '20240000000', p.no);
  check('读到姓名', p.name === '张同学', p.name);
  check('读到院系', p.dept === '示例学院', p.dept);
  check('读到专业', p.major === '示例专业', p.major);
  check('读到班级', p.cls === '24示例2班', p.cls);
  check('头像 base64 已取回', (p.avatar || '').length > 100, (p.avatar || '').length);

  // 5) 真实学籍页（从教务抓下来的 HTML）
  const rollPath = SAMPLES ? path.join(SAMPLES, 'q-rollinfo.html') : '';
  if (rollPath && fs.existsSync(rollPath)) {
    const r5 = await run({
      html: fs.readFileSync(rollPath, 'utf8'),
      url: 'https://v.hbu.cn/https/77726476706e69737468656265737421eaff4b8b69386a45300b87/student/rollManagement/rollInfo/index',
      kind: 'profile',
      fetchImpl: () => Promise.resolve({
        arrayBuffer: () => Promise.resolve(new Uint8Array(Buffer.alloc(500, 1)).buffer),
      }),
    });
    const p5 = (r5.data && r5.data.data) || {};
    check('真实学籍页：学号', /^\d{8,}$/.test(p5.no || ''), p5.no);
    check('真实学籍页：姓名', !!p5.name && p5.name.length < 20, p5.name);
    check('真实学籍页：院系', !!p5.dept && p5.dept.length < 40, p5.dept);
    check('真实学籍页：专业', !!p5.major && p5.major.length < 40, p5.major);
    check('真实学籍页：班级', !!p5.cls && p5.cls.length < 30, p5.cls);
    check('真实学籍页：拿到头像', (p5.avatar || '').length > 100, (p5.avatar || '').length);
  } else {
    console.log('SKIP 学籍页样本不存在');
  }

  console.log(failed === 0 ? '\n全部通过' : `\n${failed} 项失败`);
  process.exit(failed === 0 ? 0 : 1);
})();
