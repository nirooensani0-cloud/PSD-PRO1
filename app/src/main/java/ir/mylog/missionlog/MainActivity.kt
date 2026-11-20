@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ir.mylog.missionlog

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import java.io.File
import java.util.Locale

sealed class Scr {
    object Home : Scr()
    object Step1 : Scr()
    object Step2 : Scr()
    object SuspiciousList : Scr()
    object SuspiciousEdit : Scr()
    object Search : Scr()
    object History : Scr()
    object Stats : Scr()
    object PeopleDb : Scr()
    object Settings : Scr()
    object BackupS : Scr()
    data class View(val id: String) : Scr()
}

class VM(app: Application) : AndroidViewModel(app) {
    val db = Db(app)
    val faceEngine = FaceEngine(app)
    private val ctx: Context get() = getApplication()

    var unlocked by mutableStateOf(false)
    var isDecoySession by mutableStateOf(false)
    var settings by mutableStateOf(db.getSettings())
    var scr by mutableStateOf<Scr>(Scr.Home)
    var prev by mutableStateOf<Scr>(Scr.Home)

    var draft by mutableStateOf<Report?>(null)
    var suspiciousDraft by mutableStateOf<SuspiciousEntry?>(null)
    var askExit by mutableStateOf(false)
    var dataVersion by mutableIntStateOf(0)
    var previewPersonRecord by mutableStateOf<PersonRecord?>(null)
    val added = mutableSetOf<String>()

    fun bumpDataVersion() {
        dataVersion++
    }

