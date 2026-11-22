package com.music.bitchord.auth

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Account sign-in state flow. Online account integration is removed, so this remains false.
 */
object SignedIn {
    private val state = MutableStateFlow(false)

    fun state(context: Context): MutableStateFlow<Boolean> = state
}
