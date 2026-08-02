package com.myself.browser

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class ViewerActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        scroll.addView(container)
        setContentView(scroll)

        val filePath = intent.getStringExtra("file_path") ?: return finish()
        val mimeType = intent.getStringExtra("mime_type") ?: ""
        val file = File(filePath)

        when {
            mimeType.startsWith("image/") -> showImage(container, file)
            mimeType == "application/pdf" -> showPdf(container, file)
            mimeType.startsWith("video/") -> showVideo(container, file)
            mimeType.startsWith("audio/") -> showAudio(container, file)
            else -> showUnsupported(container, file, mimeType)
        }
    }

    private fun showImage(container: LinearLayout, file: File) {
        val img = ImageView(this)
        img.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        img.adjustViewBounds = true
        img.setImageURI(Uri.fromFile(file))
        container.addView(img)
    }

    private fun showPdf(container: LinearLayout, file: File) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val img = ImageView(this)
                img.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 8, 0, 8) }
                img.setImageBitmap(bitmap)
                container.addView(img)
                page.close()
            }
            renderer.close()
        } catch (e: Exception) {
            val tv = TextView(this)
            tv.text = "PDF open nahi ho paya: ${e.message}"
            tv.setPadding(32, 32, 32, 32)
            container.addView(tv)
        }
    }

    private fun showVideo(container: LinearLayout, file: File) {
        val videoView = VideoView(this)
        videoView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 800
        )
        val controller = MediaController(this)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)
        videoView.setVideoURI(Uri.fromFile(file))
        videoView.setOnPreparedListener { videoView.start() }
        container.addView(videoView)
    }

    private fun showAudio(container: LinearLayout, file: File) {
        val tv = TextView(this)
        tv.text = "🎵 ${file.name}"
        tv.textSize = 18f
        tv.setPadding(32, 64, 32, 32)
        container.addView(tv)

        val btnPlay = Button(this)
        btnPlay.text = "Play"
        container.addView(btnPlay)

        mediaPlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
        }

        var playing = false
        btnPlay.setOnClickListener {
            if (playing) {
                mediaPlayer?.pause()
                btnPlay.text = "Play"
            } else {
                mediaPlayer?.start()
                btnPlay.text = "Pause"
            }
            playing = !playing
        }
    }

    private fun showUnsupported(container: LinearLayout, file: File, mimeType: String) {
        val tv = TextView(this)
        tv.text = "Ye file type (${mimeType}) is browser me directly nahi khul sakti abhi.\n\nFile save ho gayi hai: ${file.name}\n\nDownloads folder me milegi, kisi doosri app se khol sakte ho."
        tv.setPadding(32, 32, 32, 32)
        tv.textSize = 16f
        container.addView(tv)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
    }
}