    fun reloadSettings() {
        settings = db.getSettings()
    }

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        db.saveSettings(newSettings)
    }

    fun unlock(decoy: Boolean) {
        isDecoySession = decoy
        unlocked = true
        scr = Scr.Home
    }

    fun lockNow() {
        persistDraftNow()
        unlocked = false
        isDecoySession = false
        scr = Scr.Home
    }

    fun hasSavedDraft(): Boolean = !isDecoySession && db.loadDraft() != null

    fun resumeDraft() {
        val d = db.loadDraft() ?: return
        draft = d
        added.clear()
        scr = Scr.Step1
    }

    fun discardSavedDraft() {
        val d = db.loadDraft()
        if (d != null && db.getReport(d.id) == null) {
            runCatching { d.images().forEach { Img.delete(ctx, it) } }
        }
        runCatching { db.clearDraft() }
        bumpDataVersion()
    }

    fun persistDraftNow() {
        val d = draft ?: return
        if (!isDecoySession) db.saveDraft(d)
    }

    fun updateDraft(f: (Report) -> Report) {
        val cur = draft ?: return
        val next = f(cur)
        draft = next
        if (!isDecoySession) db.saveDraft(next)
    }

    fun newReport(prefillPerson: PersonRecord? = null) {
        val d = J.date()
        val t = J.time()
        val (seq, num) = db.nextSequenceAndNumber(d)
        val initialSuspects = if (prefillPerson != null) {
            listOf(
                Suspect(
                    nationalCode = prefillPerson.nationalCode,
                    firstName = prefillPerson.firstName,
                    lastName = prefillPerson.lastName,
                    fatherName = prefillPerson.fatherName,
                    birthDate = prefillPerson.birthDate,
                    gender = prefillPerson.gender,
                    job = prefillPerson.job,
                    address = prefillPerson.address,
                    phone = prefillPerson.phone,
                    photo = prefillPerson.photoName.takeIf { it.isNotEmpty() }?.let { PhotoItem(it, "تصویر دیتابیس") },
                    faceEmbedding = prefillPerson.faceEmbedding
                )
            )
        } else {
            emptyList()
        }
        val rep = Report(
            seq = seq,
            number = num,
            reportDate = d,
            reportTime = t,
            incidentDate = d,
            incidentTime = t,
            suspects = initialSuspects
        )
        draft = rep
        added.clear()
        if (!isDecoySession) db.saveDraft(rep)
        scr = Scr.Step1
    }

    fun editReport(id: String) {
        draft = db.getReport(id)
        added.clear()
        scr = Scr.Step1
    }

    fun openReport(id: String) {
        prev = scr
        scr = Scr.View(id)
    }

    fun openPersonFromSearch(code: String) {
        previewPersonRecord = db.findPersonInDb(code)
    }

    fun back() {
        when (scr) {
            Scr.Step1 -> askExit = true
            Scr.Step2 -> scr = Scr.Step1
            Scr.SuspiciousEdit -> {
                suspiciousDraft = null
                scr = Scr.SuspiciousList
            }
            is Scr.View -> scr = prev
            else -> scr = Scr.Home
        }
    }

    fun discardDraftAndExit() {
        val old = draft?.let { db.getReport(it.id)?.images() } ?: emptySet()
        runCatching { (added - old).forEach { Img.delete(ctx, it) } }
        draft = null
        added.clear()
        runCatching { db.clearDraft() }
        askExit = false
        scr = Scr.Home
    }

    fun saveReport() {
        val d = draft ?: return
        val r = d.copy(officers = d.officers.filter { it.name.isNotBlank() || it.vehicle.isNotBlank() })
        val old = db.getReport(r.id)?.images() ?: emptySet()
        db.saveReport(r)
        db.clearDraft()
        ((old + added) - r.images()).forEach { Img.delete(ctx, it) }
        draft = null
        added.clear()
        bumpDataVersion()
        prev = Scr.Home
        scr = Scr.View(r.id)
    }

    fun deleteReport(id: String) {
        db.getReport(id)?.images()?.forEach { Img.delete(ctx, it) }
        db.deleteReport(id)
        bumpDataVersion()
        scr = Scr.Home
    }

    // ---- Suspicious Helpers ----
    fun newSuspicious() {
        suspiciousDraft = SuspiciousEntry(date = J.date())
        added.clear()
        scr = Scr.SuspiciousEdit
    }

    fun editSuspicious(id: String) {
        suspiciousDraft = if (isDecoySession) DecoyData.sampleSuspicious().firstOrNull { it.id == id }
        else db.getSuspicious(id)
        added.clear()
        scr = Scr.SuspiciousEdit
    }

    fun saveSuspicious() {
        val sp = suspiciousDraft ?: return
        if (!isDecoySession) {
            val old = db.getSuspicious(sp.id)?.images() ?: emptySet()
            db.saveSuspicious(sp)
            ((old + added) - sp.images()).forEach { Img.delete(ctx, it) }
        }
        suspiciousDraft = null
        added.clear()
        bumpDataVersion()
        scr = Scr.SuspiciousList
    }

    fun deleteSuspicious(id: String) {
        if (!isDecoySession) {
            db.getSuspicious(id)?.images()?.forEach { Img.delete(ctx, it) }
            db.deleteSuspicious(id)
        }
        suspiciousDraft = null
        bumpDataVersion()
        scr = Scr.SuspiciousList
    }

    // ---- Decoy-aware data accessors ----
    fun visibleReports(): List<Report> = if (isDecoySession) DecoyData.sampleReports() else db.allReports()
    fun visibleReportRows(): List<RowItem> = if (isDecoySession) {
        DecoyData.sampleReports().map { RowItem(it.id, it.number, "${it.incidentDate} ${it.incidentTime}", it.type) }
    } else db.listReportRows()

    fun findVisibleReport(id: String): Report? = if (isDecoySession) {
        DecoyData.sampleReports().firstOrNull { it.id == id }
    } else db.getReport(id)

    fun visibleSuspicious(): List<SuspiciousEntry> =
        if (isDecoySession) DecoyData.sampleSuspicious() else db.allSuspicious()
}

class MainActivity : FragmentActivity() {
    private val vm: VM by viewModels()
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        File(cacheDir, "pdf").deleteRecursively()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                KashefTheme(dark = vm.settings.darkMode) {
                    Surface(Modifier.fillMaxSize()) {
                        App(this, vm)
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vm.persistDraftNow()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val timeoutMs = vm.settings.autoLockSeconds.coerceAtLeast(5) * 1000L
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > timeoutMs) {
            vm.lockNow()
        }
    }
}

