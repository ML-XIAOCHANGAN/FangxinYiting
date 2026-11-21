package com.fangxin.yiting

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.speech.SpeechRecognizer
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fangxin.yiting.audio.Cues
import com.fangxin.yiting.data.LinkStore
import com.fangxin.yiting.data.SettingsStore
import com.fangxin.yiting.domain.AlertEvent
import com.fangxin.yiting.domain.CheckResult
import com.fangxin.yiting.domain.CheckSource
import com.fangxin.yiting.domain.DEFAULT_RELAY_URL
import com.fangxin.yiting.domain.RiskCheck
import com.fangxin.yiting.domain.RiskDecision
import com.fangxin.yiting.domain.TextMasking
import com.fangxin.yiting.domain.UserRole
import com.fangxin.yiting.link.LinkClient
import com.fangxin.yiting.link.LinkEvent
import com.fangxin.yiting.net.AiAnswer
import com.fangxin.yiting.net.AskClient
import com.fangxin.yiting.speech.Dictation
import com.fangxin.yiting.speech.Tts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

enum class Phase { IDLE, LISTENING, THINKING, VERDICT }

class VoiceMachine(private val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tts = Tts(app)
    private val cues = Cues(app)
    private val links = LinkStore.get(app)
    private val settings = SettingsStore.get(app)
    private val a11y: AccessibilityManager? = app.getSystemService(AccessibilityManager::class.java)

    private var dictation: Dictation? = null
    private var listenTimer: Runnable? = null
    private var runId = 0

    var phase by mutableStateOf(Phase.IDLE)
        private set
    var transcript by mutableStateOf("")
        private set
    var note by mutableStateOf("")
        private set
    var result by mutableStateOf<CheckResult?>(null)
        private set
    var subtitle by mutableStateOf("")
        private set
    var demoing by mutableStateOf(false)
        private set
    var script by mutableStateOf(IntroSentences)
        private set
    var screenAsk by mutableStateOf(false)
        private set

    /** 系统在朗读屏幕（TalkBack / 触摸探索）时，界面朗读自动让位。 */
    var screenReaderOn by mutableStateOf(false)
        private set

    val screenSpeech: StateFlow<Boolean> = settings.screenSpeech
    val replySpeech: StateFlow<Boolean> = settings.replySpeech

    val micAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(app)
    val elderRoom: String get() = links.elderRoom

    init {
        screenReaderOn = a11y?.isTouchExplorationEnabled == true
        a11y?.addTouchExplorationStateChangeListener { enabled -> screenReaderOn = enabled }
        scope.launch {
            kotlinx.coroutines.delay(350)
            announceScreen(IntroSentences)
        }
        if (links.elderRoom.isNotBlank()) connectAsElder()
        scope.launch {
            LinkClient.events.collectLatest { event ->
                if (event is LinkEvent.ScreenRequested && LinkClient.currentRole == UserRole.ELDER) {
                    onScreenRequested()
                }
            }
        }
    }

    private fun connectAsElder() {
        val room = links.elderRoom
        if (room.isBlank()) return
        LinkClient.connect(room, UserRole.ELDER, DEFAULT_RELAY_URL)
    }

    /** 老人点「连家人」：生成配对码，并保持在线等家人的请求。 */
    fun startElderLink(): String {
        val room = links.ensureElderRoom()
        connectAsElder()
        val digits = room.map { "零一二三四五六七八九"[it - '0'] }.joinToString("，")
        speakScript(
            listOf(
                "配对码是 $digits。",
                "让家人在他们的手机上打开放心一听，点设置，再点家人，把这个码输进去。",
                "码记不住，就点「再念一遍」。",
            ),
        )
        return room
    }

    fun readElderCode() {
        val room = links.elderRoom
        if (room.isBlank()) return
        val digits = room.map { "零一二三四五六七八九"[it - '0'] }.joinToString("，")
        speakScript(listOf("配对码是 $digits。"))
    }

    private fun onScreenRequested() {
        screenAsk = true
        tts.stop()
        cues.stopHeartbeat()
        announceReply(
            listOf(
                "家人想看看你的屏幕。",
                "看一会儿就关，画面不保存。",
                "同意就点下面的同意。",
            ),
        )
    }

    fun dismissScreenAsk() {
        screenAsk = false
        speakScript(listOf("好，不给家人看。"))
    }

    fun screenConsentResult(shared: Boolean) {
        screenAsk = false
        if (shared) {
            speakScript(listOf("好，家人能看到你的屏幕了。看完可以说一声停下，或者点通知里的停下。"))
        }
    }

    fun setScreenSpeech(on: Boolean) = settings.setScreenSpeech(on)

    fun setReplySpeech(on: Boolean) = settings.setReplySpeech(on)

    /** 切屏要点：开关关着、或系统读屏正在念时不开口。 */
    fun announceScreen(sentences: List<String>) =
        enqueue(sentences, screenSpeech.value && !screenReaderOn)

    /** 结论与回复：只看「结论和回答」开关，系统读屏不让位。 */
    fun announceReply(sentences: List<String>) = enqueue(sentences, replySpeech.value)

    fun speakScript(sentences: List<String>) = enqueue(sentences, allowed = true)

    private fun enqueue(sentences: List<String>, allowed: Boolean) {
        script = sentences
        if (!allowed) {
            subtitle = ""
            return
        }
        tts.speakSentences(
            sentences,
            onSentence = { index -> subtitle = sentences.getOrElse(index) { "" } },
        )
    }

    fun speak(text: String) {
        val sentences = text.split(Regex("(?<=[。！？])")).map { it.trim() }.filter { it.isNotEmpty() }
        speakScript(if (sentences.isEmpty()) listOf(text) else sentences)
    }

    fun onPrimaryTap() {
        when (phase) {
            Phase.THINKING -> Unit
            Phase.LISTENING -> stopListening()
            Phase.IDLE, Phase.VERDICT -> beginListening()
        }
    }

    fun beginListening() {
        if (phase == Phase.THINKING || phase == Phase.LISTENING) return
        tts.stop()
        cues.stopHeartbeat()
        demoing = false
        result = null
        transcript = ""
        note = ""
        subtitle = ""

        if (!micAvailable) {
            phase = Phase.IDLE
            cues.error()
            note = "这台手机不能直接听说，可以在旁边打字。"
            announceReply(listOf("这台手机不能直接听说。你可以在下面打字，或者让家人帮你听。"))
            return
        }

        runId += 1
        phase = Phase.LISTENING
        cues.start()
        val engine = Dictation(
            app,
            onPartial = { text -> transcript = text },
            onFinal = { text -> onDictationFinal(text) },
            onError = { code -> onDictationError(code) },
        )
        dictation = engine
        engine.start()
        scheduleStop(15000)
    }

    private fun scheduleStop(millis: Long) {
        cancelStop()
        val runnable = Runnable { if (phase == Phase.LISTENING) stopListening() }
        listenTimer = runnable
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(runnable, millis)
    }

    private fun cancelStop() {
        listenTimer?.let { android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it) }
        listenTimer = null
    }

    fun stopListening() {
        if (phase != Phase.LISTENING) return
        cancelStop()
        dictation?.stop()
        dictation = null
        val text = transcript.trim()
        if (text.isEmpty()) {
            phase = Phase.IDLE
            cues.error()
            note = "没听清，靠近手机再说一次。"
            announceReply(listOf("没听清。再按一下屏幕，靠近手机，慢慢说。"))
            return
        }
        cues.stopTone()
        handleUtterance(text)
    }

    private fun onDictationFinal(text: String) {
        if (phase != Phase.LISTENING) return
        cancelStop()
        dictation = null
        if (text.isBlank()) {
            phase = Phase.IDLE
            cues.error()
            note = "没听清，靠近手机再说一次。"
            announceReply(listOf("没听清。再按一下屏幕，靠近手机，慢慢说。"))
            return
        }
        transcript = text
        cues.stopTone()
        handleUtterance(text)
    }

    private fun onDictationError(code: Int) {
        if (phase != Phase.LISTENING) return
        cancelStop()
        dictation = null
        phase = Phase.IDLE
        cues.error()
        note = when (code) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "听说要联网，网络好像不通。"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "没拿到麦克风，可以在系统设置里打开。"
            else -> "没听清，再说一次。"
        }
        announceReply(listOf(note + "也可以在下面打字。"))
    }

    private fun handleUtterance(text: String) {
        val compact = text.replace(Regex("[\\s，。！？、,.!?；;：:]"), "")
        if (compact.length <= 12 && Regex("(96110|九六一一零|九六一幺零|九六一零)").containsMatchIn(compact)) {
            dial()
            return
        }
        if (compact.length <= 12 && Regex("(再说一遍|重说一遍|再念一遍|再说一次|没听清)").containsMatchIn(compact)) {
            repeat()
            return
        }
        if (compact.length <= 8 && Regex("^(停|别说了|不听了|结束|关闭)").containsMatchIn(compact)) {
            stopAll()
            phase = Phase.IDLE
            transcript = ""
            speakScript(listOf("好，我不说了。"))
            return
        }
        if (compact.length <= 8 && Regex("(怎么用|帮助|帮助我)").containsMatchIn(compact)) {
            announceScreen(helpSentences())
            return
        }
        judge(text)
    }

    fun judgeTyped(text: String) {
        val value = text.trim()
        if (value.length < 4) return
        demoing = false
        judge(value)
    }

    private fun judge(text: String) {
        val my = ++runId
        tts.stop()
        cues.stopHeartbeat()
        result = null
        subtitle = ""
        transcript = text
        note = ""
        phase = Phase.THINKING
        cues.thinking()
        cues.startHeartbeat()

        val local = RiskCheck.analyzeText(text, if (demoing) CheckSource.DEMO else CheckSource.VOICE)
        // 本地规则已经能确定是骗子时，不让老人等云端那七八秒。
        if (local.risk == RiskDecision.RED) {
            finish(local, my)
            return
        }

        scope.launch {
            val answer = withTimeoutOrNull(18000) { AskClient.ask(text) }
            if (my != runId) return@launch
            when (answer) {
                is AiAnswer.Judge -> finish(merge(local, answer), my)
                is AiAnswer.Chat -> {
                    cues.stopHeartbeat()
                    phase = Phase.IDLE
                    note = "小望说"
                    transcript = answer.reply
                    announceReply(listOf(answer.reply))
                }
                null -> finish(RiskCheck.cloudMiss(local, local.source), my)
            }
        }
    }

    private fun merge(local: CheckResult, ai: AiAnswer.Judge): CheckResult {
        val red = ai.verdict == "red"
        return CheckResult(
            risk = if (red) RiskDecision.RED else RiskDecision.AMBER,
            label = if (red) "很可能是骗子" else "有点可疑",
            plain = if (red) "先停下，这很可能是骗子。" else "先别急着办。",
            reasons = ai.reasons.ifEmpty { local.reasons },
            actions = ai.steps.ifEmpty { local.actions },
            fallback = false,
            source = local.source,
            hits = local.hits,
        )
    }

    private fun finish(value: CheckResult, my: Int) {
        if (my != runId) return
        cues.stopHeartbeat()
        result = value
        phase = Phase.VERDICT
        when (value.risk) {
            RiskDecision.RED -> cues.verdictRed()
            RiskDecision.AMBER -> cues.verdictAmber()
            RiskDecision.GREEN -> cues.verdictGreen()
            RiskDecision.UNKNOWN -> cues.verdictAmber()
        }
        val sentences = composeSentences(value).toMutableList()
        val tellFamily = !demoing &&
            (value.risk == RiskDecision.RED || value.risk == RiskDecision.AMBER) &&
            sendAlert(value)
        if (tellFamily) sentences += "已经告诉家人了。"
        announceReply(sentences)
    }

    private fun sendAlert(value: CheckResult): Boolean = LinkClient.sendAlert(
        AlertEvent(
            id = UUID.randomUUID().toString(),
            risk = value.risk,
            label = value.label,
            plain = value.plain,
            maskedText = TextMasking.maskSensitive(transcript),
            actions = value.actions,
            source = value.source,
            createdAt = System.currentTimeMillis(),
        ),
    )

    private val ordinals = listOf("第一", "第二", "第三")

    private fun composeSentences(value: CheckResult): List<String> {
        val parts = mutableListOf<String>()
        when (value.risk) {
            RiskDecision.RED -> parts += "先停下，这很可能是骗子。"
            RiskDecision.AMBER -> parts += "先别急着办。"
            RiskDecision.GREEN -> parts += value.plain
            RiskDecision.UNKNOWN -> parts += "这段我没听准，先别急着办。"
        }
        if (value.risk != RiskDecision.GREEN && value.risk != RiskDecision.UNKNOWN && value.reasons.isNotEmpty()) {
            val why = value.reasons.take(3)
                .mapIndexed { index, reason -> "${ordinals[index]}，$reason" }
                .joinToString("；")
            parts += "为什么：$why。"
        }
        if (value.actions.isNotEmpty()) {
            val how = value.actions.take(3)
                .mapIndexed { index, action -> "${ordinals[index]}，$action" }
                .joinToString("；")
            parts += "现在这样做：$how。"
        }
        if (value.fallback && value.fallbackNote != null) parts += value.fallbackNote
        parts += "拿不准，就打 96110。"
        parts += "再按一下屏幕，可以说下一句。"
        return parts
    }

    fun repeat() {
        if (script.isEmpty()) {
            speakScript(listOf("还没有结论。按一下屏幕说一句。"))
            return
        }
        tts.stop()
        speakScript(script)
    }

    fun dial() {
        tts.stop()
        cues.stopHeartbeat()
        speakScript(listOf("好，给你开拨号。号码是 96110。"))
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:96110")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            app.startActivity(intent)
        } catch (error: Exception) {
            speakScript(listOf("这台手机开不了拨号，请拿电话直接拨 96110。"))
        }
    }

    fun helpSentences(): List<String> = listOf(
        "用法很简单。",
        "按一下屏幕中间，把可疑的话学一遍；说完再按一下，我就开始想。",
        "想再听一遍，就说再说一遍；想打 96110，就说打 96110。",
    )

    fun speakHelp() {
        announceScreen(helpSentences())
    }

    fun stopVoice() {
        tts.stop()
        cues.stopHeartbeat()
    }

    fun startDemo() {
        if (phase == Phase.LISTENING || phase == Phase.THINKING) return
        runId += 1
        tts.stop()
        cues.stopHeartbeat()
        demoing = true
        phase = Phase.THINKING
        result = null
        transcript = "示范：他说我的社保卡涉案了，让我把钱转到安全账户，还让我开屏幕共享，不要告诉家里人。"
        note = ""
        cues.thinking()
        scope.launch {
            tts.speakSentences(
                DemoIntro,
                onSentence = { index -> subtitle = DemoIntro.getOrElse(index) { "" } },
            )
            kotlinx.coroutines.delay(4200)
            if (!demoing || phase != Phase.THINKING) return@launch
            judge(DEMO_TEXT)
        }
    }

    fun micDenied() {
        phase = Phase.IDLE
        note = "没拿到麦克风。可以在系统设置里打开，或者打字。"
        announceReply(listOf("没拿到麦克风。可以去手机设置里打开，或者直接在下面打字。"))
    }

    fun stopAll() {
        runId += 1
        cancelStop()
        dictation?.stop()
        dictation = null
        tts.stop()
        cues.stopHeartbeat()
        subtitle = ""
    }

    fun shutdown() {
        stopAll()
        // 正在给家人看屏幕时别断开；家人端的常驻连接由它自己的服务管。
        if (LinkClient.currentRole == UserRole.ELDER && !LinkClient.screenSharing.value) {
            LinkClient.disconnect()
        }
        cues.release()
        tts.shutdown()
        scope.cancel()
    }

    companion object {
        val DEMO_TEXT = "他说我的社保卡涉案了，让我把钱转到安全账户，还让我开屏幕共享，不要告诉家里人。"
        private val DemoIntro = listOf("好，我给你演一遍。你听到的，就是骗子刚才说的话。")
        private val IntroSentences = listOf(
            "我在，我叫小望。",
            "按一下屏幕中间，把刚才那句可疑的话学给我听。",
            "说完，再按一下，我就开始想。",
        )
    }
}
