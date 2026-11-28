package app.hihu.state

import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A text to attach to a bug report: Hihu's version, the phone's Android and model, and the log
 * lines of this run of Hihu only, with every web address taken out, since stream URLs carry the
 * phone's IP address. Nothing leaves the phone unless the user shares it.
 */
class BugReport(private val version: String, private val scope: CoroutineScope) {
    private var job: Job? = null

    /** Reads the log off the main thread and hands the finished text to [ready] on the main thread. */
    fun build(ready: (String) -> Unit) {
        job?.cancel()
        job = scope.launch {
            try {
                val log = withContext(Dispatchers.IO) { readLog() }
                ready(header() + "\n\n" + log)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "report: cannot read the log", e)
                ready(header())
            }
        }
    }

    private fun header(): String =
        "Hihu $version · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"

    /** The last lines this process wrote to the log; an app may read its own lines without a permission. */
    private fun readLog(): String {
        val command = arrayOf("logcat", "-d", "-v", "time", "--pid", Process.myPid().toString())
        val process = Runtime.getRuntime().exec(command)
        val lines = process.inputStream.bufferedReader().use { it.readLines() }
        process.waitFor()
        return lines.takeLast(MAX_LINES).joinToString("\n", transform = ::scrub)
    }

    /**
     * [line] without what could point at the user: addresses, hosts, files on the phone, and the
     * values of cookies or tokens, which another library could have written.
     */
    private fun scrub(line: String): String = line
        .replace(URL, "[url]")
        .replace(SECRET) { "${it.groupValues[1]}=[hidden]" }
        .replace(HOST, "[host]")
        .replace(IPV4, "[ip]")
        .replace(IPV6, "[ip]")

    private companion object {
        const val TAG = "Hihu"
        const val MAX_LINES = 500
        val URL = Regex("""(?:https?|file|content)://\S+""")
        val SECRET = Regex("""(?i)\b(cookie|authorization|sapisid|__Secure-[\w-]+|[a-z]*sid|token|key)\s*[=:]\s*\S+""")
        val HOST = Regex("""\b(?:[a-z0-9-]+\.)+(?:com|net|org|io|dev|be)\b""", RegexOption.IGNORE_CASE)
        val IPV4 = Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b""")
        val IPV6 = Regex("""\b[0-9a-fA-F]{1,4}(?::[0-9a-fA-F]{0,4}){3,7}\b""")
    }
}
