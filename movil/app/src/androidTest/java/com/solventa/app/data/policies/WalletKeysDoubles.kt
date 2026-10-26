package com.solventa.app.data.policies

import java.util.concurrent.CountDownLatch

/**
 * Llaves reales del Keystore que la prueba puede volver no disponibles o detener dentro del turno de la
 * cuenta, para ordenar operaciones concurrentes sin depender del tiempo (HU-MOV-05).
 */
class ControlledWalletKeys(private val real: WalletKeys) : WalletKeys {
    @Volatile var unavailable = false
    @Volatile var hold: CountDownLatch? = null
    val holding = CountDownLatch(1)

    override fun wrap(alias: String, plaintext: ByteArray): WrappedSecret? {
        pause()
        return if (unavailable) null else real.wrap(alias, plaintext)
    }

    override fun unwrap(alias: String, wrapped: WrappedSecret): Unwrapped {
        pause()
        return if (unavailable) Unwrapped.Unavailable else real.unwrap(alias, wrapped)
    }

    override fun delete(alias: String) = real.delete(alias)

    private fun pause() {
        hold?.let {
            holding.countDown()
            it.await()
        }
    }
}
