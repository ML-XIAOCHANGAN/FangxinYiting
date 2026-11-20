package com.fangxin.yiting.domain

import java.net.URI
import java.text.Normalizer
import kotlin.math.max
import kotlin.math.min

object RiskCheck {
    private val ZERO_WIDTH = Regex("[\\u200B-\\u200D\\uFEFF]")
    private val SPACES = Regex("\\s+")
    private val CJK = Regex("[\\u4e00-\\u9fff]")
    private val LEGIT_CODE = Regex("验证码(为|是|：|:)\\s*\\d{4,8}")
    private val IP_HOST = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    private val LABELS = mapOf(
        RiskDecision.RED to "很可能是骗子",
        RiskDecision.AMBER to "有点可疑",
        RiskDecision.GREEN to "没听出问题",
        RiskDecision.UNKNOWN to "没听准",
    )

    private val PLAIN = mapOf(
        RiskDecision.RED to "先停下，这很可能是骗子。",
        RiskDecision.AMBER to "先别急着办。",
        RiskDecision.GREEN to "我没听出明显的骗法，但动钱之前先跟家人说一声。",
        RiskDecision.UNKNOWN to "这段我没听准，先别急着办。",
    )

    private val ACTIONS: Map<RiskDecision, List<String>> = mapOf(
        RiskDecision.RED to listOf("先挂断，别再听他说", "别转账，别说验证码", "打 96110 问一句"),
        RiskDecision.AMBER to listOf("先放一放，别急着办", "打 96110 问一句", "让家人帮你听一遍"),
        RiskDecision.GREEN to listOf("动钱前先跟家人说", "别把验证码给别人", "拿不准打 96110"),
        RiskDecision.UNKNOWN to listOf("先别急着办", "打 96110 问一句", "让家人再听一遍"),
    )

    private fun defaultReasons(decision: RiskDecision): List<String> = when (decision) {
        RiskDecision.RED -> listOf("有几处很不对劲", "正规单位不会这么催", "先停下来最稳")
        RiskDecision.AMBER -> listOf("有一处听着不对劲", "信息不全，没法放心", "先问清楚最稳")
        RiskDecision.GREEN -> listOf("没听到常见的骗法", "没要验证码和卡号", "没催你马上转钱")
        RiskDecision.UNKNOWN -> listOf("话太少，我没听准", "先别急着办", "拿不准打 96110")
    }

    fun analyzeText(rawText: String, source: CheckSource = CheckSource.VOICE): CheckResult {
        val text = cleanText(rawText)
        val domains = inspectDomains(text)
        val evidence = analyzeRules(text) + analyzeSemantics(text, domains).evidence

        if (evidence.any { it.level == RiskLevel.RED }) {
            return baseResult(RiskDecision.RED, uniqueReasons(evidence, RiskDecision.RED), evidence, source)
        }

        // 说话不像打字，三五个字也可能是完整的一句「他让我转钱」。
        val cjkCount = CJK.findAll(text).count()
        val tooLittle = text.length < 4 || (cjkCount < 2 && !domains.hasLink)
        if (tooLittle) {
            return baseResult(
                RiskDecision.AMBER, emptyList(), emptyList(), source,
                fallback = true,
                fallbackNote = "话太少，我没听准。再多说两句，或者直接打 96110。",
                labelOverride = LABELS.getValue(RiskDecision.UNKNOWN),
                plainOverride = PLAIN.getValue(RiskDecision.UNKNOWN),
            )
        }

        val amberGroups = evidence
            .filter { it.level == RiskLevel.AMBER && it.group != "family" }
            .map { it.group }
            .toSet()
        if (amberGroups.size >= 2) {
            return baseResult(RiskDecision.RED, uniqueReasons(evidence, RiskDecision.RED), evidence, source)
        }

        if (evidence.isNotEmpty()) {
            return baseResult(RiskDecision.AMBER, uniqueReasons(evidence, RiskDecision.AMBER), evidence, source)
        }

        val reasons = uniqueReasons(emptyList(), RiskDecision.GREEN).toMutableList()
        return baseResult(RiskDecision.GREEN, reasons, emptyList(), source)
    }

    // 云端没答上来时不能用本地结果说「没问题」，只能给保守的黄色。
    fun cloudMiss(local: CheckResult, source: CheckSource = CheckSource.VOICE): CheckResult {
        if (local.risk == RiskDecision.RED) return local
        val reasons = if (local.risk == RiskDecision.AMBER) local.reasons.take(2) else emptyList()
        return CheckResult(
            risk = RiskDecision.AMBER,
            label = LABELS.getValue(RiskDecision.UNKNOWN),
            plain = "联网没连上，我没能细听。先别急着办。",
            reasons = reasons,
            actions = ACTIONS.getValue(RiskDecision.AMBER),
            fallback = true,
            fallbackNote = "等会儿网络好了，再按一下屏幕说一遍；拿不准就打 96110。",
            source = source,
            hits = local.hits,
        )
    }

