package com.fangxin.yiting.domain

enum class RiskLevel { RED, AMBER, GREEN }

enum class RiskDecision { RED, AMBER, GREEN, UNKNOWN }

enum class CheckSource { VOICE, DEMO }

data class TermRule(
    val word: String,
    val id: String,
    val group: String,
    val level: RiskLevel,
    val reason: String,
    val needsRequest: Boolean = false,
)

data class Evidence(
    val id: String,
    val group: String,
    val level: RiskLevel,
    val reason: String,
    val layer: String,
)

data class CheckResult(
    val risk: RiskDecision,
    val label: String,
    val plain: String,
    val reasons: List<String>,
    val actions: List<String>,
    val fallback: Boolean = false,
    val fallbackNote: String? = null,
    val source: CheckSource,
    val hits: List<String> = emptyList(),
)
