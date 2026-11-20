package com.fangxin.yiting.ui

import android.app.Activity
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fangxin.yiting.Phase
import com.fangxin.yiting.R
import com.fangxin.yiting.VoiceMachine
import com.fangxin.yiting.domain.RiskDecision
import com.fangxin.yiting.service.ScreenShareService

@Composable
fun YitingApp(machine: VoiceMachine, onTalk: () -> Unit) {
    var textOpen by remember { mutableStateOf(false) }
    var helpOpen by remember { mutableStateOf(false) }
    var familyOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            ScreenShareService.start(context, result.resultCode, data)
            machine.screenConsentResult(true)
        } else {
            machine.screenConsentResult(false)
        }
    }

    YitingTheme {
        Box(Modifier.fillMaxSize().background(Bg).safeDrawingPadding()) {
            if (familyOpen) {
                FamilyScreen(machine) { familyOpen = false }
            } else {
                AudioScreen(
                    machine,
                    onTalk,
                    onType = { textOpen = true },
                    onFamily = {
                        machine.stopVoice()
                        familyOpen = true
                    },
                    onHelp = {
                        helpOpen = true
                        machine.speakHelp()
                    },
                )
            }
        }
        if (machine.screenAsk) {
            ScreenConsentDialog(
                onAgree = {
                    val manager = context.getSystemService(MediaProjectionManager::class.java)
                    val intent = manager?.createScreenCaptureIntent()
                    if (intent != null) {
                        projectionLauncher.launch(intent)
                    } else {
                        machine.screenConsentResult(false)
                    }
                },
                onDecline = { machine.dismissScreenAsk() },
            )
        }
        if (textOpen) {
            TextDialog(
                onClose = { textOpen = false },
                onSubmit = { text ->
                    textOpen = false
                    machine.judgeTyped(text)
                },
            )
        }
        if (helpOpen) {
            HelpDialog(
                onClose = {
                    helpOpen = false
                    machine.stopVoice()
                },
                onDemo = {
                    helpOpen = false
                    machine.startDemo()
                },
            )
        }
    }
}

@Composable
private fun AudioScreen(
    machine: VoiceMachine,
    onTalk: () -> Unit,
    onType: () -> Unit,
    onFamily: () -> Unit,
    onHelp: () -> Unit,
) {
    val status = when (machine.phase) {
        Phase.LISTENING -> "我在听"
        Phase.THINKING -> "我在想"
        Phase.VERDICT -> machine.result?.label ?: "听完了"
        else -> "我在"
    }
    val prompt = when (machine.phase) {
        Phase.LISTENING -> "你慢慢说，说完再按一下"
        Phase.THINKING -> "别急，我想一想"
        Phase.VERDICT -> "再按一下，可以说下一句"
        else -> "按一下屏幕，把可疑的话学给我听"
    }
    val owl = when {
        machine.phase == Phase.THINKING -> R.drawable.thinking
        machine.phase == Phase.LISTENING -> R.drawable.chat
        machine.result?.risk == RiskDecision.RED -> R.drawable.alert
        machine.result?.risk == RiskDecision.AMBER -> R.drawable.worry
        else -> R.drawable.idle
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 12.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("放心一听", color = Cream, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onFamily, modifier = Modifier.semantics { contentDescription = "家人，配对和看警报" }) {
                Text("家人", color = CreamSoft, fontSize = 18.sp)
            }
            TextButton(onClick = onHelp, modifier = Modifier.semantics { contentDescription = "怎么用，看用法说明" }) {
                Text("怎么用", color = CreamSoft, fontSize = 18.sp)
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(36.dp))
                .clickable(
                    onClick = onTalk,
                    onClickLabel = if (machine.phase == Phase.LISTENING) "结束说话" else "开始说话",
                )
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = buildString {
                        append("说话按钮。")
                        append(status)
                        append("。")
                        append(prompt)
                        append("。")
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
            ) {
                Text(
                    text = status,
                    color = when (machine.result?.risk) {
                        RiskDecision.RED -> RiskRed
                        RiskDecision.AMBER -> RiskAmber
                        else -> Cream
                    },
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = prompt,
                    color = CreamSoft,
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Image(
                    painter = painterResource(owl),
                    contentDescription = null,
                    modifier = Modifier.size(210.dp),
                )
                if (machine.subtitle.isNotBlank() && machine.phase != Phase.LISTENING && machine.note.isBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = machine.subtitle,
                        color = CreamSoft,
                        fontSize = 19.sp,
                        lineHeight = 28.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 18.dp),
                    )
                }
                if (machine.note.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = machine.note,
                        color = RiskAmber,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (machine.phase == Phase.VERDICT) {
                AudioAction(
                    text = "打 96110",
                    description = "打开拨号，打 96110",
                    filled = true,
                    modifier = Modifier.weight(1f),
                ) { machine.dial() }
                AudioAction(
                    text = "再说一遍",
                    description = "再听一遍结论",
                    modifier = Modifier.weight(1f),
                ) { machine.repeat() }
            } else {
                AudioAction(
                    text = "打字",
                    description = "用键盘打字判一判",
                    modifier = Modifier.weight(1f),
                ) { onType() }
                AudioAction(
                    text = "试一次",
                    description = "不用麦克风，听一遍完整的示范",
                    modifier = Modifier.weight(1f),
                ) { machine.startDemo() }
            }
        }
    }
}

