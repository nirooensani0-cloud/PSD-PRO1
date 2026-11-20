package ir.mylog.missionlog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class FaceQualityResult(
    val ok: Boolean,
    val issues: List<String>,
    val croppedFace: Bitmap?,
    val embedding: List<Float>
)

data class FaceMatchItem(
    val title: String,
    val subtitle: String,
    val similarityPercent: Int,
    val photoName: String,
    val source: SearchSource,
    val reportId: String? = null,
    val suspiciousId: String? = null,
    val personCode: String? = null
)

class FaceEngine(private val ctx: Context) {
    private val detector by lazy {
        val opts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f)
            .build()
        FaceDetection.getClient(opts)
    }

    private val interpreter: Interpreter? by lazy {
        runCatching {
            val afd = ctx.assets.openFd("mobile_face_net.tflite")
            val buffer: MappedByteBuffer = FileInputStream(afd.fileDescriptor).channel.map(
                FileChannel.MapMode.READ_ONLY,
                afd.startOffset,
                afd.declaredLength
            )
            Interpreter(buffer, Interpreter.Options().apply { setNumThreads(2) })
        }.getOrNull()
    }

    /** بررسی نور، وضوح (تاری)، وجود چهره و زاویه؛ و استخراج بردار چهره */
    fun analyze(bmp: Bitmap): FaceQualityResult {
        val issues = ArrayList<String>()

        val (meanLum, lapVar) = measureBrightnessAndSharpness(bmp)
        if (meanLum < 42.0) issues.add("نور تصویر بسیار کم است")
        else if (meanLum > 225.0) issues.add("نور تصویر بیش از حد زیاد است")
        if (lapVar < 95.0) issues.add("تصویر تار یا دارای کیفیت پایین است")

        val faces = runCatching {
            Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)))
        }.getOrDefault(emptyList())

        var cropped: Bitmap? = null
        if (faces.isEmpty()) {
            issues.add("چهره‌ای در تصویر تشخیص داده نشد")
        } else {
            val mainFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }!!
            val box = mainFace.boundingBox
            if (box.width() < bmp.width * 0.10f) {
                issues.add("چهره در تصویر بیش از حد کوچک یا دور است")
            }
            if (abs(mainFace.headEulerAngleY) > 22f || abs(mainFace.headEulerAngleZ) > 22f) {
                issues.add("زاویه چهره مناسب نیست؛ لطفاً عکس تمام‌رخ بگیرید")
            }
            val leftOpen = mainFace.leftEyeOpenProbability
            val rightOpen = mainFace.rightEyeOpenProbability
            if ((leftOpen != null && leftOpen < 0.25f) || (rightOpen != null && rightOpen < 0.25f)) {
                issues.add("چشم‌ها بسته یا نیمه‌باز هستند؛ لطفاً عکس با چشمان باز بگیرید")
            }
            val aligned = alignFaceByEyes(bmp, mainFace)
            cropped = safeCropFace(aligned ?: bmp, box)
            if (aligned != null && aligned !== bmp) aligned.recycle()
        }

        val targetBmp = cropped ?: safeCenterCrop(bmp)
        val embedding = computeEmbedding(targetBmp)
        if (cropped == null) targetBmp.recycle()

        return FaceQualityResult(
            ok = issues.isEmpty(),
            issues = issues,
            croppedFace = cropped,
            embedding = embedding
        )
    }

    fun computeEmbeddingForStoredPhoto(photoName: String): List<Float> {
        val bmp = Img.thumb(ctx, photoName, 480) ?: return emptyList()
        return try {
            val res = analyze(bmp)
            res.croppedFace?.recycle()
            res.embedding
        } finally {
            bmp.recycle()
        }
    }

    private fun computeEmbedding(faceBmp: Bitmap): List<Float> {
        val interp = interpreter ?: return emptyList()
        val resized = Bitmap.createScaledBitmap(faceBmp, 112, 112, true)
        val pixels = IntArray(112 * 112)
        resized.getPixels(pixels, 0, 112, 0, 0, 112, 112)
        if (resized !== faceBmp) resized.recycle()

        val input = ByteBuffer.allocateDirect(1 * 112 * 112 * 3 * 4).apply {
            order(ByteOrder.nativeOrder())
        }
        for (px in pixels) {
            val r = ((px shr 16) and 0xFF)
            val g = ((px shr 8) and 0xFF)
            val b = (px and 0xFF)
            input.putFloat((r - 127.5f) / 128.0f)
            input.putFloat((g - 127.5f) / 128.0f)
            input.putFloat((b - 127.5f) / 128.0f)
        }
        input.rewind()

        val dim = interp.getOutputTensor(0).shape().lastOrNull() ?: 192
        val out = Array(1) { FloatArray(dim) }
        interp.run(input, out)
        val raw = out[0]
        var normSq = 0.0
        for (v in raw) normSq += v * v
        val inv = if (normSq > 1e-12) (1.0 / sqrt(normSq)).toFloat() else 1f
        return raw.map { it * inv }
    }

    fun searchMatches(db: Db, queryEmbedding: List<Float>, minScore: Float = 0.45f): List<FaceMatchItem> {
        if (queryEmbedding.isEmpty()) return emptyList()
        val out = ArrayList<FaceMatchItem>()

        db.allPeople().forEach { p ->
            var emb = p.faceEmbedding
            if (emb.isEmpty() && p.photoName.isNotEmpty()) {
                emb = computeEmbeddingForStoredPhoto(p.photoName)
                if (emb.isNotEmpty()) db.savePerson(p.copy(faceEmbedding = emb))
            }
            if (emb.size == queryEmbedding.size) {
                val sim = cosine(queryEmbedding, emb)
                if (sim >= minScore) {
                    out.add(
                        FaceMatchItem(
                            title = "${p.firstName} ${p.lastName}".trim().ifEmpty { p.nationalCode },
                            subtitle = "دیتابیس اولیه • کد ملی: ${p.nationalCode}",
                            similarityPercent = (sim * 100).toInt().coerceIn(0, 100),
                            photoName = p.photoName,
                            source = SearchSource.PEOPLE_DB,
                            personCode = p.nationalCode
                        )
                    )
                }
            }
        }

        db.allReports().forEach { r ->
            r.suspects.forEach { s ->
                val pName = s.photo?.name.orEmpty()
                val emb = if (s.faceEmbedding.isNotEmpty()) s.faceEmbedding
                else if (pName.isNotEmpty()) computeEmbeddingForStoredPhoto(pName) else emptyList()
                if (emb.size == queryEmbedding.size) {
                    val sim = cosine(queryEmbedding, emb)
                    if (sim >= minScore) {
                        out.add(
                            FaceMatchItem(
                                title = "${s.firstName} ${s.lastName}".trim().ifEmpty { "فرد بررسی‌شده" },
                                subtitle = "گزارش ${r.number} • کد ملی: ${s.nationalCode.ifBlank { "-" }}",
                                similarityPercent = (sim * 100).toInt().coerceIn(0, 100),
                                photoName = pName,
                                source = SearchSource.REPORT,
                                reportId = r.id
                            )
                        )
                    }
                }
            }
        }

        db.allSuspicious().forEach { sp ->
            val s = sp.person ?: return@forEach
            val pName = s.photo?.name.orEmpty()
            val emb = if (s.faceEmbedding.isNotEmpty()) s.faceEmbedding
            else if (pName.isNotEmpty()) computeEmbeddingForStoredPhoto(pName) else emptyList()
            if (emb.size == queryEmbedding.size) {
                val sim = cosine(queryEmbedding, emb)
                if (sim >= minScore) {
                    out.add(
                        FaceMatchItem(
                            title = "${s.firstName} ${s.lastName}".trim().ifEmpty { "فرد مشکوک" },
                            subtitle = "فهرست مشکوکین • ${sp.date}",
                            similarityPercent = (sim * 100).toInt().coerceIn(0, 100),
                            photoName = pName,
                            source = SearchSource.SUSPICIOUS,
                            suspiciousId = sp.id
                        )
                    )
                }
            }
        }

        return out.sortedByDescending { it.similarityPercent }.take(12)
    }

    private fun cosine(a: List<Float>, b: List<Float>): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    private fun alignFaceByEyes(bmp: Bitmap, face: Face): Bitmap? {
        val leftEye = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val dx = (rightEye.x - leftEye.x).toDouble()
        val dy = (rightEye.y - leftEye.y).toDouble()
        if (abs(dx) < 4.0) return null
        val angleDeg = (atan2(dy, dx) * 180.0 / Math.PI).toFloat()
        if (abs(angleDeg) < 2f || abs(angleDeg) > 35f) return null
        val cx = face.boundingBox.exactCenterX()
        val cy = face.boundingBox.exactCenterY()
        val m = Matrix().apply { postRotate(-angleDeg, cx, cy) }
        return runCatching {
            Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }.getOrNull()
    }

    private fun safeCropFace(bmp: Bitmap, box: Rect): Bitmap {
        val padW = (box.width() * 0.22f).toInt()
        val padH = (box.height() * 0.22f).toInt()
        val left = (box.left - padW).coerceIn(0, bmp.width - 1)
        val top = (box.top - padH).coerceIn(0, bmp.height - 1)
        val right = (box.right + padW).coerceIn(left + 1, bmp.width)
        val bottom = (box.bottom + padH).coerceIn(top + 1, bmp.height)
        return Bitmap.createBitmap(bmp, left, top, right - left, bottom - top)
    }

    private fun safeCenterCrop(bmp: Bitmap): Bitmap {
        val side = minOf(bmp.width, bmp.height)
        val left = (bmp.width - side) / 2
        val top = (bmp.height - side) / 2
        return Bitmap.createBitmap(bmp, left, top, side, side)
    }

    private fun measureBrightnessAndSharpness(bmp: Bitmap): Pair<Double, Double> {
        val scaled = Bitmap.createScaledBitmap(bmp, 160, 160, true)
        val px = IntArray(160 * 160)
        scaled.getPixels(px, 0, 160, 0, 0, 160, 160)
        if (scaled !== bmp) scaled.recycle()

        val gray = DoubleArray(px.size)
        var sum = 0.0
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val y = 0.299 * r + 0.587 * g + 0.114 * b
            gray[i] = y
            sum += y
        }
        val meanLum = sum / gray.size

        var lapSum = 0.0
        var lapSqSum = 0.0
        var cnt = 0
        for (y in 1 until 159) {
            for (x in 1 until 159) {
                val idx = y * 160 + x
                val lap = 4 * gray[idx] - gray[idx - 1] - gray[idx + 1] - gray[idx - 160] - gray[idx + 160]
                lapSum += lap
                lapSqSum += lap * lap
                cnt++
            }
        }
        val lapMean = lapSum / cnt
        val lapVar = (lapSqSum / cnt) - (lapMean * lapMean)
        return meanLum to lapVar
    }
}
