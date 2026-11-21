package de.fgchamburg.vodhelper.core.http

/** The server could not be reached (offline, timeout, refused). */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)
