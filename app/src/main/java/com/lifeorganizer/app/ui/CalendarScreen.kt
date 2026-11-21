package com.lifeorganizer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifeorganizer.app.data.AppData
import com.lifeorganizer.app.domain.DayItem
import com.lifeorganizer.app.domain.EventInstance
import com.lifeorganizer.app.domain.Planner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs

fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun calendarTitle(mode: CalendarMode, d: LocalDate): String = when (mode) {
    CalendarMode.YEAR -> d.year.toString()
    CalendarMode.MONTH -> d.format(Fmt.monthYear)
    CalendarMode.WEEK -> {
        val s = mondayOf(d)
        val e = s.plusDays(6)
        val range = if (s.month == e.month) "${s.dayOfMonth}–${e.dayOfMonth} ${e.format(Fmt.monthName).take(3)}"
        else "${s.format(Fmt.dayMonth)} – ${e.format(Fmt.dayMonth)}"
        "$range · W${s.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}"
    }
    CalendarMode.DAY -> d.format(Fmt.dayMedium)
}

fun shiftDate(mode: CalendarMode, d: LocalDate, delta: Long): LocalDate = when (mode) {
    CalendarMode.YEAR -> d.plusYears(delta)
    CalendarMode.MONTH -> d.plusMonths(delta)
    CalendarMode.WEEK -> d.plusWeeks(delta)
    CalendarMode.DAY -> d.plusDays(delta)
}

/** Horizontal swipe to go to the previous / next period. */
fun Modifier.swipeToShift(onShift: (Long) -> Unit): Modifier = this.pointerInput(Unit) {
    var total = 0f
    detectHorizontalDragGestures(
        onDragStart = { total = 0f },
        onDragEnd = {
            if (abs(total) > 140f) onShift(if (total < 0) 1 else -1)
            total = 0f
        },
        onHorizontalDrag = { change, amount -> total += amount; change.consume() },
    )
}

@Composable
fun CalendarContent(vm: MainViewModel, data: AppData) {
    when (vm.calendarMode) {
        CalendarMode.YEAR -> YearView(vm, data)
        CalendarMode.MONTH -> MonthView(vm, data)
        CalendarMode.WEEK -> WeekView(vm, data)
        CalendarMode.DAY -> DayView(vm, data)
    }
}

// ============================================================== Year

@Composable
private fun YearView(vm: MainViewModel, data: AppData) {
    val year = vm.selectedDate.year
    val from = LocalDate.of(year, 1, 1)
    val to = LocalDate.of(year, 12, 31)
    val busy: Set<LocalDate> = remember(data, year) {
        val s = mutableSetOf<LocalDate>()
        Planner.eventsInRange(data.events, data.exceptions, from, to).forEach { s += it.date }
        s += Planner.dayItemsInRange(data, from, to).keys
        s += Planner.deadlinesInRange(data.tasks, from, to).keys
        s
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().swipeToShift { vm.selectedDate = shiftDate(CalendarMode.YEAR, vm.selectedDate, it) },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 120.dp),
    ) {
        items((1..12).toList()) { m ->
            MiniMonth(YearMonth.of(year, m), vm.today, busy,
                onMonth = { vm.selectedDate = it.atDay(1); vm.calendarMode = CalendarMode.MONTH },
                onDay = { vm.openDay(it) })
        }
    }
}

