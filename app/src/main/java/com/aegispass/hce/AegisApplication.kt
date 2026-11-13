package com.aegispass.hce

import android.app.Application
import com.aegispass.hce.data.security.TamperDetector
import com.aegispass.hce.domain.engine.EmulationMemoryBuffer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AegisApplication : Application() {

    @Inject
    lateinit var memoryBuffer: EmulationMemoryBuffer

    override fun onCreate() {
        super.onCreate()
        // NFR 6.1: Anti-Hooking & Tampering Awareness passive check on startup
        val isCompromised = TamperDetector.isEnvironmentTampered()
        memoryBuffer.setEnvironmentCompromised(isCompromised)
    }
}
