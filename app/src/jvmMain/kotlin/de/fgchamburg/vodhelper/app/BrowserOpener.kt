package de.fgchamburg.vodhelper.app

import java.awt.Desktop
import java.net.URI

/** Opens an address in the user's web browser. */
fun interface BrowserOpener {
    fun open(url: String)
}

object SystemBrowserOpener : BrowserOpener {
    // Failures are ignored: the sign-in dialog also shows the address and the code.
    override fun open(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
            }
        }
    }
}
