// Minimal stand-in for the OnePage firmware HTTP API, for building the app
// without hardware. Mirrors CrossPointWebServer.cpp 1:1 (JSON shapes matter).
//
//   node server.mjs [--port 8080]
//
// Then in the app: 手动输入 IP → 10.0.2.2 : 8080  (Android emulator → host)

import http from 'node:http'
import dgram from 'node:dgram'

const PORT = Number(process.argv.includes('--port') ? process.argv[process.argv.indexOf('--port') + 1] : 8080)
const WS_PORT = 81

// ---------------------------------------------------------------- fake state

const state = {
  settings: [
    { key: 'sleepScreen', name: '休眠画面', category: '显示', type: 'enum', value: 3,
      options: ['深色', '浅色', '自定义', '封面', '无', '自定义封面', '快速恢复'] },
    { key: 'sleepScreenCoverMode', name: '封面模式', category: '显示', type: 'enum', value: 0, options: ['适配', '裁剪'] },
    { key: 'hideBatteryPercentage', name: '隐藏电量', category: '显示', type: 'enum', value: 0, options: ['从不', '阅读时', '总是'] },
    { key: 'refreshFrequency', name: '刷新频率', category: '显示', type: 'enum', value: 2, options: ['1 页', '5 页', '10 页', '15 页', '30 页'] },
    { key: 'uiTheme', name: '界面主题', category: '显示', type: 'enum', value: 1, options: ['经典', 'Lyra', 'Lyra 扩展', 'Rounded'] },
    { key: 'fadingFix', name: '残影修复', category: '显示', type: 'toggle', value: 0 },
    { key: 'fontFamily', name: '字体', category: '阅读', type: 'enum', value: 0, options: ['Noto Serif', 'Noto Sans'] },
    { key: 'fontSize', name: '字号', category: '阅读', type: 'enum', value: 2, options: ['小', '中', '大', '特大'] },
    { key: 'lineSpacing', name: '行距', category: '阅读', type: 'enum', value: 1, options: ['紧凑', '标准', '宽松'] },
    { key: 'screenMargin', name: '页边距', category: '阅读', type: 'value', value: 15, min: 5, max: 40, step: 5 },
    { key: 'paragraphAlignment', name: '段落对齐', category: '阅读', type: 'enum', value: 0, options: ['两端对齐', '左对齐', '居中', '右对齐', '跟随书籍样式'] },
    { key: 'embeddedStyle', name: '使用内嵌样式', category: '阅读', type: 'toggle', value: 1 },
    { key: 'hyphenationEnabled', name: '连字符断词', category: '阅读', type: 'toggle', value: 0 },
    { key: 'orientation', name: '方向', category: '阅读', type: 'enum', value: 0, options: ['竖屏', '横屏 CW', '竖屏倒置', '横屏 CCW'] },
    { key: 'extraParagraphSpacing', name: '段间距', category: '阅读', type: 'toggle', value: 1 },
    { key: 'textAntiAliasing', name: '文字抗锯齿', category: '阅读', type: 'toggle', value: 1 },
    { key: 'imageRendering', name: '图片', category: '阅读', type: 'enum', value: 0, options: ['显示', '占位符', '忽略'] },
    { key: 'sideButtonLayout', name: '侧键布局', category: '控制', type: 'enum', value: 0, options: ['上一页/下一页', '下一页/上一页', '禁用'] },
    { key: 'frontButtonFollowOrientation', name: '按键跟随方向', category: '控制', type: 'toggle', value: 1 },
    { key: 'longPressButtonBehavior', name: '长按行为', category: '控制', type: 'enum', value: 1, options: ['关闭', '跳页', '切换方向'] },
    { key: 'shortPwrBtn', name: '短按电源键', category: '控制', type: 'enum', value: 1, options: ['忽略', '休眠', '翻页', '强制刷新', '脚注'] },
    { key: 'sleepTimeoutMinutes', name: '休眠时间', category: '系统', type: 'value', value: 10, min: 1, max: 60, step: 1 },
    { key: 'showHiddenFiles', name: '显示隐藏文件', category: '系统', type: 'toggle', value: 0 },
    { key: 'removeReadBooksFromRecents', name: '自动移除已读', category: '系统', type: 'toggle', value: 0 },
    { key: 'koreaderUsername', name: 'KOReader 用户名', category: '系统', type: 'string', value: '' },
  ],
  files: {
    '/': [
      { name: 'Books', size: 0, isDirectory: true, isEpub: false },
      { name: 'fonts', size: 0, isDirectory: true, isEpub: false },
      { name: 'readme.txt', size: 1288, isDirectory: false, isEpub: false },
    ],
    '/Books': [
      { name: '三体.epub', size: 4_812_344, isDirectory: false, isEpub: true },
      { name: 'The Little Prince.epub', size: 1_204_112, isDirectory: false, isEpub: true },
      { name: '夜航船.txt', size: 892_331, isDirectory: false, isEpub: false },
    ],
    '/fonts': [],
  },
  fonts: {
    maxFamilies: 8,
    families: [
      { name: 'Noto Serif SC', sizes: [12, 14, 16, 18], files: [
        { name: 'NotoSerifSC_12.cpfont', size: 220_112 },
        { name: 'NotoSerifSC_14.cpfont', size: 268_400 },
        { name: 'NotoSerifSC_16.cpfont', size: 310_233 },
        { name: 'NotoSerifSC_18.cpfont', size: 361_002 },
      ] },
    ],
  },
  opds: [
    { index: 0, name: 'Calibre', url: 'http://192.168.1.10:8080/opds', username: 'reader', hasPassword: true },
  ],
  wifi: [
    { index: 0, ssid: 'Home-2.4G', hasPassword: true, isLastConnected: true },
    { index: 1, ssid: 'Studio', hasPassword: true, isLastConnected: false },
  ],
  uploads: new Map(),
}