@Composable
private fun MiniMonth(ym: YearMonth, today: LocalDate, busy: Set<LocalDate>, onMonth: (YearMonth) -> Unit, onDay: (LocalDate) -> Unit) {
    Column {
        Text(
            ym.atDay(1).format(Fmt.monthName),
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
            color = if (YearMonth.from(today) == ym) Mono.Black else Mono.G800,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).clickable { onMonth(ym) }.padding(vertical = 4.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            for (w in listOf("M", "T", "W", "T", "F", "S", "S")) {
                Text(w, Modifier.weight(1f), fontSize = 8.sp, color = Mono.G400, textAlign = TextAlign.Center)
            }
        }
        val offset = ym.atDay(1).dayOfWeek.value - 1
        val len = ym.lengthOfMonth()
        val cells = offset + len
        val rows = (cells + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - offset + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (idx in 1..len) {
                            val d = ym.atDay(idx)
                            val isToday = d == today
                            Box(
                                Modifier.fillMaxSize().padding(1.dp).clip(CircleShape)
                                    .background(if (isToday) Mono.Black else Mono.White)
                                    .clickable { onDay(d) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    idx.toString(), fontSize = 9.sp,
                                    fontWeight = if (d in busy) FontWeight.Bold else FontWeight.Normal,
                                    color = when { isToday -> Mono.White; d in busy -> Mono.Black; else -> Mono.G500 },
                                    textDecoration = if (d in busy && !isToday) TextDecoration.Underline else null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================== Month

private sealed interface CellEntry {
    data class Ev(val e: EventInstance) : CellEntry
    data class Td(val item: DayItem) : CellEntry
    data class Dl(val title: String) : CellEntry
}

@Composable
private fun MonthView(vm: MainViewModel, data: AppData) {
    val ym = YearMonth.from(vm.selectedDate)
    val gridStart = mondayOf(ym.atDay(1))
    val weeks = ((ChronoUnit.DAYS.between(gridStart, ym.atEndOfMonth()) / 7) + 1).toInt()
    val gridEnd = gridStart.plusDays(weeks * 7L - 1)
    val entries: Map<LocalDate, List<CellEntry>> = remember(data, gridStart, weeks) {
        val map = mutableMapOf<LocalDate, MutableList<CellEntry>>()
        Planner.eventsInRange(data.events, data.exceptions, gridStart, gridEnd)
            .forEach { map.getOrPut(it.date) { mutableListOf() } += CellEntry.Ev(it) }
        Planner.deadlinesInRange(data.tasks, gridStart, gridEnd)
            .forEach { (d, ts) -> ts.forEach { map.getOrPut(d) { mutableListOf() } += CellEntry.Dl(it.title) } }
        Planner.dayItemsInRange(data, gridStart, gridEnd)
            .forEach { (d, items) -> items.forEach { map.getOrPut(d) { mutableListOf() } += CellEntry.Td(it) } }
        map
    }

    Column(Modifier.fillMaxSize().swipeToShift { vm.selectedDate = shiftDate(CalendarMode.MONTH, vm.selectedDate, it) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)) {
            for (w in listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) {
                Text(w, Modifier.weight(1f), fontSize = 10.sp, color = Mono.G500, textAlign = TextAlign.Center)
            }
        }
        ThinDivider()
        Column(Modifier.weight(1f)) {
            for (w in 0 until weeks) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (c in 0 until 7) {
                        val d = gridStart.plusDays(w * 7L + c)
                        MonthCell(
                            date = d,
                            inMonth = d.month == ym.month,
                            isToday = d == vm.today,
                            entries = entries[d].orEmpty(),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { vm.openDay(d) },
                        )
                    }
                }
            }
        }
        // Legend
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem("Event") { Box(Modifier.size(10.dp, 8.dp).clip(RoundedCornerShape(2.dp)).background(Mono.Ink)) }
            LegendItem("To-do") { Text("○", fontSize = 10.sp, color = Mono.G600) }
            LegendItem("Deadline") { Box(Modifier.size(10.dp, 8.dp).border(0.8.dp, Mono.Ink, RoundedCornerShape(2.dp))) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun LegendItem(label: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 10.sp, color = Mono.G500)
    }
}

@Composable
private fun MonthCell(date: LocalDate, inMonth: Boolean, isToday: Boolean, entries: List<CellEntry>, modifier: Modifier, onClick: () -> Unit) {
    BoxWithConstraints(
        modifier
            .drawBehind {
                val stroke = 0.5.dp.toPx()
                drawLine(Mono.G200, Offset(0f, size.height), Offset(size.width, size.height), stroke)
                drawLine(Mono.G200, Offset(size.width, 0f), Offset(size.width, size.height), stroke)
            }
            .background(if (inMonth) Mono.White else Mono.G50)
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        val lineH = 15.dp
        val capacity = ((maxHeight - 22.dp) / lineH).toInt().coerceAtLeast(0)
        Column {
            Box(Modifier.fillMaxWidth().height(20.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(20.dp).clip(CircleShape).background(if (isToday) Mono.Black else Mono.White.copy(alpha = 0f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        date.dayOfMonth.toString(), fontSize = 11.sp,
                        fontWeight = if (isToday || entries.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                        color = when { isToday -> Mono.White; inMonth -> Mono.Ink; else -> Mono.G400 },
                    )
                }
            }
            val shown = if (entries.size > capacity) (capacity - 1).coerceAtLeast(0) else entries.size
            for (e in entries.take(shown)) {
                Box(Modifier.height(lineH).fillMaxWidth().padding(vertical = 1.dp), contentAlignment = Alignment.CenterStart) { CellLine(e) }
            }
            if (entries.size > shown) {
                Text("+${entries.size - shown}", fontSize = 9.sp, color = Mono.G600, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 2.dp))
            }
        }
    }
}

private val CellText = androidx.compose.ui.text.TextStyle(
    fontSize = 9.sp,
    lineHeight = 12.sp,
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
    ),
)

@Composable
private fun CellLine(e: CellEntry) {
    val shape = RoundedCornerShape(2.dp)
    when (e) {
        is CellEntry.Ev -> Text(
            e.e.title, style = CellText, color = Mono.White, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxSize().clip(shape).background(Mono.Ink).padding(horizontal = 2.dp),
        )
        is CellEntry.Dl -> Text(
            e.title, style = CellText, color = Mono.Ink, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxSize().border(0.7.dp, Mono.Ink, shape).padding(horizontal = 2.dp),
        )
        is CellEntry.Td -> Text(
            (if (e.item.done) "● " else "○ ") + e.item.title, style = CellText, softWrap = false,
            color = if (e.item.done || e.item.skipped) Mono.G400 else Mono.G800, maxLines = 1, overflow = TextOverflow.Clip,
            textDecoration = if (e.item.done || e.item.skipped) TextDecoration.LineThrough else null,
            modifier = Modifier.fillMaxSize().padding(horizontal = 1.dp),
        )
    }
}

// ============================================================== Week

private val WEEK_HOUR = 44.dp

@Composable
private fun WeekView(vm: MainViewModel, data: AppData) {
    val start = mondayOf(vm.selectedDate)
    val end = start.plusDays(6)
    val days = (0..6).map { start.plusDays(it.toLong()) }
    val events = remember(data, start) { Planner.eventsInRange(data.events, data.exceptions, start, end).groupBy { it.date } }
    val todos = remember(data, start) { Planner.dayItemsInRange(data, start, end) }
    val deadlines = remember(data, start) { Planner.deadlinesInRange(data.tasks, start, end) }
    val density = LocalDensity.current
    val scroll = rememberScrollState(initial = with(density) { (WEEK_HOUR * 7).roundToPx() })

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            Spacer(Modifier.width(34.dp))
            for (d in days) {
                val isToday = d == vm.today
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(6.dp)).clickable { vm.openDay(d) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(d.format(Fmt.weekdayShort), fontSize = 10.sp, color = Mono.G500)
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (isToday) Mono.Black else Mono.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.dayOfMonth.toString(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isToday) Mono.White else Mono.Ink)
                    }
                    val open = todos[d].orEmpty().count { !it.done && !it.skipped }
                    val total = todos[d].orEmpty().size
                    Text(if (total > 0) "○ $open/$total" else " ", fontSize = 9.sp, color = Mono.G600)
                    val dl = deadlines[d].orEmpty().size
                    if (dl > 0) {
                        Text(
                            "! $dl", fontSize = 9.sp, color = Mono.Ink, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.border(0.7.dp, Mono.Ink, RoundedCornerShape(2.dp)).padding(horizontal = 3.dp),
                        )
                    } else {
                        Text(" ", fontSize = 9.sp)
                    }
                }
            }
        }
        ThinDivider()
        Row(Modifier.fillMaxWidth().weight(1f).verticalScroll(scroll)) {
            Column(Modifier.width(34.dp)) {
                for (h in 0..23) {
                    Box(Modifier.height(WEEK_HOUR).fillMaxWidth()) {
                        if (h > 0) Text("%02d".format(h), fontSize = 9.sp, color = Mono.G400, modifier = Modifier.offset(x = 8.dp, y = (-6).dp))
                    }
                }
            }
            for (d in days) {
                TimeColumn(
                    events = events[d].orEmpty(),
                    hourHeight = WEEK_HOUR,
                    fromHour = 0,
                    toHour = 24,
                    compact = true,
                    highlight = d == vm.today,
                    modifier = Modifier.weight(1f),
                    onEvent = { vm.navigate(Screen.EditEvent(id = it.event.id, occurrenceDate = if (it.recurring) it.originalDate else null)) },
                    onEmpty = { t -> vm.navigate(Screen.EditEvent(date = d, start = t)) },
                )
            }
        }
    }
}

