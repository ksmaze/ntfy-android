package io.heckel.ntfy.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputLayout
import io.heckel.ntfy.R
import io.heckel.ntfy.app.Application
import io.heckel.ntfy.msg.ApiService
import io.heckel.ntfy.shortcut.RecentTopicShareShortcuts
import io.heckel.ntfy.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.view.size
import androidx.core.view.get

class ShareActivity : AppCompatActivity() {
    private val repository by lazy { (application as Application).repository }
    private val api = ApiService()

    // File to share
    private var fileUri: Uri? = null

    // Context-dependent things
    private lateinit var appBaseUrl: String
    private var defaultBaseUrl: String? = null

    // UI elements
    private lateinit var menu: Menu
    private lateinit var sendItem: MenuItem
    private lateinit var contentImage: ImageView
    private lateinit var contentFileBox: View
    private lateinit var contentFileInfo: TextView
    private lateinit var contentFileIcon: ImageView
    private lateinit var contentText: TextView
    private lateinit var topicText: TextView
    private lateinit var baseUrlLayout: TextInputLayout
    private lateinit var baseUrlText: AutoCompleteTextView
    private lateinit var useAnotherServerCheckbox: CheckBox
    private lateinit var suggestedTopicsList: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var errorText: TextView
    private lateinit var errorImage: ImageView

    // State
    private var clipboardPopulateAttempted = false
    private var clipboardPopulateAttemptedOnFocus = false
    private var autoSendTriggered = false
    private var shortcutAutoSendTriggered = false
    private var preselectedTopicUrl: String? = null
    private val autoSendTopic = "ksmaze"
    private val autoSendDomains: Set<String> = setOf(
        "javdb.com",
        "onejav.com",
        "sukebei.nyaa.si",
        "zodgame.xyz",
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_share)

        Log.init(this) // Init logs in all entry points
        Log.d(TAG, "Create $this with intent $intent")

        // Action bar
        val toolbarLayout = findViewById<View>(R.id.app_bar_drawer)
        val dynamicColors = repository.getDynamicColorsEnabled()
        val darkMode = isDarkThemeOn(this)
        val statusBarColor = Colors.statusBarNormal(this, dynamicColors, darkMode)
        val toolbarTextColor = Colors.toolbarTextColor(this, dynamicColors, darkMode)
        toolbarLayout.setBackgroundColor(statusBarColor)

        val toolbar = toolbarLayout.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        toolbar.setTitleTextColor(toolbarTextColor)
        toolbar.setNavigationIconTint(toolbarTextColor)
        toolbar.overflowIcon?.setTint(toolbarTextColor)
        setSupportActionBar(toolbar)
        title = getString(R.string.share_title)

        // Set system status bar appearance
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
            Colors.shouldUseLightStatusBar(dynamicColors, darkMode)

        // Show 'Back' button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Context-dependent things
        appBaseUrl = getString(R.string.app_base_url)
        defaultBaseUrl = repository.getDefaultBaseUrl()

        // UI elements
        val root: View = findViewById(R.id.share_root_view)
        contentText = findViewById(R.id.share_content_text)
        contentImage = findViewById(R.id.share_content_image)
        contentFileBox = findViewById(R.id.share_content_file_box)
        contentFileInfo = findViewById(R.id.share_content_file_info)
        contentFileIcon = findViewById(R.id.share_content_file_icon)
        topicText = findViewById(R.id.share_topic_text)
        baseUrlLayout = findViewById(R.id.share_base_url_layout)
        baseUrlLayout.background = root.background
        baseUrlLayout.makeEndIconSmaller(resources) // Hack!
        baseUrlText = findViewById(R.id.share_base_url_text)
        baseUrlText.background = root.background
        baseUrlText.hint = defaultBaseUrl ?: appBaseUrl
        useAnotherServerCheckbox = findViewById(R.id.share_use_another_server_checkbox)
        suggestedTopicsList = findViewById(R.id.share_suggested_topics)
        progress = findViewById(R.id.share_progress)
        progress.visibility = View.GONE
        errorText = findViewById(R.id.share_error_text)
        errorText.visibility = View.GONE
        errorImage = findViewById(R.id.share_error_image)
        errorImage.visibility = View.GONE

