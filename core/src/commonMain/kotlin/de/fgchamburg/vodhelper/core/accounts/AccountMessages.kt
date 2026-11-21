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

    /** What stops a project from starting, or null when nothing does. Accounts this build can't connect are named separately. */
    fun startProjectHint(overview: AccountsOverview): String? {
        val (unavailable, notConnected) = overview.missingRequired.partition {
            overview.statuses[it] == AccountStatus.NotConfigured
        }
        val parts = buildList {
            if (notConnected.isNotEmpty()) add("Connect ${names(notConnected)} to start a project.")
            if (unavailable.isNotEmpty()) {
                val verb = if (unavailable.size == 1) "isn't" else "aren't"
                add("${names(unavailable)} $verb available in this build of the app.")
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun names(kinds: List<AccountKind>): String {
        val labels = kinds.map { it.label }
        return if (labels.size == 1) labels[0] else labels.dropLast(1).joinToString(", ") + " and " + labels.last()
    }

    fun connectTitle(kind: AccountKind): String = "Connect ${kind.label}"

    fun connectNotAvailableYet(kind: AccountKind): String = "Signing in to ${kind.label} will be added in a later update."
}
