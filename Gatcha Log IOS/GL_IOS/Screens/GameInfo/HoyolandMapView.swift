import SwiftUI
import Shared

/**
 호요랜드 맵스 — 행사장 배치도를 **다시 그린 판.**

 ## 왜 이미지 한 장이 아닌가

 공식 배치도는 이미지로만 공개된다. 그대로 띄우면 확대해서 보는 것 말고 할 수 있는 게 없다.
 구역을 좌표 데이터로 들고 있으면 **누른 구역의 목록으로 곧장 갈 수 있다** — 굿즈존을 누르면
 굿즈 105종이, 무대존을 누르면 시간표가 열린다. 앱이 그 목록을 이미 들고 있어서 되는 일이고,
 공식 사이트도 예매처도 못 하는 각도다. 지도가 목록의 입구가 된다.

 좌표는 전부 비율(0~100)이라 어떤 화면에서도 같은 그림이 나온다.
 (Android `HoyolandMapContent` 와 파리티)
 */
struct HoyolandMapView: View {
    let event: HoyolandEvent
    let store: SpendingStore
    @Environment(\.glgAccent) private var accent
    @Environment(\.horizontalSizeClass) private var hSize
    /// 페이지(창) 크기 — 판 크기를 여기서 뽑는다.
    ///
    /// `UIScreen.main.bounds` 를 쓰던 자리다. 기기 화면은 **창이 아니다** — iPad 분할·Stage Manager
    /// 에서는 창이 화면보다 한참 작은데 그 값으로 재면 판이 창을 넘고, 반대로 화면을 꽉 쓰는
    /// iPad 에서는 판이 520 에 묶여 가운데 작게 남았다(2026-09-21 지적).
    @State private var viewport: CGSize = .zero

    /// 판을 놓을 수 있는 가로 — 창에서 페이지 여백(16×2)과 읽기 폭 제한을 뺀 값.
    private var contentWidth: CGFloat {
        let w = max(viewport.width, 320) - 32
        return hSize == .regular ? min(w, HoyolandMapReadableWidth - 32) : w
    }

    /// 판 높이 상한의 바탕이 되는 세로 — 아직 재기 전이면 화면 값으로 시작한다.
    private var viewportHeight: CGFloat {
        viewport.height > 0 ? viewport.height : UIScreen.main.bounds.height
    }

