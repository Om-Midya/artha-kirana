package com.artha.kirana.domain.usecase

import com.artha.kirana.domain.model.PnlPeriod
import com.artha.kirana.util.TimeRange

/** Start-of-window timestamp for a [PnlPeriod]. Single source for period→range mapping. */
fun PnlPeriod.startFrom(now: Long): Long = when (this) {
    PnlPeriod.TODAY -> TimeRange.startOfToday(now)
    PnlPeriod.THIS_WEEK -> TimeRange.startOfWeek(now)
    PnlPeriod.THIS_MONTH -> TimeRange.startOfMonth(now)
}
