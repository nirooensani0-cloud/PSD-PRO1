package ir.mylog.missionlog

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.graphics.pdf.PdfDocument
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.provider.MediaStore
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.text.TextPaint
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.FragmentActivity
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.osmdroid.views.MapView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

/** تبدیل ارقام فارسی و عربی به لاتین و یکسان‌سازی حروف ی و ک */
fun norm(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        sb.append(
            when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                'ي' -> 'ی'
                'ك' -> 'ک'
                else -> c
            }
        )
    }
    return sb.toString().trim()
}

fun plateKey(s: String): String = norm(s).filter { it.isLetterOrDigit() }

data class PlateParts(
    val code2: String = "",
    val digits3: String = "",
    val letter: String = "",
    val digits2: String = ""
)

/** قالب پلاک مورد تأیید کاربر: 67 ایران 123 حرف 45 */
fun formatPlate(p: PlateParts): String {
    val c2 = norm(p.code2).filter { it.isDigit() }.take(2)
    val d3 = norm(p.digits3).filter { it.isDigit() }.take(3)
    val ltr = norm(p.letter)
    val d2 = norm(p.digits2).filter { it.isDigit() }.take(2)
    if (c2.isEmpty() && d3.isEmpty() && ltr.isEmpty() && d2.isEmpty()) return ""
    return buildString {
        if (c2.isNotEmpty()) append(c2)
        append(" ایران ")
        if (d3.isNotEmpty()) append(d3).append(' ')
        if (ltr.isNotEmpty()) append(ltr).append(' ')
        if (d2.isNotEmpty()) append(d2)
    }.trim()
}

fun parsePlate(raw: String): PlateParts {
    val s = norm(raw)
    if (s.isBlank()) return PlateParts()
    val m = Regex("""^(\d{0,2})\s*ایران\s*(\d{0,3})\s*([^\d\s]*)\s*(\d{0,2})$""").find(s)
    if (m != null) {
        return PlateParts(
            code2 = m.groupValues[1],
            digits3 = m.groupValues[2],
            letter = m.groupValues[3],
            digits2 = m.groupValues[4]
        )
    }
    return PlateParts(digits3 = s)
}

/** پلاک موتورسیکلت: قالب ۱۲۳ - ۴۵۶۷۸ (سه رقم + پنج رقم) */
fun isMotoPlate(raw: String): Boolean =
    norm(raw).matches(Regex("""^\d{3}[\s-]?\d{5}$"""))

fun formatMotoPlate(d3: String, d5: String): String {
    val a = norm(d3).filter { it.isDigit() }.take(3)
    val b = norm(d5).filter { it.isDigit() }.take(5)
    if (a.isEmpty() && b.isEmpty()) return ""
    return if (b.isNotEmpty()) "$a $b" else a
}

fun parseMotoPlate(raw: String): Pair<String, String> {
    val s = norm(raw).replace("-", " ").trim()
    if (s.isEmpty()) return "" to ""
    val parts = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when (parts.size) {
        1 -> parts[0].take(3) to parts[0].drop(3).take(5)
        else -> parts[0].take(3) to parts.getOrElse(1) { "" }.take(5)
    }
}

fun cropCenterSquare(bmp: Bitmap): Bitmap {
    val side = minOf(bmp.width, bmp.height)
    if (side == bmp.width && side == bmp.height) return bmp
    val x = (bmp.width - side) / 2
    val y = (bmp.height - side) / 2
    return Bitmap.createBitmap(bmp, x, y, side, side)
}

/** شماره گزارش قفل‌شده با نمایش راست‌به‌چپ: م/14050824/0001 */
fun formatReportNumber(jalaliDate: String, seq: Int): String {
    val compact = norm(jalaliDate).filter { it.isDigit() }.take(8).ifEmpty { J.dateCompact() }
    val num = seq.toString().padStart(4, '0')
    return "\u200Fم/$compact/$num"
}

