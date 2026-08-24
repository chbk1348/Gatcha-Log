#!/usr/bin/env node
/**
 * 예약된 버전 특별 방송 → broadcasts.json
 *
 * ## 왜 앱이 아니라 여기서 도는가
 *
 * YouTube Data API 로 예정 라이브를 조회하려면 키가 필요한데, 이 앱은 사이드로드라 키를 숨길
 * 데가 없다. 더 큰 문제는 할당량이다 — search.list 는 호출당 100 units 이고 하루 한도가
 * 10,000 units 인데, 이건 **사용자별이 아니라 프로젝트 하나의 통장**이다. 앱이 직접 부르면
 * 사용자 몇 명만 새로고침해도 그날 몫이 사라진다.
 *
 * 그래서 6시간마다 Actions 가 대신 조회해 JSON 으로 떠 두고, 앱은 그 JSON 만 읽는다
 * (zzz_banners.json·version.json 과 같은 방식). 키는 GitHub Secret 에만 있고,
 * 쿼터는 한 번에 약 301 units × 하루 4회 = 1,204 units — 한도의 12% 다.
 *
 * ## 실패해도 앱은 멀쩡하다
 *
 * 이 스크립트가 못 돌거나 조회에 실패하면 JSON 이 그대로 남고, 앱은
 * BroadcastSchedule 의 역산("버전 시작 12일 전 금요일")으로 조용히 되돌아간다.
 * 그래서 한 게임이 실패해도 나머지는 계속 갱신하고, 실패한 게임은 **직전 값을 물려준다**.
 */

import { readFileSync, writeFileSync } from 'node:fs'

const OUT = 'broadcasts.json'
const API = 'https://www.googleapis.com/youtube/v3'
const KEY = process.env.YOUTUBE_API_KEY

/**
 * 게임별 공식 한국 채널.
 *
 * ⚠️ BroadcastSchedule.kt 의 byGame 과 같은 값이다. 채널을 바꾸면 양쪽을 같이 고쳐야 한다
 * (앱은 영상 주소를 못 얻었을 때 채널 /streams 로 폴백하므로 여전히 채널 ID 가 필요하다).
 * 핸들이 아니라 채널 ID 로 거는 이유도 같다 — 핸들은 바뀌지만 ID 는 안 바뀐다.
 */
const GAMES = [
  { key: 'genshin', name: '원신', channelId: 'UCcum1rCJ5GJeQ_xv0xrohqg' },
  { key: 'hsr', name: '스타레일', channelId: 'UCH33CJMcI0XZUpIhWRHiUuw' },
  { key: 'zzz', name: '젠레스', channelId: 'UCmry1hfaRHI_iTfxUMhC8mA' },
]

/**
 * 제목이 '버전 특별 방송'임을 알리는 말. BroadcastSchedule.BROADCAST_WORDS 와 맞춰 둔다.
 *
 * 버전 번호만으로 거르지 않는 이유: 공식 채널은 「4.4 버전 프리뷰 토론실」처럼 버전 번호가
 * 붙은 부대 생방송도 올린다. 그걸 특별 방송이라고 내놓으면 확정 배지까지 달려서, 예상값보다
 * 오히려 나쁘다. 놓치는 쪽은 역산이 받아주지만 틀리는 쪽은 받아줄 데가 없다.
 */
const BROADCAST_WORDS = [
  '스페셜 프로그램', '특별 방송', '특별방송', '특별 생방송', '프리뷰 방송', '생방송', '라이브 스트리밍',
]

const VERSION_RE = /(\d+\.\d+)/

const log = (...a) => console.log(...a)

async function getJson(path) {
  const res = await fetch(`${API}/${path}&key=${KEY}`)
  const body = await res.json().catch(() => null)
  if (!res.ok) {
    const reason = body?.error?.errors?.[0]?.reason ?? res.statusText
    throw new Error(`HTTP ${res.status} (${reason})`)
  }
  return body
}

/** 채널의 예정 라이브 videoId 목록. 100 units. */
async function upcomingIds(channelId) {
  const r = await getJson(
    `search?part=snippet&type=video&eventType=upcoming&maxResults=10&channelId=${channelId}`,
  )
  return (r.items ?? []).map((i) => i.id?.videoId).filter(Boolean)
}

