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
        private const val FREE_DAYS = 7
        private const val MAX_ROWS = 90
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
        val entries = HistoryStore(this).all()
        val premium = prefs.hasFullAccess
        val now = System.currentTimeMillis()

        renderTitle(entries)
        renderStats(entries, premium, now)

        findViewById<View>(R.id.sectionChart).visibility = if (premium) View.VISIBLE else View.GONE
        findViewById<View>(R.id.cardPremiumTeaser).visibility = if (premium) View.GONE else View.VISIBLE
        if (premium) renderWeekBars(entries)

        val visible = if (premium) {
            entries.take(MAX_ROWS)
        } else {
            entries.filter { now - it.timestampMillis < FREE_DAYS * DAY_MILLIS }
        }
        renderList(visible)
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
        val weekStart = startOfWeek().timeInMillis
        val thisWeek = entries.filter { it.timestampMillis >= weekStart }
        tvTitle.text = if (thisWeek.isEmpty()) {
            getString(R.string.history_week_none)
        } else {
            getString(R.string.history_week_title, thisWeek.count { it.isOnTime }, thisWeek.size)
        }
        tvSub.text = getString(R.string.history_sub, PreferencesManager.ON_TIME_THRESHOLD_MINUTES)
    }

    private fun renderStats(entries: List<HistoryStore.Entry>, premium: Boolean, now: Long) {
        findViewById<TextView>(R.id.tvStreak).text = days(prefs.streakCount)
        findViewById<TextView>(R.id.tvRecord).text = if (premium) days(prefs.bestStreak) else "🔒"
        findViewById<TextView>(R.id.tvLost).text = if (premium) {
            val minutes = entries
                .filter { now - it.timestampMillis < 30 * DAY_MILLIS }
                .sumOf { it.overtimeMinutes }
            formatMinutes(minutes)
        } else {
            "🔒"
        }
    }

    /** Sette colonne (lun-dom): più alta = più ritardo; arancione se in ritardo, grigia se in orario. */
    private fun renderWeekBars(entries: List<HistoryStore.Entry>) {
        val container = findViewById<LinearLayout>(R.id.weekBars)
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val maxBarPx = (80 * density).toInt()
        val minBarPx = (6 * density).toInt()

        val day = startOfWeek()
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
                else -> R.color.home_text_secondary
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
            if (entry.isOnTime) {
                setText(R.string.history_row_ontime)
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_text_secondary))
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

    /** Lunedì di questa settimana, a mezzanotte. */
    private fun startOfWeek(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        // Giorni passati da lunedì (lun = 0 ... dom = 6)
        val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) + 5) % 7
        add(Calendar.DAY_OF_YEAR, -daysSinceMonday)
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
