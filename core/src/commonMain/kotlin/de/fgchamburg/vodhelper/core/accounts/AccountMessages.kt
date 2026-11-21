package de.fgchamburg.vodhelper.core.accounts

enum class AccountAction { CONNECT, RECONNECT, SIGN_OUT }

/** Plain-language texts about accounts, shown to the user. */
object AccountMessages {
    const val ACCOUNTS_HEADING = "Accounts"
    const val CHECKING_ACCOUNTS = "Checking accounts…"

    fun requirement(kind: AccountKind): String =
        if (kind.required) "Required" else "Optional: only needed to upload to ${kind.label}"

    fun describe(status: AccountStatus): String = when (status) {
        is AccountStatus.Connected -> "Connected as ${status.accountName}"
        AccountStatus.NotConnected -> "Not connected"
        AccountStatus.NotConfigured -> "Not available in this build of the app"
    }

    fun actions(status: AccountStatus): List<AccountAction> = when (status) {
        is AccountStatus.Connected -> listOf(AccountAction.RECONNECT, AccountAction.SIGN_OUT)
        AccountStatus.NotConnected -> listOf(AccountAction.CONNECT)
        AccountStatus.NotConfigured -> emptyList()
    }

    fun actionLabel(action: AccountAction): String = when (action) {
        AccountAction.CONNECT -> "Connect"
        AccountAction.RECONNECT -> "Reconnect"
        AccountAction.SIGN_OUT -> "Sign out"
    }

    fun startProjectHint(overview: AccountsOverview): String? {
        val names = overview.missingRequired.map { it.label }
        if (names.isEmpty()) return null
        val list = if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " and " + names.last()
        return "Connect $list to start a project."
    }

    fun connectTitle(kind: AccountKind): String = "Connect ${kind.label}"

    fun connectNotAvailableYet(kind: AccountKind): String = "Signing in to ${kind.label} will be added in a later update."
}
