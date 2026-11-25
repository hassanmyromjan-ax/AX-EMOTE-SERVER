
package com.axemote.app

import android.view.View
import android.webkit.WebView

object WebViewStore {

    var webView: WebView? = null

    var overlayShowing: Boolean = false

    fun detach(view: View) {
        (view.parent as? android.view.ViewGroup)?.removeView(view)
    }
}
