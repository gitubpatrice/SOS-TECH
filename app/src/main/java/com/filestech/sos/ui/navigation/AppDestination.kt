package com.filestech.sos.ui.navigation

sealed interface AppDestination {
    val route: String

    data object Home : AppDestination { override val route = "home" }
    data object Emergency : AppDestination { override val route = "emergency" }
    data object Voice : AppDestination { override val route = "voice" }
    data object Cascade : AppDestination { override val route = "cascade" }
    data object Siren : AppDestination { override val route = "siren" }
    data object LiveGps : AppDestination { override val route = "livegps" }
    data object Recording : AppDestination { override val route = "recording" }
    data object Webhook : AppDestination { override val route = "webhook" }
    data object Contacts : AppDestination { override val route = "contacts" }
    data object Settings : AppDestination { override val route = "settings" }
}
