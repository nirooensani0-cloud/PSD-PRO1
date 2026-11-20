package ir.mylog.missionlog

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class PhotoItem(
    val name: String,
    val caption: String = ""
)

@Serializable
data class Suspect(
    val id: String = UUID.randomUUID().toString(),
    val nationalCode: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val fatherName: String = "",
    val birthDate: String = "",
    val gender: String = "",
    val marital: String = "",
    val job: String = "",
    val address: String = "",
    val phone: String = "",
    val reason: String = "",
    val reasonNotes: String = "",
    val photo: PhotoItem? = null,
    val docs: List<PhotoItem> = emptyList(),
    val faceEmbedding: List<Float> = emptyList()
)

@Serializable
data class Vehicle(
    val id: String = UUID.randomUUID().toString(),
    val plate: String = "",
    val type: String = "",
    val brand: String = "",
    val model: String = "",
    val color: String = "",
    val year: String = "",
    val vin: String = "",
    val engineNo: String = "",
    val owner: String = "",
    val ownerCode: String = "",
    val notes: String = "",
    val photos: List<PhotoItem> = emptyList()
)

@Serializable
data class StatementPerson(
    val id: String = UUID.randomUUID().toString(),
    val fullName: String = "",
    val role: String = "",
    val notes: String = ""
)

@Serializable
data class StatementPhone(
    val id: String = UUID.randomUUID().toString(),
    val phone: String = "",
    val spokenName: String = "",
    val role: String = ""
)

@Serializable
data class StatementVehicle(
    val id: String = UUID.randomUUID().toString(),
    val plate: String = "",
    val description: String = "",
    val reason: String = ""
)

@Serializable
data class OfficerEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val vehicle: String = ""
)

@Serializable
data class Report(
    val id: String = UUID.randomUUID().toString(),
    val seq: Int = 1,
    val number: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val reportDate: String = "",
    val reportTime: String = "",
    val incidentDate: String = "",
    val incidentTime: String = "",
    val location: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val type: String = "",
    val method: String = "",
    val description: String = "",
    val photos: List<PhotoItem> = emptyList(),
    val initialActions: String = "",
    val suspects: List<Suspect> = emptyList(),
    val vehicles: List<Vehicle> = emptyList(),
    val statementPeople: List<StatementPerson> = emptyList(),
    val statementPhones: List<StatementPhone> = emptyList(),
    val statementVehicles: List<StatementVehicle> = emptyList(),
    val sharedOfficerVehicle: String = "",
    val officers: List<OfficerEntry> = emptyList()
) {
    fun images(): Set<String> =
        (photos.map { it.name } +
            suspects.flatMap { listOfNotNull(it.photo?.name) + it.docs.map { d -> d.name } } +
            vehicles.flatMap { it.photos.map { p -> p.name } }).toSet()
}

@Serializable
data class SuspiciousEntry(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val date: String = "",
    val location: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val reason: String = "",
    val notes: String = "",
    val person: Suspect? = null,
    val vehicles: List<Vehicle> = emptyList(),
    val photos: List<PhotoItem> = emptyList()
) {
    fun images(): Set<String> =
        (photos.map { it.name } +
            listOfNotNull(person?.photo?.name) +
            (person?.docs?.map { it.name } ?: emptyList()) +
            vehicles.flatMap { it.photos.map { p -> p.name } }).toSet()
}