/** بررسی خطای کد ملی ایران به صورت لحظه‌ای */
fun nationalCodeError(input: String): String? {
    val s = norm(input)
    if (s.isEmpty()) return null
    if (!s.all { it.isDigit() }) return "کد ملی باید فقط شامل عدد باشد"
    if (s.length < 10) return "کد ملی باید ۱۰ رقم باشد (کمتر از حد مجاز)"
    if (s.length > 10) return "کد ملی باید ۱۰ رقم باشد (بیشتر از حد مجاز)"
    if (s.all { it == s[0] }) return "کد ملی نامعتبر است (ارقام تکراری)"
    val d = s.map { it - '0' }
    val sum = (0..8).sumOf { d[it] * (10 - it) }
    val r = sum % 11
    val ok = if (r < 2) d[9] == r else d[9] == 11 - r
    return if (ok) null else "کد ملی با فرمول رسمی مطابقت ندارد"
}

fun validNationalCode(input: String): Boolean = input.isNotBlank() && nationalCodeError(input) == null

/** استاندارد موبایل ایران: ۱۱ رقم با 09 یا ۱۰ رقم با 9 */
fun iranMobileError(input: String): String? {
    val s = norm(input).filter { it.isDigit() }
    if (s.isEmpty()) return null
    return when {
        s.startsWith("09") && s.length == 11 -> null
        s.startsWith("9") && s.length == 10 -> null
        s.length < 10 -> "شماره موبایل کمتر از حد استاندارد است (۱۱ رقم با 09)"
        s.length > 11 -> "شماره موبایل بیشتر از ۱۱ رقم است"
        else -> "فرمت استاندارد موبایل ایران باید با 09 و ۱۱ رقمی باشد"
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes بایت"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "%.1f کیلوبایت".format(Locale.US, kb)
    val mb = kb / 1024.0
    return "%.2f مگابایت".format(Locale.US, mb)
}

object J {
    val MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    )
    val WEEK_DAYS_SHORT = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

    fun toJ(gy: Int, gm: Int, gd: Int): IntArray {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + gdm[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return intArrayOf(jy, jm, jd)
    }

    fun toG(jyInput: Int, jm: Int, jd: Int): IntArray {
        val jy = jyInput + 1595
        var days = -355668 + 365 * jy + (jy / 33) * 8 + ((jy % 33 + 3) / 4) + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days--
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val isLeap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0)
        val salA = intArrayOf(0, 31, if (isLeap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 1
        while (gm <= 12 && gd > salA[gm]) {
            gd -= salA[gm]
            gm++
        }
        return intArrayOf(gy, gm, gd)
    }

    fun isLeap(jy: Int): Boolean {
        val mod = ((jy - 474) % 2820 + 2820) % 2820 + 474
        return ((mod + 38) * 682 % 2816) < 682
    }

    fun daysInMonth(jy: Int, jm: Int): Int = when {
        jm in 1..6 -> 31
        jm in 7..11 -> 30
        jm == 12 -> if (isLeap(jy)) 30 else 29
        else -> 30
    }

    /** ۰=شنبه .. ۶=جمعه */
    fun firstDayOfMonthWeekIndex(jy: Int, jm: Int): Int {
        val g = toG(jy, jm, 1)
        val cal = Calendar.getInstance().apply {
            set(g[0], g[1] - 1, g[2], 12, 0, 0)
        }
        return when (cal[Calendar.DAY_OF_WEEK]) {
            Calendar.SATURDAY -> 0
            Calendar.SUNDAY -> 1
            Calendar.MONDAY -> 2
            Calendar.TUESDAY -> 3
            Calendar.WEDNESDAY -> 4
            Calendar.THURSDAY -> 5
            else -> 6
        }
    }

    fun date(cal: Calendar = Calendar.getInstance()): String {
        val j = toJ(cal[Calendar.YEAR], cal[Calendar.MONTH] + 1, cal[Calendar.DAY_OF_MONTH])
        return "%04d/%02d/%02d".format(Locale.US, j[0], j[1], j[2])
    }

    fun dateCompact(cal: Calendar = Calendar.getInstance()): String =
        date(cal).replace("/", "")

    fun time(cal: Calendar = Calendar.getInstance()): String =
        "%02d:%02d".format(Locale.US, cal[Calendar.HOUR_OF_DAY], cal[Calendar.MINUTE])

    fun parseDate(s: String): Triple<Int, Int, Int>? {
        val parts = norm(s).split("/", "-")
        if (parts.size != 3) return null
        val y = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull()?.coerceIn(1, 12) ?: return null
        val d = parts[2].toIntOrNull()?.coerceIn(1, daysInMonth(y, m)) ?: return null
        return Triple(y, m, d)
    }

    fun ageFromBirthDate(birth: String): Int? {
        val b = parseDate(birth) ?: return null
        val cur = parseDate(date()) ?: return null
        var age = cur.first - b.first
        if (cur.second < b.second || (cur.second == b.second && cur.third < b.third)) age--
        return age.takeIf { it in 0..125 }
    }
}

/** مدیریت کلید رمزنگاری سخت‌افزاری در Android Keystore */
object KeyVault {
    private const val ALIAS = "kashef_master_aes_key"
    private const val PREFS = "sec_vault"

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        val iv = cipher.iv
        val enc = cipher.doFinal(plain)
        return byteArrayOf(iv.size.toByte()) + iv + enc
    }

    fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size < 14) return blob
        val ivLen = blob[0].toInt() and 0xFF
        if (ivLen !in 12..16 || blob.size <= 1 + ivLen) return blob
        val iv = blob.copyOfRange(1, 1 + ivLen)
        val enc = blob.copyOfRange(1 + ivLen, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(enc)
    }

    /** دریافت گذرواژه تصادفی ۳۲ بایتی برای SQLCipher رمزگذاری‌شده با Keystore */
    fun dbPassphrase(c: Context): ByteArray {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = p.getString("dbk", null)
        if (stored != null) {
            return decrypt(Base64.decode(stored, Base64.NO_WRAP))
        }
        val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hexKey = raw.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8)
        val wrapped = Base64.encodeToString(encrypt(hexKey), Base64.NO_WRAP)
        p.edit().putString("dbk", wrapped).apply()
        return hexKey
    }
}

