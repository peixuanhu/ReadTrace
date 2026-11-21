package com.dmer.neoreaderrecords

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class BookplateActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var page: LinearLayout
    private val apiKey by lazy { WeReadClient.loadApiKey(this) }
    private val archive by lazy {
        val account = MessageDigest.getInstance("SHA-256").digest(apiKey.toByteArray())
            .joinToString("") { "%02x".format(it) }
        getSharedPreferences("bookplates_$account", MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(dp(18), dp(12), dp(18), dp(12))
        }
        val frame = FrameLayout(this).apply { fitsSystemWindows = true; addView(page) }
        setContentView(frame)
        if (apiKey.isBlank()) {
            heading("藏书票")
            page.addView(label("请先在设置中保存微信读书 API Key。", 18))
            return
        }
        val id = intent.getStringExtra("bookId")
        if (id == null) loadShelf() else loadPlate(id)
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).roundToInt()
    private fun label(value: String, size: Int) = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(Color.BLACK)
        setPadding(0, dp(4), 0, dp(4))
    }

    private fun heading(title: String) {
        page.removeAllViews()
        page.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(title, 23).apply { typeface = Typeface.DEFAULT_BOLD },
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(this@BookplateActivity).apply { text = "返回"; setOnClickListener { finish() } })
        })
        rule(page)
    }

    private fun rule(parent: LinearLayout) {
        parent.addView(View(this).apply { setBackgroundColor(Color.BLACK) }, -1, dp(1))
    }

    private fun <T> load(block: () -> T, show: (Result<T>) -> Unit) {
        worker.execute {
            val result = runCatching(block)
            runOnUiThread { if (!isDestroyed && !isFinishing) show(result) }
        }
    }

    private fun loadShelf() {
        heading("藏书票")
        page.addView(label("正在读取已读完的书…", 18))
        load({
            val result = WeReadClient.fetchShelfSnapshot(this, apiKey)
            check(result.ok) { "读取书架失败，请检查网络和微信读书设置。" }
            result.books.filter { it.status == 2 && !it.bookKey.startsWith("album:") }
                .sortedByDescending { it.readUpdateTimeMs }
        }) { result ->
            heading("藏书票")
            val books = result.getOrElse {
                page.addView(label(it.message.orEmpty(), 17))
                page.addView(Button(this).apply { text = "重试"; setOnClickListener { loadShelf() } })
                return@load
            }
            if (books.isEmpty()) {
                page.addView(label("微信读书书架上还没有标记为读完的电子书。", 18))
                return@load
            }
            page.addView(ListView(this).apply {
                adapter = ArrayAdapter(this@BookplateActivity, android.R.layout.simple_list_item_1,
                    books.map { it.title })
                setOnItemClickListener { _, _, index, _ ->
                    val book = books[index]
                    startActivity(Intent(this@BookplateActivity, BookplateActivity::class.java)
                        .putExtra("bookId", book.bookKey).putExtra("title", book.title)
                        .putExtra("author", book.author).putExtra("cover", book.coverUrl))
                }
            })
        }
    }

    private fun loadPlate(id: String) {
        val book = WeReadClient.ShelfBook(id, intent.getStringExtra("title").orEmpty(),
            intent.getStringExtra("author").orEmpty(), intent.getStringExtra("cover").orEmpty(), 0, 2)
        heading("私人藏书票")
        val scroll = ScrollView(this)
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(card)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val cover = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "《${book.title}》原书封面"
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        card.addView(LinearLayout(this).apply {
            setPadding(0, dp(18), 0, dp(18))
            gravity = Gravity.CENTER_VERTICAL
            addView(cover, dp(92), dp(136))
            addView(LinearLayout(this@BookplateActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), 0, 0, 0)
                addView(label(book.title, 23).apply { typeface = Typeface.DEFAULT_BOLD })
                addView(label(book.author, 16))
            }, LinearLayout.LayoutParams(0, -2, 1f))
        })
        rule(card)
        card.addView(label("我的划线", 15))
        val quote = label("正在读取…", 20).apply { setLineSpacing(dp(3).toFloat(), 1f) }
        val chapter = label("", 15)
        card.addView(quote)
        card.addView(chapter)
        rule(page)
        val serial = archive.getInt("serial:$id", 0).takeIf { it > 0 } ?: run {
            val next = archive.getInt("count", 0) + 1
            archive.edit().putInt("count", next).putInt("serial:$id", next).apply()
            next
        }
        page.addView(label(String.format(Locale.ROOT, "微信读书 · 读讫     No. %03d", serial), 15))
        load({ WeReadClient.fetchHighlights(this, apiKey, id) }) { result ->
            val lines = result.getOrElse {
                quote.text = "划线读取失败"
                card.addView(Button(this).apply { text = "重试"; setOnClickListener { loadPlate(id) } })
                return@load
            }
            val last = archive.getString("last:$id", null)
            val line = Bookplate.pick(lines, last)
            quote.text = line?.text ?: "这本书还没有个人划线。"
            chapter.text = line?.chapter.orEmpty()
            if (line != null) archive.edit().putString("last:$id", line.text).apply()
        }
        load({ WeReadClient.cacheShelfBookCover(this, book)?.let { BitmapFactory.decodeFile(it) } }) {
            val bitmap = it.getOrNull()
            if (bitmap == null) cover.visibility = View.INVISIBLE else cover.setImageBitmap(bitmap)
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
