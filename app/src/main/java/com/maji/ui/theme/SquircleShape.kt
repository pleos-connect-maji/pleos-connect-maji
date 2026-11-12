package com.maji.ui.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * 스쿼클(연속 곡률) 모서리 계산 (docs/ui/research.md 7-0).
 * 일반 둥근 모서리는 직선이 끝나는 곳에서 곡률이 갑자기 바뀌어요.
 * 스쿼클은 곡선이 더 일찍, 서서히 시작해서 덩어리가 부드러워 보여요 (Figma "corner smoothing"과 같은 계산).
 * 안드로이드에 의존하지 않아서 단위 테스트로 확인해요.
 */
object SquircleGeometry {

    /** 한 모서리를 그리는 값. p = 곡선이 시작되는 거리, r = 가운데 원호 반지름 */
    data class Corner(
        val r: Float,
        val p: Float,
        val a: Float,
        val b: Float,
        val c: Float,
        val d: Float,
        val arcDegrees: Float,   // 가운데 원호의 각도
        val startOffset: Float   // 원호가 모서리 방향에서 얼마나 안쪽에서 시작하는지(도)
    )

    /**
     * radius: 모서리 반지름, smoothing: 0(일반 둥근 모서리)~1. 맞이는 0.6을 써요.
     * 칸이 작아서 곡선이 들어갈 자리가 모자라면 반지름과 smoothing을 줄여요.
     */
    fun corner(radius: Float, smoothing: Float, width: Float, height: Float): Corner {
        val half = min(width, height) / 2f
        val r = radius.coerceIn(0f, half)
        val maxSmoothing = if (r == 0f) 0f else (half / r - 1f).coerceAtLeast(0f)
        val s = smoothing.coerceIn(0f, 1f).coerceAtMost(maxSmoothing)

        val p = (1 + s) * r
        val arcDegrees = 90f * (1 - s)
        val arcSection = sin(rad(arcDegrees / 2)) * r * sqrt(2f)
        val alpha = (90f - arcDegrees) / 2f
        val p3ToP4 = r * tan(rad(alpha / 2))
        val beta = 45f * s
        val c = p3ToP4 * cos(rad(beta))
        val d = c * tan(rad(beta))
        val b = (p - arcSection - c - d) / 3f
        val a = 2 * b
        return Corner(r, p, a, b, c, d, arcDegrees, alpha)
    }

    private fun rad(deg: Float) = (deg * Math.PI / 180.0).toFloat()
}

/** 네 모서리가 같은 스쿼클 모양. 카드·시트·화면 테두리에 써요. 캡슐 버튼과 원은 그대로 둬요. */
class SquircleShape(private val radius: Dp, private val smoothing: Float = 0.6f) : Shape {

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val k = SquircleGeometry.corner(with(density) { radius.toPx() }, smoothing, w, h)
        if (k.r == 0f) return Outline.Rectangle(Rect(0f, 0f, w, h))

        val path = Path().apply {
            // 위 가운데에서 시작해 시계 방향으로 돌아요
            moveTo(w / 2, 0f)
            // 오른쪽 위
            lineTo(w - k.p, 0f)
            cubicTo(w - k.p + k.a, 0f, w - k.p + k.a + k.b, 0f, w - k.p + k.a + k.b + k.c, k.d)
            arcTo(Rect(w - 2 * k.r, 0f, w, 2 * k.r), 270f + k.startOffset, k.arcDegrees, false)
            cubicTo(w, k.p - k.a - k.b, w, k.p - k.a, w, k.p)
            // 오른쪽 아래
            lineTo(w, h - k.p)
            cubicTo(w, h - k.p + k.a, w, h - k.p + k.a + k.b, w - k.d, h - k.p + k.a + k.b + k.c)
            arcTo(Rect(w - 2 * k.r, h - 2 * k.r, w, h), 0f + k.startOffset, k.arcDegrees, false)
            cubicTo(w - k.p + k.a + k.b, h, w - k.p + k.a, h, w - k.p, h)
            // 왼쪽 아래
            lineTo(k.p, h)
            cubicTo(k.p - k.a, h, k.p - k.a - k.b, h, k.p - k.a - k.b - k.c, h - k.d)
            arcTo(Rect(0f, h - 2 * k.r, 2 * k.r, h), 90f + k.startOffset, k.arcDegrees, false)
            cubicTo(0f, h - k.p + k.a + k.b, 0f, h - k.p + k.a, 0f, h - k.p)
            // 왼쪽 위
            lineTo(0f, k.p)
            cubicTo(0f, k.p - k.a, 0f, k.p - k.a - k.b, k.d, k.p - k.a - k.b - k.c)
            arcTo(Rect(0f, 0f, 2 * k.r, 2 * k.r), 180f + k.startOffset, k.arcDegrees, false)
            cubicTo(k.p - k.a - k.b, 0f, k.p - k.a, 0f, k.p, 0f)
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is SquircleShape && other.radius == radius && other.smoothing == smoothing
    override fun hashCode() = 31 * radius.hashCode() + smoothing.hashCode()
}