object Img {
    fun dir(c: Context) = File(c.filesDir, "img").apply { mkdirs() }
    fun file(c: Context, n: String) = File(dir(c), n)
    fun delete(c: Context, n: String) {
        file(c, n).delete()
    }

    fun readBytes(c: Context, name: String): ByteArray? {
        val f = file(c, name)
        if (!f.exists()) return null
        return try {
            val raw = f.readBytes()
            if (name.endsWith(".enc") || !(raw.size >= 4 && raw[0] == 'R'.code.toByte() && raw[1] == 'I'.code.toByte())) {
                KeyVault.decrypt(raw)
            } else {
                raw
            }
        } catch (_: Exception) {
            null
        }
    }

    fun writeRawWebp(c: Context, name: String, webpBytes: ByteArray) {
        val enc = KeyVault.encrypt(webpBytes)
        file(c, name).writeBytes(enc)
    }

    fun decodeOrientedBitmap(c: Context, uri: Uri, maxSide: Int = 1280): Bitmap? = try {
        val cr = c.contentResolver
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, b) }
        if (b.outWidth <= 0 || b.outHeight <= 0) null else {
            var s = 1
            while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxSide) s *= 2
            val rawBmp = cr.openInputStream(uri)!!.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            }!!
            val o = try {
                cr.openInputStream(uri)!!.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }
            val m = Matrix()
            when (o) {
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            }
            val sc = maxSide.toFloat() / maxOf(rawBmp.width, rawBmp.height)
            if (sc < 1f) m.postScale(sc, sc)
            val bmp = Bitmap.createBitmap(rawBmp, 0, 0, rawBmp.width, rawBmp.height, m, true)
            if (bmp !== rawBmp) rawBmp.recycle()
            bmp
        }
    } catch (_: Exception) {
        null
    }

    fun saveBitmap(c: Context, bmp: Bitmap, q: Int = 65): String? = try {
        val name = UUID.randomUUID().toString().take(12) + ".webp"
        @Suppress("DEPRECATION")
        val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        val baos = ByteArrayOutputStream()
        bmp.compress(fmt, q, baos)
        writeRawWebp(c, name, baos.toByteArray())
        name
    } catch (_: Exception) {
        null
    }

    fun save(c: Context, uri: Uri, maxSide: Int = 1280, q: Int = 65): String? {
        val bmp = decodeOrientedBitmap(c, uri, maxSide) ?: return null
        return try {
            saveBitmap(c, bmp, q)
        } finally {
            bmp.recycle()
        }
    }

    fun thumb(c: Context, name: String, max: Int): Bitmap? {
        val data = readBytes(c, name) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2
        return BitmapFactory.decodeByteArray(
            data,
            0,
            data.size,
            BitmapFactory.Options().apply { inSampleSize = s }
        )
    }

    fun totalSize(c: Context): Pair<Int, Long> {
        val files = dir(c).listFiles() ?: return 0 to 0L
        return files.size to files.sumOf { it.length() }
    }
}

