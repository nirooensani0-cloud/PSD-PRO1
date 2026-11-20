@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package ir.mylog.missionlog

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale
import java.util.UUID

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E4078),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E4F7),
    onPrimaryContainer = Color(0xFF0B1F42),
    secondary = Color(0xFF3E5C76),
    secondaryContainer = Color(0xFFE1ECF4),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFE9EFF6),
    background = Color(0xFFF2F5FA)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8BB4F8),
    onPrimary = Color(0xFF0A2246),
    primaryContainer = Color(0xFF1B3A6B),
    onPrimaryContainer = Color(0xFFD9E4F7),
    secondary = Color(0xFF9FB8D0),
    secondaryContainer = Color(0xFF26384A),
    surface = Color(0xFF111620),
    surfaceVariant = Color(0xFF1D2533),
    background = Color(0xFF0C1018)
)

@Composable
fun KashefTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}

@Composable
fun Page(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable RowScope.() -> Unit = {},
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "بازگشت")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { p ->
        val scrollState = rememberScrollState()
        var m = Modifier
            .padding(p)
            .imePadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxSize()
        if (scroll) m = m.verticalScroll(scrollState)
        Column(m, verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun F(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    kb: KeyboardType = KeyboardType.Text,
    lines: Int = 1,
    readOnly: Boolean = false,
    error: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        label = { Text(label) },
        readOnly = readOnly,
        keyboardOptions = KeyboardOptions(keyboardType = kb),
        minLines = lines,
        singleLine = lines == 1,
        isError = error != null,
        trailingIcon = trailingIcon,
        supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        shape = RoundedCornerShape(10.dp)
    )
}

@Composable
fun Drop(
    label: String,
    value: String,
    options: List<String>,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onSel: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                "$label: ${value.ifEmpty { "انتخاب کنید" }}",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Start
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(
                    text = { Text(o) },
                    onClick = {
                        onSel(o)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
fun SectionHeader(t: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
        )
        Text(
            text = t,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

// ---------- تقویم شمسی تعاملی + ورودی متنی ----------
@Composable
fun DateFieldWithPicker(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (String) -> Unit
) {
    var showCal by remember { mutableStateOf(false) }
    F(
        label = label,
        value = value,
        modifier = modifier,
        kb = KeyboardType.Number,
        trailingIcon = {
            IconButton(onClick = { showCal = true }) {
                Icon(Icons.Default.DateRange, contentDescription = "انتخاب از تقویم شمسی")
            }
        },
        onChange = { onChange(norm(it)) }
    )
    if (showCal) {
        JalaliCalendarDialog(
            initialDate = value,
            onSelect = { selected ->
                onChange(selected)
                showCal = false
            },
            onDismiss = { showCal = false }
        )
    }
}

@Composable
fun JalaliCalendarDialog(
    initialDate: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val todayTriple = remember { J.parseDate(J.date()) ?: Triple(1405, 8, 24) }
    val initTriple = remember(initialDate) { J.parseDate(initialDate) ?: todayTriple }
    var year by remember { mutableIntStateOf(initTriple.first) }
    var month by remember { mutableIntStateOf(initTriple.second) }
    var selectedDay by remember { mutableIntStateOf(initTriple.third) }
    var showYearPicker by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تقویم شمسی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = {
                        year = todayTriple.first
                        month = todayTriple.second
                        selectedDay = todayTriple.third
                    }) { Text("امروز") }
                }

                // ناوبری ماه و سال
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (month == 1) { month = 12; year-- } else month--
                    }) { Icon(Icons.Default.ChevronRight, null) }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { showYearPicker = !showYearPicker }) {
                            Text("${J.MONTHS[month - 1]} $year")
                        }
                    }

                    IconButton(onClick = {
                        if (month == 12) { month = 1; year++ } else month++
                    }) { Icon(Icons.Default.ChevronLeft, null) }
                }

                if (showYearPicker) {
                    val years = (1320..1420).toList().reversed()
                    LazyColumn(Modifier.height(220.dp)) {
                        items(years) { yOpt ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        year = yOpt
                                        showYearPicker = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(yOpt.toString(), fontWeight = if (yOpt == year) FontWeight.Bold else FontWeight.Normal)
                                if (yOpt == year) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        J.WEEK_DAYS_SHORT.forEach { wd ->
                            Text(
                                wd,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    val firstIdx = J.firstDayOfMonthWeekIndex(year, month)
                    val totalDays = J.daysInMonth(year, month)
                    val cells = List(firstIdx) { 0 } + (1..totalDays).toList()
                    val rows = cells.chunked(7)
                    rows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            for (i in 0 until 7) {
                                val day = row.getOrElse(i) { 0 }
                                if (day == 0) {
                                    Spacer(Modifier.weight(1f).height(36.dp))
                                } else {
                                    val isSel = day == selectedDay
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(CircleShape)
                                            .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { selectedDay = day },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            day.toString(),
                                            color = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("انصراف") }
                    Button(onClick = {
                        val maxD = J.daysInMonth(year, month)
                        val d = selectedDay.coerceIn(1, maxD)
                        onSelect("%04d/%02d/%02d".format(Locale.US, year, month, d))
                    }) { Text("انتخاب تاریخ") }
                }
            }
        }
    }
}

// ---------- پرچم کوچک ایران و ورودی ساختاریافته پلاک خودرو / موتورسیکلت ----------
@Composable
private fun IranFlag() {
    Column(
        Modifier
            .width(26.dp)
            .height(17.dp)
            .clip(RoundedCornerShape(2.dp))
            .border(0.5.dp, Color(0xFF999999), RoundedCornerShape(2.dp))
    ) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF239F40)))
        Box(
            Modifier.weight(1f).fillMaxWidth().background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(4.dp).background(Color(0xFFDA0000), CircleShape))
        }
        Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFFDA0000)))
    }
}

