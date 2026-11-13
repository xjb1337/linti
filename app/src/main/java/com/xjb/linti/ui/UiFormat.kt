package com.xjb.linti.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)
private val shortDateFormatter = DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA)

fun formatDateTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "—"
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(dateTimeFormatter)
}

fun formatRelativeDay(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "—"
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> date.format(shortDateFormatter)
    }
}

fun formatShortDate(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return "—"
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(shortDateFormatter)
}

fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    val remaining = safe % 60
    return "%02d:%02d".format(Locale.ROOT, minutes, remaining)
}

fun formatScore(score: Double): String =
    if (score % 1.0 == 0.0) score.toInt().toString() else "%.1f".format(Locale.ROOT, score)

fun formatPercent(value: Double): String =
    "${(value.coerceIn(0.0, 1.0) * 100).toInt()}%"
