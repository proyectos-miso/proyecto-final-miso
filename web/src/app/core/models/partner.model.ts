/** Contrato de la gestión de socios del portal de Solventa (FE-WEB-05). */
export type PartnerEnvironment = 'sandbox' | 'production';
export type PartnerStatus = 'ACTIVE' | 'REVOKED';

export interface PartnerSummary {
  partner_id: string;
  name: string;
  environment: PartnerEnvironment;
  status: PartnerStatus;
  quota_limit: number;
  quota_used: number;
  created_at: string | null;
  has_contact: boolean;
  contact_email_masked: string | null;
}

/** Página del listado; `truncated` indica que hay más socios de los que se muestran. */
export interface PartnerPage {
  items: PartnerSummary[];
  truncated: boolean;
}

/** Credenciales que el servidor entrega una sola vez; nunca se guardan en el navegador. */
export interface PartnerCredentials {
  partner_id: string;
  client_id: string;
  client_secret: string;
  message: string;
}

export interface PartnerRegistration extends PartnerCredentials {
  name: string;
  environment: PartnerEnvironment;
  quota_limit: number;
  openapi_spec_url: string;
}

export interface PartnerApproval {
  status: 'APPROVED';
  partner_id: string;
  environment: PartnerEnvironment;
  quota_limit: number;
  message: string;
}
