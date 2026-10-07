package com.stacca.app.ui

import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
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
 * Gratis: serie attuale e, nel "Lo sapevi?", a che ora stacchi davvero.
 * Premium (o prova gratuita): anche record, tempo non vissuto del mese, giorno nero,
 * regalato dell'anno tradotto in vita, grafico della settimana e mese per mese.
 */
class HistoryActivity : AppCompatActivity() {

    companion object {
        private const val MIN_INSIGHT_ENTRIES = 5   // sotto questa soglia le medie non dicono niente
        private const val MAX_MONTHS = 12
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }

    private lateinit var prefs: PreferencesManager

    // Mese per mese: (mese, stacchi) dal più recente, e quale si sta guardando (0 = il più recente)
    private var months: List<Pair<Int, List<HistoryStore.Entry>>> = emptyList()
    private var monthIndex = 0

    // Lo sapevi?: (titolo, frase) e quale si sta guardando. Titolo null = il messaggio "ancora X stacchi"
    private var insights: List<Pair<String?, String>> = emptyList()
    private var insightIndex = 0
    private var insightsPremium = false

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
        findViewById<View>(R.id.btnMonthPrev).setOnClickListener {
            monthIndex++
            showMonth()
        }
        findViewById<View>(R.id.btnMonthNext).setOnClickListener {
            monthIndex--
            showMonth()
        }
        findViewById<View>(R.id.btnInsightPrev).setOnClickListener {
            insightIndex--
            showInsight()
        }
        findViewById<View>(R.id.btnInsightNext).setOnClickListener {
            insightIndex++
            showInsight()
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
        // Con un piano simulato l'anteprima rispetta quel piano, altrimenti si vede tutto
        val premium = (preview && PreferencesManager.simulatedPlan == null) || prefs.hasFullAccess
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

        renderInsights(entries, premium, now)
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

    /**
     * Sette colonne (ultimi 7 giorni, oggi a destra), alte in proporzione ai minuti regalati:
     * mezz'ora (o il giorno peggiore della settimana, se più lungo) riempie tutta l'altezza.
     * Verde se in orario, arancio se in ritardo (con i minuti sopra), grigia se non registrato.
     * Giorno non chiuso: arancio tenue a tutta altezza, con una ✗ sopra.
     */
    private fun renderWeekBars(entries: List<HistoryStore.Entry>) {
        val container = findViewById<LinearLayout>(R.id.weekBars)
        container.removeAllViews()
        val density = resources.displayMetrics.density
        val maxBarPx = (44 * density).toInt()
        val minBarPx = (12 * density).toInt()
        val emptyBarPx = (8 * density).toInt()

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
            val unclosed = dayEntries.isNotEmpty() && dayEntries.all { it.unclosed }
            val late = dayEntries.any { !it.isOnTime }
            val color = when {
                dayEntries.isEmpty() -> R.color.home_border
                late -> R.color.home_accent
                else -> R.color.home_success
            }
            val height = when {
                dayEntries.isEmpty() -> emptyBarPx
                unclosed -> maxBarPx
                else -> minBarPx + (maxBarPx - minBarPx) * minutes.coerceAtMost(maxMinutes) / maxMinutes
            }

            // Sopra la colonna: i minuti regalati (ritardo) o la ✗ (non chiuso)
            val topText = when {
                unclosed -> "✗"
                late && minutes > 0 -> "+$minutes"
                else -> null
            }
            if (topText != null) {
                column.addView(TextView(this).apply {
                    text = topText
                    textSize = 11f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_accent))
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, (4 * density).toInt())
                })
            }
            val bar = View(this).apply {
                layoutParams = LinearLayout.LayoutParams((26 * density).toInt(), height)
                setBackgroundResource(R.drawable.bg_segment)
                background.mutate().setTint(ContextCompat.getColor(this@HistoryActivity, color))
                if (unclosed) alpha = 0.45f
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
     * Mese per mese, uno alla volta: si parte dal più recente e con le frecce si sfogliano gli altri
     * (al massimo [MAX_MONTHS], cioè tutto lo storico che l'app conserva).
     */
    private fun renderMonths(entries: List<HistoryStore.Entry>) {
        months = entries.groupBy { monthKey(it.timestampMillis) }
            .toList()
            .sortedByDescending { it.first }
            .take(MAX_MONTHS)
        showMonth()
    }

    /**
     * Il mese scelto: nome, quanti in orario, una barretta verde/arancio, il tempo regalato
     * e il confronto con il mese prima (sulla media al giorno, così un mese appena iniziato non vince "per forza").
     */
    private fun showMonth() {
        val list = findViewById<LinearLayout>(R.id.monthsList)
        list.removeAllViews()
        if (months.isEmpty()) return
        monthIndex = monthIndex.coerceIn(0, months.size - 1)

        val (key, monthEntries) = months[monthIndex]
        // Confronto solo se il mese prima c'è ed è proprio quello precedente
        val previous = months.getOrNull(monthIndex + 1)?.takeIf { it.first == key - 1 }
        list.addView(buildMonthRow(key, monthEntries, previous, resources.displayMetrics.density))

        // ‹ va indietro nel tempo (mesi più vecchi), › torna verso il più recente
        setArrows(R.id.btnMonthPrev, R.id.btnMonthNext, months.size, monthIndex < months.size - 1, monthIndex > 0)
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
            setPadding(pad, (12 * density).toInt(), pad, (12 * density).toInt())
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

        // Riga 4: confronto con il mese prima, media al giorno
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

    /**
     * "Lo sapevi?": tre abitudini ricavate dallo storico (le medie solo dai giorni chiusi davvero).
     * Gratis si vede "a che ora stacchi davvero"; giorno nero e regalato dell'anno restano sfocati,
     * con il lucchetto, e portano alla paywall. Sotto i [MIN_INSIGHT_ENTRIES] stacchi: quanti ne mancano.
     */
    private fun renderInsights(entries: List<HistoryStore.Entry>, premium: Boolean, now: Long) {
        val section = findViewById<View>(R.id.sectionInsights)
        val list = findViewById<LinearLayout>(R.id.insightsList)
        list.removeAllViews()
        if (entries.isEmpty()) {
            section.visibility = View.GONE
            return
        }
        section.visibility = View.VISIBLE

        val closed = entries.filter { !it.unclosed }
        if (closed.size < MIN_INSIGHT_ENTRIES) {
            val missing = MIN_INSIGHT_ENTRIES - closed.size
            insights = listOf(null to resources.getQuantityString(R.plurals.history_insights_waiting, missing, missing))
        } else {
            insights = listOf(
                getString(R.string.history_insight_time_label) to insightTime(closed),
                getString(R.string.history_insight_day_label) to insightBlackDay(closed),
                getString(R.string.history_insight_gift_label) to insightGifted(entries, now)
            )
        }
        insightsPremium = premium
        showInsight()
    }

    /** La curiosità scelta. Senza Premium, dalla seconda in poi: sfocata con il lucchetto. */
    private fun showInsight() {
        val list = findViewById<LinearLayout>(R.id.insightsList)
        list.removeAllViews()
        if (insights.isEmpty()) return
        insightIndex = insightIndex.coerceIn(0, insights.size - 1)

        val (label, value) = insights[insightIndex]
        val locked = !insightsPremium && insightIndex > 0
        list.addView(buildInsightRow(label, value, locked, resources.displayMetrics.density))
        setArrows(R.id.btnInsightPrev, R.id.btnInsightNext, insights.size, insightIndex > 0, insightIndex < insights.size - 1)
    }

    /** Frecce di un carosello: nascoste se c'è un elemento solo, sbiadite quando da quella parte non c'è altro. */
    private fun setArrows(prevId: Int, nextId: Int, count: Int, prevEnabled: Boolean, nextEnabled: Boolean) {
        val prev = findViewById<View>(prevId)
        val next = findViewById<View>(nextId)
        val visibility = if (count > 1) View.VISIBLE else View.GONE
        prev.visibility = visibility
        next.visibility = visibility
        prev.isEnabled = prevEnabled
        next.isEnabled = nextEnabled
        prev.alpha = if (prevEnabled) 1f else 0.3f
        next.alpha = if (nextEnabled) 1f else 0.3f
    }

    /** "Il turno finisce alle 18:00, tu stacchi in media alle 18:14": fine turno media + ritardo medio. */
    private fun insightTime(closed: List<HistoryStore.Entry>): String {
        val avgEnd = closed.sumOf { it.endHour * 60 + it.endMinute } / closed.size
        val avgOvertime = closed.sumOf { it.overtimeMinutes.coerceAtLeast(0) } / closed.size
        return if (avgOvertime < 1) {
            getString(R.string.history_insight_time_punctual, clock(avgEnd))
        } else {
            getString(R.string.history_insight_time, clock(avgEnd), clock(avgEnd + avgOvertime))
        }
    }

    /**
     * Il giorno della settimana con il ritardo medio più alto.
     * Contano i giorni con almeno 2 stacchi (se nessuno ne ha 2, tutti): un solo giovedì storto non fa una regola.
     */
    private fun insightBlackDay(closed: List<HistoryStore.Entry>): String {
        val byDay = closed.groupBy { entry ->
            // Il giorno del turno, non dello stacco: chi stacca dopo mezzanotte resta sul giorno giusto
            Calendar.getInstance().apply {
                timeInMillis = entry.timestampMillis - entry.overtimeMinutes.coerceAtLeast(0) * 60_000L
            }.get(Calendar.DAY_OF_WEEK)
        }
        val worst = byDay.filter { it.value.size >= 2 }.ifEmpty { byDay }
            .mapValues { (_, list) -> list.sumOf { it.overtimeMinutes.coerceAtLeast(0) } / list.size }
            .maxByOrNull { it.value }
        if (worst == null || worst.value <= PreferencesManager.ON_TIME_THRESHOLD_MINUTES) {
            return getString(R.string.history_insight_day_none)
        }
        val day = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, worst.key) }
        var dayName = SimpleDateFormat("EEEE", Locale.getDefault()).format(day.time)
        if (Locale.getDefault().language == "it") dayName = dayName.lowercase(Locale.ITALIAN)
        return getString(R.string.history_insight_day, dayName, formatMinutes(worst.value))
    }

    /** Il regalato dall'inizio dell'anno, tradotto in qualcosa di vivo: voli, film, episodi o caffè. */
    private fun insightGifted(entries: List<HistoryStore.Entry>, now: Long): String {
        val year = monthKey(now) / 12
        val minutes = entries
            .filter { monthKey(it.timestampMillis) / 12 == year }
            .sumOf { it.overtimeMinutes.coerceAtLeast(0) }
        if (minutes == 0) return getString(R.string.history_insight_gift_zero)
        if (minutes < 30) return getString(R.string.history_insight_gift_little, formatMinutes(minutes))

        // L'unità più grande che ci sta almeno 2 volte (volo Roma–New York ≈ 9 ore)
        val (plural, unitMinutes) = listOf(
            R.plurals.history_gift_flights to 540,
            R.plurals.history_gift_films to 120,
            R.plurals.history_gift_episodes to 45
        ).firstOrNull { minutes / it.second >= 2 } ?: (R.plurals.history_gift_coffees to 15)
        val count = minutes / unitMinutes
        return getString(
            R.string.history_insight_gift,
            formatMinutes(minutes),
            resources.getQuantityString(plural, count, count)
        )
    }

    /** Una riga del "Lo sapevi?": etichetta piccola e frase. Se bloccata: frase sfocata, lucchetto e paywall. */
    private fun buildInsightRow(label: String?, value: String, locked: Boolean, density: Float): View {
        val pad = (20 * density).toInt()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, (12 * density).toInt(), pad, (12 * density).toInt())
        }
        if (label != null) {
            row.addView(TextView(this).apply {
                text = label
                textSize = 11f
                letterSpacing = 0.12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_accent))
            })
        }
        val tvValue = TextView(this).apply {
            text = value
            textSize = 16f
            setLineSpacing(0f, 1.1f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_text))
            if (label != null) setPadding(0, (6 * density).toInt(), 0, 0)
        }
        row.addView(tvValue)

        if (locked) {
            // La sfocatura c'è da Android 12: prima la frase non si mostra proprio
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val radius = 10 * density
                tvValue.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL))
                tvValue.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            } else {
                tvValue.visibility = View.GONE
            }
            row.addView(TextView(this).apply {
                setText(R.string.history_insight_locked)
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@HistoryActivity, R.color.home_accent))
                setPadding(0, (6 * density).toInt(), 0, 0)
            })
            row.setOnClickListener { startActivity(Intent(this, PaywallActivity::class.java)) }
        }
        return row
    }

    /** Minuti dall'inizio del giorno → "18:14". */
    private fun clock(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return String.format(Locale.getDefault(), "%02d:%02d", m / 60, m % 60)
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
