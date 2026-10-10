package com.example.acagent

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import rikka.shizuku.Shizuku

class MainActivity : android.app.Activity() {
    private lateinit var status: TextView
    private lateinit var output: TextView
    private var service: IShellService? = null
    private val ui = Handler(Looper.getMainLooper())
    private val REQ = 100

    private val args by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, ShellService::class.java.name))
            .daemon(false).processNameSuffix("shell").version(1)
    }
    private val conn = object : ServiceConnection {
        override fun onServiceConnected(n: ComponentName?, b: IBinder?) {
            service = IShellService.Stub.asInterface(b); refresh()
        }
        override fun onServiceDisconnected(n: ComponentName?) { service = null; refresh() }
    }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, r ->
        if (r == PackageManager.PERMISSION_GRANTED) bind() else refresh()
    }
    private val binderListener = Shizuku.OnBinderReceivedListener { refresh() }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 48, 32, 32) }
        status = TextView(this).apply { textSize = 15f }
        output = TextView(this).apply { textSize = 11f; typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true) }
        fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
        root.addView(status)
        root.addView(btn("1) اتصال Shizuku / طلب الإذن") { connect() })
        root.addView(btn("2) Dump الآن (يفرّغ هذا التطبيق)") { dump() })
        root.addView(btn("3) Dump بعد 6 ثوانٍ (بدّل إلى التطبيق الآخر)") {
            output.text = "بدّل الآن إلى التطبيق المطلوب…"; ui.postDelayed({ dump() }, 6000)
        })
        root.addView(ScrollView(this).apply { addView(output) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addRequestPermissionResultListener(permListener)
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    private fun refresh() = runOnUiThread {
        status.text = when {
            !Shizuku.pingBinder() -> "Shizuku: غير شغّال (شغّله ثم أعد المحاولة)"
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> "Shizuku: شغّال، الإذن غير ممنوح"
            service == null -> "Shizuku: الإذن ممنوح، الخدمة غير متصلة"
            else -> "جاهز ✔ (الخدمة تعمل بصلاحية shell)"
        }
    }

    private fun connect() {
        if (!Shizuku.pingBinder()) { refresh(); return }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) bind()
        else Shizuku.requestPermission(REQ)
    }

    private fun bind() { Shizuku.bindUserService(args, conn) }

    private fun dump() {
        val sv = service ?: run { output.text = "الخدمة غير متصلة. اضغط (1) أولًا."; return }
        Thread {
            val f = "/data/local/tmp/acagent_ui.xml"
            val t0 = System.currentTimeMillis()
            val xml = sv.exec("uiautomator dump $f >/dev/null 2>&1; cat $f", 20)
            val ms = System.currentTimeMillis() - t0
            val txt = try {
                if (!xml.contains("<hierarchy")) "فشل الـ dump:\n" + xml.take(500)
                else {
                    val r = UiParser.parse(xml)
                    val compact = r.lines.joinToString("\n")
                    "PACKAGE=${r.pkg}\nزمن الـ dump: ${ms}ms\n" +
                    "XML الخام: ${r.rawChars} حرف (~${r.rawChars / 4} توكن)\n" +
                    "المختصر: ${compact.length} حرف (~${compact.length / 4} توكن) — ${r.lines.size} من ${r.kept} عنصر\n" +
                    "----\n$compact"
                }
            } catch (e: Exception) { "خطأ في التحليل: ${e.message}" }
            runOnUiThread { output.text = txt }
        }.start()
    }
}
