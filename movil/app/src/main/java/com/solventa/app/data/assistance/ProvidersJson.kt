package com.solventa.app.data.assistance

import com.solventa.app.domain.assistance.Provider
import org.json.JSONArray
import org.json.JSONObject

/** Prestador en el JSON del servidor, que es el mismo de la copia sin conexión. */
internal object ProvidersJson {

    fun providers(array: JSONArray): List<Provider> = (0 until array.length()).map { provider(array.getJSONObject(it)) }

    fun provider(json: JSONObject) = Provider(
        id = json.getString("provider_id"),
        name = json.getString("name"),
        kind = json.getString("kind"),
        services = json.getJSONArray("services").let { s -> (0 until s.length()).map(s::getString) },
        cityCode = json.getString("city_code"),
        address = json.getString("address"),
        phone = json.getString("phone"),
        lat = json.getDouble("lat"),
        lng = json.getDouble("lng"),
        distanceKm = if (json.isNull("distance_km")) null else json.getDouble("distance_km"),
    )

    fun toJson(providers: List<Provider>): JSONArray = JSONArray(providers.map(::toJson))

    fun toJson(provider: Provider): JSONObject = JSONObject()
        .put("provider_id", provider.id)
        .put("name", provider.name)
        .put("kind", provider.kind)
        .put("services", JSONArray(provider.services))
        .put("city_code", provider.cityCode)
        .put("address", provider.address)
        .put("phone", provider.phone)
        .put("lat", provider.lat)
        .put("lng", provider.lng)
        .put("distance_km", provider.distanceKm ?: JSONObject.NULL)
}
