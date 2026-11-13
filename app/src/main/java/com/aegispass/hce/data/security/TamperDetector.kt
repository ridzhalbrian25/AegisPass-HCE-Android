package com.aegispass.hce.data.security

import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/**
 * NFR 6.1 Anti-Hooking & Tampering Awareness:
 * Passive detection of Xposed Framework and Frida default server ports during app initialization.
 */
object TamperDetector {

    fun isEnvironmentTampered(): Boolean {
        return checkStackTraceForHooking() || checkFridaDefaultPort() || checkKnownHookFiles()
    }

    private fun checkStackTraceForHooking(): Boolean {
        return try {
            throw Exception("AegisStackCheck")
        } catch (e: Exception) {
            e.stackTrace.any { element ->
                val cls = element.className.lowercase()
                cls.contains("de.robv.android.xposed") ||
                    cls.contains("com.saurik.substrate") ||
                    cls.contains("frida")
            }
        }
    }

    private fun checkFridaDefaultPort(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", 27042), 30)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun checkKnownHookFiles(): Boolean {
        val suspiciousPaths = listOf(
            "/system/framework/XposedBridge.jar",
            "/data/local/tmp/frida-server"
        )
        return suspiciousPaths.any { File(it).exists() }
    }
}