// ------------------------------------------------------------------- helpers

const json = (res, code, body) => {
  const text = JSON.stringify(body)
  res.writeHead(code, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(text) })
  res.end(text)
}

const text = (res, code, body) => {
  res.writeHead(code, { 'Content-Type': 'text/plain; charset=utf-8' })
  res.end(String(body))
}

const readBody = (req) => new Promise((resolve) => {
  const chunks = []
  req.on('data', (c) => chunks.push(c))
  req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')))
})

const readRaw = (req) => new Promise((resolve) => {
  const chunks = []
  req.on('data', (c) => chunks.push(c))
  req.on('end', () => resolve(Buffer.concat(chunks)))
})

// -------------------------------------------------------------------- routes

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PORT}`)
  const path = url.pathname
  console.log(`${req.method} ${req.url}`)

  if (path === '/api/status') {
    return json(res, 200, {
      version: 'onepage-0.1.1-mock', ip: '10.0.2.15', mode: 'STA',
      rssi: -48, freeHeap: 132_456, uptime: 3725, device: 'X4',
    })
  }

  if (path === '/api/settings' && req.method === 'GET') {
    return json(res, 200, state.settings)
  }

  if (path === '/api/settings' && req.method === 'POST') {
    const body = JSON.parse(await readBody(req) || '{}')
    let applied = 0
    for (const [k, v] of Object.entries(body)) {
      const item = state.settings.find((s) => s.key === k)
      if (!item) continue
      item.value = v
      applied++
    }
    return text(res, 200, `Applied ${applied} setting(s)`)
  }

  if (path === '/api/files') {
    const dir = url.searchParams.get('path') || '/'
    return json(res, 200, state.files[dir] ?? [])
  }

  if (path === '/download') {
    const name = (url.searchParams.get('path') || '/x').split('/').pop()
    res.writeHead(200, { 'Content-Type': 'application/octet-stream', 'Content-Length': 1024 * 64 })
    res.end(Buffer.alloc(1024 * 64, 7))
    return
  }

  if (path === '/upload' && req.method === 'POST') {
    const dir = url.searchParams.get('path') || '/'
    const body = await readRaw(req)
    state.files[dir] = state.files[dir] || []
    state.files[dir].push({ name: `upload-${Date.now()}.bin`, size: body.length, isDirectory: false, isEpub: false })
    return text(res, 200, 'OK')
  }

  if (path === '/mkdir' || path === '/rename' || path === '/move' || path === '/delete') {
    // The firmware reads these through ESP32 WebServer::arg(), which only sees
    // the query string (or a urlencoded body) — a text/plain body is invisible.
    // Mirror that contract here so a client regression fails against the mock
    // instead of only on real hardware.
    const q = Object.fromEntries(url.searchParams.entries())
    if (!q.path) return text(res, 400, 'Missing `path` or `paths` argument')
    if ((path === '/mkdir' || path === '/rename') && !q.name) {
      return text(res, 400, 'Missing `name` argument')
    }
    if (path === '/move' && !q.dest) return text(res, 400, 'Missing `dest` argument')
    if (path === '/mkdir') {
      const dir = q.path || '/'
      state.files[dir] = state.files[dir] || []
      state.files[dir].push({ name: q.name, size: 0, isDirectory: true, isEpub: false })
    }
    if (path === '/delete') {
      const dir = (q.path || '/').split('/').slice(0, -1).join('/') || '/'
      const name = (q.path || '').split('/').pop()
      state.files[dir] = (state.files[dir] || []).filter((f) => f.name !== name)
    }
    return text(res, 200, 'OK')
  }

  if (path === '/api/fonts') return json(res, 200, state.fonts)

  if (path === '/api/fonts/delete' && req.method === 'POST') {
    const body = JSON.parse(await readBody(req) || '{}')
    state.fonts.families = state.fonts.families.filter((f) => f.name !== body.family)
    return json(res, 200, { ok: true })
  }

  if (path === '/api/opds' && req.method === 'GET') return json(res, 200, state.opds)
  if (path === '/api/opds' && req.method === 'POST') {
    const body = JSON.parse(await readBody(req) || '{}')
    state.opds.push({ index: state.opds.length, name: body.name, url: body.url, username: body.username || '', hasPassword: !!body.password })
    return text(res, 200, 'OK')
  }
  if (path === '/api/opds/delete') {
    const body = JSON.parse(await readBody(req) || '{}')
    state.opds = state.opds.filter((s) => s.index !== body.index)
    return text(res, 200, 'OK')
  }

  if (path === '/api/wifi' && req.method === 'GET') return json(res, 200, state.wifi)
  if (path === '/api/wifi' && req.method === 'POST') {
    const body = JSON.parse(await readBody(req) || '{}')
    state.wifi.push({ index: state.wifi.length, ssid: body.ssid, hasPassword: !!body.password, isLastConnected: false })
    return text(res, 200, 'OK')
  }
  if (path === '/api/wifi/delete') {
    const body = JSON.parse(await readBody(req) || '{}')
    state.wifi = state.wifi.filter((s) => s.index !== body.index)
    return text(res, 200, 'OK')
  }

  return text(res, 404, 'Not found')
})

server.listen(PORT, '0.0.0.0', () => {
  console.log(`mock OnePage on http://0.0.0.0:${PORT}`)
  console.log(`emulator: 手动输入 10.0.2.2 : ${PORT}`)
  console.log(`lan:      http://<this-pc-ip>:${PORT}`)
})

// ------------------------------------------------- UDP discovery (port 8134)

const udp = dgram.createSocket({ type: 'udp4', reuseAddr: true })
udp.on('message', (msg, rinfo) => {
  if (msg.toString().trim() !== 'hello') return
  const reply = Buffer.from(`crosspoint (on mock-device);${WS_PORT}`)
  udp.send(reply, rinfo.port, rinfo.address)
})
udp.bind(8134, () => console.log('UDP discovery listening on 8134'))
