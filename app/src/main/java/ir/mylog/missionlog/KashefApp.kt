package ir.mylog.missionlog

import android.app.Application
import android.content.Context
import android.content.Intent
import kotlin.system.exitProcess

/**
 * کلاس Application برنامه «کاشف»
 * وظیفه آن نصب یک مدیریت‌کننده خطای سراسری است تا در صورت بروز هر خطای پیش‌بینی‌نشده،
 * برنامه به‌جای بسته‌شدن کامل، به‌صورت خودکار به صفحه قفل برگردد و داده‌ها (از جمله پیش‌نویس) حفظ شوند.
 */
class KashefApp : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashHandler(this)
    }

    companion object {
        private const val PREFS = "crash_log"
        private const val KEY_INFO = "info"
        private const val KEY_TIMES = "times"

        fun installCrashHandler(app: Application) {
            Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
                try {
                    val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    val info = "خطا: ${throwable.javaClass.simpleName}\n" +
                        "${throwable.message ?: "بدون پیام"}\n" +
                        "زمان: ${J.date()} ${J.time()}"
                    prefs.edit().putString(KEY_INFO, info).apply()

                    // جلوگیری از حلقه بی‌نهایت کرش در راه‌اندازی
                    val now = System.currentTimeMillis()
                    val times = (prefs.getString(KEY_TIMES, "") ?: "")
                        .split(",")
                        .mapNotNull { it.toLongOrNull() }
                        .filter { now - it < 60_000 } + now
                    prefs.edit().putString(KEY_TIMES, times.joinToString(",")).apply()

                    if (times.size < 3) {
                        val intent = Intent(app, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        app.startActivity(intent)
                    }
                } catch (_: Exception) {
                    // در صورت خطا در خود مدیریت‌کننده، سکوت می‌کنیم تا فرآیند بسته شود
                }
                exitProcess(1)
            }
        }

        /** خواندن و پاک‌کردن گزارش آخرین کرش (برای نمایش در صفحه قفل) */
        fun consumeCrashInfo(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val info = prefs.getString(KEY_INFO, null)
            if (info != null) {
                prefs.edit().remove(KEY_INFO).apply()
            }
            return info
        }
    }
}
