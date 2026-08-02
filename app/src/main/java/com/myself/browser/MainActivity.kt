package com.myself.browser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: android.content.SharedPreferences
    private var isDesktopMode = false
    private val defaultUA by lazy { webView.settings.userAgentString }
    private val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)

        if (prefs.getBoolean("dark_mode", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        webView = findViewById(R.id.webView)
        etUrl = findViewById(R.id.etUrl)
        progressBar = findViewById(R.id.progressBar)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnForward: ImageButton = findViewById(R.id.btnForward)
        val btnGo: ImageButton = findViewById(R.id.btnGo)
        val btnRefresh: ImageButton = findViewById(R.id.btnRefresh)
        val btnMenu: ImageButton = findViewById(R.id.btnMenu)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.allowFileAccess = true
        webView.settings.setSupportZoom(true)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                etUrl.setText(url)
                if (url != null && url.startsWith("http")) {
                    saveHistory(webView.title ?: url, url)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress < 100) ProgressBar.VISIBLE else ProgressBar.GONE
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimeType)
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Download shuru: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download fail: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        webView.loadUrl("https://www.google.com")

        btnGo.setOnClickListener { loadUrlFromBar() }
        etUrl.setOnEditorActionListener { _, _, _ -> loadUrlFromBar(); true }
        btnBack.setOnClickListener { if (webView.canGoBack()) webView.goBack() }
        btnForward.setOnClickListener { if (webView.canGoForward()) webView.goForward() }
        btnRefresh.setOnClickListener { webView.reload() }
        btnMenu.setOnClickListener { showMenu(it) }
    }

    private fun loadUrlFromBar() {
        var input = etUrl.text.toString().trim()
        if (input.isEmpty()) return
        input = if (input.contains(".") && !input.contains(" ")) {
            if (!input.startsWith("http")) "https://$input" else input
        } else {
            "https://www.google.com/search?q=$input"
        }
        webView.loadUrl(input)
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.browser_menu, popup.menu)
        popup.menu.findItem(R.id.action_desktop_site)?.isChecked = isDesktopMode
        popup.menu.findItem(R.id.action_dark_mode)?.isChecked = prefs.getBoolean("dark_mode", false)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_bookmark_add -> { addBookmark(); true }
                R.id.action_bookmarks -> { showBookmarks(); true }
                R.id.action_history -> { showHistory(); true }
                R.id.action_find -> { showFindDialog(); true }
                R.id.action_share -> { sharePage(); true }
                R.id.action_desktop_site -> { toggleDesktopSite(); true }
                R.id.action_downloads -> { openDownloads(); true }
                R.id.action_dark_mode -> { toggleDarkMode(); true }
                R.id.action_clear_data -> { clearBrowsingData(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun addBookmark() {
        val url = webView.url ?: return
        val title = webView.title ?: url
        val set = prefs.getStringSet("bookmarks", mutableSetOf())!!.toMutableSet()
        set.add("$title|||$url")
        prefs.edit().putStringSet("bookmarks", set).apply()
        Toast.makeText(this, "Bookmark added!", Toast.LENGTH_SHORT).show()
    }

    private fun showBookmarks() {
        val set = prefs.getStringSet("bookmarks", mutableSetOf())!!.toList()
        if (set.isEmpty()) { Toast.makeText(this, "Koi bookmark nahi hai", Toast.LENGTH_SHORT).show(); return }
        val titles = set.map { it.split("|||")[0] }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Bookmarks")
            .setItems(titles) { _, which ->
                val url = set[which].split("|||")[1]
                webView.loadUrl(url)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveHistory(title: String, url: String) {
        val list = prefs.getStringSet("history", mutableSetOf())!!.toMutableSet()
        list.add("$title|||$url")
        if (list.size > 100) {
            val trimmed = list.toList().takeLast(100).toMutableSet()
            prefs.edit().putStringSet("history", trimmed).apply()
        } else {
            prefs.edit().putStringSet("history", list).apply()
        }
    }

    private fun showHistory() {
        val set = prefs.getStringSet("history", mutableSetOf())!!.toList().reversed()
        if (set.isEmpty()) { Toast.makeText(this, "History khali hai", Toast.LENGTH_SHORT).show(); return }
        val titles = set.map { it.split("|||")[0] }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("History")
            .setItems(titles) { _, which ->
                val url = set[which].split("|||")[1]
                webView.loadUrl(url)
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showFindDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Find in Page")
            .setView(input)
            .setPositiveButton("Find") { _, _ ->
                webView.findAllAsync(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sharePage() {
        val url = webView.url ?: return
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun toggleDesktopSite() {
        isDesktopMode = !isDesktopMode
        webView.settings.userAgentString = if (isDesktopMode) desktopUA else defaultUA
        webView.reload()
        Toast.makeText(this, if (isDesktopMode) "Desktop site ON" else "Desktop site OFF", Toast.LENGTH_SHORT).show()
    }

    private fun openDownloads() {
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Downloads app nahi mila", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleDarkMode() {
        val current = prefs.getBoolean("dark_mode", false)
        prefs.edit().putBoolean("dark_mode", !current).apply()
        AppCompatDelegate.setDefaultNightMode(
            if (!current) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private fun clearBrowsingData() {
        AlertDialog.Builder(this)
            .setTitle("Clear Browsing Data")
            .setMessage("History, cache, aur cookies delete ho jayenge. Confirm?")
            .setPositiveButton("Clear") { _, _ ->
                webView.clearCache(true)
                webView.clearHistory()
                CookieManager.getInstance().removeAllCookies(null)
                prefs.edit().remove("history").apply()
                Toast.makeText(this, "Data cleared!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}