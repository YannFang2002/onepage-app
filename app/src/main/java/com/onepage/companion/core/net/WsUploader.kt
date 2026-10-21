package com.onepage.companion.core.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.io.InputStream

/**
 * Uploads a file over the firmware's WebSocket protocol (port 81):
 *
 *   → START:<filename>:<size>:<path>
 *   ← READY
 *   → [binary frames]
 *   ← PROGRESS:<received>:<total>   (every 64 KB)
 *   ← DONE
 *
 * The device keeps a single upload slot, so callers must serialise uploads.
 */
class WsUploader(private val client: OkHttpClient) {

    data class Progress(val sent: Long, val total: Long) {
        val fraction: Float get() = if (total <= 0) 0f else (sent.toFloat() / total).coerceIn(0f, 1f)
    }

    suspend fun upload(
        wsUrl: String,
        filename: String,
        size: Long,
        path: String,
        openStream: () -> InputStream,
        onProgress: (Progress) -> Unit = {},
    ): Unit = withContext(Dispatchers.IO) {
        val ready = CompletableDeferred<Unit>()
        val done = CompletableDeferred<Unit>()
        var failure: Throwable? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("START:$filename:$size:$path")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when {
                    text == "READY" -> if (!ready.isCompleted) ready.complete(Unit)
                    text == "DONE" -> {
                        if (!ready.isCompleted) ready.complete(Unit)
                        if (!done.isCompleted) done.complete(Unit)
                    }
                    text.startsWith("ERROR") -> {
                        val msg = text.substringAfter(':', "上传被设备拒绝")
                        if (!ready.isCompleted) ready.completeExceptionally(IOException(msg))
                        if (!done.isCompleted) done.completeExceptionally(IOException(msg))
                    }
                    text.startsWith("PROGRESS:") -> {
                        val parts = text.split(':')
                        if (parts.size == 3) {
                            val received = parts[1].toLongOrNull() ?: return
                            val total = parts[2].toLongOrNull() ?: return
                            onProgress(Progress(received, total))
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                failure = t
                if (!ready.isCompleted) ready.completeExceptionally(t)
                if (!done.isCompleted) done.completeExceptionally(t)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!done.isCompleted) {
                    done.completeExceptionally(IOException("连接被关闭（$code $reason）"))
                }
            }
        }

        val ws = client.newBuilder()
            .readTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .pingInterval(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
            .newWebSocket(Request.Builder().url(wsUrl).build(), listener)

        try {
            // 1. Wait for the device to open the file.
            val readyDeadline = System.currentTimeMillis() + 15_000
            while (!ready.isCompleted) {
                if (System.currentTimeMillis() > readyDeadline) throw IOException("设备无响应（15s）")
                delay(30)
            }
            ready.await()

            // 2. Stream the file, honouring the socket's queue so we never
            //    balloon memory on a 380 KB-RAM class MCU link.
            var sent = 0L
            var lastActivity = System.currentTimeMillis()
            openStream().use { input ->
                val buffer = ByteArray(CHUNK_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    val piece = if (read == buffer.size) buffer.toByteString() else buffer.copyOf(read).toByteString()
                    while (ws.queueSize() > MAX_QUEUED_BYTES) {
                        delay(15)
                        if (System.currentTimeMillis() - lastActivity > STALL_TIMEOUT_MS) {
                            throw IOException("上传停滞")
                        }
                    }
                    if (!ws.send(piece)) throw IOException("连接已断开")
                    sent += read
                    lastActivity = System.currentTimeMillis()
                    onProgress(Progress(sent, size))
                }
            }

            // 3. Wait for DONE, with a stall watchdog.
            while (!done.isCompleted) {
                if (System.currentTimeMillis() - lastActivity > STALL_TIMEOUT_MS) {
                    throw IOException("设备长时间无响应")
                }
                delay(50)
            }
            done.await()
        } catch (ce: CancellationException) {
            ws.cancel()
            throw ce
        } catch (t: Throwable) {
            ws.close(1000, "abort")
            throw failure ?: t
        } finally {
            ws.close(1000, "done")
        }
    }

    private companion object {
        const val CHUNK_SIZE = 8 * 1024
        const val MAX_QUEUED_BYTES = 512L * 1024
        const val STALL_TIMEOUT_MS = 45_000L
    }
}

private fun ByteString.lengthHint(): Int = size