    private fun cleanText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
        .replace(ZERO_WIDTH, "")
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        .trim()

    private fun clip(text: String, max: Int = 20): String {
        val normalized = SPACES.replace(text, " ").trim()
        return if (normalized.length > max) normalized.take(max - 1) + "…" else normalized
    }

    private fun isProtectiveMention(text: String, index: Int, length: Int): Boolean {
        val before = text.substring(max(0, index - 16), index)
        val after = text.substring(min(text.length, index + length), min(text.length, index + length + 12))
        return RiskRules.PROTECTIVE_BEFORE.containsMatchIn(before) ||
            RiskRules.PROTECTIVE_AFTER.containsMatchIn(after)
    }

    private fun hasRequestContext(text: String, index: Int, length: Int): Boolean {
        val window = text.substring(max(0, index - 18), min(text.length, index + length + 18))
        return RiskRules.REQUEST_WORDS.containsMatchIn(window)
    }

    private fun isLegitCodeNotice(text: String, index: Int, length: Int): Boolean {
        val near = text.substring(max(0, index - 6), min(text.length, index + length + 12))
        return LEGIT_CODE.containsMatchIn(near) && !hasRequestContext(text, index, length)
    }

    private fun termEvidence(text: String, terms: List<TermRule>): List<Evidence> {
        val found = mutableListOf<Evidence>()
        for (term in terms) {
            val index = text.indexOf(term.word, ignoreCase = true)
            if (index < 0) continue
            if (term.needsRequest && !hasRequestContext(text, index, term.word.length)) continue
            if (isProtectiveMention(text, index, term.word.length)) continue
            if (term.id == "cred-code" && isLegitCodeNotice(text, index, term.word.length)) continue
            if (found.any { it.id == term.id }) continue
            found += Evidence(term.id, term.group, term.level, term.reason, "rule")
        }
        return found
    }

    private fun analyzeRules(text: String): List<Evidence> = buildList {
        addAll(termEvidence(text, RiskRules.CREDENTIAL_TERMS))
        addAll(termEvidence(text, RiskRules.MONEY_TERMS))
        addAll(termEvidence(text, RiskRules.IMPERSONATION_TERMS))
        addAll(termEvidence(text, RiskRules.FUNNEL_TERMS))
        addAll(termEvidence(text, RiskRules.ISOLATION_TERMS))
        addAll(termEvidence(text, RiskRules.REMOTE_TERMS))
    }

    private data class UrlInfo(val host: String)

    private data class DomainFinding(
        val hasLink: Boolean,
        val officialLink: Boolean,
        val evidence: List<Evidence>,
    )

    private data class Semantics(val evidence: List<Evidence>)

    private fun normalizeHost(raw: String): String {
        val value = raw.trim().trimEnd(
            '。', '，', '；', '！', '？', '、', '）', '】', '"', '\'', '”', '’',
        )
        return try {
            val target = if (value.startsWith("http", ignoreCase = true)) value else "http://$value"
            val host = URI(target).host ?: return ""
            host.lowercase().removePrefix("www.")
        } catch (error: Exception) {
            ""
        }
    }

    private fun extractHosts(text: String): List<UrlInfo> {
        val seen = mutableSetOf<String>()
        val hosts = mutableListOf<UrlInfo>()
        for (match in RiskRules.URL.findAll(text)) {
            val host = normalizeHost(match.value)
            if (host.isNotEmpty() && seen.add(host)) hosts += UrlInfo(host)
        }
        return hosts
    }

    private fun isOfficialHost(host: String): Boolean =
        RiskRules.OFFICIAL_DOMAIN_SUFFIXES.any { host == it || host.endsWith(".$it") }

    private fun isShortHost(host: String): Boolean =
        RiskRules.SHORT_LINK_HOSTS.any { host == it || host.endsWith(".$it") }

