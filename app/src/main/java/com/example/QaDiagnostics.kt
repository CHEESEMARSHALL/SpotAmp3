package com.example

import android.os.StrictMode

/** Installs actionable diagnostics only for debuggable QA/development builds. */
object QaDiagnostics {
    fun installIfDebuggable(isDebuggable: Boolean) {
        if (!isDebuggable) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build()
        )
    }
}
