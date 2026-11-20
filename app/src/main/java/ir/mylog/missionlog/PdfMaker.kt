package ir.mylog.missionlog

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.style.TypefaceSpan
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class PdfMaker(private val c: Context, private val settings: AppSettings) {
    private val W = 595
    private val H = 842
    private val M = 36
    private val doc = PdfDocument()
    private var page: PdfDocument.Page? = null
    private lateinit var cv: android.graphics.Canvas
    private var y = 0f
    private var n = 0

    private val nazanin: Typeface = runCatching {
        Typeface.createFromAsset(c.assets, "fonts/BNazanin.ttf")
    }.getOrDefault(Typeface.DEFAULT)

    private val nazaninBold: Typeface = runCatching {
        Typeface.createFromAsset(c.assets, "fonts/BNazaninBold.ttf")
    }.getOrElse { Typeface.create(nazanin, Typeface.BOLD) }

    private val titr: Typeface = runCatching {
        Typeface.createFromAsset(c.assets, "fonts/BTitrBd.ttf")
    }.getOrDefault(nazaninBold)

    private fun tp(size: Float, tf: Typeface, col: Int = Color.BLACK) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = tf
        color = col
    }

    private val body = tp(12f, nazanin)
    private val bodyBold = tp(12f, nazaninBold)
    private val captionPaint = tp(9.5f, nazanin, Color.DKGRAY)
    private val head = tp(13f, titr, Color.WHITE)
    private val titlePaint = tp(19f, titr, Color.rgb(20, 46, 92))

    private fun newPage() {
        finish()
        n++
        page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create())
        cv = page!!.canvas
        y = M.toFloat()
    }

    private fun drawWatermark() {
        if (!settings.pdfWatermarkEnabled || settings.pdfWatermarkText.isBlank()) return
        val wm = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 36f
            typeface = titr
            color = Color.argb(30, 30, 64, 120)
            textAlign = Paint.Align.CENTER
        }
        cv.save()
        cv.rotate(-32f, W / 2f, H / 2f)
        cv.drawText(settings.pdfWatermarkText, W / 2f, H / 2f, wm)
        cv.restore()
    }

    private fun finish() {
        page?.let {
            drawWatermark()
            val p = tp(10f, nazanin, Color.GRAY).apply { textAlign = Paint.Align.CENTER }
            cv.drawText("صفحه $n", W / 2f, H - 16f, p)
            doc.finishPage(it)
        }
        page = null
    }

    private fun need(h: Float) {
        if (y + h > H - M) newPage()
    }

    private fun para(cs: CharSequence, p: TextPaint, width: Int = W - 2 * M, startX: Float = M.toFloat()) {
        val l = StaticLayout.Builder.obtain(cs, 0, cs.length, p, width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.2f)
            .build()
        var start = 0
        while (start < l.lineCount) {
            val avail = H - M - y
            var end = start
            while (end < l.lineCount && l.getLineBottom(end) - l.getLineTop(start) <= avail) end++
            if (end == start) {
                newPage()
                continue
            }
            val top = l.getLineTop(start).toFloat()
            val bot = l.getLineBottom(end - 1).toFloat()
            cv.save()
            cv.translate(startX, y - top)
            cv.clipRect(0f, top, width.toFloat(), bot)
            l.draw(cv)
            cv.restore()
            y += bot - top
            start = end
        }
    }

    private fun section(t: String) {
        val width = W - 2 * M - 14
        val l = StaticLayout.Builder.obtain(t, 0, t.length, head, width)
            .setTextDirection(TextDirectionHeuristics.RTL)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .build()
        val h = maxOf(26f, l.height + 10f)
        need(h + 18f)
        y += 8f
        val p = Paint().apply { color = Color.rgb(28, 60, 114) }
        cv.drawRect(M.toFloat(), y, (W - M).toFloat(), y + h, p)
        cv.save()
        cv.translate((M + 7).toFloat(), y + 5f)
        l.draw(cv)
        cv.restore()
        y += h + 6f
    }

    private fun kv(label: String, value: String, width: Int = W - 2 * M, startX: Float = M.toFloat()) {
        if (value.isBlank()) return
        val sb = SpannableStringBuilder("$label: ")
        sb.setSpan(object : TypefaceSpan("") {
            override fun updateDrawState(ds: TextPaint) { ds.typeface = nazaninBold }
            override fun updateMeasureState(paint: TextPaint) { paint.typeface = nazaninBold }
        }, 0, sb.length, 0)
        sb.append(value)
        para(sb, body, width, startX)
        y += 2f
    }

    /** نمایش وسط‌چین تصاویر همراه با پینوشت اختیاری */
    private fun centeredImages(items: List<PhotoItem>) {
        if (items.isEmpty()) return
        val cols = 3
        val gap = 10f
        val cw = (W - 2 * M - gap * (cols - 1)) / cols
        val ch = 128f
        val borderPaint = Paint().apply {
            style = Paint.Style.STROKE
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        items.chunked(cols).forEach { row ->
            val hasCaption = row.any { it.caption.isNotBlank() }
            val rowH = ch + (if (hasCaption) 28f else 6f)
            need(rowH + 6f)

            val totalRowW = row.size * cw + (row.size - 1) * gap
            val rowStartX = (W - totalRowW) / 2f

            row.forEachIndexed { idx, item ->
                // راست‌به‌چپ داخل گروه وسط‌چین‌شده
                val cellX = rowStartX + (row.size - 1 - idx) * (cw + gap)
                val b = Img.thumb(c, item.name, 650) ?: return@forEachIndexed
                val sc = minOf(cw / b.width, ch / b.height)
                val w = b.width * sc
                val h = b.height * sc
                val x = cellX + (cw - w) / 2f
                val imgY = y + (ch - h) / 2f
                cv.drawBitmap(b, null, RectF(x, imgY, x + w, imgY + h), null)
                cv.drawRect(x, imgY, x + w, imgY + h, borderPaint)
                b.recycle()

                if (item.caption.isNotBlank()) {
                    val capLayout = StaticLayout.Builder.obtain(
                        item.caption, 0, item.caption.length, captionPaint, cw.toInt()
                    )
                        .setTextDirection(TextDirectionHeuristics.RTL)
                        .setAlignment(Layout.Alignment.ALIGN_CENTER)
                        .setMaxLines(2)
                        .build()
                    cv.save()
                    cv.translate(cellX, y + ch + 3f)
                    capLayout.draw(cv)
                    cv.restore()
                }
            }
            y += rowH + 6f
        }
    }

    fun build(r: Report, applyPassword: Boolean = settings.pdfPasswordEnabled): File {
        newPage()
        para("گزارش مأموریت — سامانه کاشف", titlePaint)
        y += 4f

        section("اطلاعات اولیه گزارش")
        kv("شماره گزارش", r.number)
        kv("تاریخ و ساعت گزارش", listOf(r.reportDate, r.reportTime).filter { it.isNotBlank() }.joinToString(" - "))
        kv("تاریخ و ساعت وقوع", listOf(r.incidentDate, r.incidentTime).filter { it.isNotBlank() }.joinToString(" - "))
        kv("محل / آدرس", r.location)
        if (r.lat != null && r.lng != null) {
            kv("مختصات GPS", "%.6f , %.6f".format(Locale.US, r.lat, r.lng))
        }
        kv("نوع گزارش", r.type)
        kv("نحوه اعلام", r.method)
        kv("شرح حادثه", r.description)
        kv("اقدامات اولیه", r.initialActions)

        if (r.photos.isNotEmpty()) {
            section("پیوست‌های صحنه حادثه")
            centeredImages(r.photos)
        }

        r.suspects.forEachIndexed { i, s ->
            val fullName = "${s.firstName} ${s.lastName}".trim()
            section("فرد شماره ${i + 1}" + if (fullName.isNotEmpty()) ": $fullName" else "")

            val face = s.photo
            if (face != null) {
                val bmp = Img.thumb(c, face.name, 500)
                if (bmp != null) {
                    need(140f)
                    val boxW = 104f
                    val boxH = 126f
                    val topY = y
                    val sc = minOf(boxW / bmp.width, boxH / bmp.height)
                    val dw = bmp.width * sc
                    val dh = bmp.height * sc
                    val imgX = M + (boxW - dw) / 2f
                    val imgY = topY + (boxH - dh) / 2f
                    cv.drawBitmap(bmp, null, RectF(imgX, imgY, imgX + dw, imgY + dh), null)
                    cv.drawRect(
                        imgX, imgY, imgX + dw, imgY + dh,
                        Paint().apply { style = Paint.Style.STROKE; color = Color.GRAY; strokeWidth = 1f }
                    )
                    bmp.recycle()

                    val rightWidth = W - 2 * M - (boxW.toInt() + 14)
                    val startX = M + boxW + 14f
                    kv("کد ملی", s.nationalCode, rightWidth, startX)
                    kv("نام", s.firstName, rightWidth, startX)
                    kv("نام خانوادگی", s.lastName, rightWidth, startX)
                    kv("نام پدر", s.fatherName, rightWidth, startX)
                    kv("جنسیت", s.gender, rightWidth, startX)
                    kv("تاریخ تولد", s.birthDate, rightWidth, startX)
                    kv("وضعیت تأهل", s.marital, rightWidth, startX)
                    kv("شغل", s.job, rightWidth, startX)
                    kv("تلفن", s.phone, rightWidth, startX)
                    if (face.caption.isNotBlank()) {
                        kv("پینوشت چهره", face.caption, rightWidth, startX)
                    }
                    y = maxOf(y, topY + boxH + 8f)
                } else {
                    kv("کد ملی", s.nationalCode)
                    kv("نام", s.firstName)
                    kv("نام خانوادگی", s.lastName)
                    kv("نام پدر", s.fatherName)
                    kv("جنسیت", s.gender)
                    kv("تاریخ تولد", s.birthDate)
                    kv("وضعیت تأهل", s.marital)
                    kv("شغل", s.job)
                    kv("تلفن", s.phone)
                }
            } else {
                kv("کد ملی", s.nationalCode)
                kv("نام", s.firstName)
                kv("نام خانوادگی", s.lastName)
                kv("نام پدر", s.fatherName)
                kv("جنسیت", s.gender)
                kv("تاریخ تولد", s.birthDate)
                kv("وضعیت تأهل", s.marital)
                kv("شغل", s.job)
                kv("تلفن", s.phone)
            }

            kv("آدرس", s.address)
            val reasonText = listOf(s.reason, s.reasonNotes).filter { it.isNotBlank() }.joinToString(" — ")
            kv("علت دستگیری / بررسی", reasonText)
            if (s.docs.isNotEmpty()) {
                y += 4f
                para("مدارک و پیوست‌های فرد:", bodyBold)
                centeredImages(s.docs)
            }
        }

        r.vehicles.forEachIndexed { i, v ->
            section("خودرو شماره ${i + 1}")
            kv("پلاک", v.plate)
            kv("نوع خودرو", v.type)
            kv("برند و مدل", "${v.brand} ${v.model}".trim())
            kv("رنگ", v.color)
            kv("سال ساخت", v.year)
            kv("شماره شاسی (VIN)", v.vin)
            kv("شماره موتور", v.engineNo)
            kv("مالک", v.owner)
            kv("کد ملی مالک", v.ownerCode)
            kv("توضیحات", v.notes)
            centeredImages(v.photos)
        }

        if (r.statementPeople.isNotEmpty() || r.statementPhones.isNotEmpty() || r.statementVehicles.isNotEmpty()) {
            section("اظهارات و موارد معرفی‌شده توسط متهم")
            r.statementPeople.forEachIndexed { i, sp ->
                kv("فرد اظهارشده ${i + 1}", "${sp.fullName} (${sp.role.ifBlank { "نقش نامشخص" }}) ${sp.notes}".trim())
            }
            r.statementPhones.forEachIndexed { i, ph ->
                kv("شماره اظهارشده ${i + 1}", "${ph.phone} — نام: ${ph.spokenName.ifBlank { "-" }} (${ph.role.ifBlank { "-" }})")
            }
            r.statementVehicles.forEachIndexed { i, sv ->
                kv("خودروی اظهارشده ${i + 1}", listOf(sv.plate, sv.description, sv.reason).filter { it.isNotBlank() }.joinToString(" • "))
            }
        }

        if (r.officers.isNotEmpty() || r.sharedOfficerVehicle.isNotBlank()) {
            section("مأمور / مأموران حاضر و خودروی مأموران")
            kv("خودروی مشترک مأموران", r.sharedOfficerVehicle)
            r.officers.forEachIndexed { i, o ->
                val vStr = if (o.vehicle.isNotBlank()) " — خودرو: ${o.vehicle}" else ""
                para("${i + 1}. ${o.name}$vStr", body)
            }
        }

        finish()
        val dir = File(c.cacheDir, "pdf").apply { mkdirs() }
        val safeNum = r.number.filter { it.isLetterOrDigit() || it == '-' }.ifEmpty { "doc" }
        val rawFile = File(dir, "report-$safeNum-raw.pdf")
        val finalFile = File(dir, "report-$safeNum.pdf")
        FileOutputStream(rawFile).use { doc.writeTo(it) }
        doc.close()

        if (applyPassword && settings.pdfPassword.isNotBlank()) {
            runCatching {
                PDFBoxResourceLoader.init(c.applicationContext)
                PDDocument.load(rawFile).use { pd ->
                    val ap = AccessPermission().apply {
                        setCanPrint(true)
                        setCanModify(false)
                    }
                    val spp = StandardProtectionPolicy(settings.pdfPassword, settings.pdfPassword, ap).apply {
                        encryptionKeyLength = 128
                        permissions = ap
                    }
                    pd.protect(spp)
                    pd.save(finalFile)
                }
                rawFile.delete()
                return finalFile
            }
        }

        rawFile.renameTo(finalFile)
        return finalFile
    }
}