/**
 * A vertical time grid for one day with positioned event blocks. Used by both week and day view.
 * Tapping empty space calls [onEmpty] with the tapped full hour.
 */
@Composable
fun TimeColumn(
    events: List<EventInstance>,
    hourHeight: Dp,
    fromHour: Int,
    toHour: Int,
    compact: Boolean,
    highlight: Boolean,
    modifier: Modifier = Modifier,
    onEvent: (EventInstance) -> Unit,
    onEmpty: (LocalTime) -> Unit,
) {
    val lanes = remember(events) { Planner.lanes(events) }
    val currentOnEmpty by rememberUpdatedState(onEmpty)
    val density = LocalDensity.current
    val hourPx = with(density) { hourHeight.toPx() }
    BoxWithConstraints(
        modifier
            .height(hourHeight * (toHour - fromHour))
            .background(if (highlight && compact) Mono.G50 else Mono.White)
            .drawBehind {
                val stroke = 0.5.dp.toPx()
                for (h in 0..(toHour - fromHour)) {
                    val y = h * hourPx
                    drawLine(Mono.G200, Offset(0f, y), Offset(size.width, y), stroke)
                }
                drawLine(Mono.G200, Offset(0f, 0f), Offset(0f, size.height), stroke)
            }
            .pointerInput(fromHour, hourPx) {
                detectTapGestures { pos ->
                    val hour = (fromHour + (pos.y / hourPx).toInt()).coerceIn(0, 23)
                    currentOnEmpty(LocalTime.of(hour, 0))
                }
            },
    ) {
        val colWidth = maxWidth
        for (e in events) {
            val startMin = (e.start.hour * 60 + e.start.minute - fromHour * 60).coerceAtLeast(0)
            val endMinRaw = if (e.end > e.start) e.end.hour * 60 + e.end.minute else e.start.hour * 60 + e.start.minute + 15
            val endMin = (endMinRaw - fromHour * 60).coerceAtMost((toHour - fromHour) * 60)
            if (endMin <= 0 || startMin >= (toHour - fromHour) * 60) continue
            val (lane, count) = lanes[e.key] ?: (0 to 1)
            val w = colWidth / count
            val top = hourHeight * (startMin / 60f)
            val h = (hourHeight * ((endMin - startMin) / 60f)).coerceAtLeast(if (compact) 16.dp else 22.dp)
            Box(
                Modifier
                    .offset(x = w * lane, y = top)
                    .width(w)
                    .height(h)
                    .padding(horizontal = 1.dp, vertical = 1.dp)
                    .clip(RoundedCornerShape(if (compact) 3.dp else 6.dp))
                    .background(Mono.Ink)
                    .clickable { onEvent(e) }
                    .padding(horizontal = if (compact) 2.dp else 8.dp, vertical = if (compact) 1.dp else 4.dp),
            ) {
                if (compact) {
                    Text(e.title, fontSize = 9.sp, lineHeight = 10.sp, color = Mono.White, overflow = TextOverflow.Clip)
                } else if (h < 50.dp) {
                    Text(
                        e.title + "  " + Fmt.time(e.start) + "–" + Fmt.time(e.end),
                        fontSize = 12.sp, color = Mono.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Column {
                        Text(e.title, fontSize = 13.sp, color = Mono.White, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            Fmt.time(e.start) + "–" + Fmt.time(e.end) + if (e.location.isNotBlank()) " · " + e.location else "",
                            fontSize = 11.sp, color = Mono.G300, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