@Serializable
data class SecurityQuestionItem(
    val question: String,
    val saltB64: String,
    val answerHash: String
)

enum class UnlockResult { INVALID, REAL, DECOY }

object Auth {
    private fun p(c: Context) = c.getSharedPreferences("sec", Context.MODE_PRIVATE)
    fun hasPin(c: Context) = p(c).contains("h")

    private fun hash(secret: String, salt: ByteArray): String {
        val spec = PBEKeySpec(norm(secret).lowercase(Locale.ROOT).toCharArray(), salt, 60000, 256)
        return Base64.encodeToString(
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded,
            Base64.NO_WRAP
        )
    }

    fun setPin(c: Context, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p(c).edit()
            .putString("s", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("h", hash(pin, salt))
            .apply()
    }

    fun setDecoyPin(c: Context, pin: String?) {
        val ed = p(c).edit()
        if (pin.isNullOrEmpty()) {
            ed.remove("ds").remove("dh")
        } else {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            ed.putString("ds", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("dh", hash(pin, salt))
        }
        ed.apply()
    }

    fun hasDecoyPin(c: Context) = p(c).contains("dh")

    fun check(c: Context, pin: String, decoyEnabled: Boolean = false): UnlockResult {
        val saltStr = p(c).getString("s", "") ?: ""
        if (saltStr.isNotEmpty()) {
            val salt = Base64.decode(saltStr, Base64.NO_WRAP)
            if (hash(pin, salt) == p(c).getString("h", "")) return UnlockResult.REAL
        }
        if (decoyEnabled) {
            val dSaltStr = p(c).getString("ds", "") ?: ""
            if (dSaltStr.isNotEmpty()) {
                val dSalt = Base64.decode(dSaltStr, Base64.NO_WRAP)
                if (hash(pin, dSalt) == p(c).getString("dh", "")) return UnlockResult.DECOY
            }
        }
        return UnlockResult.INVALID
    }

    // ---- بازیابی رمز با سوالات امنیتی و کد بازیابی اضطراری (بدون ایمیل و شماره) ----
    fun setSecurityQuestions(c: Context, qa: List<Pair<String, String>>) {
        val items = qa.filter { it.first.isNotBlank() && it.second.isNotBlank() }.map { (q, a) ->
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            SecurityQuestionItem(q.trim(), Base64.encodeToString(salt, Base64.NO_WRAP), hash(a, salt))
        }
        p(c).edit().putString("sq", Backup.json.encodeToString(items)).apply()
    }

    fun getSecurityQuestions(c: Context): List<SecurityQuestionItem> {
        val raw = p(c).getString("sq", null) ?: return emptyList()
        return runCatching { Backup.json.decodeFromString<List<SecurityQuestionItem>>(raw) }
            .getOrDefault(emptyList())
    }

    fun verifySecurityAnswers(c: Context, answers: List<String>): Boolean {
        val qs = getSecurityQuestions(c)
        if (qs.isEmpty() || answers.size != qs.size) return false
        return qs.indices.all { i ->
            val salt = Base64.decode(qs[i].saltB64, Base64.NO_WRAP)
            hash(answers[i], salt) == qs[i].answerHash
        }
    }

    fun generateRecoveryCode(c: Context): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        val raw = (1..16).map { chars[rnd.nextInt(chars.length)] }.joinToString("")
        val formatted = raw.chunked(4).joinToString("-")
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        p(c).edit()
            .putString("rs", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("rh", hash(raw, salt))
            .apply()
        return formatted
    }

    fun hasRecoveryCode(c: Context) = p(c).contains("rh")

    fun verifyRecoveryCode(c: Context, input: String): Boolean {
        val clean = norm(input).uppercase(Locale.ROOT).replace("-", "").replace(" ", "")
        val saltStr = p(c).getString("rs", "") ?: return false
        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        return hash(clean, salt) == p(c).getString("rh", "")
    }

    fun bioAvailable(c: Context) = BiometricManager.from(c)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(a: FragmentActivity, onOk: () -> Unit) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("ورود به کاشف")
            .setSubtitle("اثر انگشت خود را روی حسگر قرار دهید")
            .setNegativeButtonText("استفاده از رمز")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                onOk()
            }
        }).authenticate(info)
    }
}

