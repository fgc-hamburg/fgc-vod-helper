package de.fgchamburg.vodhelper.core.twitch

/** Plain-language texts for the Twitch sign-in, shown to the user. */
object TwitchMessages {
    const val STARTING = "Asking Twitch for a code…"
    const val INSTRUCTIONS = "In your browser, sign in with the channel's Twitch account and enter this code:"
    const val OPEN_BROWSER = "Open twitch.tv/activate"
    const val WAITING = "Waiting for you to approve in the browser…"
    const val TRY_AGAIN = "Get a new code"
    const val CANCEL = "Cancel"
    const val DONE = "Done"
    const val OFFLINE = "Couldn't reach Twitch. Check your internet connection and try again."
    const val CODE_EXPIRED = "The code expired before it was approved. Get a new code to try again."
    const val DENIED = "The sign-in was declined on Twitch."

    fun connected(name: String) = "Connected as $name"
    fun failed(reason: String) = "Couldn't connect to Twitch: $reason"
}
