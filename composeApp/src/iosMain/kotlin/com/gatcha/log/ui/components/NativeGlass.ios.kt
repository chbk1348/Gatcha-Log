package com.gatcha.log.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSClassFromString
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIGlassEffect
import platform.UIKit.UIImage
import platform.UIKit.UIImageSymbolConfiguration
import platform.UIKit.UIImageView
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.UIVisualEffectView

// ============================================================
//  iOS 26 네이티브 리퀴드 글래스 (UIVisualEffectView + UIGlassEffect)
//
//  - 글래스 뷰는 Compose 캔버스 위(placedAsOverlay=true)에 올라가 뒤의
//    Compose 콘텐츠를 실제로 굴절/블러한다.
//  - 따라서 라벨/아이콘은 Compose 로 그릴 수 없고(글래스에 가려짐),
//    글래스의 contentView 안에 네이티브(UILabel / SF Symbol)로 그린다.
//  - 터치는 Compose 가 처리(interactionMode=null) — 눌림 피드백은
//    update 콜백에서 네이티브 뷰 alpha 로 준다.
// ============================================================

/** UIGlassEffect 클래스 존재 여부 = iOS 26+ (그 외 버전은 Compose 폴백) */
private val glassSupported: Boolean by lazy { NSClassFromString("UIGlassEffect") != null }

actual fun isNativeGlassSupported(): Boolean = glassSupported

/** Compose Color → UIColor */
private fun Color.toUIColor(): UIColor =
    UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

/** 서브뷰 태그 — update 콜백에서 라벨/배지를 다시 찾기 위함 */
private const val TAG_CONTENT = 1L
private const val TAG_BADGE = 2L

/** 리퀴드 글래스 효과 뷰 생성 (iOS 26 전용 — glassSupported 확인 후 호출) */
private fun makeGlassView(tint: Color?, cornerRadius: Double): UIVisualEffectView {
    val effect = UIGlassEffect()
    tint?.let { effect.tintColor = it.toUIColor() }
    val view = UIVisualEffectView(effect = effect)
    view.layer.cornerRadius = cornerRadius
    view.clipsToBounds = true
    // 터치는 Compose 쪽 clickable 이 처리
    view.userInteractionEnabled = false
    return view
}

/** 자식 뷰를 부모(contentView) 에 4면 고정 (Auto Layout) */
private fun pinToEdges(child: UIView, parent: UIView) {
    child.translatesAutoresizingMaskIntoConstraints = false
    parent.addSubview(child)
    NSLayoutConstraint.activateConstraints(
        listOf(
            child.leadingAnchor.constraintEqualToAnchor(parent.leadingAnchor),
            child.trailingAnchor.constraintEqualToAnchor(parent.trailingAnchor),
            child.topAnchor.constraintEqualToAnchor(parent.topAnchor),
            child.bottomAnchor.constraintEqualToAnchor(parent.bottomAnchor),
        )
    )
}

