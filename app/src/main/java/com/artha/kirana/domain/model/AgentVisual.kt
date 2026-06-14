package com.artha.kirana.domain.model

/** A structured visual the chat renders under an agent answer. Two primitives cover all tools. */
sealed interface AgentVisual {
    data class BarChart(val title: String, val bars: List<Bar>) : AgentVisual
    data class Stats(val title: String, val rows: List<Stat>) : AgentVisual
}

data class Bar(val label: String, val value: Double, val highlight: Boolean = false)

/** Colour intent for a stat value (mapped to theme colours in the UI). */
enum class StatTone { NEUTRAL, IN, OUT, UDHAAR }

data class Stat(val label: String, val value: String, val tone: StatTone = StatTone.NEUTRAL)
