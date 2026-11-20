package com.solventa.app.domain.claims

import kotlinx.coroutines.flow.StateFlow

/** Permiso de ubicación tal como lo necesita el reporte: la aproximada no sirve (AC-17). */
enum class LocationAccess { PRECISE, APPROXIMATE_ONLY, DENIED }

/** Resultado de pedir la ubicación para una foto (AC-01, AC-08). */
sealed interface LocationResult {
    data class Fix(val fix: CaptureFix) : LocationResult

    /** Sin permiso de ubicación precisa. */
    data class NoPermission(val access: LocationAccess) : LocationResult

    /** La ubicación del teléfono está apagada. */
    data object Disabled : LocationResult

    /** No llegó una ubicación buena a tiempo. */
    data object Timeout : LocationResult
}

/**
 * Ubicación del disparo. Solo escucha entre [start] y [stop], es decir, con la cámara del reporte abierta y
 * en primer plano (AC-20).
 */
interface CaptureLocator {
    /** La última ubicación recibida con la cámara abierta, para mostrarla sobre el visor; null al detenerse. */
    val latestFix: StateFlow<CaptureFix?>

    fun access(): LocationAccess

    fun isEnabled(): Boolean

    fun start()

    fun stop()

    /** La última ubicación si es fresca y precisa, o espera una hasta que pasa el tiempo límite. */
    suspend fun fixForShutter(): LocationResult
}

/** Hora que no depende del reloj que ajusta la persona, si el teléfono la tiene (AC-10). */
fun interface TrustedClock {
    fun now(): TrustedTime?
}
