package com.example.acagent

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Runs inside the Shizuku-started process (shell uid). */
class ShellService() : IShellService.Stub() {
    @Suppress("UNUSED_PARAMETER")
    constructor(context: Context) : this()

    override fun destroy() { exitProcess(0) }

    override fun exec(command: String, timeoutSec: Int): String {
        return try {
            val p = ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start()
            val out = StringBuilder()
            val t = Thread { p.inputStream.bufferedReader().use { r -> out.append(r.readText()) } }
            t.start()
            if (!p.waitFor(timeoutSec.toLong(), TimeUnit.SECONDS)) { p.destroyForcibly(); out.append("\nTIMEOUT") }
            t.join(2000)
            out.toString()
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }
}
