package com.anchor.data.repository

import kotlinx.serialization.json.Json

/**
 * Shared JSON instance used by all platform persistence layers.
 * lenient + ignoreUnknownKeys ensures forward-compatibility when new fields are added.
 */
val AnchorJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = true
}
