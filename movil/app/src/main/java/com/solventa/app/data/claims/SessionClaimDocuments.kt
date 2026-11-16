package com.solventa.app.data.claims

import com.solventa.app.data.policies.SessionPolicySync
import com.solventa.app.domain.claims.ClaimDocumentUploader
import com.solventa.app.domain.claims.ClaimDocumentsApi
import com.solventa.app.domain.claims.DocumentUploadResult
import com.solventa.app.domain.claims.ScannedPage

/**
 * Envío de documentos con el token de la sesión abierta (mejora FE-MOV-05). Con el ingreso sin red hay sesión pero
 * no tokens: es falta de red, no una sesión vencida.
 */
class SessionClaimDocuments(private val sync: SessionPolicySync, private val api: ClaimDocumentsApi) : ClaimDocumentUploader {

    override suspend fun upload(claimNumber: String, uploadId: String, pages: List<ScannedPage>): DocumentUploadResult =
        sync.withAccessToken({ it == DocumentUploadResult.Unauthorized }) { api.upload(it, claimNumber, uploadId, pages) }
            ?: if (sync.hasOpenSession) DocumentUploadResult.Offline else DocumentUploadResult.Unauthorized
}
