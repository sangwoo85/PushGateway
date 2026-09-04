package com.sangwoo.push

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.compose.collectAsLazyPagingItems
import com.sangwoo.push.data.NotificationEntity
import com.sangwoo.push.ui.DeplBlue
import com.sangwoo.push.ui.DeplRed
import com.sangwoo.push.ui.DeplYellow
import com.sangwoo.push.ui.QrScanner
import java.text.DateFormat
import java.util.Date

@Composable
fun DeplApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val registered by viewModel.registered.collectAsState()
    val enrollment by viewModel.enrollmentState.collectAsState()
    val history = viewModel.history.collectAsLazyPagingItems()
    var showScanner by remember { mutableStateOf(false) }
    var showReset by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var notificationsAllowed by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED)
    }
    var batteryOptimizationExcluded by remember { mutableStateOf(isBatteryOptimizationExcluded(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsAllowed = it
    }
    val systemSettingsRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryOptimizationExcluded = isBatteryOptimizationExcluded(context)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryOptimizationExcluded = isBatteryOptimizationExcluded(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsAllowed) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(enrollment) { if (enrollment is EnrollmentUiState.Success) showScanner = false }

    MaterialTheme(colorScheme = DeplColorScheme, typography = DeplTypography) {
        if (showScanner) {
            QrScanner(onQr = viewModel::register, onClose = { showScanner = false })
        } else {
            MainScreen(
                registered = registered,
                notificationsAllowed = notificationsAllowed,
                batteryOptimizationExcluded = batteryOptimizationExcluded,
                itemCount = history.itemCount,
                itemAt = { history[it] },
                onScan = { viewModel.dismissStatus(); showScanner = true },
                onReset = { showReset = true },
                onClear = { showClear = true },
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                },
                onRequestBatteryExclusion = {
                    val directRequest = Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")
                    )
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    systemSettingsRequest.launch(
                        directRequest.takeIf { it.resolveActivity(context.packageManager) != null } ?: fallback
                    )
                }
            )
        }

        when (val state = enrollment) {
            EnrollmentUiState.Working -> AlertDialog(
                onDismissRequest = {},
                confirmButton = {},
                title = { Text("기기를 등록하고 있습니다") },
                text = { Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Color.Black)
                    Text("개인 · 부서 · 공지 알림을 연결합니다.", Modifier.padding(start = 14.dp))
                } }
            )
            EnrollmentUiState.Success -> AlertDialog(
                onDismissRequest = viewModel::dismissStatus,
                confirmButton = { TextButton(onClick = viewModel::dismissStatus) { Text("확인") } },
                title = { Text("기기 등록 완료") },
                text = { Text("개인 · 부서 · 공지 알림을 받을 준비가 되었습니다.") }
            )
            is EnrollmentUiState.Error -> AlertDialog(
                onDismissRequest = viewModel::dismissStatus,
                confirmButton = { TextButton(onClick = viewModel::dismissStatus) { Text("확인") } },
                title = { Text("등록할 수 없습니다") },
                text = { Text(state.message) }
            )
            EnrollmentUiState.Idle -> Unit
        }

        if (showReset) ConfirmDialog(
            title = "기기 등록을 초기화할까요?",
            message = "개인 · 부서 · 공지 Topic 구독을 해제합니다.",
            onConfirm = { showReset = false; viewModel.resetEnrollment() },
            onDismiss = { showReset = false }
        )
        if (showClear) ConfirmDialog(
            title = "알림 내역을 모두 삭제할까요?",
            message = "기기에 저장된 알림 내역만 삭제됩니다.",
            onConfirm = { showClear = false; viewModel.clearHistory() },
            onDismiss = { showClear = false }
        )
    }
}

