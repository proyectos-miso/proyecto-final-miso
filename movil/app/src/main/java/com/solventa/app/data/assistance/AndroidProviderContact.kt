package com.solventa.app.data.assistance

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.solventa.app.domain.assistance.Provider
import com.solventa.app.domain.assistance.ProviderContact
import java.util.Locale

/**
 * «Llamar» abre el marcador con el número (sin permiso de llamadas) y «Ver en el mapa» abre la app de mapas del
 * teléfono con `geo:`. Sin mapa embebido ni llave de API: no se agregan dependencias ni secretos (mejora FE-MOV-07).
 */
class AndroidProviderContact(private val context: Context) : ProviderContact {

    override fun call(phone: String): Boolean = start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))

    override fun showOnMap(provider: Provider): Boolean = start(Intent(Intent.ACTION_VIEW, geoUri(provider)))

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    companion object {
        fun geoUri(provider: Provider): Uri {
            val point = String.format(Locale.ROOT, "%.6f,%.6f", provider.lat, provider.lng)
            return Uri.parse("geo:$point?q=" + Uri.encode("$point(${provider.name})"))
        }
    }
}
