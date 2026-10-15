package sh.yetkin.esigner.app

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.lang.reflect.Proxy
import javax.swing.JComponent

/**
 * Trackpad pinch on macOS. Java has no public API for it; the JDK's internal com.apple.eawt.event package
 * delivers it when the launcher exports it (--add-exports java.desktop/com.apple.eawt.event=ALL-UNNAMED).
 * It is reached by reflection because the package exists only in the macOS JDK and the build runs on Linux.
 */
object MacGestures {
    private val pinches = MutableSharedFlow<Float>(extraBufferCapacity = 64)

    /** Relative magnification per trackpad event, e.g. 0.02 for a small pinch outwards. */
    val pinch: SharedFlow<Float> = pinches

    /** Listens on [component] and everything inside it. Does nothing where the API is missing or not exported. */
    fun install(component: JComponent) {
        runCatching {
            val listenerType = Class.forName("com.apple.eawt.event.MagnificationListener")
            val magnification = Class.forName("com.apple.eawt.event.MagnificationEvent").getMethod("getMagnification")
            val listener = Proxy.newProxyInstance(MacGestures::class.java.classLoader, arrayOf(listenerType)) { proxy, method, args ->
                when (method.name) {
                    "magnify" -> pinches.tryEmit((magnification.invoke(args[0]) as Double).toFloat()).let { null }
                    "equals" -> proxy === args?.get(0)
                    "hashCode" -> System.identityHashCode(proxy)
                    "toString" -> "MacGestures.pinch"
                    else -> null
                }
            }
            Class.forName("com.apple.eawt.event.GestureUtilities")
                .getMethod("addGestureListenerTo", JComponent::class.java, Class.forName("com.apple.eawt.event.GestureListener"))
                .invoke(null, component, listener)
        }
    }
}
