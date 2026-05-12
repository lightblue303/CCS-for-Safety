package com.example.image_ai

import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.io.InputStream

class Esp32CamStreamReader(
    private val streamUrl: String,
    private val onFrame: (android.graphics.Bitmap) -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    fun start() {
        val request = Request.Builder()
            .url(streamUrl)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Stream failed: ${response.code}")
                }

                val input = response.body?.byteStream()
                    ?: throw IOException("Empty stream body")

                while (true) {
                    val jpegBytes = readJpegFrame(input)
                    val bitmap = BitmapFactory.decodeByteArray(
                        jpegBytes,
                        0,
                        jpegBytes.size
                    )

                    if (bitmap != null) {
                        onFrame(bitmap)
                    }
                }
            }
        } catch (e: Exception) {
            onError(e)
            throw e
        }
    }

    private fun readJpegFrame(input: InputStream): ByteArray {
        val buffer = ByteArrayOutputStream()

        var prev = -1
        var cur: Int

        // JPEG 시작 0xFFD8 찾기
        while (true) {
            cur = input.read()
            if (cur == -1) throw IOException("Stream ended before JPEG start")

            if (prev == 0xFF && cur == 0xD8) {
                buffer.write(0xFF)
                buffer.write(0xD8)
                break
            }

            prev = cur
        }

        prev = -1

        // JPEG 끝 0xFFD9 찾기
        while (true) {
            cur = input.read()
            if (cur == -1) throw IOException("Stream ended before JPEG end")

            buffer.write(cur)

            if (prev == 0xFF && cur == 0xD9) {
                break
            }

            prev = cur
        }

        return buffer.toByteArray()
    }}