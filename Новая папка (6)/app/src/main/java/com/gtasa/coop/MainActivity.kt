package com.gtasa.coop

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.net.wifi.WifiManager
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GTASA CO-OP prototype v0.1.
 * This app tests local Wi-Fi networking only. It does NOT yet connect to or modify GTA SA.
 */
class MainActivity : Activity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val running = AtomicBoolean(false)
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var peerSocket: Socket? = null
    @Volatile private var writer: PrintWriter? = null

    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var ipInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(18, 22, 30)
        window.navigationBarColor = Color.rgb(18, 22, 30)
        buildUi()
        appendLog("GTASA CO-OP v0.1")
        appendLog("Это тест связи между телефонами, не мод GTA.")
        appendLog("Оба телефона подключите к одной точке доступа Wi-Fi.")
        appendLog("IP этого телефона: ${localIp()}")
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            setBackgroundColor(Color.rgb(18, 22, 30))
        }
        val title = TextView(this).apply {
            text = "GTASA CO-OP"
            textSize = 27f
            setTextColor(Color.WHITE)
        }
        status = TextView(this).apply {
            text = "Статус: не подключено"
            textSize = 15f
            setTextColor(Color.rgb(110, 220, 160))
            setPadding(0, dp(8), 0, dp(12))
        }
        val hostBtn = button("Создать комнату (сервер)") { startHost() }
        ipInput = EditText(this).apply {
            hint = "IP телефона-сервера, например 192.168.43.1"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(10), 0, dp(10), 0)
        }
        val connectBtn = button("Подключиться к комнате") { connectToHost() }
        messageInput = EditText(this).apply {
            hint = "Тестовое сообщение"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            setPadding(dp(10), 0, dp(10), 0)
        }
        val sendBtn = button("Отправить сообщение") { sendMessage() }
        val stopBtn = button("Отключиться") { disconnect() }

        logView = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.rgb(225, 230, 238))
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        scroll = ScrollView(this).apply {
            addView(logView)
            setBackgroundColor(Color.rgb(29, 35, 46))
        }

        outer.addView(title)
        outer.addView(status)
        outer.addView(hostBtn, matchWrap())
        outer.addView(ipInput, matchWrap())
        outer.addView(connectBtn, matchWrap())
        outer.addView(messageInput, matchWrap())
        outer.addView(sendBtn, matchWrap())
        outer.addView(stopBtn, matchWrap())
        outer.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ).apply { topMargin = dp(12) })
        setContentView(outer)
    }

    private fun button(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(5) }

    private fun startHost() {
        if (!running.compareAndSet(false, true)) {
            appendLog("Уже запущено. Нажми «Отключиться» перед повторным запуском.")
            return
        }
        setStatus("Статус: ожидаем второго игрока на порту 7777")
        appendLog("IP сервера: ${localIp()}")
        appendLog("На втором телефоне введи этот IP и нажми «Подключиться».")
        io.execute {
            try {
                val ss = ServerSocket(7777)
                serverSocket = ss
                val socket = ss.accept()
                peerSocket = socket
                setupConnection(socket, "Игрок подключился")
            } catch (e: Exception) {
                if (running.get()) appendLog("Ошибка сервера: ${e.message}")
                running.set(false)
            }
        }
    }

    private fun connectToHost() {
        val host = ipInput.text.toString().trim()
        if (host.isBlank()) {
            appendLog("Сначала введи IP телефона-сервера.")
            return
        }
        if (!running.compareAndSet(false, true)) {
            appendLog("Уже запущено. Нажми «Отключиться» перед повторным запуском.")
            return
        }
        setStatus("Статус: подключаемся к $host:7777…")
        io.execute {
            try {
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(host, 7777), 6000)
                peerSocket = socket
                setupConnection(socket, "Подключено к $host")
            } catch (e: Exception) {
                running.set(false)
                setStatus("Статус: не подключено")
                appendLog("Не удалось подключиться: ${e.message}")
                appendLog("Проверь одну Wi-Fi-сеть и IP сервера.")
            }
        }
    }

    private fun setupConnection(socket: Socket, message: String) {
        writer = PrintWriter(socket.getOutputStream(), true)
        setStatus("Статус: соединение установлено")
        appendLog(message)
        io.execute {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (running.get()) {
                    val line = reader.readLine() ?: break
                    appendLog("Другой телефон: $line")
                }
            } catch (e: Exception) {
                if (running.get()) appendLog("Соединение прервано: ${e.message}")
            } finally {
                running.set(false)
                setStatus("Статус: соединение закрыто")
            }
        }
    }

    private fun sendMessage() {
        val msg = messageInput.text.toString().trim()
        if (msg.isBlank()) return
        val w = writer
        if (w == null || !running.get()) {
            appendLog("Сначала установи соединение.")
            return
        }
        io.execute {
            w.println(msg.replace("\n", " "))
            appendLog("Ты: $msg")
            mainHandler.post { messageInput.setText("") }
        }
    }

    private fun disconnect() {
        running.set(false)
        try { peerSocket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        peerSocket = null
        serverSocket = null
        writer = null
        setStatus("Статус: не подключено")
        appendLog("Отключено.")
    }

    private fun localIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address &&
                        addr.hostAddress.startsWith("192.168.") ||
                        (!addr.isLoopbackAddress && addr is Inet4Address &&
                        addr.hostAddress.startsWith("10."))) {
                        return addr.hostAddress ?: "IP не найден"
                    }
                }
            }
        } catch (_: Exception) {}
        return "не найден (проверь Wi-Fi)"
    }

    private fun setStatus(text: String) {
        mainHandler.post { status.text = text }
    }

    private fun appendLog(text: String) {
        mainHandler.post {
            logView.append("• $text\n")
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        disconnect()
        io.shutdownNow()
        super.onDestroy()
    }
}
