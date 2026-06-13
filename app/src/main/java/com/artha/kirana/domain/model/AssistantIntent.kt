package com.artha.kirana.domain.model

/** Intents the Assistant can route to. Thin slice: the rest are added as use-cases are wired. */
enum class AssistantIntent { LOG_SALE, RECORD_PAYMENT, QUERY_PNL, UNKNOWN }
