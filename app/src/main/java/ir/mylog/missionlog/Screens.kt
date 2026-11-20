@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ir.mylog.missionlog

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

// ---------- مرحله ۱ گزارش ----------
@Composable
fun ReportStep1Screen(vm: VM) {
    val r = vm.draft ?: return
    val c = LocalContext.current
    var showMap by remember { mutableStateOf(false) }

    fun up(f: (Report) -> Report) {
        vm.updateDraft(f)
    }

    fun fetchGps() {
        Loc.get(c) { l ->
            if (l == null) {
                Toast.makeText(c, "موقعیت در دسترس نیست (GPS را روشن کنید)", Toast.LENGTH_LONG).show()
            } else {
                up { it.copy(lat = l.latitude, lng = l.longitude) }
                Toast.makeText(c, "مختصات GPS ثبت شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { g ->
        if (g.values.any { it }) fetchGps()
    }

    Page("اطلاعات اولیه گزارش (۱ از ۲)", { vm.back() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("شماره گزارش (اندیکاتور قفل‌شده):", style = MaterialTheme.typography.labelLarge)
                }
                Text(r.number, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateFieldWithPicker("تاریخ گزارش", r.reportDate, Modifier.weight(1.3f)) { v ->
                up { it.copy(reportDate = v) }
            }
            F("ساعت گزارش", r.reportTime, Modifier.weight(0.9f), KeyboardType.Number) { v ->
                up { it.copy(reportTime = norm(v)) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateFieldWithPicker("تاریخ وقوع", r.incidentDate, Modifier.weight(1.3f)) { v ->
                up { it.copy(incidentDate = v) }
            }
            F("ساعت وقوع", r.incidentTime, Modifier.weight(0.9f), KeyboardType.Number) { v ->
                up { it.copy(incidentTime = norm(v)) }
            }
        }

        F("محل / آدرس", r.location, lines = 2) { v -> up { it.copy(location = v) } }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    val fine = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    val coarse = ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (fine || coarse) fetchGps()
                    else perm.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.MyLocation, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("ثبت GPS فعلی")
            }
            Button(
                onClick = { showMap = true },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Map, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("نقشه و اصلاح مکان")
            }
        }

        if (r.lat != null && r.lng != null) {
            Text(
                "مختصات ثبت‌شده: %.6f , %.6f".format(Locale.US, r.lat, r.lng),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Drop("نوع گزارش", r.type, vm.settings.reportTypes) { v -> up { it.copy(type = v) } }
        Drop("نحوه اعلام", r.method, vm.settings.methods) { v -> up { it.copy(method = v) } }
        F("شرح حادثه", r.description, lines = 4) { v -> up { it.copy(description = v) } }
        F("اقدامات اولیه انجام‌شده", r.initialActions, lines = 3) { v -> up { it.copy(initialActions = v) } }

        SectionHeader("پیوست‌های صحنه حادثه (جدا از تصاویر افراد)")
        SmartPhotoAdder("پیوست صحنه") { item, _ ->
            vm.added.add(item.name)
            up { it.copy(photos = it.photos + item) }
        }
        PhotoStripWithCaptions(
            items = r.photos,
            onUpdateCaption = { nm, cap ->
                up { it.copy(photos = it.photos.map { p -> if (p.name == nm) p.copy(caption = cap) else p }) }
            },
            onRemove = { nm -> up { it.copy(photos = it.photos.filterNot { p -> p.name == nm }) } }
        )

        Spacer(Modifier.height(6.dp))
        Button(
            onClick = { vm.scr = Scr.Step2 },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("مرحله بعد: افراد، خودرو، اظهارات و مأموران")
        }
    }

    if (showMap) {
        MapPickerDialog(
            initialLat = r.lat,
            initialLng = r.lng,
            onConfirm = { lat, lng ->
                up { it.copy(lat = lat, lng = lng) }
                showMap = false
            },
            onDismiss = { showMap = false }
        )
    }
}

// ---------- مرحله ۲ گزارش (با LazyColumn پایدار بدون پرش اسکرول به بالا) ----------
@Composable
fun ReportStep2Screen(vm: VM) {
    val r = vm.draft ?: return
    val listState = rememberLazyListState()
    var missingFieldsDialog by remember { mutableStateOf<List<String>?>(null) }

    fun up(f: (Report) -> Report) {
        vm.updateDraft(f)
    }

    fun updS(i: Int, f: (Suspect) -> Suspect) =
        up { x -> x.copy(suspects = x.suspects.mapIndexed { k, s -> if (k == i) f(s) else s }) }

    fun updV(i: Int, f: (Vehicle) -> Vehicle) =
        up { x -> x.copy(vehicles = x.vehicles.mapIndexed { k, v -> if (k == i) f(v) else v }) }

    fun attemptSave() {
        val empty = ArrayList<String>()
        if (r.location.isBlank()) empty.add("محل / آدرس حادثه")
        if (r.type.isBlank()) empty.add("نوع گزارش")
        if (r.description.isBlank()) empty.add("شرح حادثه")
        if (r.suspects.isEmpty() && r.vehicles.isEmpty()) empty.add("ثبت حداقل یک فرد یا خودرو")
        if (r.officers.none { it.name.isNotBlank() }) empty.add("اسامی مأموران حاضر")
        if (empty.isNotEmpty()) {
            missingFieldsDialog = empty
        } else {
            vm.saveReport()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("افراد، خودرو، اظهارات و مأموران (۲ از ۲)", maxLines = 1, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { vm.back() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { p ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(p).imePadding().fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            item(key = "sec_suspects") { SectionHeader("متهم / افراد بررسی‌شده") }

            itemsIndexed(r.suspects, key = { _, s -> "s_${s.id}" }) { i, s ->
                SuspectEditorCard(
                    title = "فرد شماره ${i + 1}",
                    s = s,
                    vm = vm,
                    onUpdate = { f -> updS(i, f) },
                    onDelete = { up { x -> x.copy(suspects = x.suspects.filterIndexed { k, _ -> k != i }) } }
                )
            }

            item(key = "add_suspect_btn") {
                Button(
                    onClick = { up { it.copy(suspects = it.suspects + Suspect()) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("+ افزودن فرد") }
            }

            item(key = "sec_vehicles") { SectionHeader("خودروها") }

            itemsIndexed(r.vehicles, key = { _, v -> "v_${v.id}" }) { i, v ->
                VehicleEditorCard(
                    title = "خودرو شماره ${i + 1}",
                    v = v,
                    onPhotoAdded = { vm.added.add(it) },
                    onUpdate = { f -> updV(i, f) },
                    onDelete = { up { x -> x.copy(vehicles = x.vehicles.filterIndexed { k, _ -> k != i }) } }
                )
            }

            item(key = "add_vehicle_btn") {
                OutlinedButton(
                    onClick = { up { it.copy(vehicles = it.vehicles + Vehicle()) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("+ افزودن خودرو") }
            }

            // ---------- بخش اظهارات متهم ----------
            item(key = "sec_statements") {
                SectionHeader("اظهارات و موارد معرفی‌شده توسط متهم")
                Text(
                    "افراد، شماره‌ها یا خودروهایی که فرد در اظهارات خود معرفی می‌کند را اینجا با نقش و علت ذکر شدن ثبت کنید.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            itemsIndexed(r.statementPeople, key = { _, sp -> "stp_${sp.id}" }) { i, sp ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("فرد معرفی‌شده ${i + 1}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton({
                                up { x -> x.copy(statementPeople = x.statementPeople.filterIndexed { k, _ -> k != i }) }
                            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                        F("نام و مشخصات فرد اظهارشده", sp.fullName) { v ->
                            up { x -> x.copy(statementPeople = x.statementPeople.mapIndexed { k, item -> if (k == i) item.copy(fullName = v) else item }) }
                        }
                        F("نسبت / چکاره بوده است؟", sp.role) { v ->
                            up { x -> x.copy(statementPeople = x.statementPeople.mapIndexed { k, item -> if (k == i) item.copy(role = v) else item }) }
                        }
                        F("توضیحات اظهارشده", sp.notes, lines = 2) { v ->
                            up { x -> x.copy(statementPeople = x.statementPeople.mapIndexed { k, item -> if (k == i) item.copy(notes = v) else item }) }
                        }
                    }
                }
            }

            itemsIndexed(r.statementPhones, key = { _, ph -> "stph_${ph.id}" }) { i, ph ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("شماره تلفن اظهارشده ${i + 1}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton({
                                up { x -> x.copy(statementPhones = x.statementPhones.filterIndexed { k, _ -> k != i }) }
                            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                        val phErr = iranMobileError(ph.phone)
                        F("شماره تلفن", ph.phone, kb = KeyboardType.Phone, error = phErr) { v ->
                            up { x -> x.copy(statementPhones = x.statementPhones.mapIndexed { k, item -> if (k == i) item.copy(phone = norm(v)) else item }) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            F("نام گفته‌شده کنار شماره", ph.spokenName, Modifier.weight(1f)) { v ->
                                up { x -> x.copy(statementPhones = x.statementPhones.mapIndexed { k, item -> if (k == i) item.copy(spokenName = v) else item }) }
                            }
                            F("چکاره بوده؟", ph.role, Modifier.weight(1f)) { v ->
                                up { x -> x.copy(statementPhones = x.statementPhones.mapIndexed { k, item -> if (k == i) item.copy(role = v) else item }) }
                            }
                        }
                    }
                }
            }

            itemsIndexed(r.statementVehicles, key = { _, sv -> "stv_${sv.id}" }) { i, sv ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("خودروی معرفی‌شده ${i + 1}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton({
                                up { x -> x.copy(statementVehicles = x.statementVehicles.filterIndexed { k, _ -> k != i }) }
                            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                        PlateInputField("پلاک (در صورت ذکر)", sv.plate) { v ->
                            up { x -> x.copy(statementVehicles = x.statementVehicles.mapIndexed { k, item -> if (k == i) item.copy(plate = v) else item }) }
                        }
                        F("مشخصات خودرو (مدل، رنگ...)", sv.description) { v ->
                            up { x -> x.copy(statementVehicles = x.statementVehicles.mapIndexed { k, item -> if (k == i) item.copy(description = v) else item }) }
                        }
                        F("علت ذکر شدن خودرو", sv.reason, lines = 2) { v ->
                            up { x -> x.copy(statementVehicles = x.statementVehicles.mapIndexed { k, item -> if (k == i) item.copy(reason = v) else item }) }
                        }
                    }
                }
            }

            item(key = "add_statement_btns") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { up { it.copy(statementPeople = it.statementPeople + StatementPerson()) } },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("+ فرد اظهارشده", maxLines = 1) }
                    OutlinedButton(
                        onClick = { up { it.copy(statementPhones = it.statementPhones + StatementPhone()) } },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("+ شماره", maxLines = 1) }
                    OutlinedButton(
                        onClick = { up { it.copy(statementVehicles = it.statementVehicles + StatementVehicle()) } },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) { Text("+ خودرو", maxLines = 1) }
                }
            }

            // ---------- مأموران حاضر و خودروی مأموران ----------
            item(key = "sec_officers") {
                SectionHeader("مأمور / مأموران حاضر و خودروی گشت")
                PlateInputField("خودروی مشترک مأموران (در صورت اعزام با یک خودرو)", r.sharedOfficerVehicle) { v ->
                    up { it.copy(sharedOfficerVehicle = v) }
                }
            }

            itemsIndexed(r.officers, key = { _, o -> "off_${o.id}" }) { i, o ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("مأمور ${i + 1}", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                            TextButton({
                                up { x -> x.copy(officers = x.officers.filterIndexed { k, _ -> k != i }) }
                            }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                        }
                        if (vm.settings.savedOfficers.isNotEmpty()) {
                            Drop("انتخاب سریع از سوابق مأموران", o.name, vm.settings.savedOfficers) { sel ->
                                up { x -> x.copy(officers = x.officers.mapIndexed { k, item -> if (k == i) item.copy(name = sel) else item }) }
                            }
                        }
                        F("نام، درجه و سمت مأمور", o.name) { v ->
                            up { x -> x.copy(officers = x.officers.mapIndexed { k, item -> if (k == i) item.copy(name = v) else item }) }
                        }
                        F("خودروی اختصاصی این مأمور (در صورت تفاوت با خودروی مشترک)", o.vehicle) { v ->
                            up { x -> x.copy(officers = x.officers.mapIndexed { k, item -> if (k == i) item.copy(vehicle = v) else item }) }
                        }
                    }
                }
            }

            item(key = "add_officer_btn") {
                OutlinedButton(
                    onClick = { up { it.copy(officers = it.officers + OfficerEntry()) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("+ افزودن مأمور حاضر") }
            }

            item(key = "save_btn") {
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = ::attemptSave,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Save, null)
                    Spacer(Modifier.width(8.dp))
                    Text("ذخیره نهایی گزارش", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    missingFieldsDialog?.let { emptyList ->
        AlertDialog(
            onDismissRequest = { missingFieldsDialog = null },
            title = { Text("بررسی موارد تکمیل‌نشده") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("این بخش‌ها خالی مانده‌اند:")
                    emptyList.forEach { Text("• $it", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.height(4.dp))
                    Text("آیا مطمئن هستید که عامدانه خالی مانده‌اند و می‌خواهید گزارش ثبت شود؟")
                }
            },
            confirmButton = {
                Button(onClick = {
                    missingFieldsDialog = null
                    vm.saveReport()
                }) { Text("بله، ثبت شود") }
            },
            dismissButton = {
                TextButton(onClick = { missingFieldsDialog = null }) { Text("بازگشت و تکمیل") }
            }
        )
    }
}

@Composable
fun SuspectEditorCard(
    title: String,
    s: Suspect,
    vm: VM,
    onUpdate: ((Suspect) -> Suspect) -> Unit,
    onDelete: () -> Unit
) {
    var dbComparisonNote by remember(s.id) { mutableStateOf<AutoFillMatch?>(null) }
    var lastAutoLookedUp by remember(s.id) { mutableStateOf("") }

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TextButton(onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }

            // ۱) کد ملی در ابتدای اطلاعات شخصی + خودکارخوانی از دیتابیس
            val codeErr = nationalCodeError(s.nationalCode)
            F(
                label = "کد ملی (۱۰ رقم)",
                value = s.nationalCode,
                kb = KeyboardType.Number,
                error = codeErr,
                onChange = { raw ->
                    val clean = norm(raw).filter { it.isDigit() }.take(10)
                    if (clean.length == 10 && clean != lastAutoLookedUp && validNationalCode(clean)) {
                        lastAutoLookedUp = clean
                        val match = vm.db.lookupByNationalCode(clean)
                        dbComparisonNote = match
                        if (match != null) {
                            onUpdate { cur ->
                                cur.copy(
                                    nationalCode = clean,
                                    firstName = cur.firstName.ifBlank { match.firstName },
                                    lastName = cur.lastName.ifBlank { match.lastName },
                                    fatherName = cur.fatherName.ifBlank { match.fatherName },
                                    birthDate = cur.birthDate.ifBlank { match.birthDate },
                                    gender = cur.gender.ifBlank { match.gender },
                                    job = cur.job.ifBlank { match.job },
                                    address = cur.address.ifBlank { match.address },
                                    phone = cur.phone.ifBlank { match.phone },
                                    photo = cur.photo ?: match.photo
                                )
                            }
                            return@F
                        }
                    }
                    onUpdate { it.copy(nationalCode = clean) }
                }
            )

            dbComparisonNote?.let { m ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("تطبیق خودکار از ${m.sourceLabel}:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${m.firstName} ${m.lastName} • فرزند: ${m.fatherName.ifBlank { "-" }} • تولد: ${m.birthDate.ifBlank { "-" }}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                F("نام", s.firstName, Modifier.weight(1f)) { v -> onUpdate { it.copy(firstName = v) } }
                F("نام خانوادگی", s.lastName, Modifier.weight(1f)) { v -> onUpdate { it.copy(lastName = v) } }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                F("نام پدر", s.fatherName, Modifier.weight(1f)) { v -> onUpdate { it.copy(fatherName = v) } }
                Drop("جنسیت", s.gender, GENDER_OPTIONS, Modifier.weight(1f)) { v ->
                    onUpdate { it.copy(gender = v) }
                }
            }

            DateFieldWithPicker("تاریخ تولد", s.birthDate) { v -> onUpdate { it.copy(birthDate = v) } }
            Drop("وضعیت تأهل", s.marital, MARITAL_OPTIONS) { v -> onUpdate { it.copy(marital = v) } }
            F("شغل", s.job) { v -> onUpdate { it.copy(job = v) } }
            F("آدرس", s.address, lines = 2) { v -> onUpdate { it.copy(address = v) } }
            F(
                label = "تلفن همراه (مثال: 09123456789)",
                value = s.phone,
                kb = KeyboardType.Phone,
                error = iranMobileError(s.phone)
            ) { v ->
                onUpdate { it.copy(phone = norm(v).filter { c -> c.isDigit() }.take(11)) }
            }

            // علت دستگیری نواری + توضیحات تکمیلی
            Text("علت دستگیری / بررسی (انتخاب نواری)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                vm.settings.arrestReasons.forEach { reasonOpt ->
                    FilterChip(
                        selected = s.reason == reasonOpt,
                        onClick = { onUpdate { it.copy(reason = if (it.reason == reasonOpt) "" else reasonOpt) } },
                        label = { Text(reasonOpt) }
                    )
                }
            }
            F("توضیحات تکمیلی علت دستگیری / بررسی", s.reasonNotes, lines = 2) { v ->
                onUpdate { it.copy(reasonNotes = v) }
            }

            Text("تصویر چهره فرد (با بررسی نور و کیفیت)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            SmartPhotoAdder("چهره", 1280, checkFaceQuality = true, faceEngine = vm.faceEngine) { item, emb ->
                vm.added.add(item.name)
                onUpdate { it.copy(photo = item, faceEmbedding = emb) }
            }
            s.photo?.let { faceItem ->
                PhotoStripWithCaptions(
                    items = listOf(faceItem),
                    onUpdateCaption = { _, cap -> onUpdate { it.copy(photo = faceItem.copy(caption = cap)) } },
                    onRemove = { onUpdate { it.copy(photo = null, faceEmbedding = emptyList()) } }
                )
            }

            Text("تصاویر مدارک فرد", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            SmartPhotoAdder("مدارک", 1600) { item, _ ->
                vm.added.add(item.name)
                onUpdate { it.copy(docs = it.docs + item) }
            }
            PhotoStripWithCaptions(
                items = s.docs,
                onUpdateCaption = { nm, cap ->
                    onUpdate { it.copy(docs = it.docs.map { p -> if (p.name == nm) p.copy(caption = cap) else p }) }
                },
                onRemove = { nm -> onUpdate { it.copy(docs = it.docs.filterNot { p -> p.name == nm }) } }
            )
        }
    }
}

@Composable
fun VehicleEditorCard(
    title: String,
    v: Vehicle,
    onPhotoAdded: (String) -> Unit,
    onUpdate: ((Vehicle) -> Vehicle) -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                TextButton(onDelete) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
            PlateInputField("پلاک خودرو (قالب: ۶۷ ایران ۱۲۳ حرف ۴۵)", v.plate) { np ->
                onUpdate { it.copy(plate = np) }
            }
            Drop("نوع خودرو", v.type, VEH_TYPES) { x -> onUpdate { it.copy(type = x) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                F("برند", v.brand, Modifier.weight(1f)) { x -> onUpdate { it.copy(brand = x) } }
                F("مدل/تیپ", v.model, Modifier.weight(1f)) { x -> onUpdate { it.copy(model = x) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                F("رنگ", v.color, Modifier.weight(1f)) { x -> onUpdate { it.copy(color = x) } }
                F("سال ساخت", v.year, Modifier.weight(1f), KeyboardType.Number) { x ->
                    onUpdate { it.copy(year = norm(x).filter { c -> c.isDigit() }.take(4)) }
                }
            }
            F("شماره شاسی (VIN)", v.vin) { x -> onUpdate { it.copy(vin = norm(x)) } }
            F("شماره موتور", v.engineNo) { x -> onUpdate { it.copy(engineNo = norm(x)) } }
            F("نام مالک", v.owner) { x -> onUpdate { it.copy(owner = x) } }
            F(
                label = "کد ملی مالک",
                value = v.ownerCode,
                kb = KeyboardType.Number,
                error = nationalCodeError(v.ownerCode)
            ) { x ->
                onUpdate { it.copy(ownerCode = norm(x).filter { c -> c.isDigit() }.take(10)) }
            }
            F("توضیحات (توقیف، نقص و ...)", v.notes, lines = 2) { x -> onUpdate { it.copy(notes = x) } }
            SmartPhotoAdder("عکس خودرو") { item, _ ->
                onPhotoAdded(item.name)
                onUpdate { it.copy(photos = it.photos + item) }
            }
            PhotoStripWithCaptions(
                items = v.photos,
                onUpdateCaption = { nm, cap ->
                    onUpdate { it.copy(photos = it.photos.map { p -> if (p.name == nm) p.copy(caption = cap) else p }) }
                },
                onRemove = { nm -> onUpdate { it.copy(photos = it.photos.filterNot { p -> p.name == nm }) } }
            )
        }
    }
}

// ---------- بخش مجزای افراد و خودروهای مشکوک ----------
@Composable
fun SuspiciousListScreen(vm: VM) {
    val items = remember(vm.dataVersion) { vm.visibleSuspicious() }
    Page(
        title = "افراد و خودروهای مشکوک (${items.size})",
        onBack = { vm.back() },
        actions = {
            IconButton(onClick = { vm.newSuspicious() }) {
                Icon(Icons.Default.Add, contentDescription = "ثبت مورد مشکوک")
            }
        },
        scroll = false
    ) {
        Button(onClick = { vm.newSuspicious() }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("ثبت فرد یا خودروی مشکوک جدید")
        }
        if (items.isEmpty()) {
            Text("هنوز مورد مشکوکی ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items, key = { it.id }) { sp ->
                    val title = listOfNotNull(
                        sp.person?.let { "${it.firstName} ${it.lastName}".trim().ifEmpty { it.nationalCode } },
                        sp.vehicles.firstOrNull()?.plate?.ifBlank { null }
                    ).joinToString(" • ").ifBlank { "مورد مشکوک" }
                    Card(
                        Modifier.fillMaxWidth().clickable { vm.editSuspicious(sp.id) },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(title, fontWeight = FontWeight.Bold)
                            Text("${sp.date} • ${sp.location.ifBlank { "بدون آدرس" }}", style = MaterialTheme.typography.bodySmall)
                            if (sp.reason.isNotBlank()) Text(sp.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuspiciousEditScreen(vm: VM) {
    val sp = vm.suspiciousDraft ?: return
    var showMap by remember { mutableStateOf(false) }

    fun up(f: (SuspiciousEntry) -> SuspiciousEntry) {
        vm.suspiciousDraft = f(vm.suspiciousDraft!!)
    }

    Page("ثبت / ویرایش مورد مشکوک", { vm.back() }) {
        DateFieldWithPicker("تاریخ ثبت", sp.date) { d -> up { it.copy(date = d) } }
        F("محل مشاهده", sp.location, lines = 2) { l -> up { it.copy(location = l) } }
        OutlinedButton(onClick = { showMap = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Map, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (sp.lat != null) "مختصات: %.5f, %.5f".format(Locale.US, sp.lat, sp.lng) else "انتخاب موقعیت روی نقشه")
        }
        F("علت مشکوک بودن", sp.reason, lines = 2) { r -> up { it.copy(reason = r) } }
        F("توضیحات تکمیلی", sp.notes, lines = 2) { n -> up { it.copy(notes = n) } }

        SectionHeader("مشخصات فرد (اختیاری)")
        if (sp.person == null) {
            OutlinedButton(
                onClick = { up { it.copy(person = Suspect()) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ افزودن مشخصات فرد مشکوک") }
        } else {
            SuspectEditorCard(
                title = "فرد مشکوک",
                s = sp.person,
                vm = vm,
                onUpdate = { f -> up { it.copy(person = f(it.person!!)) } },
                onDelete = { up { it.copy(person = null) } }
            )
        }

        SectionHeader("خودرو(های) مشکوک (اختیاری)")
        sp.vehicles.forEachIndexed { idx, v ->
            key(v.id) {
                VehicleEditorCard(
                    title = "خودرو مشکوک ${idx + 1}",
                    v = v,
                    onPhotoAdded = { vm.added.add(it) },
                    onUpdate = { f -> up { x -> x.copy(vehicles = x.vehicles.mapIndexed { k, item -> if (k == idx) f(item) else item }) } },
                    onDelete = { up { x -> x.copy(vehicles = x.vehicles.filterIndexed { k, _ -> k != idx }) } }
                )
            }
        }
        OutlinedButton(
            onClick = { up { it.copy(vehicles = it.vehicles + Vehicle()) } },
            modifier = Modifier.fillMaxWidth()
        ) { Text("+ افزودن خودرو مشکوک") }

        SectionHeader("تصاویر پیوست")
        SmartPhotoAdder("تصویر پیوست") { item, _ ->
            vm.added.add(item.name)
            up { it.copy(photos = it.photos + item) }
        }
        PhotoStripWithCaptions(
            items = sp.photos,
            onUpdateCaption = { nm, cap -> up { it.copy(photos = it.photos.map { p -> if (p.name == nm) p.copy(caption = cap) else p }) } },
            onRemove = { nm -> up { it.copy(photos = it.photos.filterNot { p -> p.name == nm }) } }
        )

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (vm.db.getSuspicious(sp.id) != null) {
                OutlinedButton(
                    onClick = { vm.deleteSuspicious(sp.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("حذف") }
            }
            Button(
                onClick = { vm.saveSuspicious() },
                modifier = Modifier.weight(2f).height(50.dp)
            ) { Text("ذخیره در فهرست مشکوکین") }
        }
    }

    if (showMap) {
        MapPickerDialog(
            initialLat = sp.lat,
            initialLng = sp.lng,
            onConfirm = { lat, lng ->
                up { it.copy(lat = lat, lng = lng) }
                showMap = false
            },
            onDismiss = { showMap = false }
        )
    }
}

// ---------- جستجوی یکپارچه + کیبورد تخصصی + جستجوی چهره ----------
@Composable
fun SearchScreen(vm: VM) {
    var mode by remember { mutableIntStateOf(0) }
    var q by remember { mutableStateOf("") }
    var showFaceSearch by remember { mutableStateOf(false) }

    val res = remember(mode, q, vm.dataVersion) {
        if (vm.isDecoySession) {
            DecoyData.sampleReports().filter { r ->
                norm("${r.number} ${r.type} ${r.location}").contains(norm(q))
            }.map {
                SearchMatch(it.id, "گزارش ${it.number}", it.type, setOf(SearchSource.REPORT), reportId = it.id)
            }
        } else {
            vm.db.unifiedSearch(mode, q)
        }
    }

    Page(
        title = "جستجوی پیشرفته",
        onBack = { vm.back() },
        actions = {
            IconButton(onClick = { showFaceSearch = true }) {
                Icon(Icons.Default.Face, contentDescription = "جستجو با چهره")
            }
        },
        scroll = false
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("کد ملی", "نام و نام خانوادگی", "پلاک", "تلفن").forEachIndexed { i, t ->
                FilterChip(
                    selected = mode == i,
                    onClick = { mode = i; q = "" },
                    label = { Text(t) }
                )
            }
            AssistChip(
                onClick = { showFaceSearch = true },
                label = { Text("جستجو با تصویر چهره") },
                leadingIcon = { Icon(Icons.Default.PhotoCamera, null, Modifier.size(16.dp)) }
            )
        }

        // تغییر پویا و تخصصی بخش ورودی بر اساس نوع جستجو
        when (mode) {
            0 -> F("کد ملی (کیبورد عددی)", q, kb = KeyboardType.Number) {
                q = norm(it).filter { c -> c.isDigit() }.take(10)
            }
            1 -> F("نام یا نام خانوادگی", q, kb = KeyboardType.Text) { q = it }
            2 -> PlateInputField("جستجوی پلاک با بخش‌بندی و نوار حروف", q) { q = it }
            else -> F("شماره تلفن (کیبورد عددی)", q, kb = KeyboardType.Phone) {
                q = norm(it).filter { c -> c.isDigit() }.take(11)
            }
        }

        if (q.isNotBlank() && res.isEmpty()) {
            Text("موردی در گزارش‌ها، مشکوکین یا دیتابیس اولیه یافت نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(res, key = { it.id }) { m ->
                Card(
                    Modifier.fillMaxWidth().clickable {
                        when {
                            m.reportId != null -> vm.openReport(m.reportId)
                            m.suspiciousId != null -> vm.editSuspicious(m.suspiciousId)
                            m.personCode != null -> vm.openPersonFromSearch(m.personCode)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(m.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            val badgeText = when {
                                SearchSource.SUSPICIOUS in m.sources -> "مشکوک"
                                SearchSource.PEOPLE_DB in m.sources -> "دیتابیس اولیه"
                                else -> "گزارش"
                            }
                            val badgeColor = when {
                                SearchSource.SUSPICIOUS in m.sources -> Color(0xFFB91C1C)
                                SearchSource.PEOPLE_DB in m.sources -> Color(0xFF0F766E)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            Text(
                                badgeText,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .background(badgeColor, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (m.subtitle.isNotBlank()) Text(m.subtitle, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showFaceSearch) {
        FaceSearchDialog(
            db = vm.db,
            faceEngine = vm.faceEngine,
            onSelectMatch = { m ->
                showFaceSearch = false
                when {
                    m.reportId != null -> vm.openReport(m.reportId)
                    m.suspiciousId != null -> vm.editSuspicious(m.suspiciousId)
                    m.personCode != null -> vm.openPersonFromSearch(m.personCode)
                }
            },
            onDismiss = { showFaceSearch = false }
        )
    }
}

// ---------- سوابق گزارش + خروجی اکسل اندیکاتور ----------
@Composable
fun HistoryScreen(vm: VM) {
    val c = LocalContext.current
    val rows = remember(vm.dataVersion) { vm.visibleReportRows() }
    val excelSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri ->
        if (uri != null) {
            vm.viewModelScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        c.contentResolver.openOutputStream(uri)?.use { out ->
                            ExcelExporter.exportIndicator(vm.visibleReports(), out)
                        }
                        true
                    }.getOrDefault(false)
                }
                Toast.makeText(c, if (ok) "فایل اکسل اندیکاتور ذخیره شد" else "خطا در خروجی اکسل", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Page(
        title = "سوابق گزارش (${rows.size})",
        onBack = { vm.back() },
        actions = {
            TextButton(onClick = { excelSaver.launch("kashef-indicator-${J.dateCompact()}.xlsx") }) {
                Icon(Icons.Default.TableView, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("خروجی اکسل")
            }
        },
        scroll = false
    ) {
        if (rows.isEmpty()) Text("گزارشی ثبت نشده است.")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.id }) { r ->
                Card(Modifier.fillMaxWidth().clickable { vm.openReport(r.id) }, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("گزارش ${r.number} • ${r.date}", fontWeight = FontWeight.Bold)
                        Text(r.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

// ---------- نمایش PDF و اشتراک امن ----------
@Composable
fun PdfView(file: File, modifier: Modifier) {
    val pages = remember(file) { mutableStateListOf<Bitmap>() }
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            for (i in 0 until r.pageCount) {
                r.openPage(i).use { pg ->
                    val w = 960
                    val h = w * pg.height / pg.width
                    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    b.eraseColor(android.graphics.Color.WHITE)
                    pg.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    withContext(Dispatchers.Main) { pages.add(b) }
                }
            }
            r.close()
            pfd.close()
        }
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(pages) {
            Image(it.asImageBitmap(), null, Modifier.fillMaxWidth().border(1.dp, Color.LightGray))
        }
    }
}

@Composable
fun ViewScreen(vm: VM, id: String) {
    val c = LocalContext.current
    val rep = remember(id, vm.dataVersion) { vm.findVisibleReport(id) }
    var previewFile by remember { mutableStateOf<File?>(null) }
    var confirmDel by remember { mutableStateOf(false) }

    LaunchedEffect(id, vm.settings) {
        previewFile = withContext(Dispatchers.IO) {
            rep?.let { PdfMaker(c, vm.settings).build(it, applyPassword = false) }
        }
    }
    DisposableEffect(id) {
        onDispose { File(c.cacheDir, "pdf").deleteRecursively() }
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        if (u != null && rep != null) {
            vm.viewModelScope.launch {
                val exportFile = withContext(Dispatchers.IO) {
                    PdfMaker(c, vm.settings).build(rep, applyPassword = vm.settings.pdfPasswordEnabled)
                }
                c.contentResolver.openOutputStream(u)?.use { o -> exportFile.inputStream().use { it.copyTo(o) } }
                Toast.makeText(c, "فایل PDF ذخیره شد", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Page("گزارش ${rep?.number ?: ""}", { vm.back() }, scroll = false) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!vm.isDecoySession) {
                OutlinedButton({ vm.editReport(id) }) { Text("ویرایش") }
            }
            OutlinedButton({
                if (rep != null) {
                    vm.viewModelScope.launch {
                        val shareFile = withContext(Dispatchers.IO) {
                            val pdf = PdfMaker(c, vm.settings).build(rep, applyPassword = vm.settings.pdfPasswordEnabled)
                            if (vm.settings.shareAsEncryptedZip) {
                                Backup.wrapPdfInEncryptedZip(c, pdf, vm.settings.shareZipPassword)
                            } else pdf
                        }
                        val mime = if (shareFile.extension == "zip") "application/zip" else "application/pdf"
                        val u = FileProvider.getUriForFile(c, "${c.packageName}.files", shareFile)
                        c.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, u)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                },
                                "اشتراک امن گزارش"
                            )
                        )
                    }
                }
            }) { Text("اشتراک امن") }

            OutlinedButton({
                val safeNum = rep?.number?.filter { it.isLetterOrDigit() || it == '-' }?.ifEmpty { "doc" } ?: "doc"
                saver.launch("report-$safeNum.pdf")
            }) { Text("ذخیره PDF") }

            if (!vm.isDecoySession) {
                TextButton({ confirmDel = true }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            }
        }
        val f = previewFile
        if (f == null) Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        else PdfView(f, Modifier.weight(1f))
    }

    if (confirmDel) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("حذف گزارش؟") },
            text = { Text("این کار قابل بازگشت نیست.") },
            confirmButton = { TextButton({ vm.deleteReport(id) }) { Text("حذف") } },
            dismissButton = { TextButton({ confirmDel = false }) { Text("انصراف") } }
        )
    }
}

// ---------- آمار و نقشه نقاط آلوده ----------
@Composable
fun StatsScreen(vm: VM) {
    val reports = remember(vm.dataVersion) { vm.visibleReports() }
    val suspicious = remember(vm.dataVersion) { vm.visibleSuspicious() }
    val storage = remember(vm.dataVersion) { vm.db.storageStats() }

    val allTypes = remember(reports, vm.settings.reportTypes) {
        (vm.settings.reportTypes + reports.mapNotNull { it.type.ifBlank { null } }).distinct()
    }
    var selectedTypes by remember { mutableStateOf(allTypes.toSet()) }
    var timeRange by remember { mutableIntStateOf(3) } // 0=هفتگی, 1=ماهانه, 2=سالانه, 3=همه
    var satellite by remember { mutableStateOf(false) }

    val filteredReports = remember(reports, selectedTypes, timeRange) {
        val now = System.currentTimeMillis()
        val curYear = J.date().substring(0, 4)
        reports.filter { r ->
            val typeOk = r.type.isBlank() || r.type in selectedTypes
            val timeOk = when (timeRange) {
                0 -> now - r.createdAt <= 7L * 86_400_000L
                1 -> now - r.createdAt <= 30L * 86_400_000L
                2 -> r.incidentDate.startsWith(curYear) || r.reportDate.startsWith(curYear)
                else -> true
            }
            typeOk && timeOk
        }
    }

    val allSuspects = remember(reports) { reports.flatMap { it.suspects } }
    val maleCount = allSuspects.count { it.gender == "مرد" }
    val femaleCount = allSuspects.count { it.gender == "زن" }
    val unknownGender = allSuspects.size - maleCount - femaleCount

    val ageBuckets = remember(allSuspects) {
        var u18 = 0; var a18_30 = 0; var a31_45 = 0; var a46p = 0; var unk = 0
        allSuspects.forEach { s ->
            val age = J.ageFromBirthDate(s.birthDate)
            when {
                age == null -> unk++
                age < 18 -> u18++
                age <= 30 -> a18_30++
                age <= 45 -> a31_45++
                else -> a46p++
            }
        }
        listOf("زیر ۱۸ سال" to u18, "۱۸ تا ۳۰ سال" to a18_30, "۳۱ تا ۴۵ سال" to a31_45, "۴۶ سال به بالا" to a46p, "نامشخص" to unk)
    }

    Page("آمار، حافظه و نقاط آلوده", { vm.back() }) {
        SectionHeader("خلاصه وضعیت آماری")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("کل گزارش‌ها", reports.size.toString(), Modifier.weight(1f))
            StatCard("افراد بررسی‌شده", allSuspects.size.toString(), Modifier.weight(1f))
            StatCard("موارد مشکوک", suspicious.size.toString(), Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("تفکیک جنسیت افراد بررسی‌شده", fontWeight = FontWeight.Bold)
                Text("مرد: $maleCount  •  زن: $femaleCount  •  نامعلوم: $unknownGender")
                HorizontalDivider()
                Text("تفکیک رده سنی", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ageBuckets.forEach { (lbl, cnt) ->
                        Text("$lbl: $cnt", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        SectionHeader("نقشه نقاط آلوده و تمرکز جرائم")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                listOf("هفتگی", "ماهانه", "سالانه", "همه").forEachIndexed { idx, lbl ->
                    FilterChip(selected = timeRange == idx, onClick = { timeRange = idx }, label = { Text(lbl) })
                }
            }
            FilterChip(
                selected = satellite,
                onClick = { satellite = !satellite },
                label = { Text(if (satellite) "ماهواره" else "جاده") }
            )
        }

        // فیلتر چندانتخابی موضوعات گزارش
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { selectedTypes = allTypes.toSet() }) { Text("انتخاب همه") }
            TextButton(onClick = { selectedTypes = emptySet() }) { Text("لغو همه") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            allTypes.forEach { tp ->
                val active = tp in selectedTypes
                FilterChip(
                    selected = active,
                    onClick = {
                        selectedTypes = if (active) selectedTypes - tp else selectedTypes + tp
                    },
                    leadingIcon = {
                        Box(Modifier.size(10.dp).background(Color(reportTypeColorHex(tp)), CircleShape))
                    },
                    label = { Text(tp) }
                )
            }
        }

        HotspotMapView(reports = filteredReports, satellite = satellite)

        // راهنمای نقشه (Legend)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("راهنمای نقشه (رنگ موضوعات و تعداد نقاط ثبت‌شده):", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allTypes.filter { it in selectedTypes }.forEach { tp ->
                        val cnt = filteredReports.count { it.type == tp && it.lat != null }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(12.dp).background(Color(reportTypeColorHex(tp)), CircleShape))
                            Text("$tp ($cnt)", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        SectionHeader("میزان حافظه مصرفی")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("دیتابیس رمزنگاری‌شده SQLCipher: ${formatBytes(storage.dbBytes)}")
                Text("تصاویر WebP (${storage.imageCount} فایل): ${formatBytes(storage.imagesBytes)}")
                Text("کش آفلاین نقشه: ${formatBytes(storage.mapCacheBytes)}")
                HorizontalDivider()
                Text("حجم کل مصرفی: ${formatBytes(storage.totalBytes)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(10.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------- دیتابیس اولیه افراد (ورود و بروزرسانی ۱۰۰۰+ فرد) ----------
@Composable
fun PeopleDbScreen(vm: VM) {
    val c = LocalContext.current
    var q by remember { mutableStateOf("") }
    var editingPerson by remember { mutableStateOf<PersonRecord?>(null) }
    val people = remember(q, vm.dataVersion) { vm.db.searchPeopleDb(q, 100) }
    val totalCount = remember(vm.dataVersion) { vm.db.peopleCount() }

    val csvImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            vm.viewModelScope.launch {
                val n = withContext(Dispatchers.IO) {
                    runCatching {
                        c.contentResolver.openInputStream(u)!!.use { inp -> Backup.importPeopleCsv(inp, vm.db) }
                    }.getOrDefault(0)
                }
                vm.bumpDataVersion()
                Toast.makeText(c, "$n نفر در دیتابیس اولیه ثبت/بروزرسانی شد", Toast.LENGTH_LONG).show()
            }
        }
    }

    val jsonImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            vm.viewModelScope.launch {
                val n = withContext(Dispatchers.IO) {
                    runCatching {
                        c.contentResolver.openInputStream(u)!!.use { inp -> Backup.importPeopleJson(inp, vm.db) }
                    }.getOrDefault(0)
                }
                vm.bumpDataVersion()
                Toast.makeText(c, "$n نفر از فایل JSON وارد شد", Toast.LENGTH_LONG).show()
            }
        }
    }

    val templateSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u ->
        if (u != null) {
            c.contentResolver.openOutputStream(u)?.use { out ->
                val sample = "\uFEFFکد ملی,نام,نام خانوادگی,نام پدر,تاریخ تولد,جنسیت,تلفن,آدرس,شغل\n" +
                    "0012345679,علی,رضایی,حسین,1371/04/15,مرد,09123456789,تهران، خیابان آزادی,آزاد\n"
                out.write(sample.toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(c, "فایل نمونه CSV ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    Page(
        title = "دیتابیس اولیه افراد ($totalCount)",
        onBack = { vm.back() },
        actions = {
            IconButton(onClick = { editingPerson = PersonRecord(nationalCode = "") }) {
                Icon(Icons.Default.PersonAdd, contentDescription = "افزودن فرد")
            }
        },
        scroll = false
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { csvImporter.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }) {
                Text("ورود / آپدیت CSV")
            }
            OutlinedButton(onClick = { jsonImporter.launch(arrayOf("application/json", "*/*")) }) {
                Text("ورود JSON")
            }
            OutlinedButton(onClick = { templateSaver.launch("kashef-people-template.csv") }) {
                Text("نمونه CSV")
            }
        }

        F("جستجو در دیتابیس اولیه (کد ملی یا نام)", q) { q = it }

        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(people, key = { it.nationalCode }) { p ->
                Card(Modifier.fillMaxWidth().clickable { editingPerson = p }, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (p.photoName.isNotEmpty()) {
                            Thumb(p.photoName, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("${p.firstName} ${p.lastName}".trim().ifEmpty { p.nationalCode }, fontWeight = FontWeight.Bold)
                            Text("کد ملی: ${p.nationalCode} • فرزند: ${p.fatherName.ifBlank { "-" }} • تولد: ${p.birthDate.ifBlank { "-" }}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }

    editingPerson?.let { initP ->
        var code by remember(initP) { mutableStateOf(initP.nationalCode) }
        var fn by remember(initP) { mutableStateOf(initP.firstName) }
        var ln by remember(initP) { mutableStateOf(initP.lastName) }
        var father by remember(initP) { mutableStateOf(initP.fatherName) }
        var dob by remember(initP) { mutableStateOf(initP.birthDate) }
        var gender by remember(initP) { mutableStateOf(initP.gender) }
        var phone by remember(initP) { mutableStateOf(initP.phone) }
        var addr by remember(initP) { mutableStateOf(initP.address) }
        var photoName by remember(initP) { mutableStateOf(initP.photoName) }
        var emb by remember(initP) { mutableStateOf(initP.faceEmbedding) }

        AlertDialog(
            onDismissRequest = { editingPerson = null },
            title = { Text("مشخصات فرد در دیتابیس اولیه") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    F("کد ملی", code, kb = KeyboardType.Number, error = nationalCodeError(code)) {
                        code = norm(it).filter { ch -> ch.isDigit() }.take(10)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        F("نام", fn, Modifier.weight(1f)) { fn = it }
                        F("نام خانوادگی", ln, Modifier.weight(1f)) { ln = it }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        F("نام پدر", father, Modifier.weight(1f)) { father = it }
                        Drop("جنسیت", gender, GENDER_OPTIONS, Modifier.weight(1f)) { gender = it }
                    }
                    DateFieldWithPicker("تاریخ تولد", dob) { dob = it }
                    F("تلفن", phone, kb = KeyboardType.Phone, error = iranMobileError(phone)) { phone = norm(it) }
                    F("آدرس", addr) { addr = it }
                    SmartPhotoAdder("چهره", 1280, checkFaceQuality = true, faceEngine = vm.faceEngine) { item, e ->
                        photoName = item.name
                        emb = e
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = code.length == 10,
                    onClick = {
                        vm.db.savePerson(
                            PersonRecord(
                                nationalCode = code, firstName = fn, lastName = ln,
                                fatherName = father, birthDate = dob, gender = gender,
                                phone = phone, address = addr, photoName = photoName, faceEmbedding = emb
                            )
                        )
                        vm.bumpDataVersion()
                        editingPerson = null
                    }
                ) { Text("ذخیره") }
            },
            dismissButton = {
                Row {
                    if (initP.nationalCode.isNotEmpty()) {
                        TextButton(onClick = {
                            vm.db.deletePerson(initP.nationalCode)
                            vm.bumpDataVersion()
                            editingPerson = null
                        }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                    }
                    TextButton(onClick = { editingPerson = null }) { Text("انصراف") }
                }
            }
        )
    }
}

// ---------- تنظیمات کامل ----------
@Composable
fun SettingsScreen(vm: VM) {
    val c = LocalContext.current
    var s by remember { mutableStateOf(vm.settings) }
    var subPage by remember { mutableStateOf<String?>(null) }
    var showChangePin by remember { mutableStateOf(false) }
    var showDecoyDialog by remember { mutableStateOf(false) }
    var showSecQuestions by remember { mutableStateOf(false) }
    var generatedRecovery by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var showIconPicker by remember { mutableStateOf(false) }
    val storage = remember(vm.dataVersion) { vm.db.storageStats() }

    fun saveAndApply(newSettings: AppSettings) {
        s = newSettings
        vm.updateSettings(newSettings)
    }

    when (subPage) {
        "appearance" -> {
            Page("ظاهر و قفل خودکار", { subPage = null }) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("حالت شب (تیره)", fontWeight = FontWeight.Bold)
                            Switch(checked = s.darkMode, onCheckedChange = { saveAndApply(s.copy(darkMode = it)) })
                        }
                        HorizontalDivider()
                        Text("زمان قفل خودکار در پس‌زمینه (حداقل ۵ ثانیه):", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(5 to "۵ ثانیه", 15 to "۱۵ ثانیه", 30 to "۳۰ ثانیه", 60 to "۶۰ ثانیه", 180 to "۳ دقیقه", 300 to "۵ دقیقه").forEach { (sec, lbl) ->
                                FilterChip(
                                    selected = s.autoLockSeconds == sec,
                                    onClick = { saveAndApply(s.copy(autoLockSeconds = sec)) },
                                    label = { Text(lbl) }
                                )
                            }
                        }
                    }
                }
            }
        }
        "security" -> {
            Page("رمز ورود و امنیت", { subPage = null }) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { showChangePin = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("تغییر رمز ورود اصلی")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("ورود با اثر انگشت")
                            Switch(checked = s.biometricEnabled, onCheckedChange = { saveAndApply(s.copy(biometricEnabled = it)) })
                        }
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("فعال‌سازی رمز فریب", fontWeight = FontWeight.Bold)
                                Text("با ورود رمز فریب، گزارش‌های ساختگی نمایش داده می‌شود و داده‌های واقعی مخفی می‌مانند.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = s.decoyEnabled, onCheckedChange = {
                                saveAndApply(s.copy(decoyEnabled = it))
                                if (it && !Auth.hasDecoyPin(c)) showDecoyDialog = true
                            })
                        }
                        if (s.decoyEnabled) {
                            OutlinedButton(onClick = { showDecoyDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("تنظیم / تغییر رمز فریب")
                            }
                        }
                    }
                }
            }
        }
        "recovery" -> {
            Page("بازیابی رمز", { subPage = null }) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "بازیابی رمز بدون شماره موبایل و ایمیل انجام می‌شود؛ فقط با سوالات امنیتی و کد بازیابی آفلاین.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedButton(onClick = { showSecQuestions = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("تنظیم سوالات امنیتی بازیابی رمز")
                        }
                        OutlinedButton(
                            onClick = { generatedRecovery = Auth.generateRecoveryCode(c) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("تولید کد بازیابی اضطراری آفلاین (۱۶ کاراکتری)")
                        }
                    }
                }
            }
        }
        "share" -> {
            Page("تنظیمات اشتراک‌گذاری و PDF", { subPage = null }) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("درج واترمارک روی صفحات PDF")
                            Switch(checked = s.pdfWatermarkEnabled, onCheckedChange = { saveAndApply(s.copy(pdfWatermarkEnabled = it)) })
                        }
                        if (s.pdfWatermarkEnabled) {
                            F("متن واترمارک", s.pdfWatermarkText) { saveAndApply(s.copy(pdfWatermarkText = it)) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("رمزدار کردن فایل خروجی PDF")
                            Switch(checked = s.pdfPasswordEnabled, onCheckedChange = { saveAndApply(s.copy(pdfPasswordEnabled = it)) })
                        }
                        if (s.pdfPasswordEnabled) {
                            F("رمز فایل PDF", s.pdfPassword) { saveAndApply(s.copy(pdfPassword = it)) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("اشتراک در قالب ZIP رمزدار")
                            Switch(checked = s.shareAsEncryptedZip, onCheckedChange = { saveAndApply(s.copy(shareAsEncryptedZip = it)) })
                        }
                        if (s.shareAsEncryptedZip) {
                            F("رمز فایل ZIP اشتراکی", s.shareZipPassword) { saveAndApply(s.copy(shareZipPassword = it)) }
                        }
                    }
                }
            }
        }
        "toolbars" -> {
            Page("مدیریت نوار ابزارها و فهرست‌ها", { subPage = null }) {
                ToolbarEditorCard("اسامی مأموران حاضر", s.savedOfficers) { list -> saveAndApply(s.copy(savedOfficers = list)) }
                ToolbarEditorCard("انواع گزارش", s.reportTypes) { list -> saveAndApply(s.copy(reportTypes = list)) }
                ToolbarEditorCard("نوار علت دستگیری / بررسی", s.arrestReasons) { list -> saveAndApply(s.copy(arrestReasons = list)) }
                ToolbarEditorCard("نحوه اعلام", s.methods) { list -> saveAndApply(s.copy(methods = list)) }
            }
        }
        "storage" -> {
            Page("حافظه و بازنشانی", { subPage = null }) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("کل فضای اشغال‌شده: ${formatBytes(storage.totalBytes)}", fontWeight = FontWeight.Bold)
                        Text("حجم دیتابیس رمزنگاری‌شده: ${formatBytes(storage.dbBytes)}")
                        Text("حجم تصاویر رمزنگاری‌شده: ${formatBytes(storage.imagesBytes)}")
                        OutlinedButton(
                            onClick = {
                                File(c.cacheDir, "osmdroid").deleteRecursively()
                                vm.bumpDataVersion()
                                Toast.makeText(c, "کش آفلاین نقشه پاک‌سازی شد", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("پاک‌سازی کش نقشه (${formatBytes(storage.mapCacheBytes)})") }

                        OutlinedButton(
                            onClick = { confirmReset = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("بازگشت تنظیمات به حالت پیش‌فرض") }
                    }
                }
            }
        }
        "icon" -> {
            Page("آیکون برنامه", { subPage = null }) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("یک آیکون داخلی را انتخاب کنید:", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconManager.CHOICES.forEach { key ->
                                val resId = when (key) {
                                    "kashef2" -> R.mipmap.ic_launcher_kashef2
                                    "kashef3" -> R.mipmap.ic_launcher_kashef3
                                    "kashef4" -> R.mipmap.ic_launcher_kashef4
                                    else -> R.mipmap.ic_launcher_kashef1
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .border(
                                                if (s.iconChoice == key && !s.customIcon) 3.dp else 1.dp,
                                                if (s.iconChoice == key && !s.customIcon) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                saveAndApply(s.copy(iconChoice = key, customIcon = false))
                                                IconManager.applyIcon(c, key)
                                                Toast.makeText(c, "آیکون برنامه تغییر کرد", Toast.LENGTH_SHORT).show()
                                            }
                                    ) {
                                        Image(
                                            painter = painterResource(resId),
                                            contentDescription = key,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Text(
                                        when (key) {
                                            "kashef2" -> "چشم"
                                            "kashef3" -> "اثرانگشت"
                                            "kashef4" -> "ستاره"
                                            else -> "ذره‌بین"
                                        },
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                        Text("آیکون دلخواه از گالری گوشی:", fontWeight = FontWeight.Bold)
                        Text(
                            "اندروید اجازه تغییر مستقیم آیکون برنامه با عکس دلخواه را نمی‌دهد؛ به‌جای آن یک میانبر «کاشف» با عکس شما در صفحه اصلی گوشی ساخته می‌شود.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { showIconPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("انتخاب عکس دلخواه از گالری")
                        }
                        if (s.customIcon) {
                            Text(
                                "میانبر دلخواه فعال است. برای حذف آن، دکمه زیر را بزنید.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            OutlinedButton(
                                onClick = {
                                    IconManager.clearCustomIcon(c)
                                    saveAndApply(s.copy(customIcon = false))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("حذف میانبر دلخواه") }
                        }
                    }
                }
            }
        }
        else -> {
            Page("تنظیمات و امنیت سامانه کاشف", { vm.back() }) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        listOf(
                            Triple("appearance", "ظاهر و قفل خودکار", "حالت شب و زمان قفل شدن برنامه"),
                            Triple("security", "رمز ورود و امنیت", "تغییر رمز، اثر انگشت و رمز فریب"),
                            Triple("recovery", "بازیابی رمز", "سوالات امنیتی و کد بازیابی آفلاین"),
                            Triple("share", "اشتراک‌گذاری و PDF", "واترمارک، رمز PDF و ZIP رمزدار"),
                            Triple("toolbars", "نوار ابزارها و فهرست‌ها", "مأموران، انواع گزارش، علت‌ها و نحوه اعلام"),
                            Triple("icon", "آیکون برنامه", "انتخاب آیکون داخلی یا عکس دلخواه"),
                            Triple("storage", "حافظه و بازنشانی", "آمار فضا، پاک‌سازی کش و حالت پیش‌فرض")
                        ).forEach { (key, title, subtitle) ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { subPage = key }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(title, fontWeight = FontWeight.Bold)
                                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }

    if (showIconPicker) {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            showIconPicker = false
            if (uri != null) {
                val bmp = Img.decodeOrientedBitmap(c, uri, 512)
                if (bmp != null) {
                    val square = cropCenterSquare(bmp)
                    IconManager.setCustomIcon(c, square, s.iconChoice)
                    saveAndApply(s.copy(customIcon = true))
                    Toast.makeText(c, "میانبر با عکس دلخواه ساخته شد", Toast.LENGTH_SHORT).show()
                    if (square !== bmp) square.recycle()
                    bmp.recycle()
                } else {
                    Toast.makeText(c, "خواندن عکس ممکن نشد", Toast.LENGTH_SHORT).show()
                }
            }
        }
        LaunchedEffect(Unit) { launcher.launch("image/*") }
    }

    if (showChangePin) {
        var oldP by remember { mutableStateOf("") }
        var newP by remember { mutableStateOf("") }
        var err by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showChangePin = false },
            title = { Text("تغییر رمز ورود") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(oldP, { oldP = it }, label = { Text("رمز فعلی") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(newP, { newP = it }, label = { Text("رمز جدید (حداقل ۴ کاراکتر)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (Auth.check(c, oldP) != UnlockResult.REAL) err = "رمز فعلی اشتباه است"
                    else if (newP.length < 4) err = "رمز جدید باید حداقل ۴ کاراکتر باشد"
                    else {
                        Auth.setPin(c, newP)
                        showChangePin = false
                        Toast.makeText(c, "رمز عبور با موفقیت تغییر یافت", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("ذخیره رمز") }
            },
            dismissButton = { TextButton(onClick = { showChangePin = false }) { Text("انصراف") } }
        )
    }

    if (showDecoyDialog) {
        var dp by remember { mutableStateOf("") }
        var err by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDecoyDialog = false },
            title = { Text("تنظیم رمز فریب") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("رمزی متفاوت با رمز اصلی وارد کنید:")
                    OutlinedTextField(dp, { dp = it }, label = { Text("رمز فریب") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (dp.length < 4) err = "حداقل ۴ کاراکتر"
                    else if (Auth.check(c, dp, false) == UnlockResult.REAL) err = "رمز فریب نباید با رمز اصلی یکی باشد"
                    else {
                        Auth.setDecoyPin(c, dp)
                        showDecoyDialog = false
                        Toast.makeText(c, "رمز فریب ثبت شد", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("ثبت") }
            },
            dismissButton = { TextButton(onClick = { showDecoyDialog = false }) { Text("انصراف") } }
        )
    }

    if (showSecQuestions) {
        var q1 by remember { mutableStateOf("نام اولین مدرسه شما چیست؟") }
        var a1 by remember { mutableStateOf("") }
        var q2 by remember { mutableStateOf("نام شهر محل تولد مادر چیست؟") }
        var a2 by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSecQuestions = false },
            title = { Text("تنظیم سوالات امنیتی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    F("سوال ۱", q1) { q1 = it }
                    F("پاسخ سوال ۱", a1) { a1 = it }
                    F("سوال ۲", q2) { q2 = it }
                    F("پاسخ سوال ۲", a2) { a2 = it }
                }
            },
            confirmButton = {
                Button(
                    enabled = a1.isNotBlank() && a2.isNotBlank(),
                    onClick = {
                        Auth.setSecurityQuestions(c, listOf(q1 to a1, q2 to a2))
                        showSecQuestions = false
                        Toast.makeText(c, "سوالات امنیتی ذخیره شد", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("ذخیره") }
            },
            dismissButton = { TextButton(onClick = { showSecQuestions = false }) { Text("انصراف") } }
        )
    }

    generatedRecovery?.let { code ->
        AlertDialog(
            onDismissRequest = { generatedRecovery = null },
            title = { Text("کد بازیابی اضطراری") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("این کد را روی کاغذ در جای امن یادداشت کنید؛ در صورت فراموشی رمز، تنها راه بازیابی فوری بدون اینترنت است:")
                    Text(code, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = { Button(onClick = { generatedRecovery = null }) { Text("یادداشت کردم") } }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("بازگشت به حالت پیش‌فرض؟") },
            text = { Text("تمام تنظیمات ظاهری، نوار ابزارها و گزینه‌های خروجی به حالت پیش‌فرض برمی‌گردد (گزارش‌ها و دیتابیس حفظ می‌شوند).") },
            confirmButton = {
                Button(onClick = {
                    val def = vm.db.resetSettings()
                    saveAndApply(def)
                    confirmReset = false
                    Toast.makeText(c, "تنظیمات به حالت پیش‌فرض بازگردانده شد", Toast.LENGTH_SHORT).show()
                }) { Text("بازنشانی") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("انصراف") } }
        )
    }
}

@Composable
fun ToolbarEditorCard(title: String, items: List<String>, onSaveList: (List<String>) -> Unit) {
    var newText by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items.forEach { item ->
                    InputChip(
                        selected = false,
                        onClick = {},
                        label = { Text(item) },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "حذف",
                                modifier = Modifier.size(16.dp).clickable {
                                    onSaveList(items.filterNot { it == item })
                                }
                            )
                        }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                F("افزودن مورد جدید", newText, Modifier.weight(1f)) { newText = it }
                Button(
                    enabled = newText.isNotBlank(),
                    onClick = {
                        val trimmed = newText.trim()
                        if (trimmed !in items) onSaveList(items + trimmed)
                        newText = ""
                    }
                ) { Text("افزودن") }
            }
        }
    }
}

// ---------- پشتیبان‌گیری و انتقال به گوشی دوم ----------
@Composable
fun BackupScreen(vm: VM) {
    val c = LocalContext.current
    var msg by remember { mutableStateOf("") }
    var zipPassword by remember { mutableStateOf("") }

    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { u ->
        if (u != null) {
            vm.viewModelScope.launch {
                msg = "در حال تهیه پشتیبان..."
                msg = withContext(Dispatchers.IO) {
                    runCatching {
                        c.contentResolver.openOutputStream(u)!!.use {
                            Backup.export(c, vm.db, it, zipPassword.ifBlank { null })
                        }
                        "پشتیبان با موفقیت ذخیره شد (آماده انتقال به گوشی دوم)"
                    }.getOrElse { "خطا: ${it.message}" }
                }
            }
        }
    }

    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) {
            vm.viewModelScope.launch {
                msg = "در حال بازیابی..."
                msg = withContext(Dispatchers.IO) {
                    runCatching {
                        val (reps, susps, ppl) = c.contentResolver.openInputStream(u)!!.use {
                            Backup.import(c, vm.db, it, zipPassword.ifBlank { null })
                        }
                        withContext(Dispatchers.Main) {
                            vm.reloadSettings()
                            vm.bumpDataVersion()
                        }
                        "بازیابی کامل شد: $reps گزارش، $susps مورد مشکوک و $ppl فرد دیتابیس اولیه"
                    }.getOrElse { "خطا در بازیابی (در صورت رمزدار بودن فایل، رمز صحیح را وارد کنید): ${it.message}" }
                }
            }
        }
    }

    Page("پشتیبان‌گیری و انتقال اطلاعات", { vm.back() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "این بخش تمام گزارش‌ها، عکس‌های فشرده، فهرست مشکوکین، دیتابیس اولیه افراد و تنظیمات شما را یکجا خروجی می‌گیرد تا به گوشی دوم یا نصب مجدد منتقل کنید.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        F("رمز اختیاری برای فایل پشتیبان (توصیه می‌شود)", zipPassword) { zipPassword = it }
        Button(
            onClick = { exp.launch("kashef-backup-${J.dateCompact()}.zip") },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("گرفتن پشتیبان کامل (خروجی ZIP)") }
        OutlinedButton(
            onClick = { imp.launch(arrayOf("application/zip", "application/octet-stream")) },
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("بازیابی / انتقال از فایل پشتیبان") }
        if (msg.isNotEmpty()) Text(msg, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
