package com.fangxin.yiting.domain

enum class UserRole { ELDER, GUARDIAN }

/** 家人通道复用「放心一眼」已上线的同源中转，配对码随机、消息读走即删。 */
const val DEFAULT_RELAY_URL = "https://fangxin-yiyan-oqspdkiq3lk.qoder.zone"

data class AlertEvent(
    val id: String,
    val risk: RiskDecision,
    val label: String,
    val plain: String,
    val maskedText: String,
    val actions: List<String>,
    val source: CheckSource,
    val createdAt: Long,
)

object TextMasking {
    private val CODE = Regex("(验证码|校验码|动态码|动态密码)(\\s*[为是:：]?\\s*)(\\d{4,8})")
    private val ID_CARD = Regex("\\b\\d{17}[\\dXx]\\b")
    private val BANK_CARD = Regex("\\b(?:\\d[ -]?){15,19}\\d\\b")
    private val MOBILE = Regex("\\b1[3-9]\\d{9}\\b")

    fun maskSensitive(input: String): String {
        var text = input
        text = CODE.replace(text) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}******"
        }
        text = ID_CARD.replace(text) { match ->
            "**************${match.value.takeLast(4)}"
        }
        text = BANK_CARD.replace(text) { match ->
            val digits = match.value.filter { it.isDigit() }
            "**** **** **** ${digits.takeLast(4)}"
        }
        text = MOBILE.replace(text) { match ->
            "*******${match.value.takeLast(4)}"
        }
        return text
    }
}