@Composable
private fun MainScreen(
    registered: Boolean,
    notificationsAllowed: Boolean,
    batteryOptimizationExcluded: Boolean,
    itemCount: Int,
    itemAt: (Int) -> NotificationEntity?,
    onScan: () -> Unit,
    onReset: () -> Unit,
    onClear: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestBatteryExclusion: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().background(DeplYellow).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(38.dp)); BrandHeader() }
        item {
            StatusCard(
                registered,
                notificationsAllowed,
                batteryOptimizationExcluded,
                onOpenSettings,
                onRequestBatteryExclusion
            )
        }
        item {
            Button(
                onClick = onScan,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, null)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text("QR로 기기 등록", fontWeight = FontWeight.Bold)
                    Text("개인 · 부서 · 공지 알림 연결", fontSize = 12.sp, color = Color.White.copy(alpha = .68f))
                }
            }
        }
        if (registered) item {
            TextButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("기기 등록 초기화", color = Color.Black) }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("알림 내역", fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (itemCount > 0) {
                    Text("${itemCount}개", fontSize = 12.sp, color = Color.Black.copy(alpha = .55f))
                    IconButton(onClick = onClear) { Icon(Icons.Default.DeleteSweep, "알림 내역 전체 삭제") }
                }
            }
        }
        if (itemCount == 0) {
            item {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(34.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.NotificationsOff, null, tint = Color.Gray)
                        Text("도착한 알림이 없습니다.", color = Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            items(itemCount) { index -> itemAt(index)?.let { HistoryRow(it) } }
        }
        item {
            Text(
                "업무 상세 없이 알림 유형과 수신 시각만 표시합니다.",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black.copy(alpha = .55f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp).semantics {
                    contentDescription = "업무 상세 없이 알림 유형과 수신 시각만 표시합니다"
                }
            )
        }
    }
}

@Composable
private fun BrandHeader() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "DEPL",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = .2.sp
        )
        Image(
            painterResource(R.drawable.e9pay_dots_extracted), "E9pay 브랜드 점 심볼",
            contentScale = ContentScale.Fit,
            modifier = Modifier.padding(start = 7.dp).size(32.dp).clip(CircleShape)
        )
        Spacer(Modifier.weight(1f))
        Text("알림 전용", fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(Color.White.copy(alpha = .48f), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp))
    }
}

@Composable
private fun StatusCard(
    registered: Boolean,
    allowed: Boolean,
    batteryOptimizationExcluded: Boolean,
    onOpenSettings: () -> Unit,
    onRequestBatteryExclusion: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(5.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("알림 설정", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "알림 설정 접기" else "알림 설정 펼치기",
                    tint = Color.Black.copy(alpha = .55f)
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = Color.Black.copy(alpha = .08f))
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(48.dp).background(Color(0xFFFFF3BF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (registered && allowed && batteryOptimizationExcluded) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                                null,
                                modifier = Modifier.size(23.dp),
                                tint = Color.Black
                            )
                            if (registered && allowed && batteryOptimizationExcluded) {
                                Box(Modifier.align(Alignment.TopEnd).size(12.dp).background(DeplBlue, CircleShape))
                            }
                        }
                        Column(
                            Modifier.padding(start = 14.dp).weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                when {
                                    !allowed -> "알림 권한이 꺼져 있습니다."
                                    !batteryOptimizationExcluded -> "배터리 최적화를 제외해 주세요."
                                    registered -> "알림을 받을 준비가 되었습니다."
                                    else -> "QR로 기기를 등록해 주세요."
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text("알림 권한 · ${if (allowed) "허용" else "거부"}", fontSize = 12.sp, color = Color.Gray)
                            Text(
                                "배터리 최적화 · ${if (batteryOptimizationExcluded) "제외됨" else "사용 중"}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            if (!allowed) {
                                TextButton(onClick = onOpenSettings) { Text("알림 설정 열기", color = DeplRed) }
                            } else if (!batteryOptimizationExcluded) {
                                Button(
                                    onClick = onRequestBatteryExclusion,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("배터리 최적화 제외", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                            }
                            TextButton(onClick = onOpenSettings) {
                                Text("잠금 화면 · 팝업 설정", color = Color.Black, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: NotificationEntity) {
    Card(
        Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background(
                    if (item.type()?.channel() == com.sangwoo.push.data.NotificationChannelKind.IMPORTANT) DeplRed.copy(alpha = .16f) else DeplBlue.copy(alpha = .16f),
                    CircleShape
                ), contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Notifications, null, tint = Color.Black) }
            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.receivedAt)),
                    fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

private val DeplColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    background = DeplYellow,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black
)

private val DeplTypography = Typography(
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp)
)

private fun isBatteryOptimizationExcluded(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(PowerManager::class.java)
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

@Composable
private fun ConfirmDialog(title: String, message: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("확인") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
