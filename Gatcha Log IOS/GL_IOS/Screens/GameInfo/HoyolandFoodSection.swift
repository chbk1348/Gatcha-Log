import SwiftUI
import Shared

// ── 호요랜드 푸드존 ─────────────────────────────────────────────────────────
// 값은 프로그램 목록에 있던 것 그대로다(HoyolandEvent.foodPrograms 가 제목으로 갈라낸다).
// 어드민에서 이미 관리되고 있어 config 스키마도 입력 화면도 건드리지 않는다.
// Android 대응 = HoyolandSection.kt 의 HoyolandFoodContent.

private let GLGFoodRowBg = Color(hex: 0xFFF7F8FA)
private let GLGFoodTextThird = Color(hex: 0xFF98A0AB)

/**
 메뉴판 한 덩이 — 파는 것들의 목록이거나, 그 앞뒤의 문장(카운터 이름·세트 안내)이다.

 원격 config 의 설명글은 줄 단위로 규칙이 있다. `Text` 하나에 통째로 넣으면
 "· 행운의 황금 레몬 만두 — 7,000원" 이 본문과 같은 무게로 깔려 **가격이 글 속에 묻힌다.**
 값은 그대로 두고 표시만 나눈다(옛 빌드에서도 글자는 그대로 나온다).
 */
enum HoyolandFoodBlock {
    case menu([HoyolandFoodRow])
    case para(String)
}

struct HoyolandFoodRow {
    let name: String
    let price: String
    var sub: String
}

/**
 메뉴 설명글을 **파는 줄 / 읽는 줄**로 가른다.

 - 들여쓴 줄 → 바로 위 메뉴의 부연(`·` 가 붙어 있어도 부연이다)
 - `· 이름 — 7,000원` → 메뉴 줄. 이어지는 구간이 하나의 목록으로 묶인다
 - 그 외 → 문장. 여기서 목록이 끊기는 건 의도다(그 문장은 앞 목록에만 걸린다)
 */
func hoyolandFoodBlocks(_ desc: String) -> [HoyolandFoodBlock] {
    var blocks: [HoyolandFoodBlock] = []
    var buffer: [HoyolandFoodRow] = []
    func flush() {
        if !buffer.isEmpty {
            blocks.append(.menu(buffer))
            buffer.removeAll()
        }
    }
    for raw in desc.components(separatedBy: "\n") {
        let indented = !raw.trimmingCharacters(in: .whitespaces).isEmpty
            && (raw.hasPrefix("  ") || raw.hasPrefix("\t"))
        let line = raw.trimmingCharacters(in: .whitespaces)
        if line.isEmpty { continue }
        if indented, !buffer.isEmpty {
            var last = buffer.removeLast()
            let sub = line.hasPrefix("· ") ? String(line.dropFirst(2)) : line
            last.sub = last.sub.isEmpty ? sub : "\(last.sub) \(sub)"
            buffer.append(last)
        } else if line.hasPrefix("· ") {
            let item = String(line.dropFirst(2))
            if let r = item.range(of: " — ", options: .backwards) {
                buffer.append(HoyolandFoodRow(name: String(item[..<r.lowerBound]),
                                              price: String(item[r.upperBound...]),
                                              sub: ""))
            } else {
                buffer.append(HoyolandFoodRow(name: item, price: "", sub: ""))
            }
        } else {
            flush()
            blocks.append(.para(line))
        }
    }
    flush()
    return blocks
}

/**
 푸드존 — 게임별 메뉴판.

 게임 탭은 달지 않는다 — 굿즈(100종)·부스(24곳)와 달리 카드가 게임당 하나라 목록 전체가
 세 장이다. 거를 것이 없는 자리에 탭을 세우면 화면 위 한 줄을 늘 먹는다.
 */
/// 크게 보기 중인 메뉴 사진.
private struct HoyolandFoodPhoto: Identifiable {
    let name: String
    let price: String
    let url: String
    let game: String
    var id: String { url }
}

struct HoyolandFoodView: View {
    let event: HoyolandEvent
    @State private var viewingFood: HoyolandFoodPhoto? = nil
    /// 넓은 창(iPad) 두 열 — [hoyolandWide] 가 채운다.
    @State private var wide = false

