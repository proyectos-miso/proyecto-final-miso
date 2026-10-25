package com.solventa.app.ui.wallet

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val COLOMBIA = Locale.forLanguageTag("es-CO")
private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", COLOMBIA)
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", COLOMBIA)

/** Pesos sin centavos y con el código, para que el símbolo $ no se lea como dólares (AC-15). */
fun formatCop(amount: Double): String =
    NumberFormat.getCurrencyInstance(COLOMBIA).apply { maximumFractionDigits = 0 }.format(amount) + " COP"

fun formatDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String = DATE_FORMAT.format(instant.atZone(zone))

fun formatDateTime(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    DATE_TIME_FORMAT.format(instant.atZone(zone))
