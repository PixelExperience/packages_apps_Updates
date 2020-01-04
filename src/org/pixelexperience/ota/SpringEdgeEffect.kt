package io.protowise.max.android.SpringLayout

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.EdgeEffect
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.recyclerview.widget.RecyclerView.EdgeEffectFactory.*
import kotlin.reflect.KMutableProperty0

class SpringEdgeEffect(
    context: Context,
    private val getMax: () -> Int,
    private val target: KMutableProperty0<Float>,
    private val activeEdge: KMutableProperty0<SpringEdgeEffect?>,
    private val velocityMultiplier: Float,
    private val reverseAbsorb: Boolean) : EdgeEffect(context) {

    private val spring = SpringAnimation(this, KFloatPropertyCompat(target, "value"), 0f).apply {
        spring = SpringForce(0f).setStiffness(850f).setDampingRatio(0.5f)
    }
    private var distance = 0f

    override fun draw(canvas: Canvas) = false

    override fun onAbsorb(velocity: Int) {
        if (reverseAbsorb) {
            releaseSpring(-velocityMultiplier * velocity)
        } else {
            releaseSpring(velocityMultiplier * velocity)
        }
    }

    override fun onPull(deltaDistance: Float, displacement: Float) {
        activeEdge.set(this)
        distance += deltaDistance * (velocityMultiplier * 2)
        target.set(OverScroll.dampedScroll(distance * getMax(), getMax()).toFloat())
    }

    override fun onRelease() {
        distance = 0f
        releaseSpring(0f)
    }

    private fun releaseSpring(velocity: Float) {
        spring.setStartVelocity(velocity)
        spring.setStartValue(target.get())
        spring.start()
    }

    class Manager(val view: View) {

        var shiftX = 0f
            set(value) {
                if (field != value) {
                    field = value
                    view.invalidate()
                }
            }
        var shiftY = 0f
            set(value) {
                if (field != value) {
                    field = value
                    view.invalidate()
                }
            }

        private var activeEdgeX: SpringEdgeEffect? = null
            set(value) {
                if (field != value) {
                    field?.run { value?.distance = distance }
                }
                field = value
            }
        private var activeEdgeY: SpringEdgeEffect? = null
            set(value) {
                if (field != value) {
                    field?.run { value?.distance = distance }
                }
                field = value
            }

        inline fun withSpring(canvas: Canvas, allow: Boolean = true, body: () -> Boolean): Boolean {
            val result: Boolean
            if ((shiftX == 0f && shiftY == 0f) || !allow) {
                result = body()
            } else {
                canvas.translate(shiftX, shiftY)
                result = body()
                canvas.translate(-shiftX, -shiftY)
            }
            return result
        }

        fun createEdgeEffect(direction: Int, reverseAbsorb: Boolean = false): EdgeEffect? {
            return when (direction) {
                DIRECTION_LEFT -> SpringEdgeEffect(view.context, view::getWidth, ::shiftX, ::activeEdgeX, 0.3f, reverseAbsorb)
                DIRECTION_TOP -> SpringEdgeEffect(view.context, view::getHeight, ::shiftY, ::activeEdgeY, 0.3f, reverseAbsorb)
                DIRECTION_RIGHT -> SpringEdgeEffect(view.context, view::getWidth, ::shiftX, ::activeEdgeX, -0.3f, reverseAbsorb)
                DIRECTION_BOTTOM -> SpringEdgeEffect(view.context, view::getWidth, ::shiftY, ::activeEdgeY, -0.3f, reverseAbsorb)
                else -> null
            }
        }
    }
}
