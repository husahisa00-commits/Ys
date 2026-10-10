package com.example.acagent

import android.util.Xml
import java.io.StringReader

/** Turns a raw uiautomator XML dump into a short list the LLM (or a rule engine) can use. */
object UiParser {
    data class Result(val lines: List<String>, val pkg: String, val kept: Int, val rawChars: Int)

    fun parse(xml: String, maxLines: Int = 40, maxField: Int = 60): Result {
        val p = Xml.newPullParser()
        p.setInput(StringReader(xml))
        val out = LinkedHashSet<String>()
        var pkg = ""
        var kept = 0
        var ev = p.eventType
        while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            if (ev == org.xmlpull.v1.XmlPullParser.START_TAG && p.name == "node") {
                fun a(n: String) = p.getAttributeValue(null, n) ?: ""
                val pk = a("package")
                if (pkg.isEmpty() && pk.isNotEmpty() && pk != "com.android.systemui") pkg = pk
                val text = a("text"); val desc = a("content-desc"); val clickable = a("clickable") == "true"
                if (clickable || text.isNotBlank() || desc.isNotBlank()) {
                    kept++
                    val res = a("resource-id").substringAfter(":id/")
                    val line = "TEXT=${cut(text, maxField)} | DESC=${cut(desc, maxField)} | RESOURCE=$res | BOUNDS=${a("bounds")} | CLICKABLE=$clickable"
                    out.add(line)
                }
            }
            ev = p.next()
        }
        return Result(out.take(maxLines), pkg, kept, xml.length)
    }

    private fun cut(s: String, n: Int) = s.replace("\n", " ").let { if (it.length > n) it.take(n) + "…" else it }
}
