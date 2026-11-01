package com.solventa.app.domain.policies

import kotlinx.coroutines.flow.SharedFlow

/** Sincroniza la copia de la cuenta con sesión abierta: en la billetera y en segundo plano (HU-MOV-07). */
interface PolicySync {
    /** Resultados de todas las sincronizaciones, sin repetición; si nadie escucha se pierden. */
    val outcomes: SharedFlow<SyncOutcome>

    /** Una a la vez. [online] lo pasa el trabajo, que solo corre con red. Solo lanza la cancelación. */
    suspend fun sync(online: Boolean = false): SyncOutcome

    /** Pide sincronizar en segundo plano cuando haya red. */
    fun schedule()

    /** Cancela al instante las sincronizaciones en curso y el trabajo pendiente. */
    fun cancel()
}

sealed interface SyncOutcome {
    data class Synced(val accountEmail: String, val copy: WalletCopy) : SyncOutcome
    data object Failed : SyncOutcome
    data class SessionEnded(val accountEmail: String, val reason: SessionEndReason) : SyncOutcome
    data object NoSession : SyncOutcome
}

enum class SessionEndReason {
    /** El servidor rechazó la sesión: se borra la copia. */
    EXPIRED,

    /** El servidor rechazó la credencial del teléfono: se borra la copia y se desvincula la huella. */
    DEVICE_REJECTED,

    /** Hay que volver a ingresar para actualizar; la copia se conserva. */
    RELOGIN,
}

/** Trabajo en segundo plano que llama a [PolicySync.sync] cuando hay red. */
interface SyncScheduler {
    fun request()
    fun cancel()
}
