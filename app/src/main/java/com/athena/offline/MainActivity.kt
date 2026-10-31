package com.athena.offline

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class MainActivity : Activity() {
    private val backgroundColor = Color.rgb(8, 12, 22)
    private val panel = Color.rgb(17, 25, 42)
    private val accent = Color.rgb(69, 145, 255)
    private lateinit var content: LinearLayout
    private val prefs by lazy { getSharedPreferences("athena_local", MODE_PRIVATE) }
    private val profiles = mutableListOf<String>()
    private var importingInto: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 9, 18)
        window.navigationBarColor = Color.rgb(5, 9, 18)
        PDFBoxResourceLoader.init(applicationContext)
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

    private fun profileDir(profile: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(profile.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(filesDir, "knowledge"), digest).apply { mkdirs() }
    }

    private fun docsKey(profile: String) = "documents_" + profile

    private fun documents(profile: String): JSONArray =
        try { JSONArray(prefs.getString(docsKey(profile), "[]") ?: "[]") }
        catch (_: Exception) { JSONArray() }

    private fun saveDocuments(profile: String, docs: JSONArray) {
        prefs.edit().putString(docsKey(profile), docs.toString()).apply()
    }

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(backgroundColor)
            setPadding(dp(18), dp(22), dp(18), dp(12))
        }
        val header = TextView(this).apply {
            text = "ATHENA  /  OFFLINE STUDY INTELLIGENCE"
            setTextColor(accent); textSize = 12f
            typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.11f
            setPadding(dp(2), dp(4), dp(2), dp(4))
        }
        root.addView(header)
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(38, 54, 80)) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(14); bottomMargin = dp(12) })
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(content) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        listOf("HOME", "PROFESSOR", "KNOWLEDGE", "EXAM", "STUDY").forEach { label ->
            val button = Button(this).apply {
                text = label; textSize = 8f; setTextColor(Color.WHITE)
                isAllCaps = false
                background = gradient(if (label == "HOME") Color.rgb(35, 83, 150) else Color.rgb(24, 36, 58), Color.rgb(12, 20, 35), 12)
                elevation = dp(3).toFloat()
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
            text = title; textSize = 29f; setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(7))
            letterSpacing = -0.015f
        })
        if (subtitle != null) content.addView(TextView(this).apply {
            text = subtitle; textSize = 14f; setTextColor(Color.rgb(174, 187, 207))
            setLineSpacing(dp(3).toFloat(), 1f)
            setPadding(0, 0, 0, dp(20))
        })
    }

    private fun card(title: String, body: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = gradient(Color.rgb(25, 39, 65), Color.rgb(13, 21, 37), 18, Color.rgb(48, 78, 121))
            setPadding(dp(17), dp(16), dp(17), dp(16))
            elevation = dp(2).toFloat()
        }
        box.addView(TextView(this).apply {
            text = title; textSize = 16f; setTextColor(accent)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            letterSpacing = 0.025f
        })
        box.addView(TextView(this).apply {
            text = body; textSize = 14f; setTextColor(Color.rgb(207, 216, 230))
            setLineSpacing(dp(3).toFloat(), 1f); setPadding(0, dp(8), 0, 0)
        })
        content.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun action(label: String, onClick: () -> Unit) {
        val button = Button(this).apply {
            text = label; textSize = 13f; setTextColor(Color.WHITE)
            isAllCaps = false
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            background = gradient(Color.rgb(83, 161, 255), Color.rgb(38, 105, 218), 13)
            elevation = dp(4).toFloat()
            setPadding(dp(10), 0, dp(10), 0)
            setOnClickListener { onClick() }
        }
        content.addView(button, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) })
    }

    private fun showHome() {
        val totalDocs = profiles.sumOf { documents(it).length() }
        reset("Your study command centre", "A private learning workspace designed for offline use.")
        card("LOCAL FIRST", "Your profiles and imported study files are stored privately in this app.")
        card("DOCUMENT LIBRARY", "$totalDocs document(s) imported on this device.")
        card("AI PROFESSOR", "Document reading is now available. The local language model is not integrated yet.")
        card("KNOWLEDGE PROFILES", "${profiles.size} profile(s) created on this device.")
        action("OPEN KNOWLEDGE MANAGER") { showKnowledge() }
        action("ABOUT THIS BUILD") {
            Toast.makeText(this, "ATHENA v0.2.0 — local PDF/TXT import and extraction.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showKnowledge() {
        reset("Knowledge library", "Each profile keeps its own imported documents and extracted text.")
        action("＋ CREATE PROFILE") { createProfile() }
        if (profiles.isEmpty()) card("NO PROFILES YET", "Create a profile such as Current Semester.")
        profiles.toList().forEach { name ->
            val docs = documents(name)
            card(name, "${docs.length()} document(s) stored locally.")
            action("IMPORT PDF / TXT INTO $name") { pickDocument(name) }
            action("＋ PASTE TRANSCRIPT / AI NOTES") { pasteText(name) }
            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val docName = doc.optString("name", "Document")
                val chars = doc.optInt("characters", 0)
                action("READ: $docName ($chars characters)") { previewDocument(name, doc) }
                action("DELETE: $docName") { deleteDocument(name, doc.optString("id")) }
            }
            action("DELETE PROFILE: $name") {
                AlertDialog.Builder(this).setTitle("Delete $name?")
                    .setMessage("This permanently deletes this profile's imported files and extracted text.")
                    .setNegativeButton("CANCEL", null)
                    .setPositiveButton("DELETE") { _, _ ->
                        profiles.remove(name); saveProfiles()
                        profileDir(name).deleteRecursively()
                        prefs.edit().remove(docsKey(name)).apply()
                        showKnowledge()
                    }.show()
            }
        }
    }

    private fun createProfile() {
        val input = EditText(this).apply {
            hint = "e.g. Current Semester"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
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

    private fun pickDocument(profile: String) {
        importingInto = profile
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/pdf", "text/plain"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        try { startActivityForResult(intent, 41) }
        catch (_: Exception) { Toast.makeText(this, "No file picker available.", Toast.LENGTH_SHORT).show() }
    }

    @Deprecated("Deprecated by Android; retained for compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 41 || resultCode != RESULT_OK || data == null) return
        val profile = importingInto ?: return
        val uris = mutableListOf<Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        }
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return
        var imported = 0
        val failures = mutableListOf<String>()
        uniqueUris.forEach { uri ->
            try {
                importOne(profile, uri)
                imported++
            } catch (e: Exception) {
                failures.add("${displayName(uri)}: ${e.message ?: "unsupported or unreadable file"}")
            }
        }
        val message = buildString {
            append("$imported file(s) imported into $profile.")
            if (failures.isNotEmpty()) append("\nCould not import: " + failures.joinToString("; "))
        }
        AlertDialog.Builder(this).setTitle("Import complete").setMessage(message)
            .setPositiveButton("OK") { _, _ -> showKnowledge() }.show()
    }

    private fun displayName(uri: Uri): String {
        var name = "study_material"
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) name = it.getString(index) ?: name
        }
        return name
    }

    private fun importOne(profile: String, uri: Uri) {
        val originalName = displayName(uri)
        val lower = originalName.lowercase()
        val isPdf = lower.endsWith(".pdf")
        val isText = lower.endsWith(".txt") || contentResolver.getType(uri) == "text/plain"
        require(isPdf || isText) { "Only PDF and TXT files are supported in this version." }
        val id = System.currentTimeMillis().toString() + "_" + (0..9999).random()
        val safeName = originalName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(90)
        val dir = profileDir(profile)
        val sourceFile = File(dir, "${id}_$safeName")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open file." }.use { inputStream ->
                sourceFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = inputStream.read(buffer)
                        if (count <= 0) break
                        total += count
                        require(total <= 100L * 1024L * 1024L) { "File exceeds 100 MB limit." }
                        output.write(buffer, 0, count)
                    }
                }
            }
        }
        var textFile: File? = null
        try {
            val extracted = if (isPdf) extractPdf(sourceFile) else sourceFile.readText(Charsets.UTF_8)
            require(extracted.isNotBlank()) {
                "This PDF appears scanned/image-only and has no selectable text layer. OCR is planned; meanwhile paste its transcript or OCR text."
            }
            textFile = File(dir, "${id}_extracted.txt")
            textFile.writeText(extracted, Charsets.UTF_8)
            val pages = if (isPdf) countPdfPages(sourceFile) else 1
            val docs = documents(profile)
            docs.put(JSONObject().apply {
                put("id", id); put("name", originalName); put("source", sourceFile.name)
                put("text", textFile.name); put("pages", pages); put("characters", extracted.length)
            })
            saveDocuments(profile, docs)
        } catch (e: Exception) {
            sourceFile.delete()
            textFile?.delete()
            throw e
        }
    }


    private fun pasteText(profile: String) {
        val titleInput = EditText(this).apply {
            hint = "Title (e.g. YouTube: DBMS Normalization)"
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); setSingleLine(true)
        }
        val bodyInput = EditText(this).apply {
            hint = "Paste a YouTube transcript, AI explanation, class notes, or your own text..."
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            textSize = 14f; gravity = Gravity.TOP or Gravity.START
            minLines = 9; maxLines = 16
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panel, 12, Color.rgb(55, 77, 112))
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
            addView(titleInput, LinearLayout.LayoutParams(-1, dp(52)))
            addView(bodyInput, LinearLayout.LayoutParams(-1, dp(230)).apply { topMargin = dp(10) })
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add text to $profile")
            .setMessage("Paste a transcript, another AI's response, or your notes. Saved locally on this device.")
            .setView(form).setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE TO LIBRARY", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val title = titleInput.text.toString().trim().ifBlank { "Pasted study notes" }
                val body = bodyInput.text.toString().trim()
                if (body.isBlank()) {
                    Toast.makeText(this, "Paste some text first.", Toast.LENGTH_SHORT).show()
                } else {
                    try {
                        savePastedText(profile, title, body)
                        dialog.dismiss()
                        Toast.makeText(this, "Text saved to $profile.", Toast.LENGTH_LONG).show()
                        showKnowledge()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not save text: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun savePastedText(profile: String, title: String, body: String) {
        require(body.length <= 2_000_000) { "Text is too large (maximum 2 million characters)." }
        val id = System.currentTimeMillis().toString() + "_" + (0..9999).random()
        val textFile = File(profileDir(profile), "${id}_pasted.txt")
        textFile.writeText(body, Charsets.UTF_8)
        val docs = documents(profile)
        docs.put(JSONObject().apply {
            put("id", id); put("name", title); put("source", textFile.name)
            put("text", textFile.name); put("pages", 1); put("characters", body.length)
            put("kind", "pasted_text")
        })
        saveDocuments(profile, docs)
    }

    private fun extractPdf(file: File): String {
        PDDocument.load(file).use { document ->
            return PDFTextStripper().getText(document)
        }
    }

    private fun countPdfPages(file: File): Int =
        PDDocument.load(file).use { it.numberOfPages }

    private fun previewDocument(profile: String, doc: JSONObject) {
        val textFile = File(profileDir(profile), doc.optString("text"))
        if (!textFile.exists()) {
            Toast.makeText(this, "Extracted text file is missing.", Toast.LENGTH_SHORT).show()
            return
        }
        val body = textFile.readText(Charsets.UTF_8)
        val preview = if (body.length > 18000) body.take(18000) + "\n\n[Preview limited. Full text remains stored locally.]" else body
        val view = TextView(this).apply {
            text = preview; textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val scroll = ScrollView(this).apply { addView(view) }
        AlertDialog.Builder(this).setTitle(doc.optString("name", "Document"))
            .setView(scroll).setPositiveButton("CLOSE", null).show()
    }

    private fun deleteDocument(profile: String, id: String) {
        val docs = documents(profile)
        val kept = JSONArray()
        for (i in 0 until docs.length()) {
            val doc = docs.optJSONObject(i) ?: continue
            if (doc.optString("id") == id) {
                File(profileDir(profile), doc.optString("source")).delete()
                File(profileDir(profile), doc.optString("text")).delete()
            } else kept.put(doc)
        }
        saveDocuments(profile, kept)
        showKnowledge()
    }

    private fun showComing(section: String) {
        reset(section, "This section is reserved in the ATHENA roadmap.")
        card("COMING IN A LATER BUILD", "The offline AI model, grounded professor chat, exam tools and voice service still need to be integrated and tested.")
    }

    private fun gradient(top: Int, bottom: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(top, bottom)).apply {
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(radiusDp).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
