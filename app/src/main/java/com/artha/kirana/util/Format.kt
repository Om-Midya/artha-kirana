package com.artha.kirana.util

/** Whole-rupee display, e.g. 80.0 -> "₹80". */
fun formatRupees(amount: Double): String = "₹${amount.toLong()}"
