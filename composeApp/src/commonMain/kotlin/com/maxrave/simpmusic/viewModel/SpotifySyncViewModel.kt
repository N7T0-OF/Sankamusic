package com.maxrave.simpmusic.viewModel

import androidx.lifecycle.viewModelScope
import com.maxrave.domain.repository.SpotifyPlaylistItem
import com.maxrave.domain.repository.SpotifySyncProgress
import com.maxrave.domain.repository.SpotifySyncRepository
import com.maxrave.simpmusic.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted

class SpotifySyncViewModel(
    private val spotifySyncRepository: SpotifySyncRepository,
) : BaseViewModel() {

    private val _syncProgress = MutableStateFlow<SpotifySyncProgress>(SpotifySyncProgress.Idle)
    val syncProgress: StateFlow<SpotifySyncProgress> = _syncProgress.asStateFlow()

    private val _playlists = MutableStateFlow<List<SpotifyPlaylistItem>>(emptyList())
    val playlists: StateFlow<List<SpotifyPlaylistItem>> = _playlists.asStateFlow()

    private val _selectedPlaylists = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlaylists: StateFlow<Set<String>> = _selectedPlaylists.asStateFlow()

    /** True once OAuth tokens exist — the sync screen flips from "connect" to "import". */
    val oauthLoggedIn: StateFlow<Boolean> =
        spotifySyncRepository.oauthLoggedIn
            .map { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Builds the Spotify authorize URL and opens the browser. Returns null on failure. */
    fun startOAuthLogin(): String? = spotifySyncRepository.startOAuthLogin()

    /** Pasted-callback fallback: same as the deep link, for hosts without a scheme handler. */
    fun completeOAuthLoginFromCallback(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        val code =
            if (trimmed.contains("code=")) {
                trimmed.substringAfter("code=").substringBefore("&").trim()
            } else {
                trimmed
            }
        if (code.isEmpty()) return
        completeOAuthLogin(code)
    }

    /** Exchanges the callback code for tokens; the screen watches [oauthLoggedIn] for the result. */
    fun completeOAuthLogin(code: String) {
        viewModelScope.launch {
            spotifySyncRepository.completeOAuthLogin(code)
        }
    }

    fun logout() {
        viewModelScope.launch {
            spotifySyncRepository.logout()
            _playlists.value = emptyList()
            _selectedPlaylists.value = emptySet()
            _syncProgress.value = SpotifySyncProgress.Idle
        }
    }

    fun fetchPlaylists() {
        viewModelScope.launch {
            spotifySyncRepository.fetchPlaylists().collect { progress ->
                _syncProgress.value = progress
                if (progress is SpotifySyncProgress.PlaylistsReady) {
                    _playlists.value = progress.playlists
                }
            }
        }
    }

    fun togglePlaylistSelection(playlistId: String) {
        _selectedPlaylists.value = _selectedPlaylists.value.let { current ->
            if (playlistId in current) {
                current - playlistId
            } else {
                current + playlistId
            }
        }
    }

    fun toggleSelectAll() {
        if (_selectedPlaylists.value.size == _playlists.value.size) {
            _selectedPlaylists.value = emptySet()
        } else {
            _selectedPlaylists.value = _playlists.value.map { it.id }.toSet()
        }
    }

    fun importSelectedPlaylists() {
        val selected = _selectedPlaylists.value
        if (selected.isEmpty()) return

        val playlistPairs = _playlists.value
            .filter { it.id in selected }
            .map { it.id to it.name }

        viewModelScope.launch {
            spotifySyncRepository.importAllPlaylists(playlistPairs).collect { progress ->
                _syncProgress.value = progress
            }
        }
    }

    fun resetProgress() {
        _syncProgress.value = SpotifySyncProgress.Idle
    }
}
