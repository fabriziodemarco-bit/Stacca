package com.stacca.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.appbar.MaterialToolbar
import com.stacca.app.R
import com.stacca.app.data.HistoryStore
import com.stacca.app.data.PreferencesManager
import com.stacca.app.util.PreviewMode
import com.stacca.app.util.SystemBarsHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Storico degli stacchi.
 * Gratis: serie attuale e ultimi 7 giorni.
 * Premium (o prova gratuita): anche record, tempo non vissuto del mese, grafico della settimana
 * e tutto l'elenco.
 */
class HistoryActivity : AppCompatActivity() {

    companion object {
        private const val LIST_WORKDAYS = 7   // elenco: le ultime 7 giornate registrate, per tutti
        private const val MAX_MONTHS = 12
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }

    private lateinit var prefs: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        window.setBackgroundDrawableResource(R.color.home_bg)
        SystemBarsHelper.applyInsets(this)

        prefs = PreferencesManager(this)
        findViewById<MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { finish() }
        findViewById<View>(R.id.cardPremiumTeaser).setOnClickListener {
            startActivity(Intent(this, PaywallActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Ridisegna ogni volta: dopo un acquisto dalla paywall si vede tutto subito
        render()
    }

    private fun render() {
        // In anteprima (solo versione di prova): 15 giorni inventati, lo storico vero non si tocca
        val preview = PreviewMode.isOn(intent)
        val entries = if (preview) sampleEntries() else HistoryStore(this).all()
        val premium = preview || prefs.hasFullAccess
        val now = System.currentTimeMillis()

        renderTitle(entries)
        renderStats(entries, premium, now)

        findViewById<View>(R.id.sectionChart).visibility = if (premium) View.VISIBLE else View.GONE
        findViewById<View>(R.id.sectionMonths).visibility =
            if (premium && entries.isNotEmpty()) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardPremiumTeaser).visibility = if (premium) View.GONE else View.VISIBLE
        if (premium) {
            renderWeekBars(entries)
            renderMonths(entries)
        }

        renderList(entries.take(LIST_WORKDAYS))
    }

    // ------------------------------------------------------------------

    private fun renderTitle(entries: List<HistoryStore.Entry>) {
        val tvTitle = findViewById<TextView>(R.id.tvHistoryTitle)
        val tvSub = findViewById<TextView>(R.id.tvHistorySub)
        if (entries.isEmpty()) {
            tvTitle.setText(R.string.history_empty_title)
            tvSub.setText(R.string.history_empty_sub)
            return
        }
        val weekStart = System.currentTimeMillis() - 7 * DAY_MILLIS
        val thisWeek = entries.filter { it.timestampMillis >= weekStart }
        tvTitle.text = if (thisWeek.isEmpty()) {
            getString(R.string.history_week_none)
        } else {
            getString(R.string.history_week_title, thisWeek.count { it.isOnTime }, thisWeek.size)
        }
        tvSub.text = getString(R.string.history_sub, PreferencesManager.ON_TIME_THRESHOLD_MINUTES)
    }

    private fun renderStats(entries: List<HistoryStore.Entry>, premium: Boolean, now: Long) {
        val preview = PreviewMode.isOn(intent)
        val streak = if (preview) entries.takeWhile { it.isOnTime }.size else prefs.streakCount
        val record = if (preview) 6 else prefs.bestStreak
        findViewById<TextView>(R.id.tvStreak).text = days(streak)
        findViewById<TextView>(R.id.tvRecord).text = if (premium) days(record) else "🔒"
        findViewById<TextView>(R.id.tvLost).text = if (premium) {
            // Regalato di questo mese (dal giorno 1): coincide con la prima riga del "mese per mese"
            val thisMonth = monthKey(now)
            val minutes = entries
                .filter { monthKey(it.timestampMillis) == thisMonth }
                .sumOf { it.overtimeMinutes }
            formatMinutes(minutes)
        } else {
            "🔒"
        }
    }

    /** Sette colonne (ultimi 7 giorni, oggi a destra): più alta = più ritardo; arancione se in ritardo, grigia se in orario. */
    private fun renderWeekBars(entries: List<HistoryStore.Entry>) {
        val container = findViewById<LinearLayout>(R.id.weekBars)
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val maxBarPx = (80 * density).toInt()
        val minBarPx = (6 * density).toInt()

        // Si parte da 6 giorni fa, a mezzanotte
        val day = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -6)
        }
        val dayLetter = SimpleDateFormat("EEEEE", Locale.getDefault())
        val days = (0 until 7).map {
            val start = day.timeInMillis
            val label = dayLetter.format(day.time).uppercase()
            day.add(Calendar.DAY_OF_YEAR, 1)
            val dayEntries = entries.filter { e -> e.timestampMillis >= start && e.timestampMillis < day.timeInMillis }
            Triple(label, dayEntries.sumOf { e -> e.overtimeMinutes }, dayEntries)
        }
        val maxMinutes = maxOf(30, days.maxOf { it.second })