    var body: some View {
        let map = event.map
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                if !map.note.isEmpty {
                    Text(map.note)
                        .font(.pretendard(size: 12))
                        .foregroundStyle(GLGColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.bottom, 12)
                }
                board(map)
                    .padding(.bottom, 12)
                legend
                Color.clear.frame(height: 24)
            }
            .padding(16)
            .glgReadableWidth(HoyolandMapReadableWidth)
        }
        .onGeometryChange(for: CGSize.self) { $0.size } action: { viewport = $0 }
        .scrollIndicators(.hidden)
        .background(GLGBackground { Color.clear })
        .glgPageTitle(map.title.isEmpty ? "행사장 배치도" : map.title)
        .navigationBarTitleDisplayMode(.inline)
    }

    // ── 판 ────────────────────────────────────────────────────────────────

    /**
     구역을 비율 좌표로 얹는다.

     좌표를 **구역들이 실제로 차지하는 범위로 다시 펴서** 그린다. 원본 도면에는 판 둘레에 빈
     여백이 있는데, 그걸 그대로 옮기면 화면에서 배치도만 작아지고 둘레가 텅 빈다.

     판 크기는 **폭과 높이 양쪽에서 막는다.** 폭만 보고 그리면 아이패드에서 판이 화면 폭만큼
     커져 글자만 둥둥 뜨고, 세로가 짧은 기기에서는 판이 화면을 넘겨 아래가 잘린다.
     */
    @ViewBuilder private func board(_ map: HoyolandMap) -> some View {
        let zones = map.drawable
        let minX = zones.map(\.x).min() ?? 0
        let maxX = zones.map { $0.x + $0.w }.max() ?? 100
        let minY = zones.map(\.y).min() ?? 0
        let maxY = zones.map { $0.y + $0.h }.max() ?? 100
        let spanX = max(maxX - minX, 1)
        let spanY = max(maxY - minY, 1)
        let ratio = min(max((spanX / spanY) * map.ratio, 0.4), 4)

        GeometryReader { geo in
            let pad: CGFloat = 10
            // 폭 상한(520)을 걷었다 — 판은 **창이 주는 만큼** 쓰고, 세로가 모자라면 아래 높이
            // 상한이 먼저 걸린다. 두 값 중 작은 쪽이라 창을 넘지 않는다.
            let widthCap = geo.size.width
            let heightCap = (viewportHeight * 0.52) * CGFloat(ratio)
            let boardW = max(min(widthCap, heightCap), 160)
            let innerW = boardW - pad * 2
            let innerH = innerW / CGFloat(ratio)
            ZStack(alignment: .topLeading) {
                ForEach(Array(zones.enumerated()), id: \.offset) { _, z in
                    let x = innerW * CGFloat((z.x - minX) / spanX)
                    let y = innerH * CGFloat((z.y - minY) / spanY)
                    let w = innerW * CGFloat(z.w / spanX)
                    let h = innerH * CGFloat(z.h / spanY)
                    Group {
                        if z.kind == "flow-in" || z.kind == "flow-out" {
                            HoyolandMapFlow(up: z.kind == "flow-in", tint: accent.deep.opacity(0.45))
                                .frame(width: w, height: h)
                        } else {
                            zoneBox(z).frame(width: w, height: h)
                        }
                    }
                    .offset(x: x, y: y)
                }
            }
            .frame(width: innerW, height: innerH, alignment: .topLeading)
            .padding(pad)
            // 판 바탕 — `accent.tint` 는 이 페이지의 회색 배경 위에서 거의 안 보여 판의 경계가
            // 사라진다(히어로가 같은 이유로 강조색 옅은 면을 쓴다).
            .background(accent.primary.opacity(0.10),
                        in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .frame(height: boardHeight(map))
    }

    /// 판이 차지할 높이 — `GeometryReader` 는 높이를 스스로 못 정해서 밖에서 잡아 준다.
    private func boardHeight(_ map: HoyolandMap) -> CGFloat {
        let zones = map.drawable
        let minX = zones.map(\.x).min() ?? 0
        let maxX = zones.map { $0.x + $0.w }.max() ?? 100
        let minY = zones.map(\.y).min() ?? 0
        let maxY = zones.map { $0.y + $0.h }.max() ?? 100
        let spanX = max(maxX - minX, 1)
        let spanY = max(maxY - minY, 1)
        let ratio = min(max((spanX / spanY) * map.ratio, 0.4), 4)
        // `board` 안의 `GeometryReader` 와 **같은 식**이어야 한다 — 여기서 잡아 주는 높이가 그
        // 판이 실제로 그리는 크기와 어긋나면 판 아래가 비거나 범례를 덮는다.
        let boardW = max(min(contentWidth, (viewportHeight * 0.52) * CGFloat(ratio)), 160)
        return (boardW - 20) / CGFloat(ratio) + 20
    }

    /**
     구역 한 칸.

     면 색이 **무엇을 하는 곳인지**를 먼저 말한다 — 게임 부스는 게임색, 무대·굿즈는 강조색,
     입장 동선은 먹색.

     글자 크기는 **고정 pt** 다. 이 글자들은 문단이 아니라 칸에 매인 라벨이라, 시스템 글꼴을
     키운 기기에서 글자만 커지면 칸을 넘쳐 배치도가 뭉개진다(`.dynamicTypeSize` 를 잠근다).
     게임 부스만 크기를 고정하는 이유는 넷이 나란히 서는 자리라 크기 차이가 곧 중요도로
     읽히기 때문이다 — 나머지 칸은 서로 견줄 일이 없어 칸에 맞춰 줄어든다.
     */
    @ViewBuilder private func zoneBox(_ z: HoyolandMapZone) -> some View {
        let gameColor: Color? = z.game.isEmpty ? nil : Color(argb64: GameData.shared.colorFor(name: z.game))
        let fill: Color = {
            switch z.kind {
            case "game": return (gameColor ?? accent.primary).opacity(0.85)
            case "stage": return accent.primary.opacity(0.75)
            case "goods": return accent.primary.opacity(0.55)
            case "food": return accent.primary.opacity(0.45)
            case "entry": return HoyolandMapEntryColor
            case "booth": return accent.primary.opacity(0.30)
            default: return accent.primary.opacity(0.18)
            }
        }()
        let fg: Color = {
            if z.accent && z.kind != "game" { return gameColor ?? accent.deep }
            switch z.kind {
            case "game", "stage", "entry", "goods", "food": return .white
            default: return accent.deep
            }
        }()
        // 입장 동선(접수·게이트)은 지나는 길이라 갈 데가 없다 — **링크로 감싸지 않는다.**
        //
        // `.disabled` 로 막으면 SwiftUI 가 그 뷰를 통째로 흐리게 칠해, 짙은 면 위 흰 글자가
        // 회색으로 죽는다(2026-09-16 지적). 누를 데가 없으면 애초에 버튼을 만들지 않는 게 맞다.
        let linkable = !(z.kind == "entry" || z.kind == "etc")
        if linkable {
            NavigationLink {
                // 누른 구역의 목록으로 — 지도가 목록의 입구가 된다. 되돌아오기는 내비게이션이
                // 알아서 하므로(스크롤 위치까지) 따로 기억할 것이 없다.
                switch z.kind {
                case "goods": HoyolandGoodsView(event: event, store: store)
                case "stage": HoyolandStageView(event: event, entry: store.hoyolandEntry)
                case "food": HoyolandFoodView(event: event)
                default: HoyolandBoothView(event: event, initialGame: z.game.isEmpty ? nil : z.game)
                }
            } label: {
                zoneFace(z, fill: fill, fg: fg)
            }
            .buttonStyle(.plain)
            // 글자색은 **링크 바깥에서** 건다 — 안쪽 `Text` 에만 걸면 링크 틴트가 덮는다.
            .tint(fg)
            .dynamicTypeSize(.large)
        } else {
            zoneFace(z, fill: fill, fg: fg)
                .dynamicTypeSize(.large)
        }
    }

    /// 칸의 겉모습 — 링크로 감싸든 아니든 같은 그림이어야 한다.
    @ViewBuilder private func zoneFace(_ z: HoyolandMapZone, fill: Color, fg: Color) -> some View {
        GeometryReader { g in
            ZStack {
                RoundedRectangle(cornerRadius: 8, style: .continuous).fill(fill)
                // 칸이 글자를 담기엔 너무 작으면 **색만 남긴다.** 억지로 넣으면 한 글자만
                // 보이거나 말줄임표만 남아, 없는 것만 못하다.
                if g.size.width >= 22 && g.size.height >= 12 {
                    Text(z.label)
                        .font(.pretendard(size: z.kind == "game" ? 12 : 10, weight: z.kind == "game" ? .black : .bold))
                        .foregroundStyle(fg)
                        .multilineTextAlignment(.center)
                        .lineLimit(3)
                        .minimumScaleFactor(z.kind == "game" ? 1 : 0.6)
                        .padding(.horizontal, 3)
                        .padding(.vertical, 2)
                }
            }
        }
    }

    // ── 범례 ──────────────────────────────────────────────────────────────

    /**
     색이 무엇을 뜻하는지 — 판만 보고는 알 수 없다.

     **판에 실제로 선 종류만 세운다.** 목록을 고정해 두면 둘 다 틀린다 — 아직 구역이 없는
     푸드존은 설명할 색이 없는데 칸을 차지하고, 반대로 어드민이 새 종류를 올리면 판에는 뜨는데
     범례에는 없는 색이 생긴다(푸드가 그 상태였다).

     게임 칸은 **판에 든 게임 색을 그대로** 점으로 찍는다. 대표로 원신 하나만 걸던 때는 판에
     색이 셋인데 범례는 하나라, 스타레일 보라가 무슨 색인지 범례가 답하지 못했다.
     (Android `HoyolandMapLegend` 와 파리티)
     */
    private var legend: some View {
        let zones = event.map.drawable
        let kinds = Set(zones.map(\.kind))
        // 판에 선 게임들(원본 순서, 중복 제거) — 색 점이 판의 칸 색과 하나씩 대응한다.
        var games: [String] = []
        for z in zones where z.kind == "game" && !z.game.isEmpty && !games.contains(z.game) {
            games.append(z.game)
        }
        var items: [([Color], String)] = []
        if !games.isEmpty {
            items.append((games.map { Color(argb64: GameData.shared.colorFor(name: $0)).opacity(0.85) }, "게임"))
        }
        if kinds.contains("stage") { items.append(([accent.primary.opacity(0.75)], "무대")) }
        if kinds.contains("goods") { items.append(([accent.primary.opacity(0.55)], "굿즈")) }
        if kinds.contains("food") { items.append(([accent.primary.opacity(0.45)], "푸드")) }
        // 「체험」 이었던 자리 — 이 색으로 칠하는 건 파트너사 부스 · 창작 전시존 · DIY 존이라
        // 체험이 아닌 칸이 더 많았다. 넷을 다 덮는 말로 부른다.
        if kinds.contains("booth") { items.append(([accent.primary.opacity(0.30)], "부스")) }
        if kinds.contains("entry") { items.append(([HoyolandMapEntryColor], "입장")) }
        if kinds.contains("flow-in") || kinds.contains("flow-out") {
            items.append(([accent.deep.opacity(0.45)], "동선"))
        }
        return HoyolandMapLegendRow(items: items)
    }
}

/// 범례 — **한 줄에 가운데**. 좁으면 글자가 줄어들 뿐 줄은 나뉘지 않는다.
private struct HoyolandMapLegendRow: View {
    /// 색 점(게임만 여럿) + 이름.
    let items: [([Color], String)]

    var body: some View {
        HStack(spacing: 8) {
            ForEach(Array(items.enumerated()), id: \.offset) { _, it in
                HStack(spacing: 4) {
                    HStack(spacing: 2) {
                        ForEach(Array(it.0.enumerated()), id: \.offset) { _, c in
                            RoundedRectangle(cornerRadius: 2, style: .continuous)
                                .fill(c).frame(width: 8, height: 8)
                        }
                    }
                    Text(it.1).font(.pretendard(size: 10))
                        .foregroundStyle(GLGColor.textSecondary)
                        .lineLimit(1)
                        .fixedSize()
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .center)
        // 여섯 항목이 한 줄에 안 들어가는 폭에서는 줄을 나누는 대신 **통째로 줄인다** —
        // 범례는 색과 이름이 짝지어 보이는 게 전부라, 줄이 나뉘면 짝이 흐트러진다.
        .minimumScaleFactor(0.7)
        .dynamicTypeSize(.large)
    }
}

/**
 동선 화살표 — 입장(위로) · 퇴장(아래로).

 구역이 아니라 **지나는 방향**이라 면도 라벨도 없다. 축은 머리(삼각형)가 시작되는 자리에서
 딱 끊는다 — 둥근 마감이면 삼각형 안으로 파고들어 목이 뭉쳐 보인다.
 */
private struct HoyolandMapFlow: View {
    let up: Bool
    let tint: Color

    var body: some View {
        GeometryReader { g in
            let w = g.size.width
            let h = g.size.height
            let cx = w / 2
            let head = min(w, h / 3)
            let strokeW = max(w * 0.42, 2)
            Path { p in
                p.move(to: CGPoint(x: cx, y: up ? h : 0))
                p.addLine(to: CGPoint(x: cx, y: up ? head : h - head))
            }
            .stroke(tint, style: StrokeStyle(lineWidth: strokeW, lineCap: .butt))
            Path { p in
                if up {
                    p.move(to: CGPoint(x: cx, y: 0))
                    p.addLine(to: CGPoint(x: cx - head / 1.4, y: head))
                    p.addLine(to: CGPoint(x: cx + head / 1.4, y: head))
                } else {
                    p.move(to: CGPoint(x: cx, y: h))
                    p.addLine(to: CGPoint(x: cx - head / 1.4, y: h - head))
                    p.addLine(to: CGPoint(x: cx + head / 1.4, y: h - head))
                }
                p.closeSubpath()
            }
            .fill(tint)
        }
    }
}

/// 판 최대 폭 — 아이패드에서 도면만 커지고 글자가 둥둥 뜨는 것을 막는다.
/// 배치도 페이지 읽기 폭 — 판이 창을 따라 커지므로 다른 호요랜드 페이지(720)보다 넉넉히 준다.
/// 안내 · 범례는 한 줄짜리라 이 폭에서도 늘어져 읽히지 않는다.
private let HoyolandMapReadableWidth: CGFloat = 960

/**
 입장 동선 색 — 테마 강조색을 따르지 않는 유일한 구역이다.

 입장 접수·게이트는 "고를 것" 이 아니라 **반드시 지나는 길**이라, 강조색으로 칠하면 앱이 미는
 자리처럼 보인다. 공식 배치도도 이 칸만 짙은 먹색으로 빼 뒀다.
 */
private let HoyolandMapEntryColor = Color(hex: 0xFF4A5A6B)
