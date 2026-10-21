package sh.zeron.zerona.app

import uniffi.zeron_core.ClientEvent
import uniffi.zeron_core.ClientListener

class ClientListenerBridge(
    private val onEventMain: (ClientEvent) -> Unit,
) : ClientListener {
    override fun onEvent(event: ClientEvent) {
        onEventMain(event)
    }
}
