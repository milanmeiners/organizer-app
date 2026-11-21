package com.lifeorganizer.app.print

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.domain.GoalTree
import com.lifeorganizer.app.domain.Planner
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Prints the day or week overview through the Android print dialog, where the user can pick a printer or
 * "Save as PDF". Pages are drawn directly with Canvas in black & white.
 */
object PdfPrinter {
    private val L = Locale.ENGLISH
    private val DAY_TITLE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", L)
    private val SHORT = DateTimeFormatter.ofPattern("d MMM", L)
    private val WEEKDAY = DateTimeFormatter.ofPattern("EEE d", L)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", L)

    fun printDay(context: Context, data: AppData, date: LocalDate) {
        val lines = dayLines(data, date)
        val name = "Day " + date.toString()
        print(context, name, landscape = false) { pageW, pageH -> paginate(lines, pageW, pageH, "Day overview", date.format(DAY_TITLE)) }
    }

    fun printWeek(context: Context, data: AppData, anyDay: LocalDate) {
        val start = anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val name = "Week " + start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " " + start.year
        print(context, name, landscape = true) { pageW, pageH -> listOf(WeekPage(data, start, pageW, pageH)) }
    }

    /** Renders the pages to bitmaps (used by tests to check the layout visually). */
    internal fun previewBitmaps(data: AppData, date: LocalDate, week: Boolean, scale: Float = 1.5f): List<android.graphics.Bitmap> {
        val w = if (week) 842 else 595
        val h = if (week) 595 else 842
        val pages = if (week) listOf(WeekPage(data, date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), w, h))
        else paginate(dayLines(data, date), w, h, "Day overview", date.format(DAY_TITLE))
        return pages.map { p ->
            android.graphics.Bitmap.createBitmap((w * scale).toInt(), (h * scale).toInt(), android.graphics.Bitmap.Config.ARGB_8888).also {
                val c = Canvas(it)
                c.drawColor(Color.WHITE)
                c.scale(scale, scale)
                p.draw(c)
            }
        }
    }

    // ------------------------------------------------------------------ Model

    private enum class Style { HEADER, SECTION, EVENT, TODO, TODO_DONE, DEADLINE, NOTE, SPACER }
    private data class Line(val style: Style, val left: String, val right: String = "")

    private interface Page { fun draw(c: Canvas) }

    private fun dayLines(data: AppData, date: LocalDate): List<Line> {
        val out = mutableListOf<Line>()
        val goals = GoalTree(data.goals)
        val events = Planner.eventsInRange(data.events, data.exceptions, date, date)
        val items = Planner.dayItems(data, date)
        val deadlines = Planner.deadlinesInRange(data.tasks, date, date)[date].orEmpty()

        out += Line(Style.SECTION, "SCHEDULE")
        if (events.isEmpty()) out += Line(Style.NOTE, "No fixed events")
        for (e in events) {
            out += Line(Style.EVENT, e.title, e.start.format(TIME) + " – " + e.end.format(TIME))
            if (e.location.isNotBlank()) out += Line(Style.NOTE, "      " + e.location)
        }
        out += Line(Style.SPACER, "")
        out += Line(Style.SECTION, "TO-DO")
        if (items.isEmpty()) out += Line(Style.NOTE, "Nothing planned")
        for (i in items) {
            val extra = buildList {
                if (i.skipped) add("skipped")
                if (i.task.trackProgress) add("${i.task.progress}%")
                goals.get(i.task.goalId)?.let { add(it.title) }
                i.task.deadline?.let { add("due " + it.format(SHORT)) }
            }.joinToString(" · ")
            out += Line(if (i.done) Style.TODO_DONE else Style.TODO, i.title, extra)
        }
        if (deadlines.isNotEmpty()) {
            out += Line(Style.SPACER, "")
            out += Line(Style.SECTION, "DEADLINES TODAY")
            for (t in deadlines) out += Line(Style.DEADLINE, t.title)
        }
        out += Line(Style.SPACER, "")
        out += Line(Style.SECTION, "NOTES")
        repeat(6) { out += Line(Style.NOTE, "_".repeat(200)) }
        return out
    }

    // ------------------------------------------------------------------ Paint helpers

    private fun paint(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private val GRAY = Color.rgb(110, 110, 110)
    private val LIGHT = Color.rgb(200, 200, 200)

    private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f) return ""
        if (p.measureText(text) <= maxWidth) return text
        val n = p.breakText(text, true, maxWidth - p.measureText("…"), null)
        return text.take(n.coerceAtLeast(0)) + "…"
    }

    // ------------------------------------------------------------------ Day pages

    private const val MARGIN = 40f
    private const val LINE_H = 20f

    private fun paginate(lines: List<Line>, w: Int, h: Int, kind: String, title: String): List<Page> {
        val firstTop = MARGIN + 60f
        val perFirst = ((h - MARGIN - firstTop) / LINE_H).toInt().coerceAtLeast(1)
        val perOther = ((h - MARGIN - (MARGIN + 20f)) / LINE_H).toInt().coerceAtLeast(1)
        val pages = mutableListOf<Page>()
        var idx = 0
        var first = true
        while (idx < lines.size || first) {
            val count = if (first) perFirst else perOther
            val chunk = lines.subList(idx, minOf(lines.size, idx + count))
            val isFirst = first
            pages += object : Page {
                override fun draw(c: Canvas) = drawDayPage(c, w, chunk, isFirst, kind, title)
            }
            idx += count
            first = false
        }
        return pages
    }

    private fun drawDayPage(c: Canvas, w: Int, lines: List<Line>, header: Boolean, kind: String, title: String) {
        var y = MARGIN
        val right = w - MARGIN
        if (header) {
            c.drawText(kind.uppercase(L), MARGIN, y + 10f, paint(9f, true, GRAY))
            c.drawText(title, MARGIN, y + 34f, paint(20f, true))
            c.drawLine(MARGIN, y + 46f, right, y + 46f, paint(1f).apply { strokeWidth = 1f })
            y += 60f
        } else {
            y += 20f
        }
        val body = paint(11f)
        val bold = paint(11f, true)
        val gray = paint(9.5f, color = GRAY)
        for (l in lines) {
            val base = y + 14f
            when (l.style) {
                Style.SECTION -> c.drawText(l.left, MARGIN, base, paint(9f, true, GRAY))
                Style.EVENT -> {
                    c.drawRect(MARGIN, y + 4f, MARGIN + 3f, y + 17f, paint(1f))
                    c.drawText(l.right, MARGIN + 10f, base, bold)
                    c.drawText(ellipsize(l.left, body, right - (MARGIN + 100f)), MARGIN + 100f, base, body)
                }
                Style.TODO, Style.TODO_DONE -> {
                    val box = RectF(MARGIN, y + 4f, MARGIN + 11f, y + 15f)
                    val stroke = paint(1f).apply { style = Paint.Style.STROKE; strokeWidth = 1f }
                    c.drawRect(box, stroke)
                    if (l.style == Style.TODO_DONE) {
                        c.drawLine(box.left + 2f, box.centerY(), box.left + 5f, box.bottom - 2f, stroke)
                        c.drawLine(box.left + 5f, box.bottom - 2f, box.right - 1.5f, box.top + 2f, stroke)
                    }
                    val rightW = if (l.right.isBlank()) 0f else minOf(gray.measureText(l.right), (right - MARGIN) * 0.4f)
                    val titleMax = right - (MARGIN + 20f) - rightW - 10f
                    val tp = if (l.style == Style.TODO_DONE) paint(11f, color = GRAY).apply { isStrikeThruText = true } else body
                    c.drawText(ellipsize(l.left, tp, titleMax), MARGIN + 20f, base, tp)
                    if (l.right.isNotBlank()) {
                        val t = ellipsize(l.right, gray, rightW)
                        c.drawText(t, right - gray.measureText(t), base, gray)
                    }
                }
                Style.DEADLINE -> {
                    c.drawText("!", MARGIN + 3f, base, bold)
                    c.drawText(ellipsize(l.left, body, right - MARGIN - 20f), MARGIN + 20f, base, body)
                }
                Style.NOTE -> {
                    if (l.left.startsWith("___")) c.drawLine(MARGIN, y + 16f, right, y + 16f, paint(1f, color = LIGHT).apply { strokeWidth = 0.6f })
                    else c.drawText(ellipsize(l.left, gray, right - MARGIN), MARGIN, base, gray)
                }
                Style.HEADER, Style.SPACER -> Unit
            }
            y += LINE_H
        }
    }

    // ------------------------------------------------------------------ Week page

    private class WeekPage(val data: AppData, val start: LocalDate, val w: Int, val h: Int) : Page {
        override fun draw(c: Canvas) {
            val end = start.plusDays(6)
            val events = Planner.eventsInRange(data.events, data.exceptions, start, end).groupBy { it.date }
            val deadlines = Planner.deadlinesInRange(data.tasks, start, end)
            val right = w - MARGIN

            c.drawText("WEEK OVERVIEW", MARGIN, MARGIN + 10f, paint(9f, true, GRAY))
            val title = "Week ${start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${start.format(SHORT)} – ${end.format(SHORT)} ${end.year}"
            c.drawText(title, MARGIN, MARGIN + 32f, paint(18f, true))

            val top = MARGIN + 48f
            val bottom = h - MARGIN
            val colW = (right - MARGIN) / 7f
            val thin = paint(1f).apply { strokeWidth = 0.8f; style = Paint.Style.STROKE }
            val light = paint(1f, color = LIGHT).apply { strokeWidth = 0.5f }
            c.drawRect(MARGIN, top, right, bottom, thin)

            val head = paint(10f, true)
            val small = paint(8f)
            val smallBold = paint(8f, true)
            val gray = paint(7.5f, color = GRAY)
            val lineH = 12f
            for (i in 0..6) {
                val d = start.plusDays(i.toLong())
                val x = MARGIN + i * colW
                if (i > 0) c.drawLine(x, top, x, bottom, thin)
                c.drawText(d.format(WEEKDAY), x + 5f, top + 15f, head)
                c.drawLine(x, top + 22f, x + colW, top + 22f, light)
                var y = top + 34f
                val maxY = bottom - 6f
                val textW = colW - 10f

                fun line(text: String, p: Paint, indent: Float = 0f) {
                    if (y > maxY) return
                    c.drawText(ellipsize(text, p, textW - indent), x + 5f + indent, y, p)
                    y += lineH
                }

                val dayEvents = events[d].orEmpty()
                for (e in dayEvents) {
                    line(e.start.format(TIME) + "–" + e.end.format(TIME), smallBold)
                    line(e.title, small, 4f)
                }
                if (dayEvents.isNotEmpty()) y += 4f
                for (t in deadlines[d].orEmpty()) line("! " + t.title, smallBold)
                val items = Planner.dayItems(data, d)
                if (items.isNotEmpty() && y <= maxY) {
                    c.drawText("TO-DO", x + 5f, y, gray); y += lineH
                }
                for (it in items) {
                    if (y > maxY) break
                    val box = RectF(x + 5f, y - 7f, x + 12f, y)
                    c.drawRect(box, thin)
                    if (it.done) c.drawLine(box.left + 1.5f, box.bottom - 1.5f, box.right - 1.5f, box.top + 1.5f, thin)
                    val p = if (it.done || it.skipped) paint(8f, color = GRAY).apply { isStrikeThruText = true } else small
                    c.drawText(ellipsize(it.title, p, textW - 12f), x + 17f, y, p)
                    y += lineH
                }
                val hidden = (events[d].orEmpty().size + items.size)
                if (y > maxY && hidden > 0) c.drawText("…", x + colW - 12f, bottom - 6f, small)
            }
        }
    }

    // ------------------------------------------------------------------ Print plumbing

    private fun print(context: Context, jobName: String, landscape: Boolean, layout: (Int, Int) -> List<Page>) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val media = if (landscape) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4
        val attrs = PrintAttributes.Builder()
            .setMediaSize(media)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        pm.print(jobName, Adapter(jobName, landscape, layout), attrs)
    }

    private class Adapter(
        private val name: String,
        private val forceLandscape: Boolean,
        private val layout: (Int, Int) -> List<Page>,
    ) : PrintDocumentAdapter() {
        private var pages: List<Page> = emptyList()
        private var pageW = 595
        private var pageH = 842

        override fun onLayout(
            oldAttributes: PrintAttributes?, newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
            val media = newAttributes.mediaSize ?: PrintAttributes.MediaSize.ISO_A4
            // 1 inch = 72 PostScript points; media size is in mils (1/1000 inch).
            var w = (media.widthMils / 1000f * 72f).toInt()
            var h = (media.heightMils / 1000f * 72f).toInt()
            if (forceLandscape && h > w) { val t = w; w = h; h = t }
            if (!forceLandscape && w > h) { val t = w; w = h; h = t }
            pageW = w; pageH = h
            pages = layout(w, h)
            val info = PrintDocumentInfo.Builder("$name.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pages.size)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pageRanges: Array<out PageRange>?, destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?, callback: WriteResultCallback,
        ) {
            val doc = PdfDocument()
            try {
                pages.forEachIndexed { i, p ->
                    if (cancellationSignal?.isCanceled == true) { callback.onWriteCancelled(); return }
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    p.draw(page.canvas)
                    doc.finishPage(page)
                }
                FileOutputStream(destination.fileDescriptor).use { doc.writeTo(it) }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            } finally {
                doc.close()
            }
        }
    }
}