    private fun inspectDomains(text: String): DomainFinding {
        val hosts = extractHosts(text)
        val evidence = mutableListOf<Evidence>()
        var officialLink = false

        fun add(id: String, group: String, reason: String) {
            if (evidence.any { it.id == id }) return
            evidence += Evidence(id, group, RiskLevel.AMBER, reason, "semantic")
        }

        for (item in hosts) {
            val host = item.host
            if (isOfficialHost(host)) {
                officialLink = true
                continue
            }
            if (isShortHost(host)) add("link-short", "link", "链接是短链接，看不出真假")
            if (IP_HOST.matches(host)) add("link-ip", "link", "链接是一串数字，不像官网")
            if (host.contains("xn--")) add("link-puny", "link", "链接是编码过的怪地址")
            if (RiskRules.BRAND_TOKENS.any { host.contains(it) }) {
                add("link-lookalike", "link", "域名很像官网，但不是")
            }
            if (RiskRules.OFFICIAL_BRANDS.containsMatchIn(text) &&
                RiskRules.REQUEST_ACTION_WORDS.containsMatchIn(text)
            ) {
                add("link-unofficial", "link-official", "链接不是官方网站")
            }
            if (RiskRules.REQUEST_ACTION_WORDS.containsMatchIn(text)) {
                add("link-form", "link-form", "点开就要你填资料")
            }
        }

        return DomainFinding(hosts.isNotEmpty(), officialLink, evidence)
    }

    private fun analyzeSemantics(text: String, domains: DomainFinding): Semantics {
        val evidence = mutableListOf<Evidence>()

        for (item in termEvidence(text, RiskRules.URGENCY_TERMS)) {
            evidence += if (domains.hasLink) {
                item.copy(level = RiskLevel.RED, reason = "催你马上办，还带你点链接")
            } else {
                item
            }
        }

        evidence += termEvidence(text, RiskRules.REFUND_TERMS)
        evidence += termEvidence(text, RiskRules.LOAN_TERMS)
        evidence += termEvidence(text, RiskRules.INVEST_TERMS)
        evidence += termEvidence(text, RiskRules.PRIZE_TERMS)
        evidence += termEvidence(text, RiskRules.CASH_TERMS)
        evidence += termEvidence(text, RiskRules.SUBSCRIPTION_TERMS)
        evidence += domains.evidence

        val familyBorrow = RiskRules.FAMILY_CHANGE_PATTERN.containsMatchIn(text) &&
            RiskRules.FAMILY_MONEY_PATTERN.containsMatchIn(text) &&
            (RiskRules.FAMILY_IDENTITY_PATTERN.containsMatchIn(text) ||
                RiskRules.FAMILY_BORROW_PATTERN.containsMatchIn(text))

        if (familyBorrow) {
            evidence += Evidence(
                "family-borrow", "family", RiskLevel.AMBER,
                "说是家人，但换了号码要钱", "semantic",
            )
        }

        if (RiskRules.TYPO_TERMS.any { text.contains(it) } && domains.hasLink) {
            evidence += Evidence("typo", "typo", RiskLevel.AMBER, "文字里有仿造的错别字", "semantic")
        }

        val kept = if (familyBorrow) evidence.filter { it.group != "transfer-soft" } else evidence
        return Semantics(kept)
    }

    private fun uniqueReasons(evidence: List<Evidence>, decision: RiskDecision): List<String> {
        val ranked = evidence.sortedWith(
            compareBy<Evidence>(
                { if (it.level == RiskLevel.RED) 0 else 1 },
                { if (it.layer == "rule") 0 else 1 },
            ),
        )
        val groups = mutableSetOf<String>()
        val reasons = mutableListOf<String>()
        for (item in ranked) {
            if (!groups.add(item.group)) continue
            reasons += clip(item.reason)
            if (reasons.size == 3) break
        }
        for (fallback in defaultReasons(decision)) {
            if (reasons.size == 3) break
            if (reasons.none { it == fallback }) reasons += clip(fallback)
        }
        return reasons.take(3)
    }

    private fun pickActions(decision: RiskDecision, evidence: List<Evidence>): List<String> {
        if (decision == RiskDecision.RED &&
            evidence.any { it.group == "transfer" || it.group == "credential" }
        ) {
            return listOf("马上停下，别再转钱", "打银行客服或 96110", "把这事告诉家里人")
        }
        return ACTIONS.getValue(decision)
    }

    private fun baseResult(
        decision: RiskDecision,
        reasons: List<String>,
        evidence: List<Evidence>,
        source: CheckSource,
        fallback: Boolean = false,
        fallbackNote: String? = null,
        labelOverride: String? = null,
        plainOverride: String? = null,
    ): CheckResult {
        val level = if (fallback) RiskDecision.UNKNOWN else decision
        return CheckResult(
            risk = level,
            label = labelOverride ?: LABELS.getValue(level),
            plain = plainOverride ?: PLAIN.getValue(level),
            reasons = reasons,
            actions = pickActions(level, evidence),
            fallback = fallback,
            fallbackNote = fallbackNote,
            source = source,
            hits = evidence.map { it.id },
        )
    }
}
