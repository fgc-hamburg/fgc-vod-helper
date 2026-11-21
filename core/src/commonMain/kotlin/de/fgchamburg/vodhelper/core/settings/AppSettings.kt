package de.fgchamburg.vodhelper.core.settings

import kotlinx.serialization.Serializable

/** Non-secret app settings, kept in settings.json. Sign-in details never go here. */
@Serializable
data class AppSettings(val setupDone: Boolean = false)