@Composable
private fun AudioAction(
    text: String,
    description: String,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(if (filled) Cream else Color.Transparent)
            .border(2.dp, if (filled) Color.Transparent else Cream.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
            .clickable(onClick = onClick, onClickLabel = description)
            .semantics(mergeDescendants = true) { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (filled) Ink else Cream,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TextDialog(onClose: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(180)
        field.requestFocus()
    }
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BgSoft)
                .padding(20.dp),
        ) {
            Text("把可疑的话打在这里", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 300) text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(field),
                minLines = 3,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, color = Cream),
                placeholder = { Text("例如：他说我的社保卡涉案，让我转到安全账户", color = Muted, fontSize = 17.sp) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RiskAmber,
                    unfocusedBorderColor = Cream.copy(alpha = 0.3f),
                    cursorColor = RiskAmber,
                ),
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { onSubmit(text.trim()) },
                    enabled = text.trim().length >= 4,
                    colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Ink),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                ) {
                    Text("判一判", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = CreamSoft),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                        .border(1.dp, Cream.copy(alpha = 0.3f), RoundedCornerShape(28.dp)),
                ) {
                    Text("收起", fontSize = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun ScreenConsentDialog(onAgree: () -> Unit, onDecline: () -> Unit) {
    Dialog(onDismissRequest = onDecline) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BgSoft)
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "家人想看看您的屏幕",
                color = Cream,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "看一会儿就关，最多五分钟，画面不保存。",
                color = CreamSoft,
                fontSize = 19.sp,
                lineHeight = 28.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            AudioAction(
                text = "同意",
                description = "同意家人看屏幕",
                filled = true,
                modifier = Modifier.fillMaxWidth(),
                onClick = onAgree,
            )
            Spacer(Modifier.height(12.dp))
            AudioAction(
                text = "不同意",
                description = "不同意家人看屏幕",
                modifier = Modifier.fillMaxWidth(),
                onClick = onDecline,
            )
        }
    }
}

@Composable
private fun HelpDialog(onClose: () -> Unit, onDemo: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BgSoft)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text("怎么用", color = Cream, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text("1. 点屏幕中间，把可疑的话说一遍；说完再点一下。", color = CreamSoft, fontSize = 19.sp, lineHeight = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text("2. 我会告诉你：是不是骗子、为什么、怎么做。", color = CreamSoft, fontSize = 19.sp, lineHeight = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text("3. 说「再说一遍」再听；说「打 96110」就拨号。", color = CreamSoft, fontSize = 19.sp, lineHeight = 28.sp)
            Spacer(Modifier.height(12.dp))
            Text("只作参考，绝不说「安全」；拿不准就打 96110。", color = RiskAmber, fontSize = 19.sp, lineHeight = 28.sp)
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDemo, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("不用麦克风，试一次", color = RiskAmber, fontSize = 19.sp)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Ink),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            ) {
                Text("知道了", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
