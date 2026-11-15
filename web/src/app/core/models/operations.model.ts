/** Contrato del tablero operativo del portal de Solventa (FE-WEB-07). */
export type OperationsPeriod = 'today' | '7d' | '30d';
export type LatencyGroupName = 'quote' | 'issue' | 'payment' | 'claim';
export type DependencyName = 'database' | 's3' | 'sqs' | 'firebase';
export type DependencyState = 'ok' | 'degraded' | 'down' | 'not_configured';

export interface LatencyGroup {
  group: LatencyGroupName;
  samples: number;
  p50_ms: number | null;
  p95_ms: number | null;
  p99_ms: number | null;
  target_ms: number;
  within_target: boolean | null;
}

export interface DependencyStatus {
  name: DependencyName;
  status: DependencyState;
  latency_ms: number | null;
}

/** Los conteos llegan en null si la base no respondió; `conversion` también si no hubo cotizaciones. */
export interface OperationsIndicators {
  period: OperationsPeriod;
  since: string;
  generated_at: string;
  quotes: number | null;
  quotes_per_minute: number | null;
  conversion: number | null;
  policies_issued: number | null;
  open_claims: number | null;
  outbox_pending: number | null;
  latency: LatencyGroup[];
  dependencies: DependencyStatus[];
}
