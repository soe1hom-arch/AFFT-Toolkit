/*
 * Copyright (c) 2026 Wandi (soe1hom-arch)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.afft.app.ui
import com.afft.app.ui.theme.LocalFontFamily

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.afft.app.R
import com.afft.app.service.AFFTService
import com.afft.app.ui.FileManagerScreen
import com.afft.app.ui.navigation.AppScreen
import com.afft.app.ui.navigation.AppScreenListSaver
import com.afft.app.ui.navigation.DeepLinkRouter
import com.afft.app.ui.navigation.FirmwareTool
import com.afft.app.ui.components.ColoredLogLine
import com.afft.app.ui.components.LiveStatusCard
import com.afft.app.ui.components.LogsPanel
import com.afft.app.ui.components.rememberDoneMessage
import com.afft.app.ui.components.AboutCreditsContent
import com.afft.app.ui.components.AboutDeveloperContent
import com.afft.app.ui.components.AboutFeaturesContent
import com.afft.app.ui.components.AboutLegalContent
import com.afft.app.ui.components.SettingsContentPage
import com.afft.app.ui.components.ProcessingOverlay
import com.afft.app.ui.components.TerminalView
import com.afft.app.ui.components.dashboard.OperationTimeline
import com.afft.app.ui.components.dashboard.StatusPanel
import com.afft.app.ui.components.dashboard.TimelineState
import com.afft.app.ui.components.dashboard.TimelineStep
import com.afft.app.core.coordinator.WorkspaceCoordinator
import com.afft.app.core.workspace.OperationResult
import com.afft.app.core.workspace.WorkspaceOperation
import com.afft.app.core.workspace.WorkspaceState
import com.afft.app.core.workspace.toStatusType
import com.afft.app.ui.components.dashboard.QuickMetric
import com.afft.app.ui.components.dashboard.QuickMetrics
import com.afft.app.ui.components.dashboard.WorkspaceCard
import com.afft.app.ui.components.dashboard.WorkspaceInfo
import com.afft.app.ui.components.dashboard.StatusSection
import com.afft.app.ui.components.dashboard.StatusType
import com.afft.app.ui.theme.*
import com.afft.app.ui.theme.LocalIconTint
import com.afft.app.util.BinaryManager
import com.afft.app.util.booleanMapSaver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_BACK_STACK_SIZE = 16

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    afftService: AFFTService,
    initialScreen: AppScreen? = null,
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val workspace = remember { WorkspaceCoordinator.create(context.applicationContext) }
    var backStack by rememberSaveable(stateSaver = AppScreenListSaver) {
        mutableStateOf(listOf(AppScreen.Home))
    }
    val screen = backStack.last()

    fun navigate(target: AppScreen) {
        if (backStack.last() == target) return
        backStack = (backStack + target).takeLast(MAX_BACK_STACK_SIZE)
    }

    fun replaceCurrent(target: AppScreen) {
        backStack = backStack.dropLast(1) + target
    }

    fun resetToHome() {
        backStack = listOf(AppScreen.Home)
    }

    fun openDeepLink(target: AppScreen) {
        backStack =
            if (target == AppScreen.Home) {
                listOf(AppScreen.Home)
            } else {
                listOf(AppScreen.Home, target)
            }
    }

    BackHandler(enabled = backStack.size > 1) {
        backStack = backStack.dropLast(1)
    }
    var debugMode by rememberSaveable { mutableStateOf(false) }
    var logToFileEnabled by rememberSaveable { mutableStateOf(afftService.isLogToFileEnabled()) }
    val logs by afftService.logs.collectAsState()
    val isRunning by afftService.isRunning.collectAsState()
    val progressMessage by afftService.progressMessage.collectAsState()
    val progressPercent by afftService.progressPercent.collectAsState()
    val currentPartition by afftService.currentPartition.collectAsState()
    var binariesReady by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var aboutPage by rememberSaveable { mutableStateOf("about") }
    val languageState by LanguageController.language.collectAsState()
    val aboutEnglish = languageState.isEnglish
    var binaryStatus by rememberSaveable(
        stateSaver = booleanMapSaver,
    ) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val versionName =
        remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
            } catch (_: Exception) {
                "?"
            }
        }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scopeDrawer = rememberCoroutineScope()
    val screenStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(Unit) {
        // Sinkronkan state debug dari UI (rememberSaveable) ke service baru
        // setelah activity di-recreate, agar badge & log debug tetap konsisten.
        if (debugMode != afftService.isDebugMode()) {
            afftService.toggleDebug()
        }
        // Sinkronkan juga toggle pencatatan log ke service baru
        if (logToFileEnabled != afftService.isLogToFileEnabled()) {
            afftService.setLogToFileEnabled(logToFileEnabled)
        }

        val result =
            withContext(Dispatchers.IO) {
                BinaryManager.deployBinaries(context)
            }
        binariesReady = result.isSuccess
        binaryStatus = BinaryManager.verifyBinaries(context)
        if (!binariesReady) {
            Toast
                .makeText(
                    context,
                    "Failed to deploy binaries: ${result.exceptionOrNull()?.message}",
                    Toast.LENGTH_LONG,
                ).show()
        }
    }

    // Deep link: rute dari intent (afft://...) dibuka lewat back-stack.
    // initialScreen hanya diterapkan saat stack masih di akar (cold start);
    // event onNewIntent dikumpulkan terus menerus dari DeepLinkRouter.
    LaunchedEffect(initialScreen) {
        if (initialScreen != null && backStack.size == 1 && backStack.first() == AppScreen.Home) {
            openDeepLink(initialScreen)
        }
        DeepLinkRouter.events.collect { target -> openDeepLink(target) }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.logo_afft),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "AFFT Toolkit",
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = LocalFontFamily.current,
                        fontWeight = FontWeight.Bold,
                    )
                }
                HorizontalDivider()

                // Menu items in drawer
                Column(modifier = Modifier.fillMaxWidth()) {
                    DrawerMenuItem(
                        iconRes = R.drawable.ic_home,
                        label = "Home",
                        selected = screen == AppScreen.Home,
                        onClick = {
                            resetToHome()
                            scopeDrawer.launch { drawerState.close() }
                        },
                    )
                    DrawerMenuItem(
                        iconRes = R.drawable.ic_archive,
                        label = "Tools",
                        selected = screen is AppScreen.Tools,
                        onClick = {
                            navigate((screen as? AppScreen.Tools) ?: AppScreen.Tools(FirmwareTool.PAYLOAD))
                            scopeDrawer.launch { drawerState.close() }
                        },
                    )
                    DrawerMenuItem(
                        iconRes = R.drawable.ic_file_manager,
                        label = "AFFT Manager",
                        selected = screen == AppScreen.Files,
                        onClick = {
                            navigate(AppScreen.Files)
                            scopeDrawer.launch { drawerState.close() }
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp, 12.dp, 16.dp, 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Console Output",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = LocalFontFamily.current,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Record",
                            fontSize = 10.sp,
                            fontFamily = LocalFontFamily.current,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Switch(
                            checked = logToFileEnabled,
                            onCheckedChange = { enabled ->
                                logToFileEnabled = enabled
                                afftService.setLogToFileEnabled(enabled)
                                Toast
                                    .makeText(
                                        context,
                                        if (enabled) "Pencatatan log: ON" else "Pencatatan log: OFF",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            },
                            modifier = Modifier.scale(0.8f),
                        )
                    }
                }

                TerminalView(
                    logs = logs,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .heightIn(min = 160.dp)
                            .padding(horizontal = 8.dp),
                    maxHeight = 1000,
                    isRunning = isRunning,
                    progressPercent = progressPercent,
                    currentPartition = currentPartition,
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${logs.size} lines",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = LocalFontFamily.current,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        // Copy log ke clipboard
                        val context = LocalContext.current
                        IconButton(
                            onClick = {
                                val clipboard =
                                    context.getSystemService(
                                        android.content.Context.CLIPBOARD_SERVICE,
                                    ) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("AFFT Log", logs.joinToString("\n"))
                                clipboard.setPrimaryClip(clip)
                                android.widget.Toast
                                    .makeText(
                                        context,
                                        "Log disalin ke clipboard",
                                        android.widget.Toast.LENGTH_SHORT,
                                    ).show()
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_content_copy),
                                contentDescription = "Copy Log",
                                tint = LocalIconTint.current,
                            )
                        }
                        // Simpan log ke file
                        IconButton(
                            onClick = {
                                try {
                                    val logDir = afftService.getExportDir()
                                    logDir.mkdirs()
                                    val timestamp =
                                        java.text
                                            .SimpleDateFormat(
                                                "yyyyMMdd_HHmmss",
                                                java.util.Locale.US,
                                            ).format(java.util.Date())
                                    val logFile = File(logDir, "afft_log_$timestamp.txt")
                                    logFile.writeText(logs.joinToString("\n"))
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            "Log tersimpan: ${logFile.absolutePath}",
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                } catch (e: Exception) {
                                    android.widget.Toast
                                        .makeText(
                                            context,
                                            "Gagal simpan log: ${e.message}",
                                            android.widget.Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_save_alt),
                                contentDescription = "Save Log",
                                tint = LocalIconTint.current,
                            )
                        }
                        TextButton(onClick = {
                            afftService.clearLogs()
                        }) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "AFFT Toolkit",
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = LocalFontFamily.current,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scopeDrawer.launch { drawerState.open() }
                        }) {
                            Icon(painterResource(R.drawable.ic_menu), "Menu", tint = LocalIconTint.current)
                        }
                    },
                    actions = {
                        if (debugMode) {
                            Text(
                                "DEBUG",
                                color = TerminalError,
                                fontFamily = LocalFontFamily.current,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        IconButton(onClick = {
                            debugMode = !debugMode
                            afftService.toggleDebug()
                            Toast
                                .makeText(
                                    context,
                                    if (!debugMode) "Debug mode: OFF" else "Debug mode: ON",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }) {
                            Icon(
                                painterResource(R.drawable.ic_bug_report),
                                "Debug",
                                tint = LocalIconTint.current,
                            )
                        }
                        IconButton(onClick = { showAboutDialog = true }) {
                            Icon(painterResource(R.drawable.ic_info), "About", tint = LocalIconTint.current)
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                        ),
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                ) {
                    NavigationBarItem(
                        selected = screen == AppScreen.Home,
                        onClick = { resetToHome() },
                        icon = { Icon(painterResource(R.drawable.ic_home), "Home", tint = LocalIconTint.current) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = screen is AppScreen.Tools,
                        onClick = {
                            navigate((screen as? AppScreen.Tools) ?: AppScreen.Tools(FirmwareTool.PAYLOAD))
                        },
                        icon = { Icon(painterResource(R.drawable.ic_archive), "Tools", tint = LocalIconTint.current) },
                        label = { Text("Tools") },
                    )
                    NavigationBarItem(
                        selected = screen == AppScreen.Files,
                        onClick = { navigate(AppScreen.Files) },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_file_manager),
                                "Files",
                                tint = LocalIconTint.current,
                            )
                        },
                        label = { Text("Files") },
                    )
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                screenStateHolder.SaveableStateProvider(screen.key) {
                    when (val current = screen) {
                        AppScreen.Home ->
                            HomeScreen(
                                workspace = workspace,
                                afftService = afftService,
                                binariesReady = binariesReady,
                                binaries = binaryStatus,
                                logs = logs,
                                isRunning = isRunning,
                                progressMessage = progressMessage,
                                onOpenFolder = { navigate(AppScreen.Files) },
                                onOpenTool = { tool -> openDeepLink(AppScreen.Tools(tool)) },
                            )
                        is AppScreen.Tools ->
                            ToolsHub(
                                workspace = workspace,
                                afftService = afftService,
                                logs = logs,
                                isRunning = isRunning,
                                selectedTool = current.tool,
                                onToolSelect = { tool -> replaceCurrent(AppScreen.Tools(tool)) },
                            )
                        AppScreen.Files ->
                            FileManagerScreen(
                                afftService = afftService,
                                logs = logs,
                                isRunning = isRunning,
                            )
                    }
                }
            }
        }

        // About dialog — full-screen
        if (showAboutDialog) {
            Dialog(
                onDismissRequest = { showAboutDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when (aboutPage) {
                        "settings" ->
                            SettingsContentPage(
                                onDismiss = { showAboutDialog = false },
                                onBack = { aboutPage = "about" },
                            )
                        "developer" ->
                            AboutDeveloperContent(
                                aboutEnglish,
                                onDismiss = { showAboutDialog = false },
                                onBack = { aboutPage = "about" },
                            )
                        "credits" ->
                            AboutCreditsContent(
                                aboutEnglish,
                                onDismiss = { showAboutDialog = false },
                                onBack = { aboutPage = "about" },
                            )
                        "features" ->
                            AboutFeaturesContent(
                                aboutEnglish,
                                onDismiss = { showAboutDialog = false },
                                onBack = { aboutPage = "about" },
                            )
                        "legal" ->
                            AboutLegalContent(
                                onDismiss = { showAboutDialog = false },
                                onBack = { aboutPage = "about" },
                            )
                        else ->
                            Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            ) {
                        // ── Header ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(R.drawable.logo_afft),
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    contentScale = ContentScale.Fit,
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "AFFT Toolkit",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "Android Firmware Full Toolkit",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = LocalFontFamily.current,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "v$versionName",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            IconButton(onClick = { showAboutDialog = false }) {
                                Icon(
                                    painterResource(R.drawable.ic_close),
                                    contentDescription = "Tutup",
                                    tint = LocalIconTint.current,
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            // ── About ──
                            Text(
                                if (aboutEnglish) "About" else "Tentang Aplikasi",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (aboutEnglish) {
                                    "AFFT is an Android firmware modification tool that supports payload.bin, super.img, EROFS/ext4 filesystem, and various boot image types.\n\nBuilt for the Android modding community."
                                } else {
                                    "AFFT adalah alat modifikasi firmware Android yang mendukung payload.bin, super.img, filesystem EROFS/ext4, dan berbagai jenis boot image.\n\nDibangun untuk komunitas modding Android."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            // ── Appearance & Theme ──
                            Text(
                                if (aboutEnglish) "Settings" else "Pengaturan",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { aboutPage = "settings" },
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_palette),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (aboutEnglish) "Settings" else "Pengaturan",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            if (aboutEnglish) {
                                                "Language, appearance & theme"
                                            } else {
                                                "Bahasa, tampilan & tema"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = LocalIconTint.current,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(12.dp))

                            // ── Developer & Tech ──
                            Text(
                                if (aboutEnglish) "Developer & Tech Stack" else "Pengembang & Teknologi",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { aboutPage = "developer" },
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_person),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (aboutEnglish) "Developer & Tech Stack" else "Pengembang & Tumpukan Teknologi",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            if (aboutEnglish) "Creator, engines and stack" else "Pembuat, mesin, dan teknologi",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = LocalIconTint.current,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // ── Credits ──
                            Text(
                                if (aboutEnglish) "Third-Party Credits" else "Kredit Pihak Ketiga",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { aboutPage = "credits" },
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_code),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (aboutEnglish) "Third-Party Credits" else "Kredit Pihak Ketiga",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            if (aboutEnglish) "Bundled binaries & fonts" else "Binary & font yang dibundel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = LocalIconTint.current,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // ── Features ──
                            Text(
                                if (aboutEnglish) "Features" else "Fitur",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { aboutPage = "features" },
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_check_circle),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (aboutEnglish) "Features & Engines" else "Fitur & Mesin",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            if (aboutEnglish) "What AFFT can do" else "Kemampuan dan bagian mesin",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = LocalIconTint.current,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            // ── Legal & Attribution ──
                            Text(
                                if (aboutEnglish) "Legal & Licenses" else "Lisensi & Atribusi",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { aboutPage = "legal" },
                                colors =
                                    CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_description),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            if (aboutEnglish) "Open Source Licenses" else "Lisensi & Atribusi",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            if (aboutEnglish) "Licenses, notices and fonts" else "Lisensi, notice, dan atribusi pihak ketiga",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Icon(
                                        painterResource(R.drawable.ic_chevron_right),
                                        contentDescription = null,
                                        tint = LocalIconTint.current,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                            HorizontalDivider()

                            Spacer(modifier = Modifier.height(12.dp))

                            // License
                            Text(
                                "© 2026 Wandi (soe1hom-arch) · Apache License 2.0",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = LocalFontFamily.current,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

    }
}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    workspace: WorkspaceCoordinator,
    afftService: AFFTService,
    binariesReady: Boolean,
    binaries: Map<String, Boolean> = emptyMap(),
    logs: List<String>,
    isRunning: Boolean,
    progressMessage: String = "",
    onOpenFolder: () -> Unit = {},
    onOpenTool: (FirmwareTool) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    var showHistorySheet by rememberSaveable { mutableStateOf(false) }
    var showCleanConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var logsExpanded by rememberSaveable { mutableStateOf(false) }
    var exportOptions by rememberSaveable(stateSaver = booleanMapSaver) {
        mutableStateOf(
            mapOf(
                "payload" to true,
                "img" to true,
                "repacked" to true,
                "boot_out" to true,
                "contents" to true,
                "input" to false,
            ),
        )
    }
    var cleanOptions by rememberSaveable(stateSaver = booleanMapSaver) {
        mutableStateOf(
            mapOf(
                "img" to true,
                "contents" to true,
                "repacked" to true,
                "payload" to true,
                "boot" to true,
                "boot_out" to true,
                "img_src" to true,
                "filesystem_work" to true,
                "logs" to true,
            ),
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Banner with background image
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                Image(
                    painter = painterResource(id = com.afft.app.R.drawable.bg_banner),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    alpha = 1.0f,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status panel — reused Phase-1 dashboard component (card + status + live rows)
        // Nilai status/task/workspace kini dari WorkspaceCoordinator (single source of truth).
        val loadedCount = binaries.count { it.value }
        val totalCount = binaries.size
        val workspaceState by workspace.state.collectAsState()
        val workspaceStatus = workspaceState.state.toStatusType()
        val engineStatus =
            when {
                !binariesReady -> StatusType.ERROR
                workspaceState.state == WorkspaceState.FAILED -> StatusType.ERROR
                workspaceState.state == WorkspaceState.BUSY -> StatusType.RUNNING
                workspaceState.state == WorkspaceState.IDLE -> StatusType.INFO
                else -> StatusType.READY
            }
        val currentTask =
            when {
                isRunning -> "Running"
                workspaceState.state == WorkspaceState.BUSY ->
                    "Analyzing ${workspaceState.currentFile ?: "Firmware"}..."
                workspaceState.state == WorkspaceState.FAILED -> "Failed"
                workspaceState.state == WorkspaceState.IDLE -> "Idle"
                workspaceState.lastOperation != null ->
                    "${workspaceState.lastOperation!!.type} Completed"
                else -> "Ready"
            }
        val failedCount = workspaceState.history.count { it.result == OperationResult.FAILED }
        val statusMessage =
            when {
                !binariesReady -> "Binary deployment failed. Workspace may be incomplete."
                workspaceState.state == WorkspaceState.FAILED -> "Last operation failed. Check workspace history."
                workspaceState.state == WorkspaceState.BUSY ->
                    "Analyzing ${workspaceState.currentFile ?: "firmware"}..."
                workspaceState.state == WorkspaceState.IDLE -> "Select a firmware file to begin."
                else -> "All operation binaries loaded successfully."
            }
        val statusSections =
            remember(binariesReady, isRunning, workspaceState, loadedCount, totalCount) {
                listOf(
                    StatusSection("Engine Status", if (binariesReady) "Ready" else "Error"),
                    StatusSection("Current Task", currentTask),
                    StatusSection("Binary Status", "$loadedCount / $totalCount Loaded"),
                    StatusSection("Workspace", workspaceState.state.name),
                    StatusSection("Last Operation", workspaceState.lastOperation?.type ?: "None"),
                    StatusSection(
                        "Session",
                        workspaceState.project?.let {
                            "Opened " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it.metadata.lastOpenedAt))
                        } ?: "Just Started",
                    ),
                )
            }
        StatusPanel(
            status = engineStatus,
            title = "Current Status",
            message = statusMessage,
            sections = statusSections,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Global live status — menampilkan semua proses aplikasi, entah
        // berjalan di layar depan (analisis) maupun latar belakang
        // (ekstraksi/copy/clean/export), secara rinci.
        val globalDoneMessage = rememberDoneMessage(progressMessage, isRunning)
        val globalLiveActivity =
            when {
                isRunning -> progressMessage.ifBlank { "Menjalankan proses di belakang layar..." }
                globalDoneMessage != null -> globalDoneMessage
                workspaceState.state == WorkspaceState.BUSY ->
                    "Menganalisis ${workspaceState.currentFile ?: "firmware"}..."
                progressMessage.isNotBlank() -> progressMessage
                else -> null
            }
        LiveStatusCard(
            activity = globalLiveActivity,
            idleText = "Idle — belum ada proses berjalan",
            busy =
                isRunning ||
                    workspaceState.state == WorkspaceState.BUSY ||
                    progressMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(12.dp))
        RecentActivityFeed(
            logs = logs,
            onOpenLogs = { logsExpanded = true },
            processActive =
                isRunning ||
                    workspaceState.state == WorkspaceState.BUSY ||
                    progressMessage.isNotBlank(),
            isBackground = isRunning && workspaceState.state != WorkspaceState.BUSY,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Workspace card — nilai asli dari WorkspaceCoordinator (project-based)
        val workspaceInfo =
            remember(workspaceState) {
                val project = workspaceState.project
                if (project == null) {
                    WorkspaceInfo(
                        status = workspaceStatus,
                        isEmpty = true,
                    )
                } else {
                    WorkspaceInfo(
                        status = workspaceStatus,
                        project = project.metadata.name,
                        firmware = workspaceState.currentFile ?: project.metadata.firmwareType ?: "None",
                        androidVersion = project.metadata.androidVersion ?: "Unknown",
                        device = project.metadata.device ?: "Unknown",
                        path = project.rootDir.absolutePath,
                        lastActivity = workspaceState.lastOperation?.let {
                            "${it.type} — ${it.result}"
                        } ?: "None",
                        lastOpened =
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                .format(Date(project.metadata.lastOpenedAt)),
                        isEmpty = false,
                    )
                }
            }
        WorkspaceCard(
            workspace = workspaceInfo,
            onOpenFolder = onOpenFolder,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quick metrics — reused Phase-1 dashboard component (responsive 2x2 grid)
        // Nilai live dari Workspace (health score, history error, status engine).
        QuickMetrics(
            metrics =
                listOf(
                    QuickMetric(
                        label = "Binaries",
                        value = "$loadedCount / $totalCount",
                        iconRes = R.drawable.ic_check_circle,
                        status = if (binariesReady) StatusType.READY else StatusType.ERROR,
                        statusLabel = if (binariesReady) "Loaded" else "Failed",
                    ),
                    QuickMetric(
                        label = "Engine",
                        value = currentTask,
                        iconRes = R.drawable.ic_refresh,
                        status = engineStatus,
                        statusLabel = workspaceState.state.name,
                    ),
                    QuickMetric(
                        label = "Workspace",
                        value = workspaceState.healthScore?.let { "$it / 100" } ?: "Ready",
                        iconRes = R.drawable.ic_folder,
                        status =
                            when {
                                workspaceState.state == WorkspaceState.FAILED -> StatusType.ERROR
                                workspaceState.healthScore?.let { it < 50 } == true -> StatusType.WARNING
                                else -> StatusType.READY
                            },
                        statusLabel = if (workspaceState.project != null) "Healthy" else "No Project",
                    ),
                    QuickMetric(
                        label = "Errors",
                        value = failedCount.toString(),
                        iconRes = R.drawable.ic_warning,
                        status = if (failedCount == 0) StatusType.READY else StatusType.ERROR,
                        statusLabel = if (failedCount == 0) "No Issues" else "Check History",
                    ),
                ),
            onMetricClick = { index ->
                if (index == 3) showHistorySheet = true
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Quick actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { showCleanConfirmDialog = true },
                modifier = Modifier.weight(1f),
                enabled = !isRunning,
            ) {
                Icon(painterResource(R.drawable.ic_cleaning_services), null, tint = LocalIconTint.current)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Clean", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = { showExportDialog = true },
                modifier = Modifier.weight(1f),
                enabled = !isRunning,
            ) {
                Icon(painterResource(R.drawable.ic_save_alt), null, tint = LocalIconTint.current)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Export All", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Shortcut ke tiap tool — deep link antar-tool dari Home
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolShortcutButton(
                tool = FirmwareTool.PAYLOAD,
                onOpenTool = onOpenTool,
                isRunning = isRunning,
                modifier = Modifier.weight(1f),
            )
            ToolShortcutButton(
                tool = FirmwareTool.SUPER,
                onOpenTool = onOpenTool,
                isRunning = isRunning,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolShortcutButton(
                tool = FirmwareTool.FILESYSTEM,
                onOpenTool = onOpenTool,
                isRunning = isRunning,
                modifier = Modifier.weight(1f),
            )
            ToolShortcutButton(
                tool = FirmwareTool.BOOT,
                onOpenTool = onOpenTool,
                isRunning = isRunning,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isRunning && progressMessage.isNotEmpty()) {
            ProcessingOverlay(
                isRunning = true,
                message = progressMessage,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Console Logs — panel yang bisa dibuka/tutup langsung di Home
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { logsExpanded = !logsExpanded }
                            .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_text_snippet),
                        contentDescription = null,
                        tint = LocalIconTint.current,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        "Console Logs",
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = LocalFontFamily.current,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (logsExpanded) "Sembunyikan" else "Tampilkan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = LocalIconTint.current,
                        modifier = Modifier.scale(if (logsExpanded) -1f else 1f),
                    )
                }
                if (logsExpanded) {
                    LogsPanel(
                        afftService = afftService,
                        modifier = Modifier.fillMaxWidth().height(420.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom spacing agar tombol/isi terakhir tidak menempel bottom nav
        Spacer(modifier = Modifier.height(24.dp))

        // Export dialog
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text("Export ke Downloads/AFFT") },
                text = {
                    Column {
                        Text("Pilih folder yang akan diekspor:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        exportOptions.forEach { (folder, selected) ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            exportOptions = exportOptions + (folder to !selected)
                                        }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        exportOptions = exportOptions + (folder to checked)
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(folder, fontFamily = LocalFontFamily.current)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showExportDialog = false
                        scope.launch {
                            val selectedFolders = exportOptions.filter { it.value }.keys.toList()
                            if (selectedFolders.isEmpty()) {
                                Toast.makeText(context, "Pilih minimal satu folder", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            Toast
                                .makeText(
                                    context,
                                    "Mengekspor ${selectedFolders.size} folder...",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            afftService.exportSelectedToDownloads(selectedFolders)
                        }
                    }) {
                        Text("Export")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExportDialog = false }) {
                        Text("Batal")
                    }
                },
            )
        }

        // Clean dialog
        if (showCleanConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showCleanConfirmDialog = false },
                icon = {
                    Icon(painterResource(R.drawable.ic_warning), null, tint = LocalIconTint.current)
                },
                title = { Text("Pilih Folder untuk Dibersihkan") },
                text = {
                    Column {
                        Text("Centang folder yang ingin dihapus:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "File di input/ dan Downloads/AFFT/ TIDAK akan terhapus.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        cleanOptions.forEach { (folder, selected) ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            cleanOptions = cleanOptions + (folder to !selected)
                                        }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = { checked ->
                                        cleanOptions = cleanOptions + (folder to checked)
                                    },
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(folder, fontFamily = LocalFontFamily.current)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showCleanConfirmDialog = false
                            scope.launch {
                                val selectedFolders = cleanOptions.filter { it.value }.keys.toList()
                                if (selectedFolders.isEmpty()) {
                                    Toast.makeText(context, "Pilih minimal satu folder", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                Toast
                                    .makeText(
                                        context,
                                        "Membersihkan ${selectedFolders.size} folder...",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                afftService.cleanSelected(selectedFolders)
                            }
                        },
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Icon(painterResource(R.drawable.ic_delete), null, tint = LocalIconTint.current)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Hapus Folder Terpilih")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCleanConfirmDialog = false }) {
                        Text("Batal")
                    }
                },
            )
        }

        if (showHistorySheet) {
            HistoryBottomSheet(
                operations = workspaceState.history,
                onDismiss = { showHistorySheet = false },
            )
        }

        // NOTE: TerminalView telah dipindahkan ke sidebar drawer
    }
}

private val FeedGreen = Color(0xFF00E676)
private val FeedCyan = Color(0xFF00BCD4)
private val FeedYellow = Color(0xFFFFD54F)
private val FeedRed = Color(0xFFFF5252)

/** Ringkasan aktivitas global dari AFFTService — menampilkan proses yang
 * berjalan (foreground/background) selengkapnya (line log terbaru) plus
 * ringkasan info/warning/error dan badge proses aktif (depan/latar).
 */