        days.forEach { (label, minutes, dayEntries) ->
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            }
            val late = dayEntries.any { !it.isOnTime }
            val color = when {
                dayEntries.isEmpty() -> R.color.home_border
                late -> R.color.home_accent
                else -> R.color.home_success
            }
            val height = if (dayEntries.isEmpty() || !late) minBarPx
            else maxOf(minBarPx, maxBarPx * minutes / maxMinutes)
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams((14 * density).toInt(), height)
                setBackgroundResource(R.drawable.bg_segment)
                background.mutate().setTint(ContextCompat.getColor(this@HistoryActivity, color))
            }
            val tvLabel = TextView(this).apply {
                text = label
                textSize = 12f
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_text_secondary))
                gravity = Gravity.CENTER
                setPadding(0, (8 * density).toInt(), 0, 0)
            }
            column.addView(bar)
            column.addView(tvLabel)
            container.addView(column)
        }
    }

    /**
     * Una riga per mese (il più recente in alto): nome, quanti in orario, una barretta verde/arancio
     * e il tempo regalato. Sul mese più recente, il confronto con il mese prima.
     * Il confronto è sulla media al giorno, così un mese appena iniziato non vince "per forza".
     */
    private fun renderMonths(entries: List<HistoryStore.Entry>) {
        val list = findViewById<LinearLayout>(R.id.monthsList)
        list.removeAllViews()
        val density = resources.displayMetrics.density

        val months = entries.groupBy { monthKey(it.timestampMillis) }
            .toList()
            .sortedByDescending { it.first }
            .take(MAX_MONTHS)

        months.forEachIndexed { index, (key, monthEntries) ->
            if (index > 0) {
                list.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = (20 * density).toInt()
                    }
                    setBackgroundColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_border))
                })
            }
            // Confronto solo se il mese prima c'è ed è proprio quello precedente
            val previous = months.getOrNull(index + 1)?.takeIf { index == 0 && it.first == key - 1 }
            list.addView(buildMonthRow(key, monthEntries, previous, density))
        }
    }

    private fun buildMonthRow(
        key: Int,
        monthEntries: List<HistoryStore.Entry>,
        previous: Pair<Int, List<HistoryStore.Entry>>?,
        density: Float
    ): View {
        val pad = (20 * density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (14 * density).toInt(), pad, (14 * density).toInt())
        }
        val onTime = monthEntries.count { it.isOnTime }
        val gifted = monthEntries.sumOf { it.overtimeMinutes }

        // Riga 1: mese a sinistra, "15 su 18 in orario" a destra
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = monthName(key).replaceFirstChar { it.uppercase() }
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_text))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = getString(R.string.history_month_ontime, onTime, monthEntries.size)
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_text_secondary))
        })
        row.addView(top)

        // Barretta: verde per i giorni in orario, arancio per gli altri
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (6 * density).toInt()).apply {
                topMargin = (10 * density).toInt()
            }
        }
        val late = monthEntries.size - onTime
        if (onTime > 0) bar.addView(barPiece(onTime, R.color.home_success, density, late > 0))
        if (late > 0) bar.addView(barPiece(late, R.color.home_accent, density, false))
        row.addView(bar)

        // Riga 3: tempo regalato
        row.addView(TextView(this).apply {
            text = getString(R.string.history_month_gifted, formatMinutes(gifted))
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_text_secondary))
            setPadding(0, (8 * density).toInt(), 0, 0)
        })

        // Riga 4 (solo mese più recente): confronto con il mese prima, media al giorno
        if (previous != null) {
            val avgNow = gifted / monthEntries.size
            val avgBefore = previous.second.sumOf { it.overtimeMinutes } / previous.second.size
            val diff = avgNow - avgBefore
            val prevName = monthName(previous.first, inSentence = true)
            row.addView(TextView(this).apply {
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, (6 * density).toInt(), 0, 0)
                when {
                    diff < 0 -> {
                        text = getString(R.string.history_month_better, formatMinutes(-diff), prevName)
                        setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_success))
                    }
                    diff > 0 -> {
                        text = getString(R.string.history_month_worse, formatMinutes(diff), prevName)
                        setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_accent))
                    }
                    else -> {
                        text = getString(R.string.history_month_same, prevName)
                        setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_text_secondary))
                    }
                }
            })
        }
        return row
    }

    private fun barPiece(weight: Int, colorRes: Int, density: Float, gapAfter: Boolean): View =
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight.toFloat()).apply {
                if (gapAfter) marginEnd = (3 * density).toInt()
            }
            setBackgroundResource(R.drawable.bg_segment)
            background.mutate().setTint(ContextCompat.getColor(this@HistoryActivity, colorRes))
        }

    /** Mese come numero unico (anno * 12 + mese): così "il mese prima" è semplicemente key - 1. */
    private fun monthKey(millis: Long): Int {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    /** Nome del mese nella lingua del telefono. Dentro una frase: "di settembre" (minuscolo in italiano). */
    private fun monthName(key: Int, inSentence: Boolean = false): String {
        val c = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.YEAR, key / 12)
            set(Calendar.MONTH, key % 12)
        }
        val name = SimpleDateFormat("LLLL", Locale.getDefault()).format(c.time)
        // Alcuni telefoni scrivono "Settembre" anche dentro la frase: in italiano va minuscolo
        return if (inSentence && Locale.getDefault().language == "it") name.lowercase(Locale.ITALIAN) else name
    }

    private fun renderList(entries: List<HistoryStore.Entry>) {
        val section = findViewById<View>(R.id.sectionList)
        val list = findViewById<LinearLayout>(R.id.historyList)
        list.removeAllViews()
        if (entries.isEmpty()) {
            section.visibility = View.GONE
            return
        }
        section.visibility = View.VISIBLE

        val density = resources.displayMetrics.density
        val dateFormat = SimpleDateFormat("EEE d MMM", Locale.getDefault())
        entries.forEachIndexed { index, entry ->
            if (index > 0) {
                list.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                        marginStart = (20 * density).toInt()
                    }
                    setBackgroundColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_border))
                })
            }
            list.addView(buildRow(entry, dateFormat, density))
        }
    }

    private fun buildRow(entry: HistoryStore.Entry, dateFormat: SimpleDateFormat, density: Float): View {
        val pad = (20 * density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(pad, (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = dateFormat.format(entry.timestampMillis).replaceFirstChar { it.uppercase() }
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_text))
        })
        texts.addView(TextView(this).apply {
            text = getString(
                R.string.history_row_end,
                String.format("%02d:%02d", entry.endHour, entry.endMinute)
            )
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_text_secondary))
        })
        val status = TextView(this).apply {
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            if (entry.unclosed) {
                setText(R.string.history_row_unclosed)
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_accent))
            } else if (entry.isOnTime) {
                setText(R.string.history_row_ontime)
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_success))
            } else {
                text = getString(R.string.history_row_late, entry.overtimeMinutes, entry.level)
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_accent))
            }
        }
        row.addView(texts)
        row.addView(status)
        return row
    }

    // ------------------------------------------------------------------

    /**
     * Solo anteprima: 2 mesi di giorni lavorativi inventati (fine turno 18:00), dal più recente.
     * Il secondo mese va meglio del primo, per vedere come si legge un miglioramento.
     */
    private fun sampleEntries(): List<HistoryStore.Entry> {
        val overtimes = listOf(
            0, 0, 3, -1, 8, 0, 0, 0, 12, 0, 2, 0, 0, 27, 0, 0, 5, 0, 0, 15, 0, 0, // ultimo mese (-1 = non chiuso)
            0, 35, 12, 0, 48, 20, 0, 27, 55, 0, 15, 40, 0, 22, 30, 0, 0, 18, 62, 0, 10 // mese prima
        )
        val result = mutableListOf<HistoryStore.Entry>()
        val day = Calendar.getInstance()
        var i = 0
        while (result.size < overtimes.size) {
            val weekday = day.get(Calendar.DAY_OF_WEEK)
            if (weekday != Calendar.SATURDAY && weekday != Calendar.SUNDAY) {
                val unclosed = overtimes[i] < 0
                val overtime = overtimes[i++].coerceAtLeast(0)
                val stacco = (day.clone() as Calendar).apply {
                    set(Calendar.HOUR_OF_DAY, 18)
                    set(Calendar.MINUTE, 0)
                    add(Calendar.MINUTE, overtime)
                }
                result += HistoryStore.Entry(
                    timestampMillis = stacco.timeInMillis,
                    endHour = 18,
                    endMinute = 0,
                    overtimeMinutes = overtime,
                    level = if (unclosed) 6 else (overtime / 10 + if (overtime > 5) 1 else 0).coerceAtMost(6),
                    unclosed = unclosed
                )
            }
            day.add(Calendar.DAY_OF_YEAR, -1)
        }
        return result
    }

    private fun days(count: Int): String =
        resources.getQuantityString(R.plurals.history_days, count, count)

    /** "26 minuti" oppure "1 h 05 min" (stessi testi della home). */
    private fun formatMinutes(totalMinutes: Int): String {
        val minutes = totalMinutes.coerceAtLeast(0)
        if (minutes < 60) {
            return resources.getQuantityString(R.plurals.home_minutes, minutes, minutes)
        }
        return getString(R.string.home_hours_minutes, minutes / 60, minutes % 60)
    }
}
