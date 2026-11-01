export type StartRunRequest = {
  orderId: string;
  sku: string;
  qty: number;
  amountMinor: number;
  currency: string;
};

export type StartRunResponse = {
  workflowId: string;
  runId: string;
  namespace: string;
  taskQueue: string;
  temporalUiUrl: string;
};

export type WorkflowStepView = {
  id: string;
  label: string;
  activityName: string;
  status: string;
};

export type OrderFulfillmentResult = {
  orderId: string;
  reservationId: string;
  reservationState: string;
  paymentId: string;
  paymentState: string;
  shipmentId: string;
  shipmentState: string;
};

export type RunStatusResponse = {
  workflowId: string;
  runId: string;
  namespace: string;
  temporalStatus: string;
  temporalUiUrl: string;
  steps: WorkflowStepView[];
  workflowResult: OrderFulfillmentResult | null;
  failureMessage: string | null;
  note: string;
};

export type BusinessStateResponse = {
  available: boolean;
  reason: string | null;
  reservation: { reservationId: string; orderId: string; sku: string; qty: number; state: string } | null;
  payment: { paymentId: string; orderId: string; amount: { amountMinor: number; currency: string }; state: string } | null;
  shipment: { shipmentId: string; orderId: string; state: string } | null;
  stock: { sku: string; onHand: number; held: number } | null;
  consistencyCheck: { looksConsistentWithConfirmedSuccess: boolean; summary: string } | null;
};

async function parseError(res: Response): Promise<string> {
  try {
    const body = await res.json();
    return body.message ?? JSON.stringify(body);
  } catch {
    return res.statusText;
  }
}

export async function startRun(body: StartRunRequest): Promise<StartRunResponse> {
  const res = await fetch("/api/console/runs", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(await parseError(res));
  return res.json();
}

export async function getRun(workflowId: string): Promise<RunStatusResponse> {
  const res = await fetch(`/api/console/runs/${encodeURIComponent(workflowId)}`);
  if (!res.ok) throw new Error(await parseError(res));
  return res.json();
}

export async function getBusiness(workflowId: string): Promise<BusinessStateResponse> {
  const res = await fetch(`/api/console/runs/${encodeURIComponent(workflowId)}/business`);
  if (!res.ok) throw new Error(await parseError(res));
  return res.json();
}
