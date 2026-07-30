package com.example.data

/** Executes only validated playback commands; it never resolves or invents track IDs. */
class PlaybackCommandExecutor(
    private val onPlay: () -> Unit,
    private val onPause: () -> Unit,
    private val onNext: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onSearch: (String) -> Unit,
    private val onStartRadio: (String?) -> Unit
) {
    fun execute(raw: AppCommand): Boolean {
        val command = AIOutputValidator.appCommand(raw).value ?: return false
        when (command.action) {
            "PLAY" -> onPlay()
            "PAUSE" -> onPause()
            "NEXT" -> onNext()
            "PREVIOUS" -> onPrevious()
            "SEARCH" -> {
                val query = command.query?.takeIf { it.isNotBlank() } ?: return false
                onSearch(query)
            }
            "START_RADIO" -> onStartRadio(command.query)
            "ADD_TO_QUEUE" -> return false
        }
        return true
    }
}
