package com.maji.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 맞이 누름 효과 (#109). Material 기본 물결(회색이 사각형으로 퍼지는 것) 대신,
 * 누른 것이 살짝 작아졌다가 손을 떼면 통통 튀듯 돌아와요. 모양(원·캡슐·스쿼클)을 그대로 유지해서 회색 네모가 생기지 않아요.
 * 작은 원은 더 많이, 넓은 카드·줄은 아주 조금 줄어들어요 (어디서나 약 8dp만큼).
 * MajiTheme 안의 모든 clickable·selectable에 자동으로 적용돼요.
 */
object MajiPressIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = PressNode(interactionSource)
    override fun equals(other: Any?) = other === this
    override fun hashCode() = 31
}

private class PressNode(private val source: InteractionSource) : Modifier.Node(), DrawModifierNode {
    private val pressed = Animatable(0f)  // 0 = 그대로, 1 = 꾹 누름

    override fun onAttach() {
        coroutineScope.launch {
            source.interactions.collect { interaction ->
                val target = when (interaction) {
                    is PressInteraction.Press -> 1f
                    is PressInteraction.Release, is PressInteraction.Cancel -> 0f
                    else -> return@collect
                }
                launch {
                    // 누를 땐 빠르게, 뗄 땐 살짝 튕기며 돌아와요
                    val spec = if (target == 1f) spring<Float>(stiffness = 1500f) else spring(dampingRatio = 0.45f, stiffness = 500f)
                    pressed.animateTo(target, spec) { invalidateDraw() }
                }
            }
        }
    }

    override fun ContentDrawScope.draw() {
        val p = pressed.value
        if (p == 0f) {
            drawContent()
            return
        }
        // 짧은 변 기준으로 약 8dp 줄이되, 작은 원도 너무 작아지지 않게 최소 0.9배
        val shrink = (16.dp.toPx() / max(size.minDimension, 1f)).coerceAtMost(0.1f)
        val s = 1f - shrink * p
        scale(s) { this@draw.drawContent() }
    }
}
