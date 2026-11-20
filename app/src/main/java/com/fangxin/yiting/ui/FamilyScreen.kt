package com.fangxin.yiting.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.fangxin.yiting.VoiceMachine
import com.fangxin.yiting.data.LinkStore
import com.fangxin.yiting.domain.AlertEvent
import com.fangxin.yiting.domain.DEFAULT_RELAY_URL
import com.fangxin.yiting.domain.RiskDecision
import com.fangxin.yiting.domain.UserRole
import com.fangxin.yiting.link.LinkClient
import com.fangxin.yiting.link.LinkEvent
import com.fangxin.yiting.link.LinkStatus
import com.fangxin.yiting.service.GuardianLinkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FamilyScreen(machine: VoiceMachine, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LinkStore.get(context) }
    val alerts by store.alerts.collectAsState()
    val status by LinkClient.status.collectAsState()
    var paired by remember { mutableStateOf(store.guardianPaired) }
    var pairedRoom by remember { mutableStateOf(store.guardianRoom) }
    var elderRoom by remember { mutableStateOf(machine.elderRoom) }
    var codeInput by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var watching by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(paired) {
        if (paired) GuardianLinkService.start(context)
    }

    LaunchedEffect(Unit) {
        LinkClient.events.collect { event ->
            when (event) {
                is LinkEvent.Frame -> {
                    val image = withContext(Dispatchers.Default) {
                        BitmapFactory.decodeByteArray(event.jpeg, 0, event.jpeg.size)?.asImageBitmap()
                    }
                    if (image != null) {
                        frame = image
                        watching = true
                        waiting = false
                    }
                }
                is LinkEvent.ScreenSharing -> {
                    if (!event.on) {
                        frame = null
                        watching = false
                        waiting = false
                        note = "老人已经停下来了。"
                    }
                }
                else -> Unit
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
        if (watching && frame != null) {
            LiveView(
                frame = frame!!,
                onClose = {
                    watching = false
                    frame = null
                    note = "你不看了。老人那边还会继续共享一会儿，直到他停或者满五分钟。"
                },
            )
        } else {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 12.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("放心一听 · 家人", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.semantics { contentDescription = "回到老人界面" },
                    ) {
                        Text("返回", color = CreamSoft, fontSize = 18.sp)
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    if (paired) {
                        PairedPanel(
                            status = status,
                            room = pairedRoom,
                            alerts = alerts,
                            waiting = waiting,
                            note = note,
                            onWatch = {
                                if (LinkClient.requestScreen()) {
                                    waiting = true
                                    note = "已经问老人了，等他点「同意」。"
                                } else {
                                    note = "还没连上老人手机，等一会儿再试。"
                                }
                            },
                            onClearAlerts = { store.clearAlerts() },
                            onUnpair = {
                                GuardianLinkService.stop(context)
                                if (LinkClient.currentRole == UserRole.GUARDIAN) LinkClient.disconnect()
                                store.clearGuardianPairing()
                                paired = false
                                pairedRoom = ""
                                note = "已经解除配对。老人那边不会再有提醒。"
                                if (elderRoom.isNotBlank()) {
                                    LinkClient.connect(elderRoom, UserRole.ELDER, DEFAULT_RELAY_URL)
                                }
                            },
                        )
                    } else {
                        UnpairedPanel(
                            codeInput = codeInput,
                            onCodeChange = { codeInput = it },
                            onConnect = {
                                if (!LinkClient.isValidRoom(codeInput)) {
                                    note = "配对码是 9 位数字，再对一遍。"
                                    return@UnpairedPanel
                                }
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                                    != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                store.saveGuardianPairing(codeInput)
                                paired = true
                                pairedRoom = codeInput
                                note = "正在连老人手机，稍等一下。"
                                codeInput = ""
                            },
                            elderRoom = elderRoom,
                            onMakeCode = {
                                elderRoom = machine.startElderLink()
                            },
                            onReadCode = { machine.readElderCode() },
                        )
                    }
                    if (note.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(note, color = RiskAmber, fontSize = 18.sp, lineHeight = 27.sp)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun UnpairedPanel(
    codeInput: String,
    onCodeChange: (String) -> Unit,
    onConnect: () -> Unit,
    elderRoom: String,
    onMakeCode: () -> Unit,
    onReadCode: () -> Unit,
) {
    SectionTitle("我是家人")
    Text(
        "在老人的手机上打开放心一听，点「家人」，生成配对码，再输到这里。",
        color = CreamSoft,
        fontSize = 18.sp,
        lineHeight = 27.sp,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = codeInput,
        onValueChange = { value -> onCodeChange(value.filter { it.isDigit() }.take(9)) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("输入 9 位配对码", color = Muted, fontSize = 18.sp) },
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 26.sp,
            color = Cream,
            letterSpacing = 4.sp,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = RiskAmber,
            unfocusedBorderColor = Cream.copy(alpha = 0.3f),
            cursorColor = RiskAmber,
        ),
        shape = RoundedCornerShape(16.dp),
    )
    Spacer(Modifier.height(14.dp))
    FamilyButton(
        text = "连上老人手机",
        description = "用配对码连上老人的手机",
        filled = true,
        enabled = codeInput.length == 9,
        onClick = onConnect,
    )

    Spacer(Modifier.height(30.dp))
    SectionTitle("我是老人")
    Text(
        "点下面生成配对码，念给家人，让他们输到自己的手机里。家人有事会马上知道；想看屏幕，每次都要您同意。",
        color = CreamSoft,
        fontSize = 18.sp,
        lineHeight = 27.sp,
    )
    Spacer(Modifier.height(12.dp))
    if (elderRoom.isBlank()) {
        FamilyButton(
            text = "生成配对码",
            description = "生成给家人的配对码",
            filled = false,
            enabled = true,
            onClick = onMakeCode,
        )
    } else {
        Text(
            text = elderRoom.chunked(3).joinToString(" "),
            color = Cream,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        FamilyButton(
            text = "再念一遍",
            description = "用声音再念一遍配对码",
            filled = false,
            enabled = true,
            onClick = onReadCode,
        )
    }
}

@Composable
private fun PairedPanel(
    status: LinkStatus,
    room: String,
    alerts: List<AlertEvent>,
    waiting: Boolean,
    note: String,
    onWatch: () -> Unit,
    onClearAlerts: () -> Unit,
    onUnpair: () -> Unit,
) {
    SectionTitle("守护家人")
    Text(
        text = when {
            !status.connected -> "正在连中转，稍等一下"
            status.peerOnline -> "老人手机在线"
            else -> "老人手机还没连上"
        },
        color = if (status.peerOnline) RiskGreen else RiskAmber,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        "配对码 ${room.chunked(3).joinToString(" ")}",
        color = Muted,
        fontSize = 17.sp,
        letterSpacing = 1.sp,
    )
    Spacer(Modifier.height(16.dp))
    FamilyButton(
        text = if (waiting) "等老人同意…" else "看老人屏幕",
        description = "请老人打开屏幕共享",
        filled = true,
        enabled = status.peerOnline && !waiting,
        onClick = onWatch,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        "每次看都要老人自己点同意，最多五分钟，画面不留存。",
        color = Muted,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    Spacer(Modifier.height(26.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("收到的提醒", modifier = Modifier.weight(1f))
        if (alerts.isNotEmpty()) {
            TextButton(onClick = onClearAlerts) {
                Text("清空", color = CreamSoft, fontSize = 16.sp)
            }
        }
    }
    if (alerts.isEmpty()) {
        Text(
            "老人手机上判定出可疑的话，会马上送到这里。",
            color = Muted,
            fontSize = 17.sp,
            lineHeight = 26.sp,
        )
    } else {
        for (alert in alerts) {
            AlertCard(alert)
            Spacer(Modifier.height(12.dp))
        }
    }

    Spacer(Modifier.height(10.dp))
    TextButton(onClick = onUnpair, modifier = Modifier.heightIn(min = 52.dp)) {
        Text("解除配对", color = Muted, fontSize = 17.sp)
    }
}

@Composable
private fun AlertCard(alert: AlertEvent) {
    val accent = when (alert.risk) {
        RiskDecision.RED -> RiskRed
        RiskDecision.AMBER -> RiskAmber
        RiskDecision.GREEN -> RiskGreen
        RiskDecision.UNKNOWN -> RiskAmber
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(BgSoft)
            .border(2.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                alert.label,
                color = accent,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(formatTime(alert.createdAt), color = Muted, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(alert.plain, color = Cream, fontSize = 18.sp, lineHeight = 27.sp)
        if (alert.maskedText.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(alert.maskedText, color = CreamSoft, fontSize = 17.sp, lineHeight = 26.sp)
        }
        if (alert.actions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("告诉老人这样做：", color = Muted, fontSize = 15.sp)
            alert.actions.forEachIndexed { index, action ->
                Text("${index + 1}. $action", color = CreamSoft, fontSize = 17.sp, lineHeight = 26.sp)
            }
        }
    }
}

@Composable
private fun LiveView(frame: ImageBitmap, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Image(
            bitmap = frame,
            contentDescription = "老人手机屏幕，实时画面",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("老人屏幕 · 实时", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) {
                Text("关掉", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Cream,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun FamilyButton(
    text: String,
    description: String,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(30.dp)
    val bg = when {
        !enabled -> Color.Transparent
        filled -> Cream
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> Muted
        filled -> Ink
        else -> Cream
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(shape)
            .background(bg)
            .border(2.dp, if (enabled) Cream.copy(alpha = 0.45f) else Cream.copy(alpha = 0.15f), shape)
            .clickable(enabled = enabled, onClick = onClick, onClickLabel = description)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))
