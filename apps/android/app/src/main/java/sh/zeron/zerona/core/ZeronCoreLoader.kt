package sh.zeron.zerona.core

import uniffi.zeron_core.uniffiEnsureInitialized

object ZeronCoreLoader {
    @Volatile
    private var loaded = false

    @Volatile
    private var loadError: String? = null

    fun ensureLoaded(): String? {
        if (loaded) return null
        synchronized(this) {
            if (loaded) return null
            loadError?.let { return it }
            try {
                System.loadLibrary("zeron_mobile")
                uniffiEnsureInitialized()
                loaded = true
                return null
            } catch (e: UnsatisfiedLinkError) {
                loadError = e.message ?: e.toString()
                return loadError
            } catch (e: Exception) {
                loadError = e.message ?: e.toString()
                return loadError
            }
        }
    }
}
