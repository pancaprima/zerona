package sh.zeron.zerona.ui

/**
 * Pure formatting for the read-only session model subtitle (no FFI).
 */
fun formatModelLabel(
    modelLabel: String?,
    model: String?,
    harnessLabel: String?,
    reasoning: String?,
): String? {
    val base =
        modelLabel?.trim()?.takeIf { it.isNotBlank() }
            ?: model?.trim()?.takeIf { it.isNotBlank() }
            ?: harnessLabel?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
    val reasoningPart = reasoning?.trim()?.takeIf { it.isNotBlank() }
    return if (reasoningPart != null) "$base · $reasoningPart" else base
}
