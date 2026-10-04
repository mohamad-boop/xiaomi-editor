package app.xeditor.shizuku

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import java.io.ByteArrayOutputStream

/**
 * Runs shell commands as Shizuku's user (shell/ADB, or root if Shizuku was started
 * with root). That user can write into the Themes app's store and start activities
 * from the background, which a normal app can't.
 */
object ShizukuShell {
    enum class Status(val label: String) {
        NOT_INSTALLED("Shizuku is not installed"),
        NOT_RUNNING("Shizuku is installed but not running"),
        NO_PERMISSION("Shizuku is running — allow Xiaomi Editor to use it"),
        READY("Connected to Shizuku"),
    }

    const val PACKAGE = "moe.shizuku.privileged.api"
    private const val REQUEST_CODE = 42

    fun status(ctx: Context): Status {
        val installed = runCatching { ctx.packageManager.getPackageInfo(PACKAGE, 0) }.isSuccess
        if (!installed) return Status.NOT_INSTALLED
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return Status.NOT_RUNNING
        if (Shizuku.isPreV11()) return Status.NOT_RUNNING
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) Status.READY else Status.NO_PERMISSION
    }

    fun isReady(ctx: Context) = status(ctx) == Status.READY

    fun requestPermission(onResult: (Boolean) -> Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                if (requestCode != REQUEST_CODE) return
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(REQUEST_CODE)
    }

    fun openShizukuApp(ctx: Context) {
        ctx.packageManager.getLaunchIntentForPackage(PACKAGE)?.let {
            ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    class Result(val code: Int, val out: ByteArray, val err: String) {
        val ok get() = code == 0
        val text get() = out.toString(Charsets.UTF_8)
    }

    /**
     * Shizuku 13 keeps newProcess private in favour of bound user services; for
     * plain shell commands the reflective call is the widely used equivalent.
     */
    private val newProcess by lazy {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        ).apply { isAccessible = true }
    }

    fun run(command: String, stdin: ByteArray? = null): Result =
        run(command, stdin?.let { java.io.ByteArrayInputStream(it) })

    /** [stdin] is streamed, so large theme parts never sit in memory whole. */
    fun run(command: String, stdin: java.io.InputStream?): Result {
        val p = newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
        if (stdin != null) p.outputStream.use { out -> stdin.use { it.copyTo(out, 64 * 1024) } } else p.outputStream.close()
        val out = ByteArrayOutputStream()
        val errText = StringBuilder()
        val errThread = Thread { errText.append(p.errorStream.bufferedReader().readText()) }.apply { start() }
        p.inputStream.use { it.copyTo(out) }
        errThread.join()
        return Result(p.waitFor(), out.toByteArray(), errText.toString())
    }

    fun check(command: String, stdin: ByteArray? = null): Result =
        check(command, stdin?.let { java.io.ByteArrayInputStream(it) })

    fun check(command: String, stdin: java.io.InputStream?): Result =
        run(command, stdin).also { if (!it.ok) error(it.err.ifBlank { "Command failed: $command" }.trim()) }

    fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"
}
