package com.daffa.hihu.views.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.daffa.hihu.state.Account
import com.daffa.hihu.ui.HihuTheme
import com.daffa.hihu.ui.Icons
import com.daffa.hihu.views.R

/**
 * The provider's own sign-in page in a web view. The user types their details into that page,
 * never into Hihu. Once a page on the session address loads with a finished sign-in's cookies,
 * they go to [Account.signIn] and [onDone] closes the screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SignInScreen(account: Account, onDone: () -> Unit, onBack: () -> Unit) {
    val m = HihuTheme.metrics
    val done by rememberUpdatedState(onDone)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = m.gapXs)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(Icons.back), stringResource(R.string.back), Modifier.size(m.iconMd))
            }
            Text(
                stringResource(R.string.sign_in_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = m.gapSm),
            )
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    CookieManager.getInstance().setAcceptCookie(true)
                    webViewClient = object : WebViewClient() {
                        private var finished = false

                        override fun onPageFinished(view: WebView, url: String?) {
                            if (finished || url == null || !url.startsWith(account.sessionUrl)) return
                            val cookies = CookieManager.getInstance().getCookie(account.sessionUrl) ?: return
                            if (!account.isComplete(cookies)) return
                            finished = true
                            CookieManager.getInstance().flush()
                            account.signIn(cookies)
                            done()
                        }
                    }
                    loadUrl(account.signInUrl)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
