package com.daffa.hihu

import android.content.Context
import com.daffa.hihu.music.MusicAccount
import com.daffa.hihu.state.Account
import kotlinx.coroutines.CoroutineScope

/** The standard build has no account: nothing signs in and no session is ever kept. */
@Suppress("UNUSED_PARAMETER")
internal fun accountFor(provider: MusicAccount, context: Context, scope: CoroutineScope): Account? = null