@Serializable
data class PersonRecord(
    val nationalCode: String,
    val firstName: String = "",
    val lastName: String = "",
    val fatherName: String = "",
    val birthDate: String = "",
    val gender: String = "",
    val job: String = "",
    val address: String = "",
    val phone: String = "",
    val photoName: String = "",
    val faceEmbedding: List<Float> = emptyList(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class AppSettings(
    val darkMode: Boolean = false,
    val autoLockSeconds: Int = 60,
    val biometricEnabled: Boolean = true,
    val decoyEnabled: Boolean = false,
    val pdfWatermarkEnabled: Boolean = true,
    val pdfWatermarkText: String = "محرمانه • سامانه کاشف",
    val pdfPasswordEnabled: Boolean = false,
    val pdfPassword: String = "",
    val shareAsEncryptedZip: Boolean = false,
    val shareZipPassword: String = "",
    val reportTypes: List<String> = DEFAULT_REPORT_TYPES,
    val methods: List<String> = DEFAULT_METHODS,
    val arrestReasons: List<String> = DEFAULT_ARREST_REASONS,
    val savedOfficers: List<String> = DEFAULT_OFFICERS,
    val iconChoice: String = "kashef1",
    val customIcon: Boolean = false
)

data class RowItem(
    val id: String,
    val number: String,
    val date: String,
    val summary: String,
    val source: SearchSource = SearchSource.REPORT
)

enum class SearchSource { REPORT, SUSPICIOUS, PEOPLE_DB }

data class SearchMatch(
    val id: String,
    val title: String,
    val subtitle: String,
    val sources: Set<SearchSource>,
    val reportId: String? = null,
    val suspiciousId: String? = null,
    val personCode: String? = null
)

data class StorageStats(
    val dbBytes: Long,
    val imagesBytes: Long,
    val imageCount: Int,
    val mapCacheBytes: Long,
    val reportCount: Int,
    val suspiciousCount: Int,
    val peopleDbCount: Int
) {
    val totalBytes: Long get() = dbBytes + imagesBytes + mapCacheBytes
}

val DEFAULT_REPORT_TYPES = listOf(
    "سرقت", "قتل", "منکراتی", "بررسی", "دستگیری", "بازرسی خودرو", "گشت", "تعقیب و مراقبت", "سایر"
)
val DEFAULT_METHODS = listOf(
    "سامانه ۱۱۰", "تماس تلفنی", "حضوری", "بی‌سیم", "دستور مافوق", "مشاهده مستقیم", "سایر"
)
val DEFAULT_ARREST_REASONS = listOf(
    "مظنون به سرقت", "حمل کالای قاچاق", "نقص مدارک هویتی", "تحت تعقیب قضایی",
    "نزاع و درگیری", "تخلف راهنمایی و رانندگی", "بررسی هویتی", "سایر"
)
val DEFAULT_OFFICERS = listOf(
    "استوار یکم علی رضایی",
    "گروهبان یکم محمد کریمی",
    "ستوان سوم سعید نوری"
)
val MARITAL_OPTIONS = listOf("مجرد", "متأهل", "مطلقه", "همسر فوت‌شده", "نامشخص")
val GENDER_OPTIONS = listOf("مرد", "زن", "نامعلوم")
val VEH_TYPES = listOf("سواری", "وانت", "موتورسیکلت", "کامیون", "اتوبوس", "سایر")
val PLATE_LETTERS = listOf(
    "الف", "ب", "پ", "ت", "ث", "ج", "د", "ز", "س", "ش",
    "ص", "ط", "ع", "ف", "ق", "ک", "گ", "ل", "م", "ن",
    "و", "هـ", "ی", "معلولین", "تشریفات", "سیاسی"
)

fun reportTypeColorHex(type: String): Long = when (type) {
    "سرقت" -> 0xFFD97706
    "قتل" -> 0xFFDC2626
    "منکراتی" -> 0xFF9333EA
    "دستگیری" -> 0xFF2563EB
    "بازرسی خودرو" -> 0xFF0891B2
    "گشت" -> 0xFF16A34A
    "تعقیب و مراقبت" -> 0xFFDB2777
    "بررسی" -> 0xFF4F46E5
    else -> 0xFF64748B
}

object DecoyData {
    fun sampleReports(): List<Report> = listOf(
        Report(
            id = "decoy-1",
            seq = 1,
            number = "\u200Fم/14050820/0001",
            createdAt = System.currentTimeMillis() - 86_400_000L * 3,
            reportDate = "1405/08/20",
            reportTime = "11:15",
            incidentDate = "1405/08/20",
            incidentTime = "10:45",
            location = "بلوار مرکزی، تقاطع پارک شهر",
            lat = 35.69439,
            lng = 51.42151,
            type = "گشت",
            method = "مشاهده مستقیم",
            description = "گشت عادی شهری انجام شد؛ وضعیت معبر عادی و بدون مورد خاص گزارش گردید.",
            initialActions = "ثبت گزارش روتین گشت در دفتر روزانه.",
            sharedOfficerVehicle = "67 ایران 112 الف 11",
            officers = listOf(OfficerEntry(name = "مأمور گشت نوبت صبح"))
        ),
        Report(
            id = "decoy-2",
            seq = 2,
            number = "\u200Fم/14050822/0002",
            createdAt = System.currentTimeMillis() - 86_400_000L,
            reportDate = "1405/08/22",
            reportTime = "16:30",
            incidentDate = "1405/08/22",
            incidentTime = "16:00",
            location = "میدان ولیعصر، ضلع غربی",
            lat = 35.71170,
            lng = 51.40700,
            type = "بررسی",
            method = "تماس تلفنی",
            description = "بررسی ترافیک و خودروی متوقف‌شده در حاشیه خیابان که پس از تذکر برطرف شد.",
            initialActions = "روان‌سازی مسیر و پایان مأموریت.",
            sharedOfficerVehicle = "67 ایران 205 پ 22",
            officers = listOf(OfficerEntry(name = "مأمور گشت نوبت عصر"))
        )
    )

    fun sampleSuspicious(): List<SuspiciousEntry> = listOf(
        SuspiciousEntry(
            id = "decoy-susp-1",
            date = "1405/08/21",
            location = "خیابان انقلاب",
            reason = "توقف طولانی بدون سرنشین",
            notes = "مورد عادی پس از بررسی مالک برطرف شد.",
            vehicles = listOf(Vehicle(plate = "67 ایران 300 ج 14", type = "سواری", color = "نقره‌ای"))
        )
    )
}