/** 자식 뷰를 부모(contentView) 정중앙에 고정 — 인트린식 크기 유지 (SF Symbol 아이콘용) */
private fun pinToCenter(child: UIView, parent: UIView) {
    child.translatesAutoresizingMaskIntoConstraints = false
    parent.addSubview(child)
    NSLayoutConstraint.activateConstraints(
        listOf(
            child.centerXAnchor.constraintEqualToAnchor(parent.centerXAnchor),
            child.centerYAnchor.constraintEqualToAnchor(parent.centerYAnchor),
        )
    )
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun NativeGlassButton(
    text: String,
    tint: Color?,
    textColor: Color,
    cornerRadius: Dp,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier.then(
            if (enabled) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
            else Modifier
        ),
        contentAlignment = Alignment.Center,
    ) {
        // 크기 측정용 투명 텍스트 — 네이티브 라벨과 같은 서체 크기로 인트린식 사이즈 확보
        // (UIKitView 는 matchParentSize 라 Box 크기에 기여하지 않음)
        Text(
            text,
            color = Color.Transparent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        // 틴트(테마) 변경 시 글래스 효과를 다시 생성 (effect 는 생성 후 교체 불가)
        key(tint) {
            UIKitView(
                factory = {
                    val view = makeGlassView(tint, cornerRadius.value.toDouble())
                    val label = UILabel().apply {
                        this.text = text
                        font = UIFont.boldSystemFontOfSize(15.0)
                        this.textColor = textColor.toUIColor()
                        textAlignment = NSTextAlignmentCenter
                        tag = TAG_CONTENT
                    }
                    pinToEdges(label, view.contentView)
                    view
                },
                update = { view ->
                    (view.contentView.viewWithTag(TAG_CONTENT) as? UILabel)?.let { label ->
                        label.text = text
                        label.textColor = textColor.toUIColor()
                    }
                    // 눌림/비활성 피드백 — 네이티브 뷰 alpha
                    view.alpha = when {
                        !enabled -> 0.45
                        pressed -> 0.7
                        else -> 1.0
                    }
                },
                modifier = Modifier.matchParentSize(),
                properties = UIKitInteropProperties(
                    interactionMode = null,   // 터치를 Compose 로 통과
                    placedAsOverlay = true,   // Compose 캔버스 위 — 뒤 콘텐츠 실제 굴절
                ),
            )
        }
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun NativeGlassIconButton(
    sfSymbol: String,
    tint: Color?,
    iconColor: Color,
    size: Dp,
    enabled: Boolean,
    badgeCount: Int,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (enabled) Modifier.clickable(interactionSource = interaction, indication = null) { onClick() }
                else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        key(tint, sfSymbol) {
            UIKitView(
                factory = {
                    // 원형 글래스 (반경 = 크기/2)
                    val view = makeGlassView(tint, size.value.toDouble() / 2.0)

                    // SF Symbol 아이콘 — 정중앙 배치 (인트린식 크기 유지)
                    val imageView = UIImageView(image = UIImage.systemImageNamed(sfSymbol)).apply {
                        tintColor = iconColor.toUIColor()
                        preferredSymbolConfiguration = UIImageSymbolConfiguration.configurationWithPointSize(17.0)
                        tag = TAG_CONTENT
                    }
                    pinToCenter(imageView, view.contentView)

                    // 우상단 배지 (알림 수 등)
                    val badge = UILabel().apply {
                        font = UIFont.boldSystemFontOfSize(9.0)
                        this.textColor = UIColor.whiteColor
                        textAlignment = NSTextAlignmentCenter
                        backgroundColor = UIColor(red = 1.0, green = 0.647, blue = 0.0, alpha = 1.0) // 0xFFFFA500
                        layer.cornerRadius = 8.0
                        clipsToBounds = true
                        hidden = true
                        tag = TAG_BADGE
                        translatesAutoresizingMaskIntoConstraints = false
                    }
                    view.contentView.addSubview(badge)
                    NSLayoutConstraint.activateConstraints(
                        listOf(
                            badge.widthAnchor.constraintEqualToConstant(16.0),
                            badge.heightAnchor.constraintEqualToConstant(16.0),
                            badge.topAnchor.constraintEqualToAnchor(view.contentView.topAnchor, constant = 1.0),
                            badge.trailingAnchor.constraintEqualToAnchor(view.contentView.trailingAnchor, constant = -1.0),
                        )
                    )
                    view
                },
                update = { view ->
                    (view.contentView.viewWithTag(TAG_CONTENT) as? UIImageView)?.tintColor = iconColor.toUIColor()
                    (view.contentView.viewWithTag(TAG_BADGE) as? UILabel)?.let { badge ->
                        badge.hidden = badgeCount <= 0
                        badge.text = if (badgeCount > 9) "9+" else "$badgeCount"
                    }
                    view.alpha = when {
                        !enabled -> 0.45
                        pressed -> 0.7
                        else -> 1.0
                    }
                },
                modifier = Modifier.matchParentSize(),
                properties = UIKitInteropProperties(
                    interactionMode = null,
                    placedAsOverlay = true,
                ),
            )
        }
    }
}
