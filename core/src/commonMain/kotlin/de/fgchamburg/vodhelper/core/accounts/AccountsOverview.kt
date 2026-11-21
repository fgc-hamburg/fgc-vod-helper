package de.fgchamburg.vodhelper.core.accounts

data class AccountsOverview(val statuses: Map<AccountKind, AccountStatus>) {
    /** Required accounts that are not connected, in [AccountKind] order. */
    val missingRequired: List<AccountKind> = AccountKind.entries.filter {
        it.required && statuses[it] !is AccountStatus.Connected
    }
    val canStartProject: Boolean get() = missingRequired.isEmpty()
}
