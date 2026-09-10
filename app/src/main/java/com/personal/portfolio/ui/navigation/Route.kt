package com.personal.portfolio.ui.navigation

sealed class Route(val path: String) {
    data object Dashboard : Route("dashboard")
    data object Holdings : Route("holdings")
    data object Allocation : Route("allocation")
    data object NewMoney : Route("new_money")
    data object Journal : Route("journal")
    data object Ai : Route("ai")
    data object Settings : Route("settings")
    data object AddHolding : Route("holding/edit")
    data class EditHolding(val id: Long) : Route("holding/edit/$id") {
        companion object {
            const val pattern = "holding/edit/{id}"
        }
    }
}
