package com.athena.offline

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.Bitmap
import android.speech.tts.TextToSpeech
import java.util.Locale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import android.graphics.pdf.PdfRenderer as AndroidPdfRenderer
import android.os.ParcelFileDescriptor
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
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(5, 9, 18)
        window.navigationBarColor = Color.rgb(5, 9, 18)
        PDFBoxResourceLoader.init(applicationContext)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                tts?.language = Locale.getDefault()
            }
        }
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
                        "PROFESSOR" -> showProfessor()
                        "EXAM" -> showExam()
                        "STUDY" -> showStudy()
                        else -> showHome()
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
        card("STUDY TOOLS", "Search, evidence-based answers, viva practice, tests and a local study planner are available.")
        card("KNOWLEDGE PROFILES", "${profiles.size} profile(s) created on this device.")
        action("OPEN KNOWLEDGE MANAGER") { showKnowledge() }
        action("SEARCH ALL STUDY MATERIALS") { searchAll() }
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
            action("IMPORT PDF / TXT INTO $name") { chooseSubjectAndImport(name) }
            action("＋ PASTE TRANSCRIPT / AI NOTES") { pasteText(name) }
            action("＋ ADD SUBJECT FOLDER") { addSubject(name) }
            action("SEARCH THIS PROFILE") { searchProfile(name) }
            for (i in 0 until docs.length()) {
                val doc = docs.optJSONObject(i) ?: continue
                val docName = doc.optString("name", "Document")
                val chars = doc.optInt("characters", 0)
                val subject = doc.optString("subject", "Unsorted")
                action("[$subject] READ: $docName ($chars characters)") { previewDocument(name, doc) }
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


    private fun chooseSubjectAndImport(profile: String) {
        val options = listOf("Unsorted") + subjects(profile)
        AlertDialog.Builder(this).setTitle("Choose subject folder")
            .setItems(options.toTypedArray()) { _, which ->
                currentImportSubject[profile] = options[which]
                pickDocument(profile)
            }.setNegativeButton("CANCEL", null).show()
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
            val pageTexts = if (isPdf) extractPdfPagesWithOcr(sourceFile) else listOf(sourceFile.readText(Charsets.UTF_8))
            val extracted = pageTexts.joinToString("\u000c") { it }
            require(extracted.isNotBlank()) { "No readable text found. The PDF may be encrypted, damaged, or blank." }
            textFile = File(dir, "${id}_extracted.txt")
            textFile.writeText(extracted, Charsets.UTF_8)
            val pageFile = File(dir, "${id}_pages.json")
            pageFile.writeText(JSONArray(pageTexts).toString(), Charsets.UTF_8)
            val pages = pageTexts.size
            val docs = documents(profile)
            docs.put(JSONObject().apply {
                put("id", id); put("name", originalName); put("source", sourceFile.name)
                put("text", textFile.name); put("pages", pages); put("characters", extracted.length)
                put("pageText", pageFile.name); put("subject", selectedSubject(profile))
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
                File(profileDir(profile), doc.optString("pageText")).delete()
            } else kept.put(doc)
        }
        saveDocuments(profile, kept)
        showKnowledge()
    }


    private fun subjectsKey(profile: String) = "subjects_" + profile

    private fun subjects(profile: String): MutableList<String> {
        val a = try { JSONArray(prefs.getString(subjectsKey(profile), "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        return MutableList(a.length()) { i -> a.optString(i) }
    }

    private fun addSubject(profile: String) {
        val input = EditText(this).apply { hint = "e.g. DBMS"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        AlertDialog.Builder(this).setTitle("Create subject folder").setView(input)
            .setNegativeButton("CANCEL", null).setPositiveButton("CREATE") { _, _ ->
                val name = input.text.toString().trim()
                val list = subjects(profile)
                if (name.isNotBlank() && !list.contains(name)) {
                    list.add(name)
                    val a = JSONArray(); list.forEach { a.put(it) }
                    prefs.edit().putString(subjectsKey(profile), a.toString()).apply()
                    Toast.makeText(this, "Subject folder created.", Toast.LENGTH_SHORT).show()
                    showKnowledge()
                }
            }.show()
    }

    private fun selectedSubject(profile: String): String = currentImportSubject[profile] ?: "Unsorted"
    private val currentImportSubject = mutableMapOf<String, String>()

    private fun searchAll() {
        val input = EditText(this).apply { hint = "Search concepts, keywords, questions..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        AlertDialog.Builder(this).setTitle("Search all materials").setView(input)
            .setNegativeButton("CANCEL", null).setPositiveButton("SEARCH") { _, _ ->
                searchDocuments(null, input.text.toString())
            }.show()
    }

    private fun searchProfile(profile: String) {
        val input = EditText(this).apply { hint = "Search within $profile"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        AlertDialog.Builder(this).setTitle("Search profile").setView(input)
            .setNegativeButton("CANCEL", null).setPositiveButton("SEARCH") { _, _ ->
                searchDocuments(profile, input.text.toString())
            }.show()
    }

    private fun searchDocuments(onlyProfile: String?, query: String) {
        val q = query.trim().lowercase()
        if (q.isBlank()) return
        val hits = mutableListOf<String>()
        profiles.filter { onlyProfile == null || it == onlyProfile }.forEach { profile ->
            val docs = documents(profile)
            for (i in 0 until docs.length()) {
                val d = docs.optJSONObject(i) ?: continue
                val file = File(profileDir(profile), d.optString("text"))
                if (!file.exists()) continue
                val pages = file.readText(Charsets.UTF_8).split("\u000c", "\n\n")
                pages.forEachIndexed { index, page ->
                    if (page.lowercase().contains(q)) {
                        val excerpt = page.trim().replace(Regex("\\s+"), " ").take(260)
                        hits.add("$profile / ${d.optString("subject", "Unsorted")} / ${d.optString("name")} — match near page ${index + 1}: $excerpt")
                    }
                }
            }
        }
        val result = if (hits.isEmpty()) "No matching text found in imported material." else hits.take(30).joinToString("\n\n")
        val view = TextView(this).apply { text = result; textSize = 14f; setTextColor(Color.WHITE); setPadding(dp(16), dp(12), dp(16), dp(12)) }
        AlertDialog.Builder(this).setTitle("Search results (${hits.size})").setView(ScrollView(this).apply { addView(view) }).setPositiveButton("CLOSE", null).show()
    }

    private fun showProfessor() {
        reset("ATHENA Professor", "Ask questions against your imported notes. Answers show matching source passages; this is local retrieval, not a generative LLM yet.")
        action("ASK FROM ALL MATERIALS") { askQuestion(null) }
        profiles.forEach { p -> action("ASK FROM $p") { askQuestion(p) } }
        action("READ ANSWER ALOUD") { if (ttsReady) tts?.speak("ATHENA local study assistant. Answers are based on matching passages in your saved notes.", TextToSpeech.QUEUE_FLUSH, null, "athena") }
        card("OFFLINE STATUS", "Text search and evidence retrieval run locally. A full offline generative language model is not yet bundled.")
    }

    private fun askQuestion(profile: String?) {
        val input = EditText(this).apply { hint = "Type your question"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        AlertDialog.Builder(this).setTitle("Ask ATHENA").setView(input).setNegativeButton("CANCEL", null)
            .setPositiveButton("FIND ANSWER") { _, _ -> searchDocuments(profile, input.text.toString()) }.show()
    }

    private fun showExam() {
        reset("Viva & Exam Arena", "Generate practice prompts from your imported notes. Check the source text after answering.")
        profiles.forEach { profile -> action("START VIVA — $profile") { startViva(profile) } }
        if (profiles.isEmpty()) card("NO MATERIALS", "Create a profile and import notes first.")
    }

    private fun startViva(profile: String) {
        val pool = mutableListOf<Pair<String, String>>()
        val docs = documents(profile)
        for (i in 0 until docs.length()) {
            val d = docs.optJSONObject(i) ?: continue
            val f = File(profileDir(profile), d.optString("text"))
            if (!f.exists()) continue
            f.readText(Charsets.UTF_8).split(Regex("[.!?\\n]")).map { it.trim() }
                .filter { it.length in 45..240 }.take(80).forEach { pool.add(d.optString("name") to it) }
        }
        if (pool.isEmpty()) {
            Toast.makeText(this, "Import readable notes first.", Toast.LENGTH_SHORT).show()
            return
        }
        val chosen = pool.shuffled().take(10)
        var index = 0
        var score = 0
        fun next() {
            if (index >= chosen.size) {
                AlertDialog.Builder(this).setTitle("Viva completed").setMessage("You reviewed $score of ${chosen.size} prompts. This is a self-check; ATHENA has not automatically graded correctness.")
                    .setPositiveButton("DONE", null).show()
                return
            }
            val pair = chosen[index]
            val answer = EditText(this).apply { hint = "Write your answer before revealing the reference"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); minLines = 3 }
            AlertDialog.Builder(this).setTitle("Viva ${index + 1}/${chosen.size}")
                .setMessage("Explain this concept from ${pair.first}:\\n\\n${pair.second.take(190)}")
                .setView(answer).setNegativeButton("SKIP") { _, _ -> index++; next() }
                .setPositiveButton("SHOW REFERENCE") { _, _ ->
                    score++
                    AlertDialog.Builder(this).setTitle("Reference passage").setMessage(pair.second)
                        .setPositiveButton("NEXT") { _, _ -> index++; next() }.show()
                }.show()
        }
        next()
    }

    private fun showStudy() {
        reset("Study planner", "A simple offline planner saved on this device.")
        action("ADD STUDY TASK") { addStudyTask() }
        val tasks = try { JSONArray(prefs.getString("study_tasks", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        for (i in 0 until tasks.length()) {
            val item = tasks.optJSONObject(i) ?: continue
            card(item.optString("date"), item.optString("subject") + " — " + item.optString("task"))
        }
    }

    private fun addStudyTask() {
        val subject = EditText(this).apply { hint = "Subject"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val task = EditText(this).apply { hint = "What will you study?"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val date = EditText(this).apply { hint = "Date (YYYY-MM-DD)"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), 0); addView(subject); addView(task); addView(date) }
        AlertDialog.Builder(this).setTitle("New study task").setView(box).setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE") { _, _ ->
                val a = try { JSONArray(prefs.getString("study_tasks", "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
                a.put(JSONObject().put("subject", subject.text.toString()).put("task", task.text.toString()).put("date", date.text.toString()))
                prefs.edit().putString("study_tasks", a.toString()).apply()
                showStudy()
            }.show()
    }

    private fun extractPdfPagesWithOcr(file: File): List<String> {
        PDDocument.load(file).use { document ->
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = AndroidPdfRenderer(pfd)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = mutableListOf<String>()
            for (pageIndex in 0 until document.numberOfPages) {
                val stripper = PDFTextStripper().apply { startPage = pageIndex + 1; endPage = pageIndex + 1 }
                var text = stripper.getText(document).trim()
                if (text.isBlank()) {
                    val page = renderer.openPage(pageIndex)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, AndroidPdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    try {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        text = Tasks.await(recognizer.process(image)).text
                    } finally { bitmap.recycle() }
                }
                result.add(text)
            }
            recognizer.close()
            renderer.close()
            pfd.close()
            return result
        }
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

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