/** videoId → { title, scheduledStartTime }. 여러 게임 몫을 한 번에 묶어 1 unit 만 쓴다. */
async function details(ids) {
  if (ids.length === 0) return new Map()
  const r = await getJson(`videos?part=snippet,liveStreamingDetails&id=${ids.join(',')}`)
  return new Map(
    (r.items ?? []).map((i) => [
      i.id,
      { title: i.snippet?.title ?? '', at: i.liveStreamingDetails?.scheduledStartTime ?? '' },
    ]),
  )
}

/** KST 'yyyy-MM-dd HH:mm' — 커밋 diff 를 사람이 읽을 수 있게 같이 적어 둔다. */
const kst = (ms) =>
  new Date(ms)
    .toLocaleString('sv-SE', { timeZone: 'Asia/Seoul' })
    .slice(0, 16)

async function main() {
  if (!KEY) {
    // 키가 없으면 JSON 을 건드리지 않는다 — 비워 버리면 확정이 예상으로 후퇴한다.
    log('YOUTUBE_API_KEY 없음 — JSON 을 그대로 둔다')
    return
  }

  const prev = JSON.parse(readFileSync(OUT, 'utf8'))
  const now = Date.now()
  /** 게임별 실패 여부. 실패한 게임만 직전 값을 물려준다. */
  const failed = new Set()
  const idsByGame = new Map()

  for (const g of GAMES) {
    try {
      const ids = await upcomingIds(g.channelId)
      idsByGame.set(g.key, ids)
      log(`${g.name}: 예정 라이브 ${ids.length}건`)
    } catch (e) {
      failed.add(g.key)
      log(`${g.name}: 조회 실패 — ${e.message} (직전 값 유지)`)
    }
  }

  let meta = new Map()
  const allIds = [...idsByGame.values()].flat()
  try {
    meta = await details(allIds)
  } catch (e) {
    // 상세를 통째로 못 받으면 시각을 알 수 없다 — 전부 직전 값으로 둔다.
    log(`상세 조회 실패 — ${e.message} (전부 직전 값 유지)`)
    return
  }

  const broadcasts = []
  for (const g of GAMES) {
    if (failed.has(g.key)) {
      const old = (prev.broadcasts ?? []).find((b) => b.game === g.key)
      // 이미 지난 방송은 물려주지 않는다 — '다음 방송'이 아니다.
      if (old && old.startMillis > now) broadcasts.push(old)
      continue
    }

    const picks = []
    for (const id of idsByGame.get(g.key) ?? []) {
      const m = meta.get(id)
      if (!m || !m.at) continue
      const ms = Date.parse(m.at)
      if (!Number.isFinite(ms) || ms <= now) continue

      const version = VERSION_RE.exec(m.title)?.[1]
      const isBroadcast = BROADCAST_WORDS.some((w) => m.title.includes(w))
      if (!version || !isBroadcast) {
        // 놓친 방송을 발견하면 여기 로그를 보고 BROADCAST_WORDS 를 늘리면 된다
        // (양쪽 — 이 파일과 BroadcastSchedule.kt).
        log(`  건너뜀: "${m.title}" (버전=${version ?? '-'}, 방송어=${isBroadcast})`)
        continue
      }
      picks.push({ game: g.key, version, startMillis: ms, startAtKst: kst(ms), videoId: id, title: m.title })
    }

    // 게임당 한 건 — 가장 이른 회차.
    picks.sort((a, b) => a.startMillis - b.startMillis)
    if (picks[0]) {
      const p = picks[0]
      log(`${g.name}: v${p.version} ${p.startAtKst} 확정 (${p.videoId})`)
      broadcasts.push(p)
    } else {
      log(`${g.name}: 확정 없음 — 앱은 역산으로 표시한다`)
    }
  }

  broadcasts.sort((a, b) => a.startMillis - b.startMillis)

  // 내용이 같으면 커밋하지 않는다 — 6시간마다 updatedAt 만 바뀌는 빈 커밋을 막는다.
  if (JSON.stringify(prev.broadcasts ?? []) === JSON.stringify(broadcasts)) {
    log('변경 없음')
    return
  }
  const next = { ...prev, updatedAt: new Date(now).toISOString(), broadcasts }
  writeFileSync(OUT, `${JSON.stringify(next, null, 2)}\n`)
  log(`갱신: ${broadcasts.length}건`)
}

await main()