@Composable
fun PlateInputField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    var lastEmitted by remember { mutableStateOf(value) }
    var isMoto by remember { mutableStateOf(isMotoPlate(value)) }
    var parts by remember { mutableStateOf(if (isMotoPlate(value)) PlateParts() else parsePlate(value)) }
    var motoParts by remember { mutableStateOf(if (isMotoPlate(value)) parseMotoPlate(value) else ("" to "")) }
    var showLetters by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value != lastEmitted) {
            lastEmitted = value
            if (isMotoPlate(value)) {
                isMoto = true
                motoParts = parseMotoPlate(value)
                parts = PlateParts()
            } else {
                isMoto = false
                parts = parsePlate(value)
                motoParts = "" to ""
            }
        }
    }

    fun emitCar(np: PlateParts) {
        parts = np
        val out = formatPlate(np)
        lastEmitted = out
        onChange(out)
    }

    fun emitMoto(d3: String, d5: String) {
        motoParts = d3 to d5
        val out = formatMotoPlate(d3, d5)
        lastEmitted = out
        onChange(out)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (value.isNotBlank()) {
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = !isMoto,
                    onClick = { isMoto = false; emitCar(parts) },
                    label = { Text("پلاک خودرو") }
                )
                FilterChip(
                    selected = isMoto,
                    onClick = { isMoto = true; emitMoto(motoParts.first, motoParts.second) },
                    label = { Text("پلاک موتورسیکلت") }
                )
            }
            if (!isMoto) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = parts.code2,
                        onValueChange = { emitCar(parts.copy(code2 = norm(it).filter { c -> c.isDigit() }.take(2))) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        IranFlag()
                        Text("ایران", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedTextField(
                        value = parts.digits3,
                        onValueChange = { emitCar(parts.copy(digits3 = norm(it).filter { c -> c.isDigit() }.take(3))) },
                        modifier = Modifier.weight(1.1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Box(Modifier.weight(1.0f)) {
                        OutlinedButton(
                            onClick = { showLetters = true },
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(parts.letter.ifEmpty { "حرف" }, maxLines = 1)
                        }
                        DropdownMenu(expanded = showLetters, onDismissRequest = { showLetters = false }) {
                            PLATE_LETTERS.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text(l) },
                                    onClick = {
                                        emitCar(parts.copy(letter = l))
                                        showLetters = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = parts.digits2,
                        onValueChange = { emitCar(parts.copy(digits2 = norm(it).filter { c -> c.isDigit() }.take(2))) },
                        modifier = Modifier.weight(0.9f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = motoParts.first,
                        onValueChange = { emitMoto(norm(it).filter { c -> c.isDigit() }.take(3), motoParts.second) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Text("-", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = motoParts.second,
                        onValueChange = { emitMoto(motoParts.first, norm(it).filter { c -> c.isDigit() }.take(5)) },
                        modifier = Modifier.weight(1.4f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                Text(
                    "قالب پلاک موتورسیکلت: سه رقم + پنج رقم (مثل ۱۲۳ - ۴۵۶۷۸)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------- نقشه هیبرید آفلاین/آنلاین OSMDroid (جاده + ماهواره) ----------
fun initOsmDroid(c: Context) {
    val cfg = Configuration.getInstance()
    cfg.userAgentValue = "KashefMissionLog/2.0"
    cfg.osmdroidBasePath = File(c.cacheDir, "osmdroid").apply { mkdirs() }
    cfg.osmdroidTileCache = File(cfg.osmdroidBasePath, "tiles").apply { mkdirs() }
}

private val ESRI_SATELLITE_SOURCE = object : OnlineTileSourceBase(
    "EsriWorldImagery",
    0, 19, 256, ".jpg",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        "$baseUrl${MapTileIndex.getZoom(pMapTileIndex)}/${MapTileIndex.getY(pMapTileIndex)}/${MapTileIndex.getX(pMapTileIndex)}"
}

@Composable
fun MapPickerDialog(
    initialLat: Double?,
    initialLng: Double?,
    onConfirm: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalContext.current
    LaunchedEffect(Unit) { initOsmDroid(c) }
    var lat by remember { mutableDoubleStateOf(initialLat ?: 35.6892) }
    var lng by remember { mutableDoubleStateOf(initialLng ?: 51.3890) }
    var satellite by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("انتخاب و اصلاح موقعیت روی نقشه", fontWeight = FontWeight.Bold)
                    FilterChip(
                        selected = satellite,
                        onClick = { satellite = !satellite },
                        label = { Text(if (satellite) "حالت ماهواره‌ای" else "نقشه جاده") },
                        leadingIcon = { Icon(Icons.Default.Layers, null, Modifier.size(16.dp)) }
                    )
                }
                Text(
                    "برای جابه‌جایی نشانگر روی نقشه لمس کنید. کاشی‌های بازشده به‌صورت خودکار برای حالت آفلاین ذخیره می‌شوند.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setMultiTouchControls(true)
                                setUseDataConnection(true)
                                controller.setZoom(15.0)
                                controller.setCenter(GeoPoint(lat, lng))
                            }
                        },
                        update = { map ->
                            map.setTileSource(if (satellite) ESRI_SATELLITE_SOURCE else TileSourceFactory.MAPNIK)
                            map.overlays.clear()
                            val marker = Marker(map).apply {
                                position = GeoPoint(lat, lng)
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = "موقعیت گزارش"
                            }
                            map.overlays.add(marker)
                            map.overlays.add(
                                MapEventsOverlay(object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                        lat = p.latitude
                                        lng = p.longitude
                                        return true
                                    }
                                    override fun longPressHelper(p: GeoPoint): Boolean {
                                        lat = p.latitude
                                        lng = p.longitude
                                        return true
                                    }
                                })
                            )
                            map.invalidate()
                        }
                    )
                }

                Text(
                    "مختصات انتخابی: %.6f , %.6f".format(Locale.US, lat, lng),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = {
                        Loc.get(c) { l ->
                            if (l != null) {
                                lat = l.latitude
                                lng = l.longitude
                            } else {
                                Toast.makeText(c, "GPS در دسترس نیست", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) { Text("GPS فعلی") }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = onDismiss) { Text("انصراف") }
                        Button(onClick = { onConfirm(lat, lng) }) { Text("تأیید موقعیت") }
                    }
                }
            }
        }
    }
}

private fun makeColoredPinDrawable(c: Context, argb: Long): BitmapDrawable {
    val size = 44
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val cv = Canvas(bmp)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb.toInt() }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    cv.drawCircle(size / 2f, size / 2f, 16f, fill)
    cv.drawCircle(size / 2f, size / 2f, 16f, stroke)
    return BitmapDrawable(c.resources, bmp)
}

@Composable
fun HotspotMapView(
    reports: List<Report>,
    satellite: Boolean,
    modifier: Modifier = Modifier
) {
    val c = LocalContext.current
    LaunchedEffect(Unit) { initOsmDroid(c) }
    val withCoords = remember(reports) { reports.filter { it.lat != null && it.lng != null } }
    var showFullscreen by remember { mutableStateOf(false) }

    Box(
        modifier
            .fillMaxWidth()
            .height(310.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    setUseDataConnection(true)
                    val first = withCoords.firstOrNull()
                    controller.setZoom(if (first != null) 12.5 else 11.0)
                    controller.setCenter(GeoPoint(first?.lat ?: 35.6892, first?.lng ?: 51.3890))
                }
            },
            update = { map ->
                map.setTileSource(if (satellite) ESRI_SATELLITE_SOURCE else TileSourceFactory.MAPNIK)
                map.overlays.clear()
                withCoords.forEach { rep ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(rep.lat!!, rep.lng!!)
                        icon = makeColoredPinDrawable(c, reportTypeColorHex(rep.type))
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        title = "${rep.type.ifBlank { "گزارش" }} (${rep.number})"
                        snippet = "${rep.incidentDate} • ${rep.location}"
                    }
                    map.overlays.add(marker)
                }
                map.invalidate()
            }
        )
        Surface(
            onClick = { showFullscreen = true },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            color = Color(0xCC000000),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = "تمام‌صفحه", tint = Color.White, modifier = Modifier.size(16.dp))
                Text("تمام‌صفحه", color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (withCoords.isEmpty()) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(10.dp)
                    .background(Color(0xBB000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("گزارشی با مختصات مکانی در این بازه ثبت نشده است", color = Color.White)
            }
        }
    }

    if (showFullscreen) {
        HotspotMapFullscreenDialog(
            reports = reports,
            satellite = satellite,
            onDismiss = { showFullscreen = false }
        )
    }
}

