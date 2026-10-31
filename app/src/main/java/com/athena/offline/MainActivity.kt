package com.athena.offline

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val bg = Color.rgb(8, 12, 22)
    private val panel = Color.rgb(17, 25, 42)\n    private val accent = Color.rgb(69, 145, 255)
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences("athena_local", MODE_PRIVATE) }
    private val profiles = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loadProfiles()
        buildShell()
        showHome()
    }

    private fun loadProfiles() {
        val saved = prefs.getString("profiles", "[]") ?: "[]"
        try {
            val arr = JSONArray(saved)
            for (i in 0 until arr.length()) profiles.add(arr.getString(i))
        } catch (_: Exception) { }
    }

    private fun saveProfiles() {
        val arr = JSONArray()
        profiles.forEach { arr.put(it) }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    private fun buildShell() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(22), dp(18), dp(12))
        }
        val header = TextView(this).apply {
            text = "ATHENA  /  OFFLINE STUDY INTELLIGENCE"
            setTextColor(accent); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        root.addView(header)
        val divider = View(this).apply { setBackgroundColor(Color.rgb(38, 54, 80)) }
        root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(12) })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }
        listOf("HOME", "PROFESSOR", "KNOWLEDGE", "EXAM ARENA", "STUDI").forEach { label ->
            val b = Button(this).apply {
                text = label; textSize = 8f; setTextColor(Color.WHITE)
                setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
                setOnClickListener { when(label) { "HOME" -> showHome(); "KNOWLEDGE" -> showKnowledge(); else -> showComing(label) } }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun reset(title: String, subtitle: String? = null) {
        content.removeAllViews()
        val h = TextView(this).apply { text = title; textSize = 27f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; setPadding(0, dp(8), 0, dp(4)) }
        content.addView(h)
        if (subtitle != null) content.addView(TextView(this).apply { text = subtitle; textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, 0, 0, dp(16) })
    }
    private fun card(title: String, body: String) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(panel); setPadding(dp(16), dp(14), dp(16), dp(14)) }
        val t = TextView(this).apply { text = title; textSize = 16f; setTextColor(accent); typeface = Typeface.DEFAULT_BOLD }
        val b = TextView(this).apply { text = body; textSize = 13f; setTextColor(Color.LTGRAY); setPadding(0, dp(7), 0, 0) }
        box.addView(t); box.addView(b); content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }
    private fun action(label: String, onClick: () -> Unit) {
        val b = Button(this).apply { text = label; textSize = 13f; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent)); setOnClickListener { onClick() } }
        content.addView(b, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) })
    }
    private fun showHome() {
        reset("Your study command centre", "A private learning workspace that is designed to work offline.")
        card("LOCAL FIRST", "Your study files and profile list stay on this device. No account or cloud API is configured.")
        card("AI PROFESSOR", "The local language model is not included in this starter build. It will be added and tested as a separate module.")
        card("KNOWLEDGE PROFILES", "${profiles.size} profile(s) created on this device.")
        action("OPEN KNOWLEDGE MANAGER") { showKnowledge() }
        action("ANOUT THIS BUILD") { Toast.makeText(this, "ATHENA starter v.0.1.0 — AI engine not yet integrated.", Toast.LENGTH_LONG).show() }
    }
    private fun showKnowledge() {
        reset("Knowledge library", "Create a profile, then import study files into it.")
        action("＋ CREATE PROFILE") { createProfile() }
        action("IMPORT A PDF / DOCUMENT") { pickDocument() }
        if (profiles.isEmpty()) {
            card("NO PROFILES YET", "Create a profile such as 'Current Semester' to organise your materials.")
        } else {
            profiles.toList().forEach { name ->
                card(name, "Profile created locally. Document extraction, indexing and AI-grounded answers are not implemented in this starter build.")
                action("DELETE $name") { profiles.remove(name); saveProfiles(); showKnowledge() }
            }
        }
    }
    private fun createProfile() {
        val input = EditText(this).apply { hint = "e.g. Current Semester"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        AlertDialogBuilder().show(input)
    }
    private inner class AlertDialogBuilder {
        fun show(input: EditText) {
            android.app.AlertDialog.Builder(this@MainActivity)
                .setTitle("New knowledge profile").setView(input).setNegativeButton("CANCEL", null)
                .setPositiveButton("CREATE") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty() && !profiles.contains(name)) { profiles.add(name); saveProfiles(); showKnowledge() } else Toast.makeText(this@MainActivity, "Enter a unique profile name.", Toast.LENGTH_SHORT).show()
                }.show()
        }
    }
    private fun pickDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain")) }
        try { startActivityForResult(intent, 41) } catch (_: Exception) { Toast.makeText(this, "No file picker available.", Toast.LENGTH_SHORT).show() }
    }
    @Deprecated("Deprecated by Android; retained for compatibility with this starter.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) Toast.makeText(this, "File selected: ${uri.lastPathSegment}. Import processing will be added next.", Toast.LENGTH_LONG).show()
        }
    }
    private fun showComing(section: String) { reset(section, "This section is reserved in the ATHENA roadmap."); card("COMING IN A LATER BUILD", "The first milestone is a stable Android foundation. The offline model, document indexing, professor chat, exam tools and voice service must be integrated and tested before they can be called functional.") }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
