package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppScreen
import com.example.data.TrackCalculator
import com.example.data.TrackStatus
import com.example.data.model.InspectionSession
import com.example.data.model.TrackMeasurement
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

import kotlinx.coroutines.launch

enum class MeasurementMode(val label: String) {
    GAUGE_ONLY("A. Gauge only for Plain Track"),
    GAUGE_AND_CROSS_LEVEL("B. Gauge and cross levels for Plain Track"),
    CURVE_MEASUREMENT("C. Curve Measurement"),
    PC_MEASUREMENT("D. P&C measurement")
}

@Composable
fun TrackAppUi(
    viewModel: TrackViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val selectedSession by viewModel.selectedSession.collectAsState()
    val measurements by viewModel.measurements.collectAsState()
    val sessionInEdit by viewModel.sessionInEdit.collectAsState()

    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("track_inspector_prefs", Context.MODE_PRIVATE) }

    var isRegistered by remember { mutableStateOf(sharedPref.getBoolean("is_registered", false)) }
    var isLoggedIn by remember { mutableStateOf(false) }
    var loggedInUser by remember { mutableStateOf(sharedPref.getString("user_name", "Ankith R V") ?: "Ankith R V") }
    var loggedInDesignation by remember { mutableStateOf(sharedPref.getString("user_designation", "Assistant Divisional Engineer") ?: "Assistant Divisional Engineer") }

    var selectedMode by remember { mutableStateOf(MeasurementMode.GAUGE_AND_CROSS_LEVEL) }

    var gaugeOnlySessionInEdit by remember { mutableStateOf<InspectionSession?>(null) }
    var selectedGaugeOnlySession by remember { mutableStateOf<InspectionSession?>(null) }

    val coroutineScope = rememberCoroutineScope()

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (!isRegistered) {
            RegistrationScreen(
                onRegistrationComplete = { name, gender, dob, desig, zone, division, mobile, email, pass, bioEnabled ->
                    sharedPref.edit().apply {
                        putBoolean("is_registered", true)
                        putString("user_name", name)
                        putString("user_gender", gender)
                        putString("user_dob", dob)
                        putString("user_designation", desig)
                        putString("user_zone", zone)
                        putString("user_division", division)
                        putString("user_mobile", mobile)
                        putString("user_email", email)
                        putString("user_password", pass)
                        putBoolean("biometrics_enabled", bioEnabled)
                        apply()
                    }
                    loggedInUser = name
                    loggedInDesignation = desig
                    isRegistered = true
                    isLoggedIn = true
                }
            )
        } else if (!isLoggedIn) {
            LoginScreen(
                registeredName = loggedInUser,
                registeredDesignation = loggedInDesignation,
                storedPassword = sharedPref.getString("user_password", "") ?: "",
                biometricsEnabled = sharedPref.getBoolean("biometrics_enabled", false),
                onLoginSuccess = {
                    isLoggedIn = true
                }
            )
        } else if (gaugeOnlySessionInEdit != null) {
            GaugeOnlySetupScreen(
                session = gaugeOnlySessionInEdit,
                onSave = { blockSection, chainage, interval, nominalG, gaugeTypeRemarks ->
                    val formattedRemarks = "[GAUGE_ONLY] | Chainage: $chainage | Type: $gaugeTypeRemarks"
                    val isNew = gaugeOnlySessionInEdit!!.id == 0L
                    coroutineScope.launch {
                        val toSave = gaugeOnlySessionInEdit!!.copy(
                            sectionName = blockSection,
                            intervalMeters = interval,
                            nominalGauge = nominalG,
                            remarks = formattedRemarks,
                            isCurve = false,
                            designSuperelevation = 0.0
                        )
                        if (isNew) {
                            val newId = viewModel.insertSessionDirectly(toSave)
                            val created = toSave.copy(id = newId)
                            selectedGaugeOnlySession = created
                        } else {
                            viewModel.updateSessionDirectly(toSave)
                            selectedGaugeOnlySession = toSave
                        }
                        gaugeOnlySessionInEdit = null
                    }
                },
                onCancel = {
                    gaugeOnlySessionInEdit = null
                }
            )
        } else if (selectedGaugeOnlySession != null) {
            GaugeOnlyRecorderScreen(
                session = selectedGaugeOnlySession!!,
                viewModel = viewModel,
                onBack = {
                    selectedGaugeOnlySession = null
                }
            )
        } else {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    AppScreen.DASHBOARD -> {
                        DashboardScreen(
                            sessions = sessions,
                            onSessionSelect = { session ->
                                if (session.remarks.startsWith("[GAUGE_ONLY]")) {
                                    selectedGaugeOnlySession = session
                                } else {
                                    viewModel.selectSession(session)
                                    viewModel.navigateTo(AppScreen.TRACK_RECORDER)
                                }
                            },
                            onSessionDelete = { session ->
                                viewModel.deleteSession(session)
                            },
                            onSessionEdit = { session ->
                                if (session.remarks.startsWith("[GAUGE_ONLY]")) {
                                    gaugeOnlySessionInEdit = session
                                } else {
                                    viewModel.prepareEditSession(session)
                                }
                            },
                            onNewSessionClick = {
                                if (selectedMode == MeasurementMode.GAUGE_ONLY) {
                                    gaugeOnlySessionInEdit = InspectionSession(sectionName = "", remarks = "[GAUGE_ONLY]")
                                } else {
                                    viewModel.prepareNewSession()
                                }
                            },
                            selectedMode = selectedMode,
                            onModeSelect = { selectedMode = it },
                            loggedInUser = loggedInUser,
                            loggedInDesignation = loggedInDesignation
                        )
                    }
                    AppScreen.SESSION_SETUP -> {
                        SessionSetupScreen(
                            session = sessionInEdit,
                            onSave = { name, startKm, interval, nominalG, isCurve, designSe, remarks ->
                                viewModel.saveSession(name, startKm, interval, nominalG, isCurve, designSe, remarks)
                            },
                            onCancel = {
                                if (selectedSession != null) {
                                    viewModel.navigateTo(AppScreen.TRACK_RECORDER)
                                } else {
                                    viewModel.navigateTo(AppScreen.DASHBOARD)
                                }
                            }
                        )
                    }
                    AppScreen.TRACK_RECORDER -> {
                        TrackRecorderScreen(
                            session = selectedSession,
                            measurements = measurements,
                            onBack = {
                                viewModel.clearSelectedSession()
                                viewModel.navigateTo(AppScreen.DASHBOARD)
                            },
                            onAddMeasurement = { cl, g, rem ->
                                viewModel.addMeasurement(cl, g, rem)
                            },
                            onUpdateMeasurement = { m ->
                                viewModel.updateMeasurement(m)
                            },
                            onDeleteMeasurement = { m ->
                                viewModel.deleteMeasurement(m)
                            },
                            onLoadSampleData = {
                                viewModel.generateSampleDataForSession()
                            },
                            onClearAll = {
                                viewModel.clearAllMeasurementsForSession()
                            },
                            onViewAnalytics = {
                                viewModel.navigateTo(AppScreen.ANALYTICS)
                            },
                            onEditSessionSettings = {
                                selectedSession?.let { viewModel.prepareEditSession(it) }
                            }
                        )
                    }
                    AppScreen.ANALYTICS -> {
                        AnalyticsScreen(
                            session = selectedSession,
                            measurements = measurements,
                            onBack = {
                                viewModel.navigateTo(AppScreen.TRACK_RECORDER)
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(
    sessions: List<InspectionSession>,
    onSessionSelect: (InspectionSession) -> Unit,
    onSessionDelete: (InspectionSession) -> Unit,
    onSessionEdit: (InspectionSession) -> Unit,
    onNewSessionClick: () -> Unit,
    selectedMode: MeasurementMode,
    onModeSelect: (MeasurementMode) -> Unit,
    loggedInUser: String,
    loggedInDesignation: String
) {
    val initials = remember(loggedInUser) {
        loggedInUser.split(" ")
            .filter { it.isNotEmpty() }
            .map { it[0] }
            .joinToString("")
            .take(2)
            .uppercase()
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewSessionClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(24.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = "New Session") },
                text = { Text("New Session") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Header Bar (Geometric Balance Theme)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    var showModeMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { showModeMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFDDE2F1))
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Measurement Modes Menu",
                                tint = Color(0xFF44474E)
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = { showModeMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
                        ) {
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (selectedMode == MeasurementMode.GAUGE_ONLY) Icons.Default.Check else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (selectedMode == MeasurementMode.GAUGE_ONLY) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = { Text("A. Gauge only for Plain Track", fontWeight = if (selectedMode == MeasurementMode.GAUGE_ONLY) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onModeSelect(MeasurementMode.GAUGE_ONLY)
                                    showModeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (selectedMode == MeasurementMode.GAUGE_AND_CROSS_LEVEL) Icons.Default.Check else Icons.Default.Info,
                                        contentDescription = null,
                                        tint = if (selectedMode == MeasurementMode.GAUGE_AND_CROSS_LEVEL) MaterialTheme.colorScheme.primary else Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = { Text("B. Gauge and cross levels for Plain Track", fontWeight = if (selectedMode == MeasurementMode.GAUGE_AND_CROSS_LEVEL) FontWeight.Bold else FontWeight.Normal) },
                                onClick = {
                                    onModeSelect(MeasurementMode.GAUGE_AND_CROSS_LEVEL)
                                    showModeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = { Text("C. Curve Measurement (Future)", color = Color.Gray) },
                                onClick = {
                                    onModeSelect(MeasurementMode.CURVE_MEASUREMENT)
                                    showModeMenu = false
                                }
                            )
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = { Text("D. P&C measurement (Future)", color = Color.Gray) },
                                onClick = {
                                    onModeSelect(MeasurementMode.PC_MEASUREMENT)
                                    showModeMenu = false
                                }
                            )
                        }
                    }
                    Column {
                        Text(
                            text = loggedInUser,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = loggedInDesignation.uppercase(),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = Color(0xFF74777F),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (initials.isNotEmpty()) initials else "JE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Header Banner using a custom canvas drawing of railway tracks
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .drawBehind {
                        // Background gradient
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF005AC1), Color(0xFF0F172A))
                            )
                        )

                        // Draw stylized perspective tracks in background
                        val path = Path().apply {
                            // Left rail
                            moveTo(size.width * 0.35f, size.height)
                            lineTo(size.width * 0.48f, size.height * 0.2f)
                            // Right rail
                            moveTo(size.width * 0.65f, size.height)
                            lineTo(size.width * 0.52f, size.height * 0.2f)
                        }
                        drawPath(path, Color(0x33FFB300), style = Stroke(width = 8f))

                        // Draw sleepers (horizontal ties) with fading alpha
                        for (i in 0..10) {
                            val ratio = i / 10f
                            val y = size.height * (0.2f + 0.8f * ratio * ratio)
                            val widthRatio = 0.04f + 0.26f * ratio * ratio
                            val startX = size.width * (0.5f - widthRatio)
                            val endX = size.width * (0.5f + widthRatio)
                            drawLine(
                                color = Color(0x22FFFFFF),
                                start = Offset(startX, y),
                                end = Offset(endX, y),
                                strokeWidth = 10f * ratio
                            )
                        }
                    }
                    .padding(24.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column {
                    Text(
                        text = "TRACK INSPECTOR",
                        color = Color(0xFFFBC02D),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Track Geometry Calculation & Diagnostics Panel",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .widthIn(max = 600.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Permanent Safety thoughts banner at the top of Dashboard
                SafetyThoughtsBanner()

                // Statistics Summary Card (Geometric Balance Theme style)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, Color(0xFFADC6FF).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ACTIVE SURVEY SECTIONS",
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${sessions.size} Sections Recorded",
                                color = MaterialTheme.colorScheme.onSecondary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.6f))
                                .border(1.dp, Color.White, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "BG 1676mm",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Text(
                    text = "Recent Inspections",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "No sessions",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No inspection records yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the '+' button to start logging a track section.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        sessions.forEach { session ->
                            SessionItemCard(
                                session = session,
                                onClick = { onSessionSelect(session) },
                                onDelete = { onSessionDelete(session) },
                                onEdit = { onSessionEdit(session) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionItemCard(
    session: InspectionSession,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    val dateStr = formatter.format(Date(session.date))

    val isGaugeOnly = session.remarks.startsWith("[GAUGE_ONLY]")
    val chainageValue = if (isGaugeOnly) {
        session.remarks.substringAfter("Chainage: ").substringBefore(" |")
    } else ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.sectionName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isGaugeOnly) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xE8E5F9E0))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "GAUGE ONLY",
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateStr,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit settings",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SessionParameterBadge(
                        label = "Interval",
                        value = "${session.intervalMeters} m"
                    )
                }
                Box(modifier = Modifier.weight(1.2f)) {
                    SessionParameterBadge(
                        label = if (isGaugeOnly) "Chainage" else "Start KM",
                        value = if (isGaugeOnly) chainageValue else "Km ${String.format("%.3f", session.startKm)}"
                    )
                }
                Box(modifier = Modifier.weight(1.4f)) {
                    SessionParameterBadge(
                        label = "Type",
                        value = if (isGaugeOnly) "Gauge Only" else if (session.isCurve) "Curve (${session.designSuperelevation}mm)" else "Straight"
                    )
                }
            }
        }
    }
}

@Composable
fun SessionParameterBadge(label: String, value: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label.uppercase(),
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// --- SESSION SETUP SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionSetupScreen(
    session: InspectionSession?,
    onSave: (name: String, startKm: Double, interval: Double, nominalG: Double, isCurve: Boolean, designSe: Double, remarks: String) -> Unit,
    onCancel: () -> Unit
) {
    if (session == null) return

    var sectionName by remember { mutableStateOf(session.sectionName) }
    var startKmStr by remember { mutableStateOf(if (session.id == 0L) "" else session.startKm.toString()) }
    var intervalStr by remember { mutableStateOf(if (session.id == 0L) "3.0" else session.intervalMeters.toString()) }
    var nominalGStr by remember { mutableStateOf(if (session.id == 0L) "1676.0" else session.nominalGauge.toString()) }
    var isCurve by remember { mutableStateOf(session.isCurve) }
    var designSeStr by remember { mutableStateOf(if (session.id == 0L) "0.0" else session.designSuperelevation.toString()) }
    var remarks by remember { mutableStateOf(session.remarks) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (session.id == 0L) "New Track Inspection" else "Edit Session Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .widthIn(max = 500.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = sectionName,
                onValueChange = { sectionName = it },
                label = { Text("Track / Section Name") },
                placeholder = { Text("e.g., Up Main Line - Km 120-121") },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = startKmStr,
                    onValueChange = { startKmStr = it },
                    label = { Text("Start KM") },
                    placeholder = { Text("e.g., 120.450") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                OutlinedTextField(
                    value = intervalStr,
                    onValueChange = { intervalStr = it },
                    label = { Text("Interval (m)") },
                    placeholder = { Text("3.0") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nominalGStr,
                    onValueChange = { nominalGStr = it },
                    label = { Text("Nominal Gauge (mm)") },
                    placeholder = { Text("1676") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .align(Alignment.CenterVertically)
                ) {
                    Column {
                        Text(
                            "Nominal Standard",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InputChip(
                                selected = nominalGStr == "1676.0",
                                onClick = { nominalGStr = "1676.0" },
                                label = { Text("BG") }
                            )
                            InputChip(
                                selected = nominalGStr == "1435.0",
                                onClick = { nominalGStr = "1435.0" },
                                label = { Text("SG") }
                            )
                        }
                    }
                }
            }

            // Curve Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Curve Section?", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("Enables superelevation offset calculations", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isCurve,
                            onCheckedChange = { isCurve = it }
                        )
                    }

                    if (isCurve) {
                        OutlinedTextField(
                            value = designSeStr,
                            onValueChange = { designSeStr = it },
                            label = { Text("Design Superelevation (SE) in mm") },
                            placeholder = { Text("e.g., 60") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            OutlinedTextField(
                value = remarks,
                onValueChange = { remarks = it },
                label = { Text("General Remarks (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    val startKm = startKmStr.toDoubleOrNull() ?: 0.0
                    val interval = intervalStr.toDoubleOrNull() ?: 3.0
                    val nominalG = nominalGStr.toDoubleOrNull() ?: 1676.0
                    val designSe = designSeStr.toDoubleOrNull() ?: 0.0
                    onSave(sectionName, startKm, interval, nominalG, isCurve, designSe, remarks)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("Save and Start Recording", fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(25.dp)
            ) {
                Text("Cancel")
            }
        }
    }
}

// --- TRACK RECORDER SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackRecorderScreen(
    session: InspectionSession?,
    measurements: List<TrackMeasurement>,
    onBack: () -> Unit,
    onAddMeasurement: (crossLevel: Double, gauge: Double, remarks: String) -> Unit,
    onUpdateMeasurement: (TrackMeasurement) -> Unit,
    onDeleteMeasurement: (TrackMeasurement) -> Unit,
    onLoadSampleData: () -> Unit,
    onClearAll: () -> Unit,
    onViewAnalytics: () -> Unit,
    onEditSessionSettings: () -> Unit
) {
    if (session == null) return

    // Entry State
    var selectedForEdit by remember { mutableStateOf<TrackMeasurement?>(null) }
    var crossLevelInput by remember { mutableStateOf(0.0) }
    var gaugeInput by remember { mutableStateOf(session.nominalGauge) }
    var stationRemarksInput by remember { mutableStateOf("") }

    // Synchronize inputs when a station is clicked to edit
    LaunchedEffect(selectedForEdit) {
        if (selectedForEdit != null) {
            crossLevelInput = selectedForEdit!!.crossLevel
            gaugeInput = selectedForEdit!!.gauge
            stationRemarksInput = selectedForEdit!!.remarks
        } else {
            crossLevelInput = session.designSuperelevation
            gaugeInput = session.nominalGauge
            stationRemarksInput = ""
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(session.sectionName, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Km ${String.format("%.3f", session.startKm)} • Interval ${session.intervalMeters}m",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Dashboard")
                    }
                },
                actions = {
                    IconButton(onClick = onEditSessionSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Edit Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Total Stations Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("TOTAL STATIONS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("${measurements.size}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }

                // Worst Status Card
                val worstStatus = remember(measurements) {
                    if (measurements.isEmpty()) TrackStatus.EXCELLENT
                    else {
                        val statuses = measurements.mapIndexed { index, m ->
                            val previousCl = if (index > 0) measurements[index - 1].crossLevel else null
                            val twistVal = if (previousCl != null) {
                                abs(m.crossLevel - previousCl) / session.intervalMeters
                            } else 0.0

                            TrackStatus.getWorstOf(
                                TrackCalculator.interpretCrossLevel(m.crossLevel, session.designSuperelevation),
                                TrackCalculator.interpretGauge(m.gauge, session.nominalGauge),
                                TrackCalculator.interpretTwist(twistVal)
                            )
                        }
                        statuses.maxByOrNull { it.level } ?: TrackStatus.EXCELLENT
                    }
                }

                Card(
                    modifier = Modifier.weight(1.2f),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(android.graphics.Color.parseColor(worstStatus.colorHex)).copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color(android.graphics.Color.parseColor(worstStatus.colorHex)).copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("SECTION STATUS", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            worstStatus.label,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(android.graphics.Color.parseColor(worstStatus.colorHex))
                        )
                    }
                }
            }

            // TACTILE INPUT CONTAINER
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title showing current index
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedForEdit != null) "Editing Station #${selectedForEdit!!.stationIndex}" else "New Station #${measurements.size}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Text(
                            text = "Km ${String.format("%.3f", session.startKm + ((selectedForEdit?.stationIndex ?: measurements.size) * session.intervalMeters) / 1000.0)}",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    // ADJUSTERS ROW (Cross Level & Gauge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // CROSS LEVEL ADJUSTER
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(
                                    width = if (crossLevelInput != session.designSuperelevation) 2.dp else 1.dp,
                                    color = if (crossLevelInput != session.designSuperelevation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "CROSS LEVEL",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (crossLevelInput != session.designSuperelevation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = String.format("%+1.1f", crossLevelInput),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (crossLevelInput == 0.0) MaterialTheme.colorScheme.onSurface
                                    else if (crossLevelInput > 0) Color(0xFF1E88E5) else Color(0xFFE53935)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Custom tactile adjust buttons for cross level
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { crossLevelInput -= 1.0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("-1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { crossLevelInput += 1.0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("+1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { crossLevelInput -= 5.0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("-5", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { crossLevelInput = session.designSuperelevation },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("RST", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                    Button(
                                        onClick = { crossLevelInput += 5.0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("+5", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        // GAUGE ADJUSTER
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.background)
                                .border(
                                    width = if (gaugeInput != session.nominalGauge) 2.dp else 1.dp,
                                    color = if (gaugeInput != session.nominalGauge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(12.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "GAUGE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (gaugeInput != session.nominalGauge) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = String.format("%.1f", gaugeInput),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                // Custom tactile adjust buttons for Gauge
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { gaugeInput -= 1.0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("-1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { gaugeInput += 1.0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("+1", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { gaugeInput -= 5.0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("-5", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Button(
                                        onClick = { gaugeInput = session.nominalGauge },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("RST", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                                    }
                                    Button(
                                        onClick = { gaugeInput += 5.0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Text("+5", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    // STATION REMARKS
                    OutlinedTextField(
                        value = stationRemarksInput,
                        onValueChange = { stationRemarksInput = it },
                        placeholder = { Text("Station remarks (low joint, missing key, etc.)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    // SAVE / ACTIONS ROW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (selectedForEdit != null) {
                            OutlinedButton(
                                onClick = { selectedForEdit = null },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Cancel")
                            }
                            Button(
                                onClick = {
                                    val updated = selectedForEdit!!.copy(
                                        crossLevel = crossLevelInput,
                                        gauge = gaugeInput,
                                        remarks = stationRemarksInput
                                    )
                                    onUpdateMeasurement(updated)
                                    selectedForEdit = null
                                },
                                modifier = Modifier.weight(1.5f).height(48.dp),
                                shape = RoundedCornerShape(24.dp)
                            ) {
                                Text("Update Station")
                            }
                        } else {
                            Button(
                                onClick = {
                                    onAddMeasurement(crossLevelInput, gaugeInput, stationRemarksInput)
                                    // Reset values for next station
                                    crossLevelInput = session.designSuperelevation
                                    gaugeInput = session.nominalGauge
                                    stationRemarksInput = ""
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(26.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Log Measurement", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }

            // TOOLBAR / CONTROLS FOR TABLE
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATIONS DATA LOG",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onLoadSampleData, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Demo Data", fontSize = 12.sp)
                    }
                    if (measurements.isNotEmpty()) {
                        TextButton(onClick = onClearAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text("Reset", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }

            // MEASUREMENT LIST TABLE
            if (measurements.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "No station logs yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Add stations above or click 'Demo Data' to preview analysis.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Header row
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                    .padding(vertical = 8.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("STN", modifier = Modifier.weight(0.7f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("KM", modifier = Modifier.weight(1.3f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("CL (mm)", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("G (mm)", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("TW (mm/m)", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Text("ACT", modifier = Modifier.weight(0.8f), fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.End)
                            }
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }

                        items(measurements) { measurement ->
                            val previousCl = if (measurement.stationIndex > 0) {
                                measurements.getOrNull(measurement.stationIndex - 1)?.crossLevel
                            } else null

                            val twistVal = if (previousCl != null) {
                                abs(measurement.crossLevel - previousCl) / session.intervalMeters
                            } else 0.0

                            val clStatus = TrackCalculator.interpretCrossLevel(measurement.crossLevel, session.designSuperelevation)
                            val gStatus = TrackCalculator.interpretGauge(measurement.gauge, session.nominalGauge)
                            val twStatus = if (previousCl != null) TrackCalculator.interpretTwist(twistVal) else TrackStatus.EXCELLENT

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (selectedForEdit?.id == measurement.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else Color.Transparent
                                    )
                                    .clickable { selectedForEdit = measurement }
                                    .padding(vertical = 10.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Station Index
                                Text(
                                    "#${measurement.stationIndex}",
                                    modifier = Modifier.weight(0.7f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                // Km distance
                                Text(
                                    String.format("%.3f", session.startKm + (measurement.distanceMeters / 1000.0)),
                                    modifier = Modifier.weight(1.3f),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Cross Level
                                Box(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val dev = measurement.crossLevel - session.designSuperelevation
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = String.format("%+1.1f", measurement.crossLevel),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(android.graphics.Color.parseColor(clStatus.colorHex))
                                        )
                                        if (dev != 0.0) {
                                            Text(
                                                text = "(${String.format("%+1.0f", dev)})",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                // Gauge
                                Box(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val dev = measurement.gauge - session.nominalGauge
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = String.format("%.1f", measurement.gauge),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(android.graphics.Color.parseColor(gStatus.colorHex))
                                        )
                                        if (dev != 0.0) {
                                            Text(
                                                text = "(${String.format("%+1.0f", dev)})",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                // Twist
                                Box(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .padding(horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (previousCl != null) {
                                        Text(
                                            text = String.format("%.2f", twistVal),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(android.graphics.Color.parseColor(twStatus.colorHex))
                                        )
                                    } else {
                                        Text(
                                            text = "—",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                        )
                                    }
                                }

                                // Actions (Delete)
                                Box(
                                    modifier = Modifier.weight(0.8f),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    IconButton(
                                        onClick = { onDeleteMeasurement(measurement) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            Divider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }

            // ANALYTICS NAV BOTTOM BAR
            if (measurements.size >= 2) {
                Button(
                    onClick = onViewAnalytics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "View Analysis")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("View Charts & Maintenance Report", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// --- ANALYTICS SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    session: InspectionSession?,
    measurements: List<TrackMeasurement>,
    onBack: () -> Unit
) {
    if (session == null) return

    val totalStations = measurements.size

    // Calculate core metrics
    val clList = measurements.map { it.crossLevel }
    val clDevList = measurements.map { abs(it.crossLevel - session.designSuperelevation) }
    val gDevList = measurements.map { it.gauge - session.nominalGauge }

    // Consecutive twist values
    val twistValues = remember(measurements) {
        val list = mutableListOf<Double>()
        for (i in 1 until measurements.size) {
            val prev = measurements[i - 1].crossLevel
            val curr = measurements[i].crossLevel
            list.add(abs(curr - prev) / session.intervalMeters)
        }
        list
    }

    val maxClDev = clDevList.maxOrNull() ?: 0.0
    val maxClDevIdx = clDevList.indexOf(maxClDev)

    val maxTwist = twistValues.maxOrNull() ?: 0.0
    val maxTwistIdx = if (twistValues.isNotEmpty()) twistValues.indexOf(maxTwist) + 1 else 0

    val clSD = remember(clList) { TrackCalculator.calculateStandardDeviation(clList) }

    val worstGaugeDev = remember(gDevList) {
        if (gDevList.isEmpty()) 0.0
        else {
            val maxWide = gDevList.maxOrNull() ?: 0.0
            val maxTight = gDevList.minOrNull() ?: 0.0
            if (abs(maxWide) > abs(maxTight)) maxWide else maxTight
        }
    }

    val recommendations = remember(measurements) {
        TrackCalculator.getMaintenanceRecommendation(
            worstTwist = maxTwist,
            worstClDev = maxClDev,
            worstGaugeDev = worstGaugeDev,
            avgTwist = if (twistValues.isEmpty()) 0.0 else twistValues.average(),
            clSD = clSD
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Track Analytics & Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .widthIn(max = 600.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            Text(
                text = "GEOMETRY SUMMARY REPORT",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // KPI Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "Max Twist",
                    value = String.format("%.2f mm/m", maxTwist),
                    subtitle = "Stn #$maxTwistIdx",
                    color = Color(android.graphics.Color.parseColor(TrackCalculator.interpretTwist(maxTwist).colorHex)),
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Max CL Dev",
                    value = String.format("%.1f mm", maxClDev),
                    subtitle = "Stn #$maxClDevIdx",
                    color = Color(android.graphics.Color.parseColor(TrackCalculator.interpretCrossLevel(clDevList.maxOrNull() ?: 0.0 + session.designSuperelevation, session.designSuperelevation).colorHex)),
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                KpiCard(
                    title = "CL Std. Dev (SD)",
                    value = String.format("%.2f mm", clSD),
                    subtitle = if (clSD <= 3.0) "Excellent Quality" else "Maintenance Target",
                    color = if (clSD <= 3.0) Color(0xFF2E7D32) else if (clSD <= 5.0) Color(0xFFE65100) else Color(0xFFC62828),
                    modifier = Modifier.weight(1f)
                )

                KpiCard(
                    title = "Peak Gauge Dev",
                    value = String.format("%+1.1f mm", worstGaugeDev),
                    subtitle = "Nominal: ${session.nominalGauge.toInt()}mm",
                    color = Color(android.graphics.Color.parseColor(TrackCalculator.interpretGauge(session.nominalGauge + worstGaugeDev, session.nominalGauge).colorHex)),
                    modifier = Modifier.weight(1f)
                )
            }

            // CUSTOM PROFILE CHART CANVAS
            Card(
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Track Parameter Profile Chart",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Shows variations across track stations",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Draw the profile on Canvas
                    TrackProfileChart(
                        measurements = measurements,
                        nominalGauge = session.nominalGauge,
                        designSE = session.designSuperelevation,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Chart Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        LegendItem(color = Color(0xFF1E88E5), label = "Cross Level Deviation")
                        LegendItem(color = Color(0xFFFBC02D), label = "Gauge Deviation")
                        LegendItem(color = Color(0xFFD84315), label = "Twist Rate")
                    }
                }
            }

            // MAINTENANCE & REMEDIAL ACTIONS
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Remedial Actions",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Engineers Diagnostics & Advice",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)

                    recommendations.forEach { recommendation ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("•", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Text(
                                text = recommendation,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Custom painter for track parameters profile
@Composable
fun TrackProfileChart(
    measurements: List<TrackMeasurement>,
    nominalGauge: Double,
    designSE: Double,
    modifier: Modifier = Modifier
) {
    val sizeClasses = LocalConfiguration.current
    Canvas(modifier = modifier) {
        if (measurements.size < 2) return@Canvas

        val w = size.width
        val h = size.height
        val totalStns = measurements.size
        val maxIndex = totalStns - 1

        // Horizontal axes and baselines
        val baselineY = h * 0.5f
        drawLine(
            color = Color(0x3394A3B8),
            start = Offset(0f, baselineY),
            end = Offset(w, baselineY),
            strokeWidth = 2f
        )

        // Threshold limit bounds (visual guide bands)
        // Draw standard tolerable zone band (+- 6mm)
        val toleranceClPix = 6.0f * 3.0f // Scale: 1mm = 3 pixels
        drawRect(
            color = Color(0x0F4CAF50),
            topLeft = Offset(0f, baselineY - toleranceClPix),
            size = Size(w, toleranceClPix * 2f)
        )

        // Map stations to points and plot paths
        val xStep = w / maxIndex

        val clPath = Path()
        val gPath = Path()
        val twPath = Path()

        measurements.forEachIndexed { idx, m ->
            val x = idx * xStep

            // Cross Level Deviation
            val clDev = m.crossLevel - designSE
            // Gauge Deviation
            val gDev = m.gauge - nominalGauge
            // Twist
            val previousCl = if (idx > 0) measurements[idx - 1].crossLevel else null
            val twVal = if (previousCl != null) {
                abs(m.crossLevel - previousCl) / (m.distanceMeters - measurements[idx - 1].distanceMeters)
            } else 0.0

            // Plot Y points (clamping for boundaries)
            val scaleFactor = 4.5f // 1mm = 4.5 pixels
            val clY = baselineY - (clDev.toFloat() * scaleFactor)
            val gY = baselineY - (gDev.toFloat() * scaleFactor)
            val twY = baselineY - (twVal.toFloat() * scaleFactor * 4f) // accentuate twist for visibility

            if (idx == 0) {
                clPath.moveTo(x, clY)
                gPath.moveTo(x, gY)
                twPath.moveTo(x, baselineY) // Start twist at baseline since twist is undefined for station 0
            } else {
                clPath.lineTo(x, clY)
                gPath.lineTo(x, gY)
                twPath.lineTo(x, twY)
            }

            // Draw small station circles on points
            drawCircle(
                color = Color(0xFF1E88E5),
                radius = 4f,
                center = Offset(x, clY)
            )
            drawCircle(
                color = Color(0xFFFBC02D),
                radius = 3f,
                center = Offset(x, gY)
            )
        }

        // Draw profile paths
        drawPath(
            path = clPath,
            color = Color(0xFF1E88E5),
            style = Stroke(width = 4f)
        )
        drawPath(
            path = gPath,
            color = Color(0xFFFBC02D),
            style = Stroke(width = 3f)
        )
        drawPath(
            path = twPath,
            color = Color(0xFFD84315),
            style = Stroke(width = 2.5f)
        )
    }
}

// --- SUPPORTING SCREENS FOR REGISTRATION, LOGIN, AND GAUGE ONLY WORKFLOW ---

@Composable
fun SafetyThoughtsBanner() {
    val thoughts = remember {
        listOf(
            "SAFETY FIRST: A millimeter of track deviation can be the difference between safety and hazard. Ensure absolute precision.",
            "Zero defects, zero derailments: Protect human lives by maintaining rigorous inspection standards.",
            "Your vigilance is the foundation of secure railways. Inspect every joint, spike, and sleeper.",
            "Accuracy today ensures safe journeys tomorrow. Double check every cross-level and gauge reading."
        )
    }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF9C4) // Warm warning yellow
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Color(0xFFFBC02D))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Safety Alert",
                    tint = Color(0xFFE65100),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "DAILY SAFETY COMMANDMENTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = Color(0xFFE65100)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            thoughts.forEach { thought ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "• ",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE65100)
                    )
                    Text(
                        text = thought,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF4E342E),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
fun RegistrationScreen(
    onRegistrationComplete: (
        name: String, gender: String, dob: String, designation: String,
        zone: String, division: String, mobile: String, email: String,
        pass: String, biometricsEnabled: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var dob by remember { mutableStateOf("") }
    var designation by remember { mutableStateOf("Junior Engineer") }
    var zone by remember { mutableStateOf("Southern Railway") }
    var division by remember { mutableStateOf("Chennai") }
    var mobile by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isBiometricsEnabled by remember { mutableStateOf(false) }

    // OTP related states
    var otpSent by remember { mutableStateOf(false) }
    var isSendingOtp by remember { mutableStateOf(false) }
    var generatedOtp by remember { mutableStateOf("") }
    var enteredOtp by remember { mutableStateOf("") }
    var isOtpVerified by remember { mutableStateOf(false) }
    var showOtpDialog by remember { mutableStateOf(false) }
    var showBioDialog by remember { mutableStateOf(false) }

    val isPasswordValid = password.isNotEmpty() && password.all { it.isDigit() || it.isLetter() }

    val isFormValid = name.isNotBlank() && dob.isNotBlank() && designation.isNotBlank() &&
            zone.isNotBlank() && division.isNotBlank() && mobile.length == 10 &&
            email.contains("@") && isPasswordValid && isOtpVerified

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A)) // High contrast dark slate
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "REGISTRATION PORTAL",
            color = Color(0xFFFBC02D),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Text(
            text = "Railway Track Inspector Portal Registration",
            color = Color(0xFF94A3B8),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        SafetyThoughtsBanner()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Personal Details",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBC02D),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Gender", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Male", "Female", "Other").forEach { g ->
                            val selected = gender == g
                            Button(
                                onClick = { gender = g },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) Color(0xFFFBC02D) else Color(0xFF334155),
                                    contentColor = if (selected) Color(0xFF0F172A) else Color.White
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(g, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = dob,
                    onValueChange = { dob = it },
                    label = { Text("Date of Birth (DD-MM-YYYY)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBC02D),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = designation,
                    onValueChange = { designation = it },
                    label = { Text("Designation", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBC02D),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = zone,
                    onValueChange = { zone = it },
                    label = { Text("Zone (e.g. Southern Railway)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBC02D),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = division,
                    onValueChange = { division = it },
                    label = { Text("Division (e.g. Chennai)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBC02D),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Contact & Authentication",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                OutlinedTextField(
                    value = mobile,
                    onValueChange = { if (it.all { char -> char.isDigit() } && it.length <= 10) mobile = it },
                    label = { Text("Mobile Number (10 digits)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBC02D),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBC02D),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // OTP Verification Trigger
                if (!isOtpVerified) {
                    Button(
                        onClick = {
                            if (mobile.length == 10 && email.contains("@")) {
                                isSendingOtp = true
                                val randomCode = (100000..999999).random().toString()
                                generatedOtp = randomCode
                                showOtpDialog = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3B82F6)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = mobile.length == 10 && email.contains("@")
                    ) {
                        if (isSendingOtp) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Send OTP to Mobile & Email", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF065F46).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFF059669), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(imageVector = Icons.Default.Check, contentDescription = "Verified", tint = Color(0xFF34D399))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mobile & Email Verified via OTP", color = Color(0xFF34D399), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Divider(color = Color(0xFF334155))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Set Password (numbers & letters only)", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Star else Icons.Default.Lock,
                                contentDescription = if (isPasswordVisible) "Hide" else "Show",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBC02D),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (password.isNotEmpty() && !isPasswordValid) {
                    Text(
                        "Password contains special characters! Only numbers and letters allowed.",
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Configure Biometric Logins", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Unlock instantly with device fingerprint reader", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                    Button(
                        onClick = { showBioDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBiometricsEnabled) Color(0xFF10B981) else Color(0xFF334155)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isBiometricsEnabled) "Enabled" else "Setup")
                    }
                }
            }
        }

        Button(
            onClick = {
                if (isFormValid) {
                    onRegistrationComplete(name, gender, dob, designation, zone, division, mobile, email, password, isBiometricsEnabled)
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFBC02D),
                contentColor = Color(0xFF0F172A)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            enabled = isFormValid
        ) {
            Text("REGISTER & LOG IN", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // OTP Simulated Dialog
    if (showOtpDialog) {
        AlertDialog(
            onDismissRequest = { showOtpDialog = false; isSendingOtp = false },
            title = { Text("OTP Sent Successfully!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("We have sent a 6-digit OTP verification code to both your mobile ($mobile) and email ($email).")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF9C4), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("SIMULATED SMS OTP: $generatedOtp", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                    }
                    OutlinedTextField(
                        value = enteredOtp,
                        onValueChange = { enteredOtp = it },
                        label = { Text("Enter 6-digit OTP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enteredOtp == generatedOtp) {
                            isOtpVerified = true
                            showOtpDialog = false
                        }
                    }
                ) {
                    Text("VERIFY OTP")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOtpDialog = false; isSendingOtp = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    // Biometrics Configuration Dialog
    if (showBioDialog) {
        AlertDialog(
            onDismissRequest = { showBioDialog = false },
            title = { Text("Biometric Enrollment") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Touch your finger on the biometric sensor area to bind your fingerprint.", textAlign = TextAlign.Center)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(40.dp))
                            .background(Color(0xFFE0F2FE))
                            .clickable {
                                isBiometricsEnabled = true
                                showBioDialog = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Fingerprint Sensor", tint = Color(0xFF0284C7), modifier = Modifier.size(40.dp))
                    }
                    Text("Tap the sensor icon above to successfully scan", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBioDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
fun LoginScreen(
    registeredName: String,
    registeredDesignation: String,
    storedPassword: String,
    biometricsEnabled: Boolean,
    onLoginSuccess: () -> Unit
) {
    var enteredPassword by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showBioScanDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "SECURE PORTAL LOGIN",
            color = Color(0xFFFBC02D),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        SafetyThoughtsBanner()

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFFFFF9C4)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked", tint = Color(0xFFE65100), modifier = Modifier.size(32.dp))
                }

                Text(
                    text = "Welcome back, $registeredName",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = registeredDesignation.uppercase(),
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = Color(0xFFFBC02D),
                    letterSpacing = 0.5.sp
                )

                OutlinedTextField(
                    value = enteredPassword,
                    onValueChange = { enteredPassword = it },
                    label = { Text("Enter Security Password", color = Color(0xFF94A3B8)) },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFFFBC02D),
                        unfocusedBorderColor = Color(0xFF334155)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (enteredPassword == storedPassword) {
                            onLoginSuccess()
                        } else {
                            errorMessage = "Incorrect Password! Please try again."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBC02D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("UNLOCK PORTAL", fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                }

                if (biometricsEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showBioScanDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFBC02D)),
                        border = BorderStroke(1.dp, Color(0xFFFBC02D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("UNLOCK WITH FINGERPRINT")
                    }
                }
            }
        }
    }

    if (showBioScanDialog) {
        AlertDialog(
            onDismissRequest = { showBioScanDialog = false },
            title = { Text("Biometric Authentication") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Place your fingerprint on the reader device to verify your identity.", textAlign = TextAlign.Center)
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(40.dp))
                            .background(Color(0xFFE0F2FE))
                            .clickable {
                                showBioScanDialog = false
                                onLoginSuccess()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Lock, contentDescription = "Sensor Scan", tint = Color(0xFF0284C7), modifier = Modifier.size(36.dp))
                    }
                    Text("Click the icon to simulate a matched touch", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBioScanDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
fun GaugeOnlySetupScreen(
    session: InspectionSession?,
    onSave: (blockSection: String, chainage: String, interval: Double, nominalG: Double, gaugeTypeRemarks: String) -> Unit,
    onCancel: () -> Unit
) {
    var blockSection by remember { mutableStateOf(session?.sectionName ?: "") }
    var chainageRange by remember { mutableStateOf("") }
    var intervalStr by remember { mutableStateOf(session?.intervalMeters?.toString() ?: "3.0") }
    var nominalGStr by remember { mutableStateOf(session?.nominalGauge?.toString() ?: "1676.0") }
    var gaugeTypeSelected by remember { mutableStateOf("Broad Gauge") }

    // Prefill parameters if editing
    LaunchedEffect(session) {
        if (session != null && session.id != 0L) {
            blockSection = session.sectionName
            intervalStr = session.intervalMeters.toString()
            nominalGStr = session.nominalGauge.toString()
            if (session.remarks.contains("Chainage: ")) {
                chainageRange = session.remarks.substringAfter("Chainage: ").substringBefore(" |")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = if (session?.id == 0L) "NEW GAUGE ONLY SURVEY" else "EDIT GAUGE ONLY SURVEY",
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.primary
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = blockSection,
                    onValueChange = { blockSection = it },
                    label = { Text("Block Section Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = chainageRange,
                    onValueChange = { chainageRange = it },
                    label = { Text("Chainage Range (e.g. Ch 120/4 to 121/6)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = intervalStr,
                    onValueChange = { intervalStr = it },
                    label = { Text("Inspection Intervals (meters)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = nominalGStr,
                    onValueChange = { nominalGStr = it },
                    label = { Text("Nominal Gauge Target (mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Preset Gauge Standards", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Broad Gauge" to 1676.0,
                            "Meter Gauge" to 1000.0,
                            "Narrow Gauge" to 762.0
                        ).forEach { (label, value) ->
                            val selected = gaugeTypeSelected == label
                            Button(
                                onClick = {
                                    gaugeTypeSelected = label
                                    nominalGStr = value.toString()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text(label.split(" ")[0], fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CANCEL")
            }
            Button(
                onClick = {
                    if (blockSection.isNotBlank() && chainageRange.isNotBlank()) {
                        val interval = intervalStr.toDoubleOrNull() ?: 3.0
                        val nominalG = nominalGStr.toDoubleOrNull() ?: 1676.0
                        onSave(blockSection, chainageRange, interval, nominalG, gaugeTypeSelected)
                    }
                },
                modifier = Modifier
                    .weight(1.5f)
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = blockSection.isNotBlank() && chainageRange.isNotBlank()
            ) {
                Text("SAVE & START", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GaugeOnlyRecorderScreen(
    session: InspectionSession,
    viewModel: TrackViewModel,
    onBack: () -> Unit
) {
    val measurements by viewModel.measurements.collectAsState()
    val sessionMeasurements = remember(measurements, session) {
        measurements.filter { m: TrackMeasurement -> m.sessionId == session.id }.sortedBy { m: TrackMeasurement -> m.stationIndex }
    }

    var selectedStationIndex by remember { mutableStateOf(-1) }
    var selectedDeviation by remember { mutableStateOf(0.0) }
    var customDeviationStr by remember { mutableStateOf("") }
    var remarksInput by remember { mutableStateOf("") }

    val currentNominal = session.nominalGauge
    val activeMeasurement = if (selectedStationIndex != -1 && selectedStationIndex < sessionMeasurements.size) {
        sessionMeasurements[selectedStationIndex]
    } else null

    LaunchedEffect(selectedStationIndex) {
        if (activeMeasurement != null) {
            val dev = activeMeasurement.gauge - currentNominal
            selectedDeviation = dev
            customDeviationStr = ""
            remarksInput = activeMeasurement.remarks
        } else {
            selectedDeviation = 0.0
            customDeviationStr = ""
            remarksInput = ""
        }
    }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.sectionName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Interval: ${session.intervalMeters}m | Nominal: ${session.nominalGauge}mm",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Measurements log list
            LazyColumn(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessionMeasurements.size) { idx ->
                    val item = sessionMeasurements[idx]
                    val isSelected = selectedStationIndex == idx
                    val calculatedChainage = idx * session.intervalMeters

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedStationIndex = if (isSelected) -1 else idx
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Station #${item.stationIndex + 1} (${calculatedChainage.toInt()}m from origin)",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Gauge: ${item.gauge} mm",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val dev = item.gauge - currentNominal
                                    val devSign = if (dev >= 0) "+$dev" else "$dev"
                                    Text(
                                        text = "Dev: $devSign mm",
                                        fontSize = 11.sp,
                                        color = if (abs(dev) > 6.0) Color.Red else Color(0xFF2E7D32)
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.remarks.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(item.remarks, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            viewModel.deleteMeasurement(item)
                                        }
                                        if (isSelected) selectedStationIndex = -1
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Keyboard/Selector Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val finalSelectedDeviation = if (customDeviationStr.isNotEmpty()) {
                        customDeviationStr.toDoubleOrNull() ?: selectedDeviation
                    } else {
                        selectedDeviation
                    }
                    val targetGauge = currentNominal + finalSelectedDeviation

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeMeasurement == null) "New Station #${sessionMeasurements.size + 1}" else "Edit Station #${activeMeasurement.stationIndex + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Gauge: $targetGauge mm",
                                fontWeight = FontWeight.Bold,
                                color = if (abs(finalSelectedDeviation) > 6.0) Color.Red else Color(0xFF1E293B)
                            )
                        }
                    }

                    // Deviation buttons grids
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Positive Deviations (+mm)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(0.0, 1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0, 12.0, 15.0).forEach { valDev ->
                                val active = finalSelectedDeviation == valDev
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) Color(0xFF4CAF50) else Color.White)
                                        .clickable {
                                            selectedDeviation = valDev
                                            customDeviationStr = ""
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+${valDev.toInt()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else Color.Black
                                    )
                                }
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Negative Deviations (-mm)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(-1.0, -2.0, -3.0, -4.0, -5.0, -6.0, -7.0, -8.0, -10.0).forEach { valDev ->
                                val active = finalSelectedDeviation == valDev
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) Color(0xFFF44336) else Color.White)
                                        .clickable {
                                            selectedDeviation = valDev
                                            customDeviationStr = ""
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${valDev.toInt()}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (active) Color.White else Color.Black
                                    )
                                }
                            }
                        }
                    }

                    // Custom input & remarks
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customDeviationStr,
                            onValueChange = { customDeviationStr = it },
                            label = { Text("Custom mm (e.g. +1.5)", fontSize = 11.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = remarksInput,
                            onValueChange = { remarksInput = it },
                            label = { Text("Remarks/Note", fontSize = 11.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Save Station Button
                    Button(
                        onClick = {
                            val computedGauge = currentNominal + finalSelectedDeviation
                            val calculatedDistance = if (activeMeasurement == null) {
                                sessionMeasurements.size * session.intervalMeters
                            } else {
                                activeMeasurement.distanceMeters
                            }

                            if (activeMeasurement == null) {
                                viewModel.addMeasurementGaugeOnly(
                                    session = session,
                                    gauge = computedGauge,
                                    remarks = remarksInput,
                                    stationIdx = sessionMeasurements.size,
                                    distMeters = calculatedDistance
                                )
                            } else {
                                viewModel.updateMeasurement(
                                    activeMeasurement.copy(
                                        gauge = computedGauge,
                                        remarks = remarksInput
                                    )
                                )
                                selectedStationIndex = -1
                            }
                            remarksInput = ""
                            customDeviationStr = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (activeMeasurement == null) "SAVE NEW STATION SURVEY" else "UPDATE STATION SURVEY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
