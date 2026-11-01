import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  BusinessStateResponse,
  RunStatusResponse,
  StartRunResponse,
  getBusiness,
  getRun,
  startRun,
} from "./api";

function newOrderId(): string {
  return crypto.randomUUID();
}

export default function App() {
  const [orderId, setOrderId] = useState(newOrderId);
  const [sku, setSku] = useState("SKU-MVP-1");
  const [qty, setQty] = useState(1);
  const [amountMinor, setAmountMinor] = useState<string>("");
  const [currency, setCurrency] = useState("PLN");

  const [started, setStarted] = useState<StartRunResponse | null>(null);
  const [status, setStatus] = useState<RunStatusResponse | null>(null);
  const [business, setBusiness] = useState<BusinessStateResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const workflowId = started?.workflowId;

  useEffect(() => {
    if (!workflowId) return;
    let cancelled = false;
    const tick = async () => {
      try {
        const run = await getRun(workflowId);
        if (cancelled) return;
        setStatus(run);
        if (run.workflowResult) {
          const biz = await getBusiness(workflowId);
          if (!cancelled) setBusiness(biz);
        } else {
          setBusiness(null);
        }
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : String(e));
      }
    };
    tick();
    const id = window.setInterval(tick, 1500);
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [workflowId]);

  const canSubmit = useMemo(() => {
    const amount = Number(amountMinor);
    return !!orderId && !!sku && qty >= 1 && Number.isFinite(amount) && amount >= 1 && !!currency && !busy;
  }, [orderId, sku, qty, amountMinor, currency, busy]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    setBusiness(null);
    setStatus(null);
    try {
      const res = await startRun({
        orderId,
        sku,
        qty,
        amountMinor: Number(amountMinor),
        currency,
      });
      setStarted(res);
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
      setStarted(null);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main>
      <h1>Experiment Console — Variant A</h1>
      <p className="lede">
        Local-only manual runner. Temporal execution, service snapshots, and a lightweight consistency
        check are shown separately. This is not the formal experiment oracle.
      </p>

      <div className="grid grid-2">
        <section className="panel">
          <h2>Order form</h2>
          <form onSubmit={onSubmit}>
            <label>
              Order ID
              <input value={orderId} onChange={(e) => setOrderId(e.target.value)} required />
            </label>
            <label>
              SKU
              <input value={sku} onChange={(e) => setSku(e.target.value)} required />
            </label>
            <label>
              Quantity
              <input
                type="number"
                min={1}
                value={qty}
                onChange={(e) => setQty(Number(e.target.value))}
                required
              />
            </label>
            <label>
              Amount (minor units)
              <input
                type="number"
                min={1}
                value={amountMinor}
                onChange={(e) => setAmountMinor(e.target.value)}
                placeholder="required — no silent default"
                required
              />
              <span className="hint">
                Enter explicitly (e.g. 2599 only as a local test suggestion — not an approved protocol
                constant).
              </span>
            </label>
            <label>
              Currency
              <input value={currency} onChange={(e) => setCurrency(e.target.value)} required />
            </label>
            <div className="actions">
              <button type="submit" disabled={!canSubmit}>
                Start Variant A
              </button>
              <button
                type="button"
                className="secondary"
                onClick={() => setOrderId(newOrderId())}
              >
                New order ID
              </button>
            </div>
          </form>
        </section>

        <section className="panel">
          <h2>1 · Temporal execution</h2>
          {!started && <p className="hint">Start a run to see Temporal status and activity history.</p>}
          {started && status && (
            <>
              <dl className="kv">
                <dt>Workflow ID</dt>
                <dd>{status.workflowId}</dd>
                <dt>Run ID</dt>
                <dd>{status.runId}</dd>
                <dt>Status</dt>
                <dd>
                  <span className={`badge ${status.temporalStatus}`}>{status.temporalStatus}</span>
                </dd>
                <dt>Temporal UI</dt>
                <dd>
                  <a href={status.temporalUiUrl} target="_blank" rel="noreferrer">
                    Open execution
                  </a>
                </dd>
              </dl>
              <h3 style={{ marginTop: "1rem", fontSize: "0.95rem" }}>Timeline (from history)</h3>
              <ol className="steps">
                {status.steps.map((step) => (
                  <li key={step.id}>
                    <span>{step.label}</span>
                    <span className={`badge ${step.status}`}>{step.status}</span>
                  </li>
                ))}
              </ol>
              {status.failureMessage && <div className="error-box">{status.failureMessage}</div>}
              {status.workflowResult && (
                <p className="note">
                  Workflow result states: reservation={status.workflowResult.reservationState}, payment=
                  {status.workflowResult.paymentState}, shipment={status.workflowResult.shipmentState}
                </p>
              )}
              <p className="note">{status.note}</p>
            </>
          )}
        </section>
      </div>

      <section className="panel" style={{ marginTop: "1rem" }}>
        <h2>2 · Business snapshots (service APIs)</h2>
        {!business && <p className="hint">Available after the workflow returns entity IDs.</p>}
        {business && !business.available && <p className="hint">{business.reason}</p>}
        {business?.available && (
          <div className="grid grid-2">
            <dl className="kv">
              <dt>Reservation</dt>
              <dd>
                {business.reservation?.state} ({business.reservation?.reservationId})
              </dd>
              <dt>Payment</dt>
              <dd>
                {business.payment?.state} ({business.payment?.paymentId})
              </dd>
              <dt>Shipment</dt>
              <dd>
                {business.shipment?.state} ({business.shipment?.shipmentId})
              </dd>
              <dt>Stock</dt>
              <dd>
                {business.stock
                  ? `${business.stock.sku}: onHand=${business.stock.onHand}, held=${business.stock.held}`
                  : "n/a"}
              </dd>
            </dl>
            <div>
              <h3 style={{ margin: "0 0 0.5rem", fontSize: "0.95rem" }}>
                3 · Consistency check (not formal oracle)
              </h3>
              {business.consistencyCheck && (
                <>
                  <span
                    className={`badge ${
                      business.consistencyCheck.looksConsistentWithConfirmedSuccess ? "ok" : "bad"
                    }`}
                  >
                    {business.consistencyCheck.looksConsistentWithConfirmedSuccess
                      ? "LOOKS CONSISTENT"
                      : "NOT CONSISTENT"}
                  </span>
                  <p className="note">{business.consistencyCheck.summary}</p>
                </>
              )}
            </div>
          </div>
        )}
      </section>

      {error && (
        <section className="panel" style={{ marginTop: "1rem" }}>
          <h2>Errors</h2>
          <div className="error-box">{error}</div>
        </section>
      )}
    </main>
  );
}