@Composable
private fun ToolShortcutButton(
    tool: FirmwareTool,
    onOpenTool: (FirmwareTool) -> Unit,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = { onOpenTool(tool) },
        modifier = modifier,
        enabled = !isRunning,
    ) {
        Icon(painterResource(tool.iconRes()), null, tint = LocalIconTint.current)
        Spacer(modifier = Modifier.width(4.dp))
        Text(tool.label, fontSize = 12.sp, maxLines = 1)
    }
}

private fun FirmwareTool.iconRes(): Int =
    when (this) {
        FirmwareTool.PAYLOAD -> R.drawable.ic_payload
        FirmwareTool.SUPER -> R.drawable.ic_super
        FirmwareTool.FILESYSTEM -> R.drawable.ic_filesystem
        FirmwareTool.BOOT -> R.drawable.ic_boot_image
    }

@Composable
private fun RecentActivityFeed(
    logs: List<String>,
    onOpenLogs: () -> Unit = {},
    processActive: Boolean = false,
    isBackground: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val recent = logs.takeLast(8)
    val errorCount = recent.count { it.startsWith("[ERROR]") || it.startsWith("[FAIL]") }
    val warnCount = recent.count { it.startsWith("[WARN]") }
    val okCount = recent.count { it.startsWith("[OK]") }
    val infoCount = recent.size - errorCount - warnCount - okCount

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "AKTIVITAS TERBARU",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = LocalFontFamily.current,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (processActive) {
                    Text(
                        text = if (isBackground) "● latar belakang" else "● layar depan",
                        color = if (isBackground) FeedCyan else FeedGreen,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = LocalFontFamily.current,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                TextButton(onClick = onOpenLogs) {
                    Text("Lihat Log", fontFamily = LocalFontFamily.current, fontSize = 11.sp)
                }
            }
            if (recent.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActivityCount(text = "$okCount ok", color = FeedGreen)
                    ActivityCount(text = "$infoCount info", color = FeedCyan)
                    ActivityCount(text = "$warnCount warning", color = FeedYellow)
                    ActivityCount(text = "$errorCount error", color = FeedRed)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            if (recent.isEmpty()) {
                Text(
                    text = "Belum ada aktivitas.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = LocalFontFamily.current,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    recent.forEach { log ->
                        ColoredLogLine(
                            text = log,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityCount(
    text: String,
    color: Color,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontFamily = LocalFontFamily.current,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryBottomSheet(
    operations: List<WorkspaceOperation>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val failedCount = operations.count { it.result == OperationResult.FAILED }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_description),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Operation History",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = LocalFontFamily.current,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = "Tutup",
                        tint = LocalIconTint.current,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${operations.size} operasi · $failedCount gagal",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = LocalFontFamily.current,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (operations.isEmpty()) {
                Text(
                    text = "Belum ada operasi tercatat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = LocalFontFamily.current,
                )
            } else {
                OperationTimeline(
                    steps =
                        operations.map { operation ->
                            TimelineStep(
                                title = operation.type,
                                detail =
                                    listOfNotNull(
                                        operation.detail.takeIf { it.isNotBlank() },
                                        "Durasi: ${formatDurationMillis(operation.durationMillis)}",
                                    ).joinToString(" · "),
                                timestamp =
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                                        .format(Date(operation.timestamp)),
                                state =
                                    if (operation.result == OperationResult.SUCCESS) {
                                        TimelineState.COMPLETED
                                    } else {
                                        TimelineState.FAILED
                                    },
                            )
                        },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Close")
            }
        }
    }
}

private fun formatDurationMillis(durationMillis: Long): String {
    val seconds = durationMillis / 1000
    return if (seconds >= 60) {
        val minutes = seconds / 60
        val rest = seconds % 60
        "${minutes}m ${rest}s"
    } else {
        "${seconds}s"
    }
}

@Composable
private fun DrawerMenuItem(
    iconRes: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(iconRes),
            contentDescription = label,
            tint = LocalIconTint.current,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = LocalFontFamily.current,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
private fun BulletText(text: String) {
    Row(
        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
    ) {
        Text("\u2022 ", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}
