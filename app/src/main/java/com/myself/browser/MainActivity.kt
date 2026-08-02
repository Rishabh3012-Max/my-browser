package com.myself.browser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

data class BrowserTab(
    val id: Int,
    val webView: WebView,
    val tabView: TextView,
    var title: String = "New Tab",
    val isIncognito: Boolean = false
)

class MainActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var webViewContainer: FrameLayout
    private lateinit var tabStrip: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences

    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = -1
    private var tabCounter = 0
    private var isDesktopMode = false

    private val homeUrl = "file:///android_asset/home.html"

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("browser_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("dark_mode", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        etUrl = findViewById(R.id.etUrl)
        progressBar = findViewById(R.id.progressBar)
        webViewContainer = findViewById(R.id.webViewContainer)
        tabStrip = findViewById(R.id.tabStrip)

        val btnBack: ImageButton = findViewById(R.id.btnBack)
        val btnForward: ImageButton = findViewById(R.id.btnForward)
        val btnGo: ImageButton = findViewById(R.id.btnGo)
        val btnNewTab: ImageButton = findViewById(R.id.btnNewTab)
        val btnMenu: ImageButton = findViewById(R.id.btnMenu)

        btnGo.setOnClickListener { loadUrlFromBar() }
        etUrl.setOnEditorActionListener { _, _, _ -> loadUrlFromBar(); true }
        btnBack.setOnClickListener { currentWebView()?.let { if (it.canGoBack()) it.goBack() } }
        btnForward.setOnClickListener { currentWebView()?.let { if (it.canGoForward()) it.goForward() } }
        btnNewTab.setOnClickListener { createTab(homeUrl, false) }
        btnMenu.setOnClickListener { showMenu(it) }

        createTab(homeUrl, false)
    }

    private fun currentWebView(): WebView? =
        if (activeTabIndex in tabs.indices) tabs[activeTabIndex].webView else null

    @SuppressLint("SetJavaScriptEnabled")
    private fun createTab(url: String, incognito: Boolean) {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.allowFileAccess = true
        webView.settings.cacheMode = if (incognito) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT

        tabCounter++
        val id = tabCounter

        val tabView = TextView(this)
        tabView.text = if (incognito) "🕶 Tab $id" else "Tab $id"
        tabView.setPadding(24, 16, 24, 16)
        tabView.setTextColor(android.graphics.Color.WHITE)
        tabView.textSize = 12f
        val tabLayoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        tabLayoutParams.marginEnd = 8
        tabView.layoutParams = tabLayoutParams
        tabView.setOnLongClickListener { closeTabById(id); true }

        val tab = BrowserTab(id, webView, tabView, "New Tab", incognito)
        tabs.add(tab)
        tabView.setOnClickListener { switchToTab(tabs.indexOf(tab)) }
        tabStrip.addView(tabView)

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                super.onPageFinished(view, pageUrl)
                if (activeTabIndex >= 0 && tabs.getOrNull(activeTabIndex)?.id == id) {
                    etUrl.setText(if (pageUrl == homeUrl) "" else pageUrl)
                }
                tab.title = webView.title ?: "New Tab"
                tabView.text = (if (incognito) "🕶 " else "") + tab.title.take(10)
                if (!incognito && pageUrl != null && pageUrl.startsWith("http")) {
                    saveHistory(webView.title ?: pageUrl, pageUrl)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (activeTabIndex >= 0 && tabs.getOrNull(activeTabIndex)?.id == id) {
                    progressBar.progress = newProgress
                    progressBar.visibility = if (newProgress < 100) ProgressBar.VISIBLE else ProgressBar.GONE
                }
            }
        }

        webView.setDownloadListener { dUrl, _, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(dUrl))
                request.setMimeType(mimeType)
                val fileName = URLUtil.guessFileName(dUrl, contentDisposition, mimeType)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Download shuru: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download fail: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        webView.loadUrl(url)
        switchToTab(tabs.indexOf(tab))
    }

    private fun switchToTab(index: Int) {
        if (index !in tabs.indices) return
        activeTabIndex = index
        webViewContainer.removeAllViews()
        webViewContainer.addView(tabs[index].webView)
        etUrl.setText(if (tabs[index].webView.url == homeUrl) "" else tabs[index].webView.url)
        for (t in tabs) {
            t.tabView.setBackgroundColor(
                if (t.id == tabs[index].id) android.graphics.Color.parseColor("#0A5C5F")
                else android.graphics.Color.TRANSPARENT
            )
        }
    }

    private fun closeTabById(id: Int) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx == -1) return
        tabStrip.removeView(tabs[idx].tabView)
        tabs[idx].webView.destroy()
        tabs.removeAt(idx)
        if (tabs.isEmpty()) {
            createTab(homeUrl, false)
        } else {
            switchToTab(if (idx >= tabs.size) tabs.size - 1 else idx)
        }
    }

    private fun loadUrlFromBar() {
        var input = etUrl.text.toString().trim()
        if (input.isEmpty()) return
        input = if (input.contains(".") && !input.contains(" ")) {
            if (!input.startsWith("http")) "https://$input" else input
        } else {
            "https://www.google.com/search?q=$input"
        }
        currentWebView()?.loadUrl(input)
    }

    private fun showMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.browser_menu, popup.menu)
        popup.menu.findItem(R.id.action_desktop_site)?.isChecked = isDesktopMode
        popup.menu.findItem(R.id.action_dark_mode)?.isChecked = prefs.getBoolean("dark_mode", false)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_tab -> { createTab(homeUrl, false); true }
                R.id.action_incognito -> { createTab(homeUrl, true); true }
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
        val url = currentWebView()?.url ?: return
        val title = currentWebView()?.title ?: url
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
            .setItems(titles) { _, which -> currentWebView()?.loadUrl(set[which].split("|||")[1]) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun saveHistory(title: String, url: String) {
        val list = prefs.getStringSet("history", mutableSetOf())!!.toMutableSet()
        list.add("$title|||$url")
        val trimmed = if (list.size > 100) list.toList().takeLast(100).toMutableSet() else list
        prefs.edit().putStringSet("history", trimmed).apply()
    }

    private fun showHistory() {
        val set = prefs.getStringSet("history", mutableSetOf())!!.toList().reversed()
        if (set.isEmpty()) { Toast.makeText(this, "History khali hai", Toast.LENGTH_SHORT).show(); return }
        val titles = set.map { it.split("|||")[0] }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("History")
            .setItems(titles) { _, which -> currentWebView()?.loadUrl(set[which].split("|||")[1]) }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showFindDialog() {
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Find in Page")
            .setView(input)
            .setPositiveButton("Find") { _, _ -> currentWebView()?.findAllAsync(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sharePage() {
        val url = currentWebView()?.url ?: return
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_TEXT, url)
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun toggleDesktopSite() {
        isDesktopMode = !isDesktopMode
        val wv = currentWebView() ?: return
        val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        wv.settings.userAgentString = if (isDesktopMode) desktopUA else null
        wv.reload()
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
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
                currentWebView()?.clearCache(true)
                currentWebView()?.clearHistory()
                CookieManager.getInstance().removeAllCookies(null)
                prefs.edit().remove("history").apply()
                Toast.makeText(this, "Data cleared!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            currentWebView()?.let { if (it.canGoBack()) { it.goBack(); return true } }
        }
        return super.onKeyDown(keyCode, event)
    }
}