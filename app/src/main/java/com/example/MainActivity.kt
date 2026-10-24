package com.example

import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        Box(modifier = Modifier.fillMaxSize()) {
          WebViewScreen(modifier = Modifier.fillMaxSize())
        }
      }
    }
  }
}

@Composable
fun WebViewScreen(modifier: Modifier = Modifier) {
  AndroidView(
    modifier = modifier,
    factory = { context ->
      WebView(context).apply {
        setBackgroundColor(android.graphics.Color.WHITE)
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          databaseEnabled = true
          allowFileAccess = true
          allowContentAccess = true
          mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
          loadWithOverviewMode = false
          useWideViewPort = false
          textZoom = 100
        }
        
        webViewClient = WebViewClient()
        
        webChromeClient = object : WebChromeClient() {
          override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
            consoleMessage?.apply {
              Log.d("WebViewConsole", "${message()} -- From line ${lineNumber()} of ${sourceId()}")
            }
            return true
          }
        }
        
        loadUrl("file:///android_asset/cronobus.html")
      }
    }
  )
}
