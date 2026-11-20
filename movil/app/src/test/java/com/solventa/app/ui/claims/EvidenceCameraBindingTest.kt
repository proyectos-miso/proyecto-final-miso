package com.solventa.app.ui.claims

import org.junit.Assert.assertEquals
import org.junit.Test

/** La cámara no queda tomada si CameraX responde después de que la vista se desechó (mejora FE-MOV-01). */
class EvidenceCameraBindingTest {

    private val events = mutableListOf<String>()
    private val binding = CameraBinding(bind = { events += "bind" }, release = { events += "release" })

    @Test
    fun `enlaza cuando el proveedor responde antes de desechar y libera al desechar`() {
        binding.onReady()
        binding.dispose()

        assertEquals(listOf("bind", "release"), events)
    }

    @Test
    fun `si el proveedor responde despues de desechar no enlaza y libera`() {
        binding.dispose()
        binding.onReady()

        assertEquals(listOf("release", "release"), events)
    }
}
