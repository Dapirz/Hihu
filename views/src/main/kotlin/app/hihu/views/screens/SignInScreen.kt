package app.hihu.views.screens

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.net.Uri
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
import app.hihu.state.Account
import app.hihu.ui.HihuTheme
import app.hihu.ui.Icons
import app.hihu.views.R

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
                            // The host itself, not a prefix: music.youtube.com.example.org is not it.
                            if (finished || url == null || !sameHost(url, account.sessionUrl)) return
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
            // Left before signing in finished, the web view's cookies would keep a half session.
            onRelease = {
                CookieManager.getInstance().run {
                    removeAllCookies(null)
                    flush()
                }
                it.destroy()
            },
        )
    }
}

/** Whether [url] is on the same host as [origin], compared as hosts and not as text. */
private fun sameHost(url: String, origin: String): Boolean {
    val page = Uri.parse(url)
    val home = Uri.parse(origin)
    return page.scheme == "https" && page.host != null && page.host.equals(home.host, ignoreCase = true)
}
