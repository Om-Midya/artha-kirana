package com.artha.kirana.domain.usecase

/** Pulls the first number out of a qty string ("2 kg" -> 2.0). null / no digits -> 0.0. */
internal fun parseLeadingQty(qty: String?): Double {
    if (qty == null) return 0.0
    val match = Regex("""\d+(\.\d+)?""").find(qty) ?: return 0.0
    return match.value.toDoubleOrNull() ?: 0.0
}