object Loc {
    @SuppressLint("MissingPermission")
    fun get(c: Context, cb: (Location?) -> Unit) {
        val lm = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val p = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { lm.isProviderEnabled(it) }
        if (p == null) {
            cb(null)
            return
        }
        LocationManagerCompat.getCurrentLocation(
            lm,
            p,
            null as CancellationSignal?,
            ContextCompat.getMainExecutor(c)
        ) { l ->
            cb(l ?: lm.getLastKnownLocation(p))
        }
    }
}

/** تولید فایل اکسل واقعی اندیکاتور (.xlsx) با چیدمان راست‌به‌چپ */
object ExcelExporter {
    private fun esc(s: String): String = buildString {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') append(ch)
            }
        }
    }

    private fun colLetter(idx: Int): String {
        var n = idx + 1
        val sb = StringBuilder()
        while (n > 0) {
            val r = (n - 1) % 26
            sb.insert(0, ('A' + r))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    fun exportIndicator(reports: List<Report>, out: OutputStream) {
        val headers = listOf(
            "ردیف", "شماره گزارش", "تاریخ گزارش", "ساعت گزارش",
            "تاریخ وقوع", "ساعت وقوع", "نوع گزارش", "نحوه اعلام",
            "محل / آدرس", "مختصات GPS", "تعداد افراد", "اسامی و کد ملی افراد",
            "پلاک خودروها", "مأموران حاضر و خودروها", "تعداد پیوست", "خلاصه شرح و اقدامات"
        )
        val rows = ArrayList<List<String>>()
        rows.add(headers)
        reports.forEachIndexed { idx, r ->
            val people = r.suspects.joinToString(" | ") { s ->
                "${s.firstName} ${s.lastName}".trim() +
                    (if (s.nationalCode.isNotEmpty()) " (${s.nationalCode})" else "")
            }
            val plates = r.vehicles.mapNotNull { it.plate.ifBlank { null } }.joinToString(" | ")
            val officers = buildString {
                if (r.sharedOfficerVehicle.isNotBlank()) append("خودرو مشترک: ${r.sharedOfficerVehicle} | ")
                append(
                    r.officers.joinToString(" | ") { o ->
                        o.name + (if (o.vehicle.isNotBlank()) " (${o.vehicle})" else "")
                    }
                )
            }
            val coords = if (r.lat != null && r.lng != null) "%.6f, %.6f".format(Locale.US, r.lat, r.lng) else ""
            val summary = listOf(r.description, r.initialActions).filter { it.isNotBlank() }.joinToString(" — ")
            rows.add(
                listOf(
                    (idx + 1).toString(),
                    r.number,
                    r.reportDate,
                    r.reportTime,
                    r.incidentDate,
                    r.incidentTime,
                    r.type,
                    r.method,
                    r.location,
                    coords,
                    r.suspects.size.toString(),
                    people,
                    plates,
                    officers,
                    r.images().size.toString(),
                    summary
                )
            )
        }

        val sheetXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            append("""<sheetViews><sheetView workbookViewId="0" rightToLeft="1"/></sheetViews>""")
            append("<sheetData>")
            rows.forEachIndexed { rIdx, cols ->
                val rNum = rIdx + 1
                append("""<row r="$rNum">""")
                cols.forEachIndexed { cIdx, valStr ->
                    val ref = "${colLetter(cIdx)}$rNum"
                    val style = if (rIdx == 0) " s=\"1\"" else ""
                    append("""<c r="$ref" t="inlineStr"$style><is><t>${esc(valStr)}</t></is></c>""")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="B Nazanin"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="B Titr"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF1E4078"/></patternFill></fill>
  </fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""

        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

        val relsRoot = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets><sheet name="اندیکاتور گزارش‌ها" sheetId="1" r:id="rId1"/></sheets>
</workbook>"""

        val workbookRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

        ZipOutputStream(out.buffered()).use { z ->
            fun put(name: String, xml: String) {
                z.putNextEntry(ZipEntry(name))
                z.write(xml.toByteArray(Charsets.UTF_8))
                z.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", relsRoot)
            put("xl/workbook.xml", workbookXml)
            put("xl/_rels/workbook.xml.rels", workbookRels)
            put("xl/styles.xml", stylesXml)
            put("xl/worksheets/sheet1.xml", sheetXml)
        }
    }
}

@Serializable
data class BackupPayload(
    val version: Int = 2,
    val reports: List<Report> = emptyList(),
    val suspicious: List<SuspiciousEntry> = emptyList(),
    val peopleDb: List<PersonRecord> = emptyList(),
    val settings: AppSettings? = null
)

object Backup {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun export(c: Context, db: Db, out: OutputStream, password: String? = null) {
        val payload = BackupPayload(
            version = 2,
            reports = db.allReports(),
            suspicious = db.allSuspicious(),
            peopleDb = db.allPeople(),
            settings = db.getSettings()
        )
        val staging = File(c.cacheDir, "bk_${UUID.randomUUID()}").apply { mkdirs() }
        val tmpZip = File(c.cacheDir, "bk_${UUID.randomUUID()}.zip")
        try {
            File(staging, "data.json").writeText(json.encodeToString(payload), Charsets.UTF_8)
            val imgDir = File(staging, "img").apply { mkdirs() }
            Img.dir(c).listFiles()?.forEach { f ->
                val raw = Img.readBytes(c, f.name)
                if (raw != null) File(imgDir, f.name).writeBytes(raw)
            }
            val zf = if (!password.isNullOrBlank()) ZipFile(tmpZip, password.toCharArray()) else ZipFile(tmpZip)
            val params = ZipParameters().apply {
                if (!password.isNullOrBlank()) {
                    isEncryptFiles = true
                    encryptionMethod = EncryptionMethod.AES
                    aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                }
            }
            zf.addFile(File(staging, "data.json"), params)
            if (imgDir.listFiles()?.isNotEmpty() == true) {
                zf.addFolder(imgDir, params)
            }
            tmpZip.inputStream().use { it.copyTo(out) }
        } finally {
            staging.deleteRecursively()
            tmpZip.delete()
        }
    }

    fun import(c: Context, db: Db, inp: InputStream, password: String? = null): Triple<Int, Int, Int> {
        val tmpZip = File(c.cacheDir, "imp_${UUID.randomUUID()}.zip")
        val extDir = File(c.cacheDir, "imp_ext_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            tmpZip.outputStream().use { inp.copyTo(it) }
            val zf = if (!password.isNullOrBlank()) ZipFile(tmpZip, password.toCharArray()) else ZipFile(tmpZip)
            zf.extractAll(extDir.path)

            val dataFile = File(extDir, "data.json")
            var repCount = 0
            var suspCount = 0
            var pplCount = 0

            if (dataFile.exists()) {
                val txt = dataFile.readText(Charsets.UTF_8).trim()
                if (txt.startsWith("[")) {
                    // سازگاری با بکاپ نسخه ۱
                    val reports = json.decodeFromString<List<Report>>(txt)
                    reports.forEach { db.saveReport(it) }
                    repCount = reports.size
                } else {
                    val payload = json.decodeFromString<BackupPayload>(txt)
                    payload.reports.forEach { db.saveReport(it) }
                    payload.suspicious.forEach { db.saveSuspicious(it) }
                    payload.peopleDb.forEach { db.savePerson(it) }
                    payload.settings?.let { db.saveSettings(it) }
                    repCount = payload.reports.size
                    suspCount = payload.suspicious.size
                    pplCount = payload.peopleDb.size
                }
            }
            File(extDir, "img").listFiles()?.forEach { f ->
                if (f.isFile) Img.writeRawWebp(c, f.name, f.readBytes())
            }
            return Triple(repCount, suspCount, pplCount)
        } finally {
            tmpZip.delete()
            extDir.deleteRecursively()
        }
    }

    /** ورود دسته‌ای افراد از CSV (کد ملی، نام، نام خانوادگی، نام پدر، تاریخ تولد، جنسیت، تلفن، آدرس) */
    fun importPeopleCsv(inp: InputStream, db: Db): Int {
        var count = 0
        val lines = inp.bufferedReader(Charsets.UTF_8).readLines()
        lines.forEachIndexed { idx, raw ->
            val line = raw.removePrefix("\uFEFF").trim()
            if (line.isEmpty()) return@forEachIndexed
            val cols = line.split(',', ';', '\t').map { it.trim().removeSurrounding("\"") }
            val code = norm(cols.getOrElse(0) { "" }).filter { it.isDigit() }
            if (idx == 0 && (code.isEmpty() || cols[0].contains("کد"))) return@forEachIndexed
            if (code.length != 10) return@forEachIndexed
            val existing = db.findPersonInDb(code)
            val rec = PersonRecord(
                nationalCode = code,
                firstName = cols.getOrElse(1) { existing?.firstName ?: "" },
                lastName = cols.getOrElse(2) { existing?.lastName ?: "" },
                fatherName = cols.getOrElse(3) { existing?.fatherName ?: "" },
                birthDate = norm(cols.getOrElse(4) { existing?.birthDate ?: "" }),
                gender = cols.getOrElse(5) { existing?.gender ?: "" },
                phone = norm(cols.getOrElse(6) { existing?.phone ?: "" }),
                address = cols.getOrElse(7) { existing?.address ?: "" },
                job = cols.getOrElse(8) { existing?.job ?: "" },
                photoName = existing?.photoName ?: "",
                faceEmbedding = existing?.faceEmbedding ?: emptyList(),
                updatedAt = System.currentTimeMillis()
            )
            db.savePerson(rec)
            count++
        }
        return count
    }

    fun importPeopleJson(inp: InputStream, db: Db): Int {
        val list = json.decodeFromString<List<PersonRecord>>(inp.bufferedReader(Charsets.UTF_8).readText())
        var count = 0
        list.forEach { p ->
            val code = norm(p.nationalCode).filter { it.isDigit() }
            if (code.length == 10) {
                db.savePerson(p.copy(nationalCode = code, updatedAt = System.currentTimeMillis()))
                count++
            }
        }
        return count
    }

    /** قرار دادن PDF در فایل ZIP رمزدار برای اشتراک امن */
    fun wrapPdfInEncryptedZip(c: Context, pdfFile: File, password: String): File {
        val outZip = File(c.cacheDir, "pdf/${pdfFile.nameWithoutExtension}.zip")
        if (outZip.exists()) outZip.delete()
        val zf = if (password.isNotBlank()) ZipFile(outZip, password.toCharArray()) else ZipFile(outZip)
        val params = ZipParameters().apply {
            if (password.isNotBlank()) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }
        zf.addFile(pdfFile, params)
        return outZip
    }
}

/** مدیریت آیکون‌های قابل تعویض برنامه و آیکون دلخواه کاربر */
object IconManager {
    val CHOICES = listOf("kashef1", "kashef2", "kashef3", "kashef4")

    fun aliasFor(choice: String): String = when (choice) {
        "kashef2" -> ".Icon2"
        "kashef3" -> ".Icon3"
        "kashef4" -> ".Icon4"
        else -> ".Icon1"
    }

    /** فعال‌سازی آیکون انتخاب‌شده و غیرفعال‌سازی بقیه */
    fun applyIcon(context: Context, choice: String) {
        val pm = context.packageManager
        val pkg = context.packageName
        CHOICES.forEach { key ->
            val state = if (key == choice) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                android.content.ComponentName(pkg, pkg + aliasFor(key)),
                state,
                PackageManager.DONT_KILL_APP
            )
        }
    }

    fun launchIntentFor(context: Context, choice: String): Intent {
        val alias = context.packageName + aliasFor(choice)
        return Intent(Intent.ACTION_MAIN).apply {
            setClassName(context, alias)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * ساخت میانبر با عکس دلخواه کاربر (تنها روش رسمی اندروید برای آیکون سفارشی).
     * این میانبر به‌عنوان یک آیکون «کاشف» جدا در صفحه اصلی گوشی نمایش داده می‌شود.
     */
    fun setCustomIcon(context: Context, bitmap: Bitmap, choice: String) {
        if (Build.VERSION.SDK_INT < 25) return
        val scaled = if (bitmap.width != 192 || bitmap.height != 192) {
            Bitmap.createScaledBitmap(bitmap, 192, 192, true)
        } else {
            bitmap
        }
        val shortcut = ShortcutInfo.Builder(context, "kashef_custom")
            .setShortLabel("کاشف")
            .setIcon(Icon.createWithBitmap(scaled))
            .setIntent(launchIntentFor(context, choice))
            .build()
        context.getSystemService(ShortcutManager::class.java)?.setDynamicShortcuts(listOf(shortcut))
        if (scaled !== bitmap) scaled.recycle()
    }

    fun clearCustomIcon(context: Context) {
        if (Build.VERSION.SDK_INT < 25) return
        context.getSystemService(ShortcutManager::class.java)
            ?.removeDynamicShortcuts(listOf("kashef_custom"))
    }
}

/** خروجی نقشه نقاط آلوده به‌صورت عکس (PNG) و PDF قابل دانلود و چاپ */
object MapExporter {
    fun capture(map: MapView?): Bitmap? {
        if (map == null || map.width <= 0 || map.height <= 0) return null
        val bmp = Bitmap.createBitmap(map.width, map.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        map.draw(canvas)
        return bmp
    }

    private fun drawLegend(canvas: Canvas, width: Int, height: Int, legend: List<Pair<String, Int>>) {
        if (legend.isEmpty()) return
        val rowH = 36f
        val boxW = 320f
        val boxH = 34f + legend.size * rowH
        val left = width - boxW - 16f
        val top = height - boxH - 16f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(238, 255, 255, 255)
        }
        canvas.drawRoundRect(RectF(left, top, left + boxW, top + boxH), 16f, 16f, bg)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 25f
            color = android.graphics.Color.DKGRAY
            typeface = Typeface.DEFAULT
            textAlign = Paint.Align.RIGHT
        }
        var y = top + 36f
        legend.forEach { (type, cnt) ->
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = reportTypeColorHex(type).toInt()
            }
            canvas.drawCircle(left + boxW - 26f, y - 9f, 10f, dotPaint)
            canvas.drawText("$type ($cnt)", left + boxW - 48f, y, textPaint)
            y += rowH
        }
    }

    fun exportPng(context: Context, map: MapView?, legend: List<Pair<String, Int>>): Boolean {
        val bmp = capture(map) ?: return false
        val canvas = Canvas(bmp)
        drawLegend(canvas, bmp.width, bmp.height, legend)
        val name = "kashef-map-${J.dateCompact()}-${System.currentTimeMillis() % 100000}.png"
        if (Build.VERSION.SDK_INT >= 29 && saveToPictures(context, bmp, name)) {
            bmp.recycle()
            return true
        }
        val dir = File(context.cacheDir, "maps").apply { mkdirs() }
        val out = File(dir, name)
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        shareFile(context, out, "image/png", "اشتراک / ذخیره عکس نقشه")
        return true
    }

    fun exportPdf(context: Context, map: MapView?, legend: List<Pair<String, Int>>, satellite: Boolean): Boolean {
        val bmp = capture(map) ?: return false
        val doc = PdfDocument()
        val pageW = 842
        val pageH = 595
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, 1).create())
        val canvas = page.canvas

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            color = android.graphics.Color.rgb(20, 46, 92)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("نقشه نقاط آلوده — سامانه کاشف", pageW - 24f, 36f, titlePaint)
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            color = android.graphics.Color.DKGRAY
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "تاریخ تهیه: ${J.date()} ${J.time()} • حالت نقشه: ${if (satellite) "ماهواره‌ای" else "جاده"}",
            pageW - 24f, 60f, subPaint
        )

        val imgTop = 74f
        val imgH = pageH - imgTop - 20f
        val scale = minOf((pageW - 48f) / bmp.width, imgH / bmp.height)
        val dw = bmp.width * scale
        val dh = bmp.height * scale
        val dx = (pageW - dw) / 2f
        val dy = imgTop + (imgH - dh) / 2f
        canvas.drawBitmap(bmp, null, RectF(dx, dy, dx + dw, dy + dh), null)

        canvas.save()
        canvas.translate(dx, dy)
        canvas.scale(dw / bmp.width, dh / bmp.height)
        drawLegend(canvas, bmp.width, bmp.height, legend)
        canvas.restore()

        doc.finishPage(page)
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val out = File(dir, "kashef-map-${J.dateCompact()}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        bmp.recycle()
        shareFile(context, out, "application/pdf", "اشتراک / چاپ PDF نقشه")
        return true
    }

    private fun saveToPictures(context: Context, bmp: Bitmap, name: String): Boolean {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kashef")
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            resolver.openOutputStream(uri)?.use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            } ?: return false
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun shareFile(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
