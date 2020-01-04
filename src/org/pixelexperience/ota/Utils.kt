package io.protowise.max.android.SpringLayout

import androidx.dynamicanimation.animation.FloatPropertyCompat
import org.xmlpull.v1.XmlPullParser
import java.lang.reflect.Field
import java.util.*
import kotlin.math.roundToInt
import kotlin.reflect.KMutableProperty0

class KFloatPropertyCompat(private val property: KMutableProperty0<Float>, name: String) : FloatPropertyCompat<Any>(name) {

    override fun getValue(`object`: Any) = property.get()

    override fun setValue(`object`: Any, value: Float) {
        property.set(value)
    }
}

operator fun XmlPullParser.get(index: Int): String? = getAttributeValue(index)
operator fun XmlPullParser.get(namespace: String?, key: String): String? = getAttributeValue(namespace, key)
operator fun XmlPullParser.get(key: String): String? = this[null, key]

fun <T, U : Comparable<U>> Comparator<T>.then(extractKey: (T) -> U): Comparator<T> {
    return kotlin.Comparator { o1, o2 ->
        val res = compare(o1, o2)
        if (res != 0) res else extractKey(o1).compareTo(extractKey(o2))
    }
}

inline fun <reified T> getField(name: String): Field {
    return T::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }
}