// ---------- نقشه نقاط آلوده تمام‌صفحه با خروجی عکس و PDF ----------
@Composable
fun HotspotMapFullscreenDialog(
    reports: List<Report>,
    satellite: Boolean,
    onDismiss: () -> Unit
) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var sat by remember { mutableStateOf(satellite) }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val withCoords = remember(reports) { reports.filter { it.lat != null && it.lng != null } }
    val legend = remember(withCoords) {
        withCoords.groupBy { it.type.ifBlank { "سایر" } }
            .map { (t, list) -> t to list.size }
            .sortedByDescending { it.second }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("نقشه نقاط آلوده (تمام‌صفحه)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = sat,
                        onClick = { sat = !sat },
                        label = { Text(if (sat) "ماهواره" else "جاده") },
                        leadingIcon = { Icon(Icons.Default.Layers, null, Modifier.size(16.dp)) }
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "بستن")
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setMultiTouchControls(true)
                            setUseDataConnection(true)
                            val first = withCoords.firstOrNull()
                            controller.setZoom(if (first != null) 12.5 else 11.0)
                            controller.setCenter(GeoPoint(first?.lat ?: 35.6892, first?.lng ?: 51.3890))
                        }
                    },
                    update = { map ->
                        mapView = map
                        map.setTileSource(if (sat) ESRI_SATELLITE_SOURCE else TileSourceFactory.MAPNIK)
                        map.overlays.clear()
                        withCoords.forEach { rep ->
                            val marker = Marker(map).apply {
                                position = GeoPoint(rep.lat!!, rep.lng!!)
                                icon = makeColoredPinDrawable(c, reportTypeColorHex(rep.type))
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                                title = "${rep.type.ifBlank { "گزارش" }} (${rep.number})"
                                snippet = "${rep.incidentDate} • ${rep.location}"
                            }
                            map.overlays.add(marker)
                        }
                        map.invalidate()
                    }
                )
            }

            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("راهنمای نقشه:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        legend.forEach { (t, cnt) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(Modifier.size(10.dp).background(Color(reportTypeColorHex(t)), CircleShape))
                                Text("$t ($cnt)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        exporting = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { MapExporter.exportPng(c, mapView, legend) }
                            exporting = false
                            Toast.makeText(c, if (ok) "عکس نقشه ذخیره شد" else "خطا در ذخیره عکس", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !exporting
                ) { Text("دانلود عکس نقشه") }
                Button(
                    onClick = {
                        exporting = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { MapExporter.exportPdf(c, mapView, legend, sat) }
                            exporting = false
                            Toast.makeText(c, if (ok) "PDF نقشه آماده شد" else "خطا در ساخت PDF", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = !exporting
                ) { Text("خروجی PDF نقشه") }
            }
            if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

// ---------- تصاویر با پینوشت و بررسی کیفیت چهره ----------
@Composable
fun Thumb(name: String, modifier: Modifier, max: Int = 300, scale: ContentScale = ContentScale.Crop) {
    val c = LocalContext.current
    val bmp by produceState<Bitmap?>(null, name) {
        value = withContext(Dispatchers.IO) { Img.thumb(c, name, max) }
    }
    bmp?.let { Image(it.asImageBitmap(), null, modifier, contentScale = scale) }
        ?: Box(modifier.background(Color.LightGray))
}

@Composable
fun PhotoStripWithCaptions(
    items: List<PhotoItem>,
    onUpdateCaption: (String, String) -> Unit,
    onRemove: (String) -> Unit
) {
    var big by remember { mutableStateOf<PhotoItem?>(null) }
    var editingCaption by remember { mutableStateOf<PhotoItem?>(null) }
    if (items.isEmpty()) return

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Column(Modifier.width(104.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(96.dp).clip(RoundedCornerShape(10.dp))) {
                    Thumb(item.name, Modifier.fillMaxSize().clickable { big = item })
                    IconButton(
                        onClick = { onRemove(item.name) },
                        modifier = Modifier.align(Alignment.TopStart).size(26.dp).background(Color(0xAA000000))
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { editingCaption = item },
                        modifier = Modifier.align(Alignment.BottomStart).size(26.dp).background(Color(0xAA1E4078))
                    ) {
                        Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                if (item.caption.isNotBlank()) {
                    Text(
                        item.caption,
                        maxLines = 2,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Text(
                        "افزودن پینوشت",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { editingCaption = item }
                    )
                }
            }
        }
    }

    big?.let { item ->
        Dialog({ big = null }) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Thumb(item.name, Modifier.fillMaxWidth().heightIn(max = 520.dp), 1280, ContentScale.Fit)
                    if (item.caption.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(item.caption, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    editingCaption?.let { item ->
        var capText by remember(item) { mutableStateOf(item.caption) }
        AlertDialog(
            onDismissRequest = { editingCaption = null },
            title = { Text("پینوشت تصویر") },
            text = {
                F("توضیح / پینوشت تصویر (اختیاری)", capText, lines = 2) { capText = it }
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateCaption(item.name, capText)
                    editingCaption = null
                }) { Text("ثبت پینوشت") }
            },
            dismissButton = {
                TextButton(onClick = { editingCaption = null }) { Text("انصراف") }
            }
        )
    }
}

@Composable
fun SmartPhotoAdder(
    label: String,
    maxSide: Int = 1280,
    checkFaceQuality: Boolean = false,
    faceEngine: FaceEngine? = null,
    onAdded: (PhotoItem, List<Float>) -> Unit
) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Uri?>(null) }
    var qualityWarning by remember { mutableStateOf<Pair<Bitmap, FaceQualityResult>?>(null) }
    var captionAsk by remember { mutableStateOf<Pair<String, List<Float>>?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun finalizeBitmap(bmp: Bitmap, emb: List<Float>) {
        scope.launch {
            val savedName = withContext(Dispatchers.IO) {
                val n = Img.saveBitmap(c, bmp, 65)
                bmp.recycle()
                n
            }
            if (savedName != null) {
                captionAsk = savedName to emb
            } else {
                Toast.makeText(c, "خطا در ذخیره‌ی عکس", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun processUri(u: Uri, temp: Boolean) {
        scope.launch {
            busy = true
            val decoded = withContext(Dispatchers.IO) {
                val b = Img.decodeOrientedBitmap(c, u, maxSide)
                if (temp) runCatching { c.contentResolver.delete(u, null, null) }
                b
            }
            if (decoded == null) {
                busy = false
                Toast.makeText(c, "خطا در خواندن تصویر", Toast.LENGTH_SHORT).show()
                return@launch
            }
            if (checkFaceQuality && faceEngine != null) {
                val qRes = withContext(Dispatchers.IO) { faceEngine.analyze(decoded) }
                qRes.croppedFace?.recycle()
                busy = false
                if (!qRes.ok) {
                    qualityWarning = decoded to qRes
                } else {
                    finalizeBitmap(decoded, qRes.embedding)
                }
            } else {
                busy = false
                finalizeBitmap(decoded, emptyList())
            }
        }
    }

    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pending
        pending = null
        if (ok && u != null) processUri(u, true)
    }
    val gal = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) processUri(u, false)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = {
                val dir = File(c.cacheDir, "cam").apply { mkdirs() }
                val u = FileProvider.getUriForFile(c, "${c.packageName}.files", File(dir, "${UUID.randomUUID()}.jpg"))
                pending = u
                cam.launch(u)
            },
            enabled = !busy
        ) { Text("دوربین: $label") }

        OutlinedButton(
            onClick = { gal.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !busy
        ) { Text("گالری") }

        if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }

    qualityWarning?.let { (bmp, qRes) ->
        AlertDialog(
            onDismissRequest = {
                bmp.recycle()
                qualityWarning = null
            },
            title = { Text("هشدار کیفیت تصویر چهره") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("موارد زیر در تصویر شناسایی شد:")
                    qRes.issues.forEach { issue ->
                        Text("• $issue", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                    }
                    Text("می‌توانید عکس جدید بگیرید یا در صورت نیاز همین تصویر را تأیید کنید.")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    qualityWarning = null
                    finalizeBitmap(bmp, qRes.embedding)
                }) { Text("تأیید و استفاده از همین عکس") }
            },
            dismissButton = {
                TextButton(onClick = {
                    bmp.recycle()
                    qualityWarning = null
                }) { Text("گرفتن / انتخاب عکس مجدد") }
            }
        )
    }

    captionAsk?.let { (name, emb) ->
        var cap by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = {
                captionAsk = null
                onAdded(PhotoItem(name, ""), emb)
            },
            title = { Text("پینوشت تصویر (اختیاری)") },
            text = {
                F("توضیح یا پینوشت برای این عکس", cap, lines = 2) { cap = it }
            },
            confirmButton = {
                Button(onClick = {
                    captionAsk = null
                    onAdded(PhotoItem(name, cap.trim()), emb)
                }) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = {
                    captionAsk = null
                    onAdded(PhotoItem(name, ""), emb)
                }) { Text("بدون پینوشت") }
            }
        )
    }
}

// ---------- دیالوگ جستجو با چهره ----------
@Composable
fun FaceSearchDialog(
    db: Db,
    faceEngine: FaceEngine,
    onSelectMatch: (FaceMatchItem) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var matches by remember { mutableStateOf<List<FaceMatchItem>?>(null) }
    var warningPair by remember { mutableStateOf<Pair<Bitmap, FaceQualityResult>?>(null) }

    fun runSearch(bmp: Bitmap, embedding: List<Float>) {
        scope.launch {
            busy = true
            val res = withContext(Dispatchers.IO) {
                val list = faceEngine.searchMatches(db, embedding)
                bmp.recycle()
                list
            }
            busy = false
            matches = res
        }
    }

    fun analyzeUri(u: Uri, temp: Boolean) {
        scope.launch {
            busy = true
            val bmp = withContext(Dispatchers.IO) {
                val b = Img.decodeOrientedBitmap(c, u, 800)
                if (temp) runCatching { c.contentResolver.delete(u, null, null) }
                b
            }
            if (bmp == null) {
                busy = false
                Toast.makeText(c, "خطا در خواندن تصویر", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val qRes = withContext(Dispatchers.IO) { faceEngine.analyze(bmp) }
            qRes.croppedFace?.recycle()
            busy = false
            if (!qRes.ok) {
                warningPair = bmp to qRes
            } else {
                runSearch(bmp, qRes.embedding)
            }
        }
    }

    val cam = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingUri; pendingUri = null
        if (ok && u != null) analyzeUri(u, true)
    }
    val gal = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u ->
        if (u != null) analyzeUri(u, false)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("جستجوی چهره در دیتابیس کاشف", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "تصویر چهره را از دوربین یا گالری انتخاب کنید تا با سوابق گزارش‌ها، مشکوکین و دیتابیس اولیه تطبیق داده شود.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val dir = File(c.cacheDir, "cam").apply { mkdirs() }
                            val u = FileProvider.getUriForFile(c, "${c.packageName}.files", File(dir, "${UUID.randomUUID()}.jpg"))
                            pendingUri = u
                            cam.launch(u)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("دوربین") }
                    OutlinedButton(
                        onClick = { gal.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("از گالری") }
                }

                if (busy) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }

                matches?.let { list ->
                    if (list.isEmpty()) {
                        Text("چهره مشابهی در دیتابیس یافت نشد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(list) { m ->
                                Card(
                                    Modifier.fillMaxWidth().clickable { onSelectMatch(m) },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (m.photoName.isNotEmpty()) {
                                            Thumb(m.photoName, Modifier.size(54.dp).clip(RoundedCornerShape(8.dp)))
                                        }
                                        Column(Modifier.weight(1f)) {
                                            Text(m.title, fontWeight = FontWeight.Bold)
                                            Text(m.subtitle, style = MaterialTheme.typography.bodySmall)
                                        }
                                        AssistChip(onClick = { onSelectMatch(m) }, label = { Text("${m.similarityPercent}%") })
                                    }
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("بستن") }
                }
            }
        }
    }

    warningPair?.let { (bmp, qRes) ->
        AlertDialog(
            onDismissRequest = { bmp.recycle(); warningPair = null },
            title = { Text("هشدار کیفیت تصویر ورودی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    qRes.issues.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
                    Text("آیا می‌خواهید با وجود این خطاها جستجو با همین عکس انجام شود؟")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    warningPair = null
                    runSearch(bmp, qRes.embedding)
                }) { Text("تأیید و پردازش همین عکس") }
            },
            dismissButton = {
                TextButton(onClick = { bmp.recycle(); warningPair = null }) { Text("انتخاب مجدد") }
            }
        )
    }
}
