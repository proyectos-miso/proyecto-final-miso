package com.solventa.app.data.claims

import android.os.SystemClock
import com.solventa.app.domain.claims.TimeSource
import com.solventa.app.domain.claims.TrustedClock
import com.solventa.app.domain.claims.TrustedTime
import java.time.DateTimeException

/**
 * Hora que no depende del reloj que ajusta la persona (CTL-03): la del GNSS y, si no hay, la de red de Android.
 * Si el teléfono no tiene ninguna, la foto se guarda solo con la hora del teléfono.
 */
class AndroidTrustedClock(
    private val gnssMillis: () -> Long = { SystemClock.currentGnssTimeClock().millis() },
    private val networkMillis: () -> Long = { SystemClock.currentNetworkTimeClock().millis() },
) : TrustedClock {

    override fun now(): TrustedTime? =
        read(gnssMillis)?.let { TrustedTime(it, TimeSource.GNSS) }
            ?: read(networkMillis)?.let { TrustedTime(it, TimeSource.NETWORK) }

    private fun read(source: () -> Long): Long? = try {
        source()
    } catch (e: DateTimeException) {
        null
    }
}