        val textWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validateInput()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Nothing
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // Nothing
            }
        }
        contentText.addTextChangedListener(textWatcher)
        topicText.addTextChangedListener(textWatcher)
        baseUrlText.addTextChangedListener(textWatcher)

        // Add behavior to "use another" checkbox
        useAnotherServerCheckbox.setOnCheckedChangeListener { _, isChecked ->
            baseUrlLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
            validateInput()
        }

        preselectedTopicUrl = RecentTopicShareShortcuts.resolveTopicUrlFromShareIntent(intent, repository.getLastShareTopics())
        preselectedTopicUrl?.let { url ->
            try {
                val (baseUrl, topic) = splitTopicUrl(url)
                val defaultUrl = defaultBaseUrl ?: appBaseUrl
                topicText.text = topic
                if (baseUrl == defaultUrl) {
                    useAnotherServerCheckbox.isChecked = false
                } else {
                    useAnotherServerCheckbox.isChecked = true
                    baseUrlText.setText(baseUrl)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Invalid preselected topicUrl $url", e)
            }
        }

        // Things that need the database
        lifecycleScope.launch(Dispatchers.IO) {
            // Populate "suggested topics"
            val subscriptions = repository.getSubscriptions()
            val lastShareTopics = repository.getLastShareTopics()
            val subscribedTopics = subscriptions
                .map { topicUrl(it.baseUrl, it.topic) }
                .toSet()
                .subtract(lastShareTopics.toSet())
            val suggestedTopics = (lastShareTopics.reversed() + subscribedTopics).distinct()
            val baseUrlsRaw = suggestedTopics
                .mapNotNull {
                    try { splitTopicUrl(it).first }
                    catch (_: Exception) { null }
                }
                .distinct()
            val baseUrls = if (defaultBaseUrl != null) {
                baseUrlsRaw.filterNot { it == defaultBaseUrl }
            } else {
                baseUrlsRaw.filterNot { it == appBaseUrl }
            }
            suggestedTopicsList.adapter = TopicAdapter(suggestedTopics) { topicUrl ->
                try {
                    val (baseUrl, topic) = splitTopicUrl(topicUrl)
                    val defaultUrl = defaultBaseUrl ?: appBaseUrl
                    topicText.text = topic
                    if (baseUrl == defaultUrl) {
                        useAnotherServerCheckbox.isChecked = false
                    } else {
                        useAnotherServerCheckbox.isChecked = true
                        baseUrlText.setText(baseUrl)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Invalid topicUrl $topicUrl", e)
                }
            }

            // Set topicText.text to the first suggested topic
            val activity = this@ShareActivity
            activity.runOnUiThread {
                if (!autoSendTriggered && preselectedTopicUrl == null) {
                    topicText.text = suggestedTopics.firstOrNull()?.let {
                        val (_, topic) = splitTopicUrl(it)
                        topic
                    } ?: ""
                }
                initBaseUrlDropdown(baseUrls, baseUrlText, baseUrlLayout)
                if (!autoSendTriggered && preselectedTopicUrl == null) {
                    useAnotherServerCheckbox.isChecked = if (suggestedTopics.isNotEmpty()) {
                        try {
                            val (baseUrl, _) = splitTopicUrl(suggestedTopics.first())
                            val defaultUrl = defaultBaseUrl ?: appBaseUrl
                            baseUrl != defaultUrl
                        } catch (_: Exception) {
                            false
                        }
                    } else {
                        baseUrls.count() == 1
                    }
                    baseUrlLayout.visibility = if (useAnotherServerCheckbox.isChecked) View.VISIBLE else View.GONE
                }
            }
        }

        // Incoming intent
        val intent = intent ?: return
        maybePopulateFromClipboard(intent)
        val type = intent.type ?: return
        if (intent.action != Intent.ACTION_SEND) return
        if (type == "text/plain" || type.startsWith("text/")) {
            handleSendText(intent)
        } else if (type.startsWith("image/")) {
            handleSendImage(intent)
        } else {
            handleSendFile(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Try again once we're foregrounded; on some Android versions/OEMs clipboard is only
        // accessible after the activity is resumed/focused.
        attemptPopulateFromClipboardOnForeground()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Final fallback: some devices only succeed once the window is fully focused
            attemptPopulateFromClipboardOnFocus()
        }
    }

    private fun handleSendText(intent: Intent) {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: "(no text)"
        Log.d(TAG, "Shared content is text: $text")
        contentText.text = text
        show()
        maybeAutoSendForText()
        maybeAutoSendForShortcut()
    }

    private fun maybePopulateFromClipboard(intent: Intent) {
        if (!intent.getBooleanExtra(EXTRA_LOAD_CLIPBOARD, false)) {
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val has = clipboard?.hasPrimaryClip() == true
        val desc = clipboard?.primaryClipDescription
        val mimeCount = desc?.mimeTypeCount ?: 0
        val mimeTypes = (0 until mimeCount).joinToString(",") { i -> desc?.getMimeType(i).orEmpty() }
        val itemCount = clipboard?.primaryClip?.itemCount ?: 0
        Log.d(TAG, "Clipboard state (onCreate): has=$has items=$itemCount mimes=${if (mimeTypes.isEmpty()) "-" else mimeTypes}")
        val clipText = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()

        if (!clipText.isNullOrBlank()) {
            intent.putExtra(Intent.EXTRA_TEXT, clipText)
            Log.d(TAG, "Populated share intent from clipboard: $clipText")
        } else {
            Log.d(TAG, "Clipboard empty when requested via shortcut")
        }
    }

    private fun attemptPopulateFromClipboardOnForeground() {
        if (clipboardPopulateAttempted) return
        if (!intent.getBooleanExtra(EXTRA_LOAD_CLIPBOARD, false)) {
            clipboardPopulateAttempted = true
            return
        }

        val existing = intent.getStringExtra(Intent.EXTRA_TEXT)
        val currentContent = if (this::contentText.isInitialized) contentText.text?.toString().orEmpty() else ""
        // If we already have meaningful content, don't overwrite it
        if (!existing.isNullOrBlank() && currentContent.isNotBlank() && currentContent != "(no text)") {
            clipboardPopulateAttempted = true
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val has = clipboard?.hasPrimaryClip() == true
        val desc = clipboard?.primaryClipDescription
        val mimeCount = desc?.mimeTypeCount ?: 0
        val mimeTypes = (0 until mimeCount).joinToString(",") { i -> desc?.getMimeType(i).orEmpty() }
        val itemCount = clipboard?.primaryClip?.itemCount ?: 0
        Log.d(TAG, "Clipboard state (onResume): has=$has items=$itemCount mimes=${if (mimeTypes.isEmpty()) "-" else mimeTypes}")
        val clipText = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()

        if (!clipText.isNullOrBlank()) {
            intent.putExtra(Intent.EXTRA_TEXT, clipText)
            Log.d(TAG, "Populated share intent from clipboard (onResume): $clipText")
            if (intent.action == Intent.ACTION_SEND && (intent.type == "text/plain" || intent.type?.startsWith("text/") == true)) {
                contentText.text = clipText
                show()
                validateInput()
                maybeAutoSendForText()
                maybeAutoSendForShortcut()
            }
        } else {
            Log.d(TAG, "Clipboard empty when requested via shortcut (onResume)")
        }
        clipboardPopulateAttempted = true
    }

    private fun attemptPopulateFromClipboardOnFocus() {
        if (clipboardPopulateAttemptedOnFocus) return
        if (!intent.getBooleanExtra(EXTRA_LOAD_CLIPBOARD, false)) {
            clipboardPopulateAttemptedOnFocus = true
            return
        }

        val existing = intent.getStringExtra(Intent.EXTRA_TEXT)
        val currentContent = if (this::contentText.isInitialized) contentText.text?.toString().orEmpty() else ""
        if (!existing.isNullOrBlank() && currentContent.isNotBlank() && currentContent != "(no text)") {
            clipboardPopulateAttemptedOnFocus = true
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val has = clipboard?.hasPrimaryClip() == true
        val desc = clipboard?.primaryClipDescription
        val mimeCount = desc?.mimeTypeCount ?: 0
        val mimeTypes = (0 until mimeCount).joinToString(",") { i -> desc?.getMimeType(i).orEmpty() }
        val itemCount = clipboard?.primaryClip?.itemCount ?: 0
        Log.d(TAG, "Clipboard state (onWindowFocus): has=$has items=$itemCount mimes=${if (mimeTypes.isEmpty()) "-" else mimeTypes}")

        val clipText = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()

        if (!clipText.isNullOrBlank()) {
            intent.putExtra(Intent.EXTRA_TEXT, clipText)
            Log.d(TAG, "Populated share intent from clipboard (onWindowFocus): $clipText")
            if (intent.action == Intent.ACTION_SEND && (intent.type == "text/plain" || intent.type?.startsWith("text/") == true)) {
                contentText.text = clipText
                show()
                validateInput()
                maybeAutoSendForText()
                maybeAutoSendForShortcut()
            }
        } else {
            Log.d(TAG, "Clipboard empty when requested via shortcut (onWindowFocus)")
        }
        clipboardPopulateAttemptedOnFocus = true
    }

    private fun handleSendImage(intent: Intent) {
        fileUri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
        Log.d(TAG, "Shared content is an image with URI $fileUri")
        if (fileUri == null) {
            Log.w(TAG, "Null URI is not allowed. Aborting.")
            return
        }
        try {
            contentImage.setImageBitmap(fileUri!!.readBitmapFromUri(applicationContext))
            contentText.text = getString(R.string.share_content_image_text)
            show(image = true)
            maybeAutoSendForShortcut()
        } catch (e: Exception) {
            fileUri = null
            contentText.text = ""
            errorText.text = getString(R.string.share_content_image_error, e.message)
            show(error = true)
        }
    }

    private fun handleSendFile(intent: Intent) {
        fileUri = intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
        Log.d(TAG, "Shared content is a file with URI $fileUri")
        if (fileUri == null) {
            Log.w(TAG, "Null URI is not allowed. Aborting.")
            return
        }
        try {
            val resolver = applicationContext.contentResolver
            val info = fileStat(this, fileUri)
            val mimeType = resolver.getType(fileUri!!)
            contentText.text = getString(R.string.share_content_file_text)
            contentFileInfo.text = "${info.filename}\n${formatBytes(info.size)}"
            contentFileIcon.setImageResource(mimeTypeToIconResource(mimeType))
            show(file = true)
            maybeAutoSendForShortcut()
        } catch (e: Exception) {
            fileUri = null
            contentText.text = ""
            errorText.text = getString(R.string.share_content_file_error, e.message)
            show(error = true)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_share_action_bar, menu)
        this.menu = menu
        sendItem = menu.findItem(R.id.share_menu_send)

        // Tint menu icons based on theme
        val toolbarTextColor = Colors.toolbarTextColor(this, repository.getDynamicColorsEnabled(), isDarkThemeOn(this))
        for (i in 0 until menu.size) {
            menu[i].icon?.setTint(toolbarTextColor)
        }

        validateInput() // Disable icon
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.share_menu_send -> {
                onShareClick()
                true
            }
            R.id.share_menu_paste -> {
                onPasteClick()
                true
            }
            R.id.share_menu_clipboard_info -> {
                showClipboardInfo()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun show(image: Boolean = false, file: Boolean = false, error: Boolean = false) {
        contentImage.visibility = if (image) View.VISIBLE else View.GONE
        contentFileBox.visibility = if (file) View.VISIBLE else View.GONE
        errorImage.visibility = if (error) View.VISIBLE else View.GONE
        errorText.visibility = if (error) View.VISIBLE else View.GONE
    }

    private fun onShareClick() {
        val baseUrl = getBaseUrl()
        val topic = topicText.text.toString()
        val message = contentText.text.toString()
        progress.visibility = View.VISIBLE
        contentText.isEnabled = false
        topicText.isEnabled = false
        useAnotherServerCheckbox.isEnabled = false
        baseUrlText.isEnabled = false
        suggestedTopicsList.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val user = repository.getUser(baseUrl)
            try {
                val (filename, body) = if (fileUri != null) {
                    val stat = fileStat(this@ShareActivity, fileUri)
                    val body = ContentUriRequestBody(applicationContext.contentResolver, fileUri!!, stat.size)
                    Pair(stat.filename, body)
                } else {
                    Pair("", null)
                }
                api.publish(
                    baseUrl = baseUrl,
                    topic = topic,
                    user = user,
                    message = message,
                    body = body, // May be null
                    filename = filename, // May be empty
                )
                runOnUiThread {
                    repository.addLastShareTopic(topicUrl(baseUrl, topic))
                    RecentTopicShareShortcuts.update(applicationContext, repository.getLastShareTopics())
                    Log.addScrubTerm(shortUrl(baseUrl), Log.TermType.Domain)
                    Log.addScrubTerm(topic, Log.TermType.Term)
                    finish()
                    Toast
                        .makeText(this@ShareActivity, getString(R.string.share_successful), Toast.LENGTH_LONG)
                        .show()
                }
            } catch (e: Exception) {
                val errorMessage = if (e is ApiService.UnauthorizedException) {
                    if (e.user != null) {
                        getString(R.string.detail_test_message_error_unauthorized_user, e.user.username)
                    }  else {
                        getString(R.string.detail_test_message_error_unauthorized_anon)
                    }
                } else if (e is ApiService.EntityTooLargeException) {
                    getString(R.string.detail_test_message_error_too_large)
                } else {
                    getString(R.string.detail_test_message_error, e.message)
                }
                runOnUiThread {
                    progress.visibility = View.GONE
                    errorText.text = errorMessage
                    errorImage.visibility = View.VISIBLE
                    errorText.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun onPasteClick() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val has = clipboard?.hasPrimaryClip() == true
        val desc = clipboard?.primaryClipDescription
        val mimeCount = desc?.mimeTypeCount ?: 0
        val mimeTypes = (0 until mimeCount).joinToString(",") { i -> desc?.getMimeType(i).orEmpty() }
        val itemCount = clipboard?.primaryClip?.itemCount ?: 0
        Log.d(TAG, "Clipboard state (onPasteClick): has=$has items=$itemCount mimes=${if (mimeTypes.isEmpty()) "-" else mimeTypes}")

        val clipText = clipboard?.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
        if (!clipText.isNullOrBlank()) {
            contentText.text = clipText
            intent.putExtra(Intent.EXTRA_TEXT, clipText)
            show()
            validateInput()
            maybeAutoSendForText()
            maybeAutoSendForShortcut()
        } else {
            Toast.makeText(this, getString(R.string.shortcut_clipboard_share_empty), Toast.LENGTH_LONG).show()
        }
    }

    private fun showClipboardInfo() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val has = clipboard?.hasPrimaryClip() == true
        val desc = clipboard?.primaryClipDescription
        val mimeCount = desc?.mimeTypeCount ?: 0
        val mimeTypes = (0 until mimeCount).joinToString(",") { i -> desc?.getMimeType(i).orEmpty() }
        val itemCount = clipboard?.primaryClip?.itemCount ?: 0
        val msg = getString(
            R.string.share_clipboard_info,
            has.toString(),
            itemCount,
            if (mimeTypes.isEmpty()) "-" else mimeTypes
        )
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun validateInput() {
        if (!this::sendItem.isInitialized || !this::useAnotherServerCheckbox.isInitialized || !this::contentText.isInitialized || !this::topicText.isInitialized) {
            return // sendItem is initialized late in onCreateOptionsMenu
        }
        val enabled = if (useAnotherServerCheckbox.isChecked) {
            contentText.text.isNotEmpty() && validTopic(topicText.text.toString()) && validUrl(baseUrlText.text.toString())
        } else {
            contentText.text.isNotEmpty() && topicText.text.isNotEmpty()
        }
        sendItem.isEnabled = enabled
        sendItem.icon?.alpha = if (enabled) 255 else 130
    }

    private fun maybeAutoSendForText() {
        if (autoSendTriggered) return
        val text = if (this::contentText.isInitialized) contentText.text?.toString().orEmpty() else ""
        if (text.isBlank()) return
        if (textContainsAutoSendDomain(text)) {
            autoSendTriggered = true
            // Use default server and set topic to the auto-send topic
            useAnotherServerCheckbox.isChecked = false
            topicText.text = autoSendTopic
            onShareClick()
        }
    }

    private fun maybeAutoSendForShortcut() {
        if (shortcutAutoSendTriggered) return
        if (autoSendTriggered) return
        if (preselectedTopicUrl == null) return

        val message = if (this::contentText.isInitialized) contentText.text?.toString().orEmpty() else ""
        if (message.isBlank() || message == "(no text)") return

        val baseUrl = getBaseUrl()
        val topic = if (this::topicText.isInitialized) topicText.text?.toString().orEmpty() else ""
        if (topic.isBlank()) return
        if (useAnotherServerCheckbox.isChecked && (!validTopic(topic) || !validUrl(baseUrl))) return

        shortcutAutoSendTriggered = true
        onShareClick()
    }

    private fun textContainsAutoSendDomain(text: String): Boolean {
        var s = text.trim().lowercase()
        // Strip scheme for simple prefix checks
        if (s.startsWith("https://")) s = s.removePrefix("https://")
        if (s.startsWith("http://")) s = s.removePrefix("http://")
        // Allow common prefixes like www. and m.
        if (s.startsWith("www.")) s = s.removePrefix("www.")
        if (s.startsWith("m.")) s = s.removePrefix("m.")
        return autoSendDomains.any { d ->
            s == d ||
            s.startsWith(d)
        }
    }

    private fun getBaseUrl(): String {
        return if (useAnotherServerCheckbox.isChecked) {
            baseUrlText.text.toString()
        } else {
            defaultBaseUrl ?: appBaseUrl
        }
    }

    class TopicAdapter(private val topicUrls: List<String>, val onClick: (String) -> Unit) : RecyclerView.Adapter<TopicAdapter.ViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context).inflate(R.layout.fragment_share_item, viewGroup, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            viewHolder.topicName.text = shortUrl(topicUrls[position])
            viewHolder.view.setOnClickListener { onClick(topicUrls[position]) }
        }

        override fun getItemCount() = topicUrls.size

        class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val topicName: TextView = view.findViewById(R.id.share_item_text)
        }
    }

    companion object {
        const val EXTRA_LOAD_CLIPBOARD = "io.heckel.ntfy.ksmaze.extra.LOAD_CLIPBOARD"
        const val TAG = "NtfyShareActivity"
    }
}
