package com.athena.offline

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray

class MainActivity : Activity() {
    private val backgroundColor = Color.rgb(8, 12, 22)
    private val panel = Color.rgb(17, 25, 42)
    private val accent = Color.rgb(69, 145, 255)
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
        try {
            val saved = JSONArray(prefs.getString("profiles", "[]") ?: "[]")
            for (i in 0 until saved.length()) profiles.add(saved.getString(i))
        } catch (_: Exception) { }
    }

    private fun saveProfiles() {
        val array = JSONArray()
        profiles.forEach { array.put(it) }
        prefs.edit().putString("profiles", array.toString()).apply()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(18), dp(22), dp(18), dp(12))
        }
        val header = TextView(this).apply {
            text = "ATHENA  /  OFFLINE STUDY INTELLIGENCE"
            setTextColor(accent)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.08f
        }
        root.addView(header)
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(38, 54, 80)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(12) })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        listOf("HOME", "PROFESSOR", "KNOWLEDGE", "EXAM", "STUDY").forEach { label ->
            val button = Button(this).apply {
                text = label
                textSize = 8f
                setTextColor(Color.WHITE)
                setBackgroundTintList(android.content.res.ColorStateList.valueOf(panel))
                setOnClickListener {
                    when (label) {
                        "HOME" -> showHome()
                        "KNOWLEDGE" -> showKnowledge()
                        else -> showComing(label)
                    }
                }
            }
            nav.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
        root.addView(nav)
        setContentView(root)
    }

    private fun reset(title: String, subtitle: String? = null) {
        content.removeAllViews()
        content.addView(TextView(this).apply {
            text = title
            textSize = 27f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(4))
        })
        if (subtitle != null) content.addView(TextView(this).apply {
            text = subtitle
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(16))
        })
    }

    private fun card(title: String, body: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(panel)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 16f; setTextColor(accent)
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = body; textSize = 13f; setTextColor(Color.LTGRAY)
            setPadding(0, dp(7), 0, 0)
        })
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun action(label: String, onClick: () -> Unit) {
        val button = Button(this).apply {
            text = label; textSize = 13f; setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent))
            setOnClickListener { onClick() }
        }
        content.addView(button, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) })
    }

    private fun showHome() {
        reset("Your study command centre", "A private learning workspace designed for offline use.")
        card("LOCAL FIRST", "Your profile list is stored on this device. No account or cloud API is configured.")
        card("AI PROFESSOR", "The local language model is not included in this starter build.")
        card("KNOWLEDGE PROFILES", "${profiles.size} profile(s) created on this device.")
        action("OPEN KNOWLEDGE MANAGER") { showKnowledge() }
        action("ABOUT THIS BUILD") {
            Toast.makeText(this, "ATHENA starter v0.1.0 — AI engine not yet integrated.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showKnowledge() {
        reset("Knowledge library", "Create a profile to organise your study materials.")
        action("＋ CREATE PROFILE") { createProfile() }
        action("IMPORT A PDF / DOCUMENT") { pickDocument() }
        if (profiles.isEmpty()) {
            card("NO PROFILES YET", "Create a profile such as Current Semester.")
        } else profiles.toList().forEach { name ->
            card(name, "Profile saved locally. Document extraction, indexing and AI answers are not implemented yet.")
            action("DELETE $name") {
                profiles.remove(name); saveProfiles(); showKnowledge()
            }
        }
    }

    private fun createProfile() {
        val input = EditText(this).apply {
            hint = "e.g. Current Semester"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(this).setTitle("New knowledge profile").setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("CREATE") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty() && !profiles.contains(name)) {
                    profiles.add(name); saveProfiles(); showKnowledge()
                } else Toast.makeText(this, "Enter a unique profile name.", Toast.LENGTH_SHORT).show()
            }.show()
    }

    private fun pickDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf", "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain"
            ))
        }
        try { startActivityForResult(intent, 41) }
        catch (_: Exception) { Toast.makeText(this, "No file picker available.", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated by Android; retained for compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) Toast.makeText(this,
                "Selected: ${uri.lastPathSegment}. Import processing is not ready yet.",
                Toast.LENGTH_LONG).show()
        }
    }

    private fun showComing(section: String) {
        reset(section, "This section is reserved in the ATHENA roadmap.")
        card("COMING IN A LATER BUILD", "The local AI model, document indexing, professor chat, exam tools and voice service still need to be integrated and tested.")
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
