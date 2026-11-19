package com.daffa.hihu

import android.content.Context
import com.daffa.hihu.music.MusicAccount
import com.daffa.hihu.state.Account
import kotlinx.coroutines.CoroutineScope

/** Hihu G signs in to YouTube Music, with the session kept encrypted on the phone. */
internal fun accountFor(provider: MusicAccount, context: Context, scope: CoroutineScope): Account? =
    Account(provider, context, scope)
