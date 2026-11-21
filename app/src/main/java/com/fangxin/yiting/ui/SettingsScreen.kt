package com.fangxin.yiting.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fangxin.yiting.VoiceMachine

private val SettingsSentences = listOf(
    "设置。",
    "上面是家人，往下是朗读。",
    "点一下就能开或者关。",
)

@Composable
fun SettingsScreen(
    machine: VoiceMachine,
    onBack: () -> Unit,
    onFamily: () -> Unit,
) {
    val screenOn by machine.screenSpeech.collectAsState()
    val replyOn by machine.replySpeech.collectAsState()

    LaunchedEffect(Unit) { machine.announceScreen(SettingsSentences) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .safeDrawingPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 12.dp, top = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("设置", color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "回到老人界面" }) {
                Text("返回", color = CreamSoft, fontSize = 18.sp)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingsCard(
                title = "家人配对与提醒",
                desc = "生成配对码给家人。老人手机上有可疑，家人那边马上知道。",
                action = "打开",
                onClick = onFamily,
            )
            SettingsSwitch(
                title = "换个页面，念给我听",
                desc = "念这一屏要紧的话：怎么开始，按钮在哪儿。",
                on = screenOn,
                onChange = { next ->
                    machine.setScreenSpeech(next)
                    machine.speakScript(listOf(if (next) "界面朗读开了。" else "界面朗读关了。"))
                },
            )
            SettingsSwitch(
                title = "结论和回答，念给我听",
                desc = "判完骗局、小望回话，都说出来给你听。",
                on = replyOn,
                onChange = { next ->
                    machine.setReplySpeech(next)
                    machine.speakScript(listOf(if (next) "结论和回答都念了。" else "结论和回答不念了，屏幕上照旧看得见。"))
                },
            )
            if (machine.screenReaderOn) {
                Text(
                    "手机开了系统读屏，界面朗读先让开，免得两个声音抢。结论和回答照旧念。",
                    color = Muted,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    desc: String,
    action: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(BgSoft)
            .border(2.dp, Cream.copy(alpha = 0.28f), shape)
            .clickable(onClick = onClick, onClickLabel = action)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "$title，$desc"
            }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(action, color = CreamSoft, fontSize = 17.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(desc, color = CreamSoft, fontSize = 18.sp, lineHeight = 27.sp)
    }
}

@Composable
private fun SettingsSwitch(
    title: String,
    desc: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val state = if (on) "已开启" else "已关闭"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(shape)
            .background(if (on) BgSoft else Color.Transparent)
            .border(2.dp, if (on) Cream.copy(alpha = 0.55f) else Cream.copy(alpha = 0.2f), shape)
            .clickable(onClick = { onChange(!on) }, onClickLabel = if (on) "关闭" else "开启")
            .semantics(mergeDescendants = true) {
                role = Role.Switch
                contentDescription = "$title，$desc，$state"
            }
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = if (on) Cream else Muted,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(0.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) Cream else Color.Transparent)
                    .border(2.dp, if (on) Color.Transparent else Cream.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    text = state,
                    color = if (on) Ink else Muted,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(desc, color = CreamSoft, fontSize = 18.sp, lineHeight = 27.sp)
    }
}