    var body: some View {
        let list = event.foodPrograms
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if list.isEmpty {
                    // 빈 상태도 카드 없이 글만(10/1).
                        VStack(alignment: .leading, spacing: 5) {
                            Text("메뉴는 아직 공개 전이에요")
                                .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Text("게임별 푸드존·푸드트럭 메뉴가 나오면 이 자리에 채워져요.")
                                .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .hoyolandSection()
                } else {
                    if wide {
                        // 넓은 창은 **벽돌쌓기 두 열** — 메뉴 수가 게임마다 달라 카드 높이가 크게
                        // 벌어진다. 행으로 맞추면 짧은 카드 아래가 통째로 빈다.
                        // 카드를 걷었다(10/1) — 열 안에서 둘째 칸부터 위에 헤어라인으로 가른다.
                        // 처음 두 장은 열마다 첫 칸이다(짧은 열부터 채우므로 0 → 왼쪽, 1 → 오른쪽).
                        GLGColumnMasonry(cards: list.enumerated().map { i, p in
                            GLGMasonryCard(id: i, weight: 120 + Double(p.desc.count)) {
                                VStack(spacing: 0) {
                                    if i >= 2 { HoyolandHairline().padding(.bottom, 22) }
                                    foodCard(p)
                                }
                            }
                        }, spacing: 20)
                        .padding(.top, 22)
                        foodNudge.padding(.top, 14).padding(.horizontal, 2)
                    } else {
                        // 게임마다 감싸던 카드를 걷고 **한 게임 = 한 섹션**, 사이는 띠로 가른다(10/1).
                        ForEach(Array(list.enumerated()), id: \.offset) { i, p in
                            if i > 0 { GiBand() }
                            VStack(alignment: .leading, spacing: 0) {
                                foodCard(p)
                                if i == list.count - 1 { foodNudge.padding(.top, 14) }
                            }
                            // 메뉴 줄(위아래 12)로 끝나면 아래 8 — 띠까지 눈에 20(10/1). 안내 문장 · 마감 배지로 끝나거나 맨 아래면 20.
                            .hoyolandSection(bottom: i < list.count - 1 && endsWithMenu(p) ? 8 : 20)
                        }
                    }
                }
                // 아래 여분 없음 — 마지막 섹션이 아래 20 을 둔다(GLDS 2.0, 10/1).
            }
            // 한 열은 좌우 여백 없이 화면 폭(섹션이 스스로 20) — 두 열(iPad)만 24(10/1).
            .padding(.horizontal, wide ? 24 : 0)
            .glgReadableWidth(wide ? HoyolandWideMaxWidth : 640)
        }
        .hoyolandWide($wide)
        .scrollIndicators(.hidden)
        // 흰 바탕(10/1).
        .background(Color.white)
        .glgPageTitle("푸드존")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $viewingFood) { f in
            let raw = event.stageColor(game: f.game)
            HoyolandPhotoSheet(label: f.game.isEmpty ? "" : event.stageLabel(game: f.game),
                               color: raw == 0 ? GLGColor.textSecondary : Color(argb64: raw),
                               title: f.name, price: f.price, url: URL(string: f.url))
        }
    }

    /**
     푸드존 한 칸 — 게임 배지 + 유형(푸드존/푸드트럭) + 메뉴 수, 그리고 메뉴판.

     제목("푸드트럭 — 붕괴: 스타레일")을 통째로 쓰지 않는다. 게임은 이미 배지로 서 있어
     같은 말이 두 번 나오고, 남는 폭이 그만큼 줄어든다 — 앞쪽 유형만 제목으로 쓴다.
     */
    // 넛지 — 이 화면의 숫자는 **공지 기준**이라는 것만 분명히 한다. 현장 메뉴판과
    // 다를 때 "앱이 틀렸다"가 아니라 "바뀌었구나"로 읽히게 하는 한 줄이다.
    private var foodNudge: some View {
        Text("가격·구성은 공식 공지 기준이에요. 현장 사정으로 바뀔 수 있어요.")
            .font(.pretendard(size: 12)).foregroundStyle(GLGFoodTextThird)   // 11 → 12(10/1)
            .fixedSize(horizontal: false, vertical: true)
    }

    /// 마지막이 메뉴 줄인가 — 섹션 아래 여백을 그만큼 덜어 낸다(Android HoyolandFoodContent 와 같은 판정).
    private func endsWithMenu(_ p: HoyolandProgram) -> Bool {
        guard p.deadline.isEmpty, case .menu = hoyolandFoodBlocks(p.desc).last else { return false }
        return true
    }

    /// 감싸던 카드를 걷었다(10/1) — 한 열은 섹션(hoyolandSection)이 감싸고, 두 열은 헤어라인으로 가른다.
    @ViewBuilder private func foodCard(_ p: HoyolandProgram) -> some View {
        let game = event.programGame(title: p.title)
        let raw = event.stageColor(game: game)
        let c: Color = raw == 0 ? GLGColor.textSecondary : Color(argb64: raw)
        // 메뉴 줄 세기 — 카드를 열기 전에 "몇 가지나 파나"가 보이게. 들여쓴 부연은 빼고 센다.
        let menuCount = p.desc.components(separatedBy: "\n")
            .filter { $0.hasPrefix("· ") && $0.contains(" — ") }.count
        let blocks = hoyolandFoodBlocks(p.desc)

            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    if !game.isEmpty {
                        Text(event.stageLabel(game: game))
                            .font(.pretendard(size: 9.5, weight: .black)).foregroundStyle(c)
                            .padding(.horizontal, 6).padding(.vertical, 3)
                            .background(c.opacity(0.14),
                                        in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    }
                    Text(p.title.components(separatedBy: " — ").first ?? p.title)
                        .font(.pretendard(size: 17, weight: .bold))   // 섹션 제목 17(10/1)
                        .foregroundStyle(GLGColor.textPrimary)
                    Spacer(minLength: 6)
                    if menuCount > 0 {
                        Text("\(menuCount)종")
                            .font(.pretendard(size: 10.5, weight: .bold))
                            .foregroundStyle(GLGColor.textSecondary)
                            .padding(.horizontal, 8).padding(.vertical, 3)
                            .background(GLGColor.textSecondary.opacity(0.12), in: Capsule())
                            .layoutPriority(1)
                    }
                }
                ForEach(Array(blocks.enumerated()), id: \.offset) { bi, block in
                    switch block {
                    case .para(let text):
                        // 메뉴 **바로 앞**에 오는 문장은 그 메뉴를 파는 곳의 이름이다
                        // ("오렐리아 아카데미 카페테리아" · "CuppaMoment"). 뒤에 오는 문장은
                        // 그 메뉴에 붙는 안내다("코스 A·B 를 주문하면 …"). 같은 회색 문단으로
                        // 두면 한 카드 안에 카운터가 둘이라는 사실이 안 보인다.
                        if bi + 1 < blocks.count, case .menu = blocks[bi + 1] {
                            HStack(spacing: 7) {
                                RoundedRectangle(cornerRadius: 2, style: .continuous)
                                    .fill(c).frame(width: 3, height: 13)
                                Text(text).font(.pretendard(size: 13, weight: .bold))
                                    .foregroundStyle(GLGColor.textPrimary)
                                    .fixedSize(horizontal: false, vertical: true)
                                Spacer(minLength: 0)
                            }
                        } else {
                            Text(text).font(.pretendard(size: 12.5))
                                .foregroundStyle(GLGColor.textSecondary)
                                .lineSpacing(4)
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        }
                    case .menu(let rows):
                        // 메뉴는 **헤어라인으로 나눈 목록**이다(10/1) — 회색 메뉴판 면을 걷었다. 첫 줄 위에도 헤어라인을
                        // 그어 소제목·안내 문장과 "어디서부터 파는 것인가" 의 경계를 남긴다.
                        VStack(spacing: 0) {
                            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                                HoyolandHairline()
                                // 메뉴 사진 — 있으면 줄 왼쪽 52칸(누르면 크게 보기). 없으면 글만.
                                let photo = p.menuImageUrl(name: row.name)
                                HStack(spacing: 12) {
                                    if !photo.isEmpty {
                                        Button {
                                            viewingFood = HoyolandFoodPhoto(name: row.name, price: row.price, url: photo, game: game)
                                        } label: {
                                            // 흰 바탕 사진이 흰 페이지에 녹지 않게 사진 테두리는 남긴다(사진 틀).
                                            GLGRemoteImage(url: URL(string: photo), side: 52) { Color.white }
                                                .frame(width: 52, height: 52)
                                                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                                                .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous)
                                                    .stroke(.black.opacity(0.06), lineWidth: 1))
                                        }
                                        .buttonStyle(.plain)
                                    }
                                    VStack(alignment: .leading, spacing: 3) {
                                        HStack(spacing: 10) {
                                            Text(row.name).font(.pretendard(size: 15, weight: .bold))   // 13 → 15(10/1)
                                                .foregroundStyle(GLGColor.textPrimary)
                                                .fixedSize(horizontal: false, vertical: true)
                                            Spacer(minLength: 0)
                                            if !row.price.isEmpty {
                                                Text(row.price)
                                                    .font(.pretendard(size: 15, weight: .bold)).monospacedDigit()   // 13 → 15(10/1)
                                                    .foregroundStyle(c)
                                                    .layoutPriority(1)
                                            }
                                        }
                                        if !row.sub.isEmpty {
                                            Text(row.sub).font(.pretendard(size: 12.5))
                                                .foregroundStyle(GLGFoodTextThird)
                                                .lineSpacing(3)
                                                .fixedSize(horizontal: false, vertical: true)
                                        }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                }
                                .padding(.vertical, 12)
                            }
                        }
                    }
                }
                if !p.deadline.isEmpty {
                    Text(p.deadline)
                        .font(.pretendard(size: 10.5, weight: .bold)).foregroundStyle(c)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(c.opacity(0.12), in: Capsule())
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/**
 원격 config 의 **여러 줄 안내글**을 줄 단위로 읽어 그린다 — 예매 안내가 쓴다.

 줄 규칙 — 위에서부터 먼저 맞는 것:
  - 들여쓴 줄 → 바로 위 항목의 부연(작게·흐리게)
  - `· …`   → 항목 줄. ` — ` 가 있으면 **뒤가 값**(시각)이라 오른쪽에 붙여 강조한다
  - `1. …`  → 순서 줄. 번호만 색을 준다
  - 빈 줄   → 문단 사이 간격
  - 그 외   → 문단
 */
struct HoyolandRichText: View {
    let text: String
    let valueColor: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            let lines = text.components(separatedBy: "\n")
            ForEach(Array(lines.enumerated()), id: \.offset) { i, raw in
                let indented = !raw.trimmingCharacters(in: .whitespaces).isEmpty
                    && (raw.hasPrefix("  ") || raw.hasPrefix("\t"))
                let body = raw.trimmingCharacters(in: .whitespaces)
                if body.isEmpty {
                    // 빈 줄은 그 자체가 문단 구분이다 — 간격만 준다.
                    Color.clear.frame(height: 10)
                } else {
                    // 문단 첫 줄에는 위 여백을 주지 않는다(빈 줄이 이미 벌려 놨다).
                    let firstOfPara = i == 0
                        || lines[i - 1].trimmingCharacters(in: .whitespaces).isEmpty
                    // 들여쓴 목록 줄은 깊이에 맞는 점으로 보인다(◦ · ▪) — 가운뎃점은 저장되는 글자일 뿐이다(10/6).
                    let shown = indented && body.hasPrefix("· ")
                        ? "\(HoyolandText.shared.dot(level: HoyolandText.shared.depth(line: raw))) \(body.dropFirst(2))"
                        : body
                    line(shown, indented: indented)
                        .padding(.top, firstOfPara ? 0 : (indented ? 2 : 6))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    @ViewBuilder private func line(_ body: String, indented: Bool) -> some View {
        if indented {
            Text(body).font(.pretendard(size: 11.5)).foregroundStyle(GLGFoodTextThird)
                .lineSpacing(3)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.leading, 12)
                .frame(maxWidth: .infinity, alignment: .leading)
        } else if body.hasPrefix("· ") {
            let item = String(body.dropFirst(2))
            let r = item.range(of: " — ", options: .backwards)
            HStack(spacing: 7) {
                // 목록 점은 굵은 점 — `HoyolandListText` 와 같은 도형이다(10/6). 사이 7 은 예전 그대로다.
                HoyolandListDot(level: 0, size: 13, width: nil).foregroundStyle(GLGFoodTextThird)
                Text(r.map { String(item[..<$0.lowerBound]) } ?? item)
                    .font(.pretendard(size: 13, weight: .medium))
                    .foregroundStyle(GLGColor.textPrimary)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 10)
                if let r {
                    Text(String(item[r.upperBound...]))
                        .font(.pretendard(size: 13, weight: .bold)).foregroundStyle(valueColor)
                        .layoutPriority(1)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else if let dot = body.range(of: ". "), Int(body[..<dot.lowerBound]) != nil {
            HStack(alignment: .top, spacing: 0) {
                Text(String(body[..<dot.lowerBound]))
                    .font(.pretendard(size: 12, weight: .black)).foregroundStyle(valueColor)
                    .frame(width: 18, alignment: .center)
                Text(String(body[dot.upperBound...]))
                    .font(.pretendard(size: 13)).foregroundStyle(GLGColor.textPrimary)
                    .lineSpacing(3)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        } else {
            Text(body).font(.pretendard(size: 12.5)).foregroundStyle(GLGColor.textSecondary)
                .lineSpacing(4)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}


/**
 어드민에서 적은 **여러 줄 글** — 「· 항목」 · 「1. 항목」 줄을 목록으로 그린다(10/6). Android `HoyolandListText` 와 같은 규칙 · 같은 값.

 설명 · 공지 · 받는 것처럼 `Text` 하나에 통째로 넣던 자리가 쓴다. 통째로 넣으면 목록 줄이 길어 다음 줄로 넘어갈 때
 **점 아래로 글자가 들어가** 어디서 항목이 바뀌는지 안 보인다. 점은 노션처럼 깊이마다 다르다 — 굵은 점 → 빈 동그라미 → 네모(`HoyolandListDot`). 줄 규칙은 공유 모듈 `HoyolandText.lines` 가 정한다.

 글자 크기 · 굵기 · 줄간은 **부르는 자리의 것 그대로**고, 색은 부르는 쪽이 `foregroundStyle` 로 준다 — 목록이 생겼다고
 그 자리의 글 모양이 바뀌지 않는다. 목록 줄이 하나도 없으면 예전처럼 `Text` 하나로 그린다.

 값을 오른쪽에 붙여 강조하는 줄(예매 안내의 「· 이름 — 값」)은 이것이 아니라 `HoyolandRichText` 가 그린다.
 */
/**
 목록 점 — 첫 줄 높이의 가운데에 선다. Android `HoyolandListDot` 와 같은 값.

 **글자가 아니라 도형으로 그린다**(10/6). 글꼴의 「•」는 13pt 에서 지름이 2pt 가 안 돼 가운뎃점과 구별이 안 됐다.
 노션처럼 굵게 보이도록 지름을 글자 크기의 0.38 로 잡는다 — 13pt 면 약 5pt.
 깊이마다 모양이 다르다: 채운 원 → 빈 원 → 채운 네모, 그 아래는 다시 처음부터. 색은 부르는 쪽의 `foregroundStyle` 을 따른다.
 */
struct HoyolandListDot: View {
    let level: Int32
    let size: CGFloat
    var weight: Font.Weight = .regular
    /// 머리 칸의 폭. nil 이면 점 폭만 차지한다(부르는 쪽이 사이를 따로 준다).
    let width: CGFloat?

    var body: some View {
        let d = size * 0.38
        // 보이지 않는 글자 하나가 **첫 줄의 높이와 기준선**을 잡는다 — 점은 그 줄의 가운데에 얹는다.
        Text(" ").font(.pretendard(size: size, weight: weight))
            .frame(width: width ?? d, alignment: .leading)
            .overlay(alignment: .leading) {
                switch max(level, 0) % 3 {
                case 0: Circle().frame(width: d, height: d)
                case 1: Circle().strokeBorder(lineWidth: d * 0.24).frame(width: d, height: d)
                // 네모는 같은 지름의 원보다 커 보인다 — 한 단 작게 그려 무게를 맞춘다.
                default: Rectangle().frame(width: d * 0.86, height: d * 0.86)
                }
            }
    }
}

struct HoyolandListText: View {
    let text: String
    let size: CGFloat
    var weight: Font.Weight = .regular
    let lineSpacing: CGFloat

    /// 목록 줄의 머리 칸 — 굵은 점이 서는 폭이자, 하위 항목 · 부연이 한 단 들어가는 폭(Android `HoyolandListIndent`).
    private static let indent: CGFloat = 12

    private var font: Font { .pretendard(size: size, weight: weight) }

    var body: some View {
        if !HoyolandText.shared.hasList(text: text) {
            Text(text).font(font).lineSpacing(lineSpacing)
                .fixedSize(horizontal: false, vertical: true)
        } else {
            let lines = HoyolandText.shared.lines(text: text)
            // 줄 사이는 줄간과 같다 — `Text` 하나에 넣었을 때와 같은 간격이다.
            VStack(alignment: .leading, spacing: lineSpacing) {
                ForEach(Array(lines.enumerated()), id: \.offset) { _, l in
                    row(l)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    @ViewBuilder private func row(_ l: HoyolandTextLine) -> some View {
        switch l.kind {
        case .para:
            Text(l.text).font(font).lineSpacing(lineSpacing)
                .fixedSize(horizontal: false, vertical: true)
        case .blank:
            // 빈 줄은 한 줄 높이.
            Text(" ").font(font)
        case .item:
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                HoyolandListDot(level: l.level, size: size, weight: weight, width: Self.indent)
                Text(l.text).font(font).lineSpacing(lineSpacing)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.leading, Self.indent * CGFloat(l.level))
        case .num:
            // 번호 줄 — 번호는 적은 그대로("1."), 글은 번호 뒤에서 줄을 맞춘다.
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                Text("\(l.mark) ").font(font)
                Text(l.text).font(font).lineSpacing(lineSpacing)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.leading, Self.indent * CGFloat(l.level))
        case .sub:
            Text(l.text).font(font).lineSpacing(lineSpacing)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.leading, Self.indent * CGFloat(l.level))
        }
    }
}


/// 푸드 메뉴 사진 크게 보기 — 담기가 없는 `HoyolandGoodsImageSheet`. 음식은 장바구니에 담지 않는다.
struct HoyolandPhotoSheet: View {
    let label: String
    let color: Color
    let title: String
    let price: String
    let url: URL?
    @Environment(\.dismiss) private var dismiss
    @State private var contentHeight: CGFloat = 560
    @State private var chromeHeight: CGFloat = 0
    var body: some View {
        // 굿즈 사진 시트와 같은 짜임 — 제목·닫기는 시스템 네비 바, 높이는 내용이 정한다.
        NavigationStack {
            VStack(alignment: .leading, spacing: 0) {
            HoyolandZoomableImage(url: url)
                .frame(height: 300)
                .background(GLGFoodRowBg, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
            if !label.isEmpty {
                hoyolandSheetBadge(label, color).padding(.top, 12)
            }
            Text(title).font(.pretendard(size: 18, weight: .bold))
                .foregroundStyle(GLGColor.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 7)
            if !price.isEmpty {
                Text(price).font(.pretendard(size: 20, weight: .black)).monospacedDigit()
                    .foregroundStyle(color).padding(.top, 4)
            }
            }
            // 마지막 줄이 값(가격)이라 아래 여백을 넉넉히 준다.
            .padding(.horizontal, 18).padding(.top, 8).padding(.bottom, 22)
            .frame(maxWidth: .infinity, alignment: .leading)
            .glgSheetContentHeight($contentHeight)
            // 재고 나서 위로 붙인다 — `NavigationStack` 은 자식을 세로 가운데 놓아,
            // 시트에 남는 자리가 생기면 내용이 반씩 위아래로 떠 버린다.
            .frame(maxHeight: .infinity, alignment: .top)
            .navigationTitle("메뉴 사진")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { GLGSheetCloseButton { dismiss() } }
            }
            .glgSheetChromeHeight($chromeHeight)
        }
        // ── 시트 높이 = **내용 + 네비 바 · 홈 인디케이터**(→ [glgSheetContentHeight]).
        //
        // `presentationSizing(.fitted)` 는 iPhone 시트에서 듣지 않는다(iPad · macOS 용이고,
        // 여기서는 시트가 화면 가까이까지 커져 아래가 통째로 비었다 — 2026-09-17 실측).
        // iPhone 은 detent 가 높이를 정하므로 직접 잰다.
        .presentationDetents([.height(contentHeight + chromeHeight)])
        .presentationDragIndicator(.visible)
        .presentationBackground(.white)
    }
}
