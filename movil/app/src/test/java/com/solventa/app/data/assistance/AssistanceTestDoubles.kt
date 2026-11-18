package com.solventa.app.data.assistance

import com.solventa.app.domain.assistance.ApproximateLocator
import com.solventa.app.domain.assistance.Assistance
import com.solventa.app.domain.assistance.AssistanceForm
import com.solventa.app.domain.assistance.AssistanceResult
import com.solventa.app.domain.assistance.CitiesResult
import com.solventa.app.domain.assistance.City
import com.solventa.app.domain.assistance.Coordinates
import com.solventa.app.domain.assistance.Provider
import com.solventa.app.domain.assistance.ProviderContact
import com.solventa.app.domain.assistance.ProviderQuery
import com.solventa.app.domain.assistance.ProvidersCopy
import com.solventa.app.domain.assistance.ProvidersCopyStore
import com.solventa.app.domain.assistance.ProvidersResult

// Prestadores y teléfonos ficticios (rango 555).
fun sampleProvider(
    id: String = "PRV-BOG-01",
    name: String = "Grúas de Prueba",
    services: List<String> = listOf("GRUA", "ASISTENCIA_VIAL"),
    distanceKm: Double? = 1.2,
) = Provider(id, name, "GRUA", services, "BOGOTA", "Calle 100 # 15-20", "+576015550101", 4.6867, -74.048, distanceKm)

val CITIES = listOf(City("BOGOTA", "Bogotá, D.C."), City("CALI", "Cali"))

class FakeAssistance : Assistance {
    var providers: ProvidersResult = ProvidersResult.Loaded(listOf(sampleProvider()))
    var cities: CitiesResult = CitiesResult.Loaded(CITIES)
    var result: AssistanceResult = AssistanceResult.Received("REQ-1", sampleProvider(), 18)
    val queries = mutableListOf<ProviderQuery>()
    val requests = mutableListOf<Pair<String, AssistanceForm>>()
    var cityCalls = 0

    override suspend fun providers(query: ProviderQuery): ProvidersResult {
        queries += query
        return providers
    }

    override suspend fun cities(): CitiesResult {
        cityCalls++
        return cities
    }

    override suspend fun request(idempotencyKey: String, form: AssistanceForm): AssistanceResult {
        requests += idempotencyKey to form
        return result
    }
}

class InMemoryProvidersCopy : ProvidersCopyStore {
    val copies = mutableMapOf<String, ProvidersCopy>()

    override suspend fun saveProviders(accountEmail: String, copy: ProvidersCopy): Boolean {
        copies[accountEmail] = copy
        return true
    }

    override suspend fun readProviders(accountEmail: String): ProvidersCopy? = copies[accountEmail]
}

class FakeLocator(var permission: Boolean = true, var location: Coordinates? = Coordinates(4.67612, -74.04849)) : ApproximateLocator {
    var calls = 0

    override fun hasPermission() = permission

    override suspend fun locate(): Coordinates? {
        calls++
        return location.takeIf { permission }
    }
}

class RecordingContact(var opens: Boolean = true) : ProviderContact {
    val calls = mutableListOf<String>()
    val maps = mutableListOf<Provider>()

    override fun call(phone: String): Boolean {
        calls += phone
        return opens
    }

    override fun showOnMap(provider: Provider): Boolean {
        maps += provider
        return opens
    }
}