@Composable
fun App(act: FragmentActivity, vm: VM) {
    if (!vm.unlocked) {
        LockScreen(act, vm)
        return
    }
    BackHandler(vm.scr != Scr.Home) { vm.back() }
    when (val s = vm.scr) {
        Scr.Home -> HomeScreen(vm)
        Scr.Step1 -> if (vm.draft != null) ReportStep1Screen(vm)
        Scr.Step2 -> if (vm.draft != null) ReportStep2Screen(vm)
        Scr.SuspiciousList -> SuspiciousListScreen(vm)
        Scr.SuspiciousEdit -> SuspiciousEditScreen(vm)
        Scr.Search -> SearchScreen(vm)
        Scr.History -> HistoryScreen(vm)
        Scr.Stats -> StatsScreen(vm)
        Scr.PeopleDb -> PeopleDbScreen(vm)
        Scr.Settings -> SettingsScreen(vm)
        Scr.BackupS -> BackupScreen(vm)
        is Scr.View -> ViewScreen(vm, s.id)
    }

    if (vm.askExit) {
        AlertDialog(
            onDismissRequest = { vm.askExit = false },
            title = { Text("خروج از ویرایش گزارش") },
            text = { Text("پیش‌نویس به‌صورت خودکار ذخیره شده است. آیا می‌خواهید بعداً ادامه دهید یا پیش‌نویس پاک شود؟") },
            confirmButton = {
                TextButton(onClick = {
                    vm.askExit = false
                    vm.persistDraftNow()
                    vm.draft = null
                    vm.bumpDataVersion()
                    vm.scr = Scr.Home
                }) { Text("خروج و نگهداری پیش‌نویس") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.discardDraftAndExit() }) {
                        Text("حذف پیش‌نویس", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { vm.askExit = false }) { Text("ادامه ویرایش") }
                }
            }
        )
    }

    vm.previewPersonRecord?.let { p ->
        AlertDialog(
            onDismissRequest = { vm.previewPersonRecord = null },
            title = { Text("${p.firstName} ${p.lastName}".trim().ifEmpty { p.nationalCode }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.photoName.isNotEmpty()) {
                        Thumb(p.photoName, Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)).align(Alignment.CenterHorizontally))
                    }
                    Text("کد ملی: ${p.nationalCode}")
                    if (p.fatherName.isNotBlank()) Text("نام پدر: ${p.fatherName}")
                    if (p.birthDate.isNotBlank()) Text("تاریخ تولد: ${p.birthDate}")
                    if (p.gender.isNotBlank()) Text("جنسیت: ${p.gender}")
                    if (p.phone.isNotBlank()) Text("تلفن: ${p.phone}")
                    if (p.address.isNotBlank()) Text("آدرس: ${p.address}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.previewPersonRecord = null
                    vm.newReport(p)
                }) { Text("ثبت گزارش برای این فرد") }
            },
            dismissButton = {
                TextButton(onClick = { vm.previewPersonRecord = null }) { Text("بستن") }
            }
        )
    }
}

