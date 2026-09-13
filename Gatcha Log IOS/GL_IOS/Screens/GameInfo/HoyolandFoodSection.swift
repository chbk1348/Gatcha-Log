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
struct HoyolandFoodView: View {
    let event: HoyolandEvent

    var body: some View {
        let list = event.foodPrograms
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                if list.isEmpty {
                    GLGCard(cornerRadius: 24, padding: 16) {
                        VStack(alignment: .leading, spacing: 5) {
                            Text("메뉴는 아직 공개 전이에요")
                                .font(.pretendard(size: 14, weight: .bold)).foregroundStyle(GLGColor.textPrimary)
                            Text("게임별 푸드존·푸드트럭 메뉴가 나오면 이 자리에 채워져요.")
                                .font(.pretendard(size: 12)).foregroundStyle(GLGColor.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                } else {
                    ForEach(Array(list.enumerated()), id: \.offset) { _, p in
                        foodCard(p)
                    }
                    // 넛지 — 이 화면의 숫자는 **공지 기준**이라는 것만 분명히 한다. 현장 메뉴판과
                    // 다를 때 "앱이 틀렸다"가 아니라 "바뀌었구나"로 읽히게 하는 한 줄이다.
                    Text("가격·구성은 공식 공지 기준이에요. 현장 사정으로 바뀔 수 있어요.")
                        .font(.pretendard(size: 11)).foregroundStyle(GLGFoodTextThird)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, 2).padding(.horizontal, 2)
                }
                Color.clear.frame(height: 24)
            }
            .padding(.horizontal, 16)
            .glgReadableWidth(720)
        }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle("푸드존")
        .navigationBarTitleDisplayMode(.inline)
    }

    /**
     푸드존 한 칸 — 게임 배지 + 유형(푸드존/푸드트럭) + 메뉴 수, 그리고 메뉴판.

     제목("푸드트럭 — 붕괴: 스타레일")을 통째로 쓰지 않는다. 게임은 이미 배지로 서 있어
     같은 말이 두 번 나오고, 남는 폭이 그만큼 줄어든다 — 앞쪽 유형만 제목으로 쓴다.
     */
    @ViewBuilder private func foodCard(_ p: HoyolandProgram) -> some View {
        let game = event.programGame(title: p.title)
        let raw = event.stageColor(game: game)
        let c: Color = raw == 0 ? GLGColor.textSecondary : Color(argb64: raw)
        // 메뉴 줄 세기 — 카드를 열기 전에 "몇 가지나 파나"가 보이게. 들여쓴 부연은 빼고 센다.
        let menuCount = p.desc.components(separatedBy: "\n")
            .filter { $0.hasPrefix("· ") && $0.contains(" — ") }.count
        let blocks = hoyolandFoodBlocks(p.desc)

        GLGCard(cornerRadius: 24, padding: 16) {
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
                        .font(.pretendard(size: 14, weight: .bold))
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
                        // 메뉴는 **면 위의 목록**으로 묶는다. 본문과 같은 바닥에 줄만 세우면
                        // 소제목·안내 문장과 경계가 없어 "어디까지가 파는 것인가"가 안 보였다.
                        VStack(spacing: 0) {
                            ForEach(Array(rows.enumerated()), id: \.offset) { i, row in
                                if i > 0 { Divider() }
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 10) {
                                        Text(row.name).font(.pretendard(size: 13, weight: .bold))
                                            .foregroundStyle(GLGColor.textPrimary)
                                            .fixedSize(horizontal: false, vertical: true)
                                        Spacer(minLength: 0)
                                        if !row.price.isEmpty {
                                            Text(row.price)
                                                .font(.pretendard(size: 13, weight: .bold)).monospacedDigit()
                                                .foregroundStyle(c)
                                                .layoutPriority(1)
                                        }
                                    }
                                    if !row.sub.isEmpty {
                                        Text(row.sub).font(.pretendard(size: 11.5))
                                            .foregroundStyle(GLGFoodTextThird)
                                            .lineSpacing(3)
                                            .fixedSize(horizontal: false, vertical: true)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.horizontal, 13).padding(.vertical, 11)
                            }
                        }
                        .background(GLGFoodRowBg,
                                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
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
                    line(body, indented: indented)
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
                Text("·").font(.pretendard(size: 13)).foregroundStyle(GLGFoodTextThird)
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
