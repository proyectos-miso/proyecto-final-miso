package com.solventa.app.domain.claims

/** Lo que le falta al reporte para poder enviarse (AC-04, AC-18). */
enum class Missing { PHOTO, POLICY, DESCRIPTION, POLICY_NOT_ACTIVE }

/** Reglas del reporte de siniestro (HU-MOV-08, CTL-12). */
object ClaimRules {
    const val MAX_PHOTOS = 5
    const val MAX_DESCRIPTION = 500
    const val MAX_PHOTO_BYTES = 2_000_000

    /** La descripción como se guarda: sin pasar de 500 caracteres. Los espacios de los extremos no cuentan como texto. */
    fun limitDescription(text: String): String = if (text.length <= MAX_DESCRIPTION) text else text.take(MAX_DESCRIPTION)

    fun hasDescription(text: String): Boolean = text.isNotBlank()

    /** Lo que falta, en el orden en que se muestra. [activePolicies] son los números de las pólizas vigentes de la copia. */
    fun missing(report: ClaimReport, activePolicies: Set<String>): List<Missing> = buildList {
        if (report.photos.isEmpty()) add(Missing.PHOTO)
        when {
            report.policyNumber == null -> add(Missing.POLICY)
            report.policyNumber !in activePolicies -> add(Missing.POLICY_NOT_ACTIVE)
        }
        if (!hasDescription(report.description)) add(Missing.DESCRIPTION)
    }

    fun canAddPhoto(report: ClaimReport): Boolean = !report.isReady && report.photos.size < MAX_PHOTOS
}