// ---------- صفحه قفل و بازیابی رمز ----------
@Composable
fun LockScreen(act: FragmentActivity, vm: VM) {
    val c = LocalContext.current
    val first = remember { !Auth.hasPin(c) }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var showRecovery by remember { mutableStateOf(false) }
    var firstTimeRecoveryCode by remember { mutableStateOf<String?>(null) }
    var crashInfo by remember { mutableStateOf<String?>(null) }
    var showCrashInfo by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        crashInfo = KashefApp.consumeCrashInfo(c)
    }

    val bioAvailable = remember { Auth.bioAvailable(c) }
    val bioEnabled = !first && bioAvailable && vm.settings.biometricEnabled

    LaunchedEffect(Unit) {
        if (bioEnabled) Auth.prompt(act) { vm.unlock(decoy = false) }
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Shield, null, Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text("سامانه کاشف", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (first) "تعیین رمز ورود" else "رمز عبور خود را وارد کنید",
            style = MaterialTheme.typography.titleMedium
        )
        if (first) {
            Text(
                "در تنظیمات می‌توانید سوالات امنیتی و کد بازیابی آفلاین تعریف کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("رمز ورود") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            shape = RoundedCornerShape(12.dp)
        )
        if (first) {
            OutlinedTextField(
                value = pin2,
                onValueChange = { pin2 = it },
                label = { Text("تکرار رمز ورود") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(12.dp)
            )
        }
        if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)

        Button(
            onClick = {
                if (first) {
                    if (pin.length < 4) err = "رمز باید حداقل ۴ کاراکتر باشد"
                    else if (pin != pin2) err = "تکرار رمز مطابقت ندارد"
                    else {
                        Auth.setPin(c, pin)
                        firstTimeRecoveryCode = Auth.generateRecoveryCode(c)
                    }
                } else {
                    when (Auth.check(c, pin, vm.settings.decoyEnabled)) {
                        UnlockResult.REAL -> vm.unlock(decoy = false)
                        UnlockResult.DECOY -> vm.unlock(decoy = true)
                        UnlockResult.INVALID -> err = "رمز اشتباه است"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(0.75f).height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text(if (first) "ثبت رمز و ورود" else "ورود") }

        if (bioEnabled) {
            OutlinedButton(
                onClick = { Auth.prompt(act) { vm.unlock(decoy = false) } },
                modifier = Modifier.fillMaxWidth(0.75f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(6.dp))
                Text("ورود با اثر انگشت")
            }
        }

        if (!first) {
            TextButton(onClick = { showRecovery = true }) {
                Text("فراموشی رمز عبور؟ (بازیابی آفلاین)")
            }
        }
    }

    firstTimeRecoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = {
                firstTimeRecoveryCode = null
                vm.unlock(decoy = false)
            },
            title = { Text("کد بازیابی اضطراری شما") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("این کد یک‌بار تولید شده است. آن را در جای امن بنویسید تا در صورت فراموشی رمز، بدون نیاز به اینترنت یا موبایل، رمز را بازیابی کنید:")
                    Text(code, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(onClick = {
                    firstTimeRecoveryCode = null
                    vm.unlock(decoy = false)
                }) { Text("یادداشت کردم و ورود") }
            }
        )
    }

    if (showRecovery) {
        RecoveryDialog(
            onRecovered = { newPin ->
                Auth.setPin(c, newPin)
                showRecovery = false
                Toast.makeText(c, "رمز جدید ثبت شد", Toast.LENGTH_SHORT).show()
                vm.unlock(decoy = false)
            },
            onDismiss = { showRecovery = false }
        )
    }

    if (showCrashInfo) {
        AlertDialog(
            onDismissRequest = { showCrashInfo = false },
            title = { Text("گزارش خطای برنامه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("برنامه به‌صورت خودکار به صفحه قفل برگشت و هیچ داده‌ای از دست نرفت. جزئیات آخرین خطا:")
                    Text(
                        crashInfo ?: "اطلاعاتی موجود نیست",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = { Button(onClick = { showCrashInfo = false }) { Text("بستن") } }
        )
    }
}

@Composable
fun RecoveryDialog(onRecovered: (String) -> Unit, onDismiss: () -> Unit) {
    val c = LocalContext.current
    val questions = remember { Auth.getSecurityQuestions(c) }
    val hasCode = remember { Auth.hasRecoveryCode(c) }
    var mode by remember { mutableIntStateOf(if (questions.isNotEmpty()) 0 else 1) }
    val answers = remember(questions) { mutableStateListOf(*Array(questions.size) { "" }) }
    var recCode by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بازیابی رمز عبور (آفلاین)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (questions.isNotEmpty()) {
                        FilterChip(selected = mode == 0, onClick = { mode = 0; err = "" }, label = { Text("سوالات امنیتی") })
                    }
                    if (hasCode) {
                        FilterChip(selected = mode == 1, onClick = { mode = 1; err = "" }, label = { Text("کد بازیابی") })
                    }
                }
                if (questions.isEmpty() && !hasCode) {
                    Text("هنوز سوال امنیتی یا کد بازیابی در تنظیمات تعریف نشده است.", color = MaterialTheme.colorScheme.error)
                } else {
                    if (mode == 0) {
                        questions.forEachIndexed { idx, q ->
                            F(q.question, answers[idx]) { answers[idx] = it }
                        }
                    } else {
                        F("کد ۱۶ کاراکتری بازیابی", recCode) { recCode = it }
                    }
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("رمز جدید (حداقل ۴ کاراکتر)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
                if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            if (questions.isNotEmpty() || hasCode) {
                Button(onClick = {
                    val verified = if (mode == 0) Auth.verifySecurityAnswers(c, answers) else Auth.verifyRecoveryCode(c, recCode)
                    if (!verified) err = "پاسخ یا کد بازیابی صحیح نیست"
                    else if (newPin.length < 4) err = "رمز جدید باید حداقل ۴ کاراکتر باشد"
                    else onRecovered(newPin)
                }) { Text("بازنشانی رمز") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بستن") } }
    )
}

// ---------- داشبورد گرافیکی صفحه اصلی ----------
@Composable
fun HomeScreen(vm: VM) {
    val reportCount = remember(vm.dataVersion) { vm.visibleReports().size }
    val suspCount = remember(vm.dataVersion) { vm.visibleSuspicious().size }
    val pplCount = remember(vm.dataVersion) { if (vm.isDecoySession) 0 else vm.db.peopleCount() }
    val hasDraft = remember(vm.dataVersion) { vm.hasSavedDraft() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("کاشف", fontWeight = FontWeight.Bold) },
                actions = {
                    if (!vm.isDecoySession) {
                        IconButton(onClick = { vm.scr = Scr.Settings }) {
                            Icon(Icons.Default.Settings, contentDescription = "تنظیمات")
                        }
                    }
                    IconButton(onClick = { vm.lockNow() }) {
                        Icon(Icons.Default.Lock, contentDescription = "قفل کردن")
                    }
                }
            )
        }
    ) { p ->
        Column(
            Modifier
                .padding(p)
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // کارت وضعیت امنیت
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.VerifiedUser, null, tint = MaterialTheme.colorScheme.primary)
                        Text("دفتر هوشمند گزارش مأموریت «کاشف»", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SecurityBadge("دیتابیس رمزنگاری‌شده SQLCipher")
                        SecurityBadge("ضد اسکرین‌شات فعال")
                        SecurityBadge("نقشه هیبرید کش آفلاین")
                    }
                }
            }

            if (hasDraft) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("پیش‌نویس ذخیره‌شده آماده ادامه است", fontWeight = FontWeight.Bold)
                            Text("گزارش ناتمام قبلی شما حفظ شده است.", style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            TextButton(onClick = { vm.discardSavedDraft() }) { Text("حذف") }
                            Button(onClick = { vm.resumeDraft() }) { Text("ادامه") }
                        }
                    }
                }
            }

            if (!vm.isDecoySession) {
                Button(
                    onClick = { vm.newReport() },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.NoteAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ثبت گزارش جدید", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashTile(
                    title = "مشکوکین",
                    subtitle = "$suspCount مورد ثبت‌شده",
                    icon = Icons.Default.Visibility,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.scr = Scr.SuspiciousList }
                )
                DashTile(
                    title = "جستجوی پیشرفته",
                    subtitle = "کد ملی، پلاک، چهره",
                    icon = Icons.Default.Search,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.scr = Scr.Search }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DashTile(
                    title = "سوابق گزارش",
                    subtitle = "$reportCount گزارش + اکسل",
                    icon = Icons.Default.FolderCopy,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.scr = Scr.History }
                )
                DashTile(
                    title = "آمار و نقاط آلوده",
                    subtitle = "نمودار، حافظه، نقشه",
                    icon = Icons.Default.BarChart,
                    modifier = Modifier.weight(1f),
                    onClick = { vm.scr = Scr.Stats }
                )
            }

            if (!vm.isDecoySession) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashTile(
                        title = "دیتابیس اولیه افراد",
                        subtitle = "$pplCount نفر آماده تطبیق",
                        icon = Icons.Default.Groups,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.scr = Scr.PeopleDb }
                    )
                    DashTile(
                        title = "پشتیبان و انتقال",
                        subtitle = "انتقال به گوشی دوم",
                        icon = Icons.Default.CloudDownload,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.scr = Scr.BackupS }
                    )
                }
            }
        }
    }
}

@Composable
fun SecurityBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun DashTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}
