package ir.mylog.missionlog

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File

data class AutoFillMatch(
    val sourceLabel: String,
    val fromInitialDb: Boolean,
    val firstName: String,
    val lastName: String,
    val fatherName: String,
    val birthDate: String,
    val gender: String,
    val job: String,
    val address: String,
    val phone: String,
    val photo: PhotoItem?
)

class Db(private val ctx: Context) {
    companion object {
        @Volatile
        private var loaded = false
        private fun ensureLoaded() {
            if (!loaded) {
                synchronized(this) {
                    if (!loaded) {
                        System.loadLibrary("sqlcipher")
                        loaded = true
                    }
                }
            }
        }
    }

    private val helper: SupportSQLiteOpenHelper by lazy {
        ensureLoaded()
        val passphrase = KeyVault.dbPassphrase(ctx)
        val factory = SupportOpenHelperFactory(passphrase)
        val cfg = SupportSQLiteOpenHelper.Configuration.builder(ctx)
            .name("kashef_enc.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS reports(" +
                            "id TEXT PRIMARY KEY, seq INTEGER, num TEXT, created INTEGER, " +
                            "date_str TEXT, rtype TEXT, summ TEXT, codes TEXT, names TEXT, " +
                            "plates TEXT, phones TEXT, json TEXT)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS i_rep_created ON reports(created)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS i_rep_seq ON reports(seq)")

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS suspicious(" +
                            "id TEXT PRIMARY KEY, created INTEGER, date_str TEXT, summ TEXT, " +
                            "codes TEXT, names TEXT, plates TEXT, phones TEXT, json TEXT)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS i_susp_created ON suspicious(created)")

                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS people_db(" +
                            "national_code TEXT PRIMARY KEY, first_name TEXT, last_name TEXT, " +
                            "father_name TEXT, birth_date TEXT, gender TEXT, phone TEXT, " +
                            "updated_at INTEGER, json TEXT)"
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS i_ppl_name ON people_db(first_name, last_name)")

                    db.execSQL("CREATE TABLE IF NOT EXISTS drafts(id TEXT PRIMARY KEY, updated_at INTEGER, json TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS settings(id TEXT PRIMARY KEY, json TEXT)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    onCreate(db)
                }
            })
            .build()
        factory.create(cfg)
    }

    private val w: SupportSQLiteDatabase get() = helper.writableDatabase
    private val r: SupportSQLiteDatabase get() = helper.readableDatabase

    // ---------------- Reports ----------------
    fun saveReport(rep: Report) {
        val names = rep.suspects.map { norm("${it.firstName} ${it.lastName}") }.filter { it.isNotEmpty() }
        val phones = (
            rep.suspects.map { norm(it.phone) } +
                rep.statementPhones.map { norm(it.phone) }
            ).filter { it.isNotEmpty() }
        val plates = (
            rep.vehicles.map { plateKey(it.plate) } +
                rep.statementVehicles.map { plateKey(it.plate) } +
                listOf(plateKey(rep.sharedOfficerVehicle)) +
                rep.officers.map { plateKey(it.vehicle) }
            ).filter { it.isNotEmpty() }

        val cv = ContentValues().apply {
            put("id", rep.id)
            put("seq", rep.seq)
            put("num", rep.number)
            put("created", rep.createdAt)
            put("date_str", "${rep.incidentDate} ${rep.incidentTime}".trim())
            put("rtype", rep.type)
            put("summ", rep.type.ifBlank { "گزارش" } + (if (names.isNotEmpty()) " • " + names.joinToString("، ") else ""))
            put("codes", rep.suspects.joinToString(" ") { norm(it.nationalCode) })
            put("names", names.joinToString(" | "))
            put("plates", plates.joinToString(" "))
            put("phones", phones.joinToString(" "))
            put("json", Backup.json.encodeToString(rep))
        }
        w.insert("reports", SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    fun getReport(id: String): Report? =
        r.query("SELECT json FROM reports WHERE id=?", arrayOf<Any>(id)).use { c ->
            if (c.moveToFirst()) Backup.json.decodeFromString<Report>(c.getString(0)) else null
        }

    fun allReports(): List<Report> {
        val out = ArrayList<Report>()
        r.query("SELECT json FROM reports ORDER BY created DESC").use { c ->
            while (c.moveToNext()) out.add(Backup.json.decodeFromString(c.getString(0)))
        }
        return out
    }

    fun listReportRows(): List<RowItem> {
        val out = ArrayList<RowItem>()
        r.query("SELECT id, num, date_str, summ FROM reports ORDER BY created DESC").use { c ->
            while (c.moveToNext()) {
                out.add(RowItem(c.getString(0), c.getString(1), c.getString(2), c.getString(3), SearchSource.REPORT))
            }
        }
        return out
    }

    fun deleteReport(id: String) {
        w.delete("reports", "id=?", arrayOf<Any>(id))
    }

    fun reportCount(): Int =
        r.query("SELECT COUNT(*) FROM reports").use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    /** شمارنده ترتیبی پیوسته بدون صفر شدن سالانه مطابق انتخاب کاربر */
    fun nextSequenceAndNumber(jalaliDate: String): Pair<Int, String> {
        val maxSeq = r.query("SELECT COALESCE(MAX(seq), 0) FROM reports").use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        var next = maxOf(maxSeq + 1, reportCount() + 1)
        while (true) {
            val candidate = formatReportNumber(jalaliDate, next)
            val exists = r.query("SELECT 1 FROM reports WHERE num=?", arrayOf<Any>(candidate)).use { it.moveToFirst() }
            if (!exists) return next to candidate
            next++
        }
    }

    // ---------------- Suspicious Registry ----------------
    fun saveSuspicious(s: SuspiciousEntry) {
        val pName = s.person?.let { norm("${it.firstName} ${it.lastName}") }.orEmpty()
        val pCode = s.person?.nationalCode?.let { norm(it) }.orEmpty()
        val pPhone = s.person?.phone?.let { norm(it) }.orEmpty()
        val plates = s.vehicles.map { plateKey(it.plate) }.filter { it.isNotEmpty() }.joinToString(" ")
        val summ = buildString {
            if (pName.isNotEmpty()) append(pName)
            if (s.vehicles.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append(s.vehicles.joinToString("، ") { it.plate.ifBlank { it.type.ifBlank { "خودرو" } } })
            }
            if (s.reason.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(s.reason)
            }
            if (isEmpty()) append("مورد مشکوک")
        }
        val cv = ContentValues().apply {
            put("id", s.id)
            put("created", s.createdAt)
            put("date_str", s.date)
            put("summ", summ)
            put("codes", pCode)
            put("names", pName)
            put("plates", plates)
            put("phones", pPhone)
            put("json", Backup.json.encodeToString(s))
        }
        w.insert("suspicious", SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    fun getSuspicious(id: String): SuspiciousEntry? =
        r.query("SELECT json FROM suspicious WHERE id=?", arrayOf<Any>(id)).use { c ->
            if (c.moveToFirst()) runCatching { Backup.json.decodeFromString<SuspiciousEntry>(c.getString(0)) }.getOrNull() else null
        }

    fun allSuspicious(): List<SuspiciousEntry> {
        val out = ArrayList<SuspiciousEntry>()
        r.query("SELECT json FROM suspicious ORDER BY created DESC").use { c ->
            while (c.moveToNext()) {
                runCatching { out.add(Backup.json.decodeFromString<SuspiciousEntry>(c.getString(0))) }
            }
        }
        return out
    }

    fun deleteSuspicious(id: String) {
        w.delete("suspicious", "id=?", arrayOf<Any>(id))
    }

    fun suspiciousCount(): Int =
        r.query("SELECT COUNT(*) FROM suspicious").use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    // ---------------- Initial People DB ----------------
    fun savePerson(p: PersonRecord) {
        val code = norm(p.nationalCode).filter { it.isDigit() }
        if (code.length != 10) return
        val normalized = p.copy(nationalCode = code)
        val cv = ContentValues().apply {
            put("national_code", code)
            put("first_name", norm(normalized.firstName))
            put("last_name", norm(normalized.lastName))
            put("father_name", norm(normalized.fatherName))
            put("birth_date", norm(normalized.birthDate))
            put("gender", normalized.gender)
            put("phone", norm(normalized.phone))
            put("updated_at", normalized.updatedAt)
            put("json", Backup.json.encodeToString(normalized))
        }
        w.insert("people_db", SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    fun findPersonInDb(code: String): PersonRecord? {
        val clean = norm(code).filter { it.isDigit() }
        if (clean.length != 10) return null
        return r.query("SELECT json FROM people_db WHERE national_code=?", arrayOf<Any>(clean)).use { c ->
            if (c.moveToFirst()) Backup.json.decodeFromString<PersonRecord>(c.getString(0)) else null
        }
    }

    fun allPeople(): List<PersonRecord> {
        val out = ArrayList<PersonRecord>()
        r.query("SELECT json FROM people_db ORDER BY updated_at DESC").use { c ->
            while (c.moveToNext()) out.add(Backup.json.decodeFromString(c.getString(0)))
        }
        return out
    }

    fun searchPeopleDb(q: String, limit: Int = 80): List<PersonRecord> {
        val s = norm(q)
        if (s.isEmpty()) {
            val out = ArrayList<PersonRecord>()
            r.query("SELECT json FROM people_db ORDER BY updated_at DESC LIMIT ?", arrayOf<Any>(limit)).use { c ->
                while (c.moveToNext()) out.add(Backup.json.decodeFromString(c.getString(0)))
            }
            return out
        }
        val tokens = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val where = tokens.joinToString(" AND ") {
            "(national_code LIKE ? OR first_name LIKE ? OR last_name LIKE ? OR phone LIKE ?)"
        }
        val args = (tokens.flatMap { t -> List(4) { "%$t%" } } + limit).toTypedArray<Any>()
        val out = ArrayList<PersonRecord>()
        r.query("SELECT json FROM people_db WHERE $where ORDER BY updated_at DESC LIMIT ?", args).use { c ->
            while (c.moveToNext()) out.add(Backup.json.decodeFromString(c.getString(0)))
        }
        return out
    }

    fun deletePerson(code: String) {
        w.delete("people_db", "national_code=?", arrayOf<Any>(norm(code)))
    }

    fun peopleCount(): Int =
        r.query("SELECT COUNT(*) FROM people_db").use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    // ---------------- Auto-fill Lookup by National Code ----------------
    fun lookupByNationalCode(code: String): AutoFillMatch? {
        val clean = norm(code).filter { it.isDigit() }
        if (clean.length != 10) return null

        findPersonInDb(clean)?.let { p ->
            return AutoFillMatch(
                sourceLabel = "دیتابیس اولیه افراد",
                fromInitialDb = true,
                firstName = p.firstName,
                lastName = p.lastName,
                fatherName = p.fatherName,
                birthDate = p.birthDate,
                gender = p.gender,
                job = p.job,
                address = p.address,
                phone = p.phone,
                photo = p.photoName.takeIf { it.isNotEmpty() }?.let { PhotoItem(it, "تصویر دیتابیس") }
            )
        }

        r.query("SELECT json, num FROM reports WHERE codes LIKE ? ORDER BY created DESC LIMIT 1", arrayOf("%$clean%")).use { c ->
            if (c.moveToFirst()) {
                val rep = runCatching { Backup.json.decodeFromString<Report>(c.getString(0)) }.getOrNull()
                val s = rep?.suspects?.firstOrNull { norm(it.nationalCode) == clean }
                if (rep != null && s != null) {
                    return AutoFillMatch(
                        sourceLabel = "سابقه گزارش ${rep.number}",
                        fromInitialDb = false,
                        firstName = s.firstName,
                        lastName = s.lastName,
                        fatherName = s.fatherName,
                        birthDate = s.birthDate,
                        gender = s.gender,
                        job = s.job,
                        address = s.address,
                        phone = s.phone,
                        photo = s.photo
                    )
                }
            }
        }

        r.query("SELECT json FROM suspicious WHERE codes LIKE ? ORDER BY created DESC LIMIT 1", arrayOf("%$clean%")).use { c ->
            if (c.moveToFirst()) {
                val susp = runCatching { Backup.json.decodeFromString<SuspiciousEntry>(c.getString(0)) }.getOrNull()
                val s = susp?.person
                if (s != null && norm(s.nationalCode) == clean) {
                    return AutoFillMatch(
                        sourceLabel = "فهرست افراد مشکوک",
                        fromInitialDb = false,
                        firstName = s.firstName,
                        lastName = s.lastName,
                        fatherName = s.fatherName,
                        birthDate = s.birthDate,
                        gender = s.gender,
                        job = s.job,
                        address = s.address,
                        phone = s.phone,
                        photo = s.photo
                    )
                }
            }
        }
        return null
    }

    // ---------------- Unified Search (mode: 0=کد ملی, 1=نام, 2=پلاک, 3=تلفن) ----------------
    fun unifiedSearch(mode: Int, q: String): List<SearchMatch> {
        val s = norm(q)
        if (s.isEmpty()) return emptyList()
        val results = ArrayList<SearchMatch>()

        fun buildWhere(): Pair<String, Array<String>>? = when (mode) {
            0 -> "codes LIKE ?" to arrayOf("%$s%")
            1 -> {
                val tokens = s.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (tokens.isEmpty()) null
                else tokens.joinToString(" AND ") { "names LIKE ?" } to tokens.map { "%$it%" }.toTypedArray()
            }
            2 -> {
                val pk = plateKey(s)
                if (pk.isEmpty()) null else "plates LIKE ?" to arrayOf("%$pk%")
            }
            else -> {
                val ph = s.filter { it.isDigit() }
                if (ph.isEmpty()) null else "phones LIKE ?" to arrayOf("%$ph%")
            }
        }

        val pair = buildWhere() ?: return emptyList()
        val (where, args) = pair

        r.query("SELECT id, num, date_str, summ FROM reports WHERE $where ORDER BY created DESC", args).use { c ->
            while (c.moveToNext()) {
                results.add(
                    SearchMatch(
                        id = "rep_${c.getString(0)}",
                        title = "گزارش ${c.getString(1)} • ${c.getString(2)}",
                        subtitle = c.getString(3),
                        sources = setOf(SearchSource.REPORT),
                        reportId = c.getString(0)
                    )
                )
            }
        }

        r.query("SELECT id, date_str, summ FROM suspicious WHERE $where ORDER BY created DESC", args).use { c ->
            while (c.moveToNext()) {
                results.add(
                    SearchMatch(
                        id = "susp_${c.getString(0)}",
                        title = "مشکوک • ${c.getString(1)}",
                        subtitle = c.getString(2),
                        sources = setOf(SearchSource.SUSPICIOUS),
                        suspiciousId = c.getString(0)
                    )
                )
            }
        }

        if (mode in listOf(0, 1, 3)) {
            searchPeopleDb(s, 40).forEach { p ->
                results.add(
                    SearchMatch(
                        id = "ppl_${p.nationalCode}",
                        title = "${p.firstName} ${p.lastName}".trim().ifEmpty { p.nationalCode } + " • کد ملی: ${p.nationalCode}",
                        subtitle = listOfNotNull(
                            p.fatherName.takeIf { it.isNotBlank() }?.let { "فرزند $it" },
                            p.birthDate.takeIf { it.isNotBlank() }?.let { "تولد $it" },
                            p.phone.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        sources = setOf(SearchSource.PEOPLE_DB),
                        personCode = p.nationalCode
                    )
                )
            }
        }

        return results
    }

    // ---------------- Auto-Save Draft ----------------
    fun saveDraft(rep: Report) {
        val cv = ContentValues().apply {
            put("id", "current")
            put("updated_at", System.currentTimeMillis())
            put("json", Backup.json.encodeToString(rep))
        }
        w.insert("drafts", SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    fun loadDraft(): Report? =
        r.query("SELECT json FROM drafts WHERE id='current'").use { c ->
            if (c.moveToFirst()) runCatching { Backup.json.decodeFromString<Report>(c.getString(0)) }.getOrNull() else null
        }

    fun clearDraft() {
        w.delete("drafts", "id='current'", null)
    }

    // ---------------- Settings ----------------
    fun getSettings(): AppSettings =
        r.query("SELECT json FROM settings WHERE id='main'").use { c ->
            if (c.moveToFirst()) {
                runCatching { Backup.json.decodeFromString<AppSettings>(c.getString(0)) }.getOrDefault(AppSettings())
            } else {
                AppSettings()
            }
        }

    fun saveSettings(s: AppSettings) {
        val cv = ContentValues().apply {
            put("id", "main")
            put("json", Backup.json.encodeToString(s))
        }
        w.insert("settings", SQLiteDatabase.CONFLICT_REPLACE, cv)
    }

    fun resetSettings(): AppSettings {
        val def = AppSettings()
        saveSettings(def)
        return def
    }

    // ---------------- Storage Stats ----------------
    fun storageStats(): StorageStats {
        val dbFile = ctx.getDatabasePath("kashef_enc.db")
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L
        val (imgCount, imgBytes) = Img.totalSize(ctx)
        val osmDir = File(ctx.cacheDir, "osmdroid")
        val mapBytes = if (osmDir.exists()) osmDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } else 0L
        return StorageStats(
            dbBytes = dbBytes,
            imagesBytes = imgBytes,
            imageCount = imgCount,
            mapCacheBytes = mapBytes,
            reportCount = reportCount(),
            suspiciousCount = suspiciousCount(),
            peopleDbCount = peopleCount()
        )
    }
}
