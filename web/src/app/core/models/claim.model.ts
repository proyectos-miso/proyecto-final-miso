/** Contrato del back office de siniestros (HU-WEB-15). */
export type ClaimStatus = 'REPORTED' | 'IN_REVIEW' | 'REJECTED' | 'SETTLED' | 'PAID';
export type ClaimOrigin = 'APP' | 'PARAMETRIC';
export type PhotoIntegrity = 'INTACT' | 'ALTERED' | 'UNVERIFIED';

export interface ClaimListItem {
  claim_number: string;
  policy_number: string;
  reported_at: string;
  status: ClaimStatus;
  origin: ClaimOrigin;
  assigned_to: string | null;
}

export interface ClaimList {
  items: ClaimListItem[];
  has_more: boolean;
}

export interface ClaimPhoto {
  photo_id: string;
  position: number;
  url: string | null;
  latitude: number;
  longitude: number;
  accuracy_m: number;
  mock_location: boolean;
  captured_at: string;
  trusted_time: boolean;
  integrity: PhotoIntegrity;
}

export interface ClaimEvent {
  kind: string;
  actor: string;
  justification: string | null;
  amount: number | null;
  created_at: string;
}

export interface ClaimPayout {
  beneficiary_name: string;
  beneficiary_document: string;
  percentage: number;
  amount: number;
  status: string;
  receipt_number: string | null;
}

export interface Expert {
  expert_id: string;
  name: string;
  city: string;
}

export interface ClaimCoverage {
  coverage_id: string;
  name: string;
  limit: number;
}

export interface ClaimDetail {
  claim_number: string;
  status: ClaimStatus;
  origin: ClaimOrigin;
  reported_at: string;
  description: string | null;
  parametric_event: Record<string, unknown> | null;
  policy_number: string;
  policy_status: string;
  plan: string;
  insured_name: string | null;
  insured_document: string;
  insured_phone: string | null;
  coverage: ClaimCoverage | null;
  coverages: ClaimCoverage[];
  assigned_to: string | null;
  expert: Expert | null;
  settled_amount: number | null;
  photos: ClaimPhoto[];
  events: ClaimEvent[];
  payouts: ClaimPayout[];
}

export interface ClaimDecisionRequest {
  decision: 'APPROVE' | 'REJECT';
  justification: string;
  coverage_id?: string;
  amount?: number;
}
