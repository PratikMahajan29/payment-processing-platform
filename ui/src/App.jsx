import { useMemo, useState } from 'react'
import {
  createPayment,
  getAttempts,
  getPayment,
  processPayment,
  retryPayment,
} from './api.js'

function buildDefaults() {
  return {
    merchantId: crypto.randomUUID(),
    idempotencyKey: `payment-${crypto.randomUUID()}`,
    orderId: crypto.randomUUID(),
    customerId: crypto.randomUUID(),
    amount: '499.50',
    currency: 'INR',
  }
}

function normalizeCurrency(value) {
  return value.trim().toUpperCase()
}

function getCurrencyFractionDigits(currency) {
  try {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
    }).resolvedOptions().maximumFractionDigits
  } catch {
    return 2
  }
}

function validateAmount(value, currency) {
  const normalized = String(value)
    .trim()
    .replace(/,/g, '')

  if (!/^\d+(\.\d+)?$/.test(normalized)) {
    throw new Error(
      'Amount must be a positive decimal value.',
    )
  }

  const fractionDigits =
    getCurrencyFractionDigits(currency)

  const [, fraction = ''] =
    normalized.split('.')

  if (fraction.length > fractionDigits) {
    throw new Error(
      `${currency} supports ${fractionDigits} decimal ${
        fractionDigits === 1 ? 'place' : 'places'
      }.`,
    )
  }

  const numeric = Number(normalized)

  if (!Number.isFinite(numeric) || numeric <= 0) {
    throw new Error(
      'Amount must be greater than zero.',
    )
  }

  // Return the original decimal string.
  // BigDecimal on the backend can consume it exactly.
  return normalized
}

function formatMoney(amount, currency) {
  const normalized = String(amount ?? '').trim()

  if (!normalized) {
    return '—'
  }

  try {
    const numeric = Number(normalized)

    if (!Number.isFinite(numeric)) {
      return `${currency} ${normalized}`
    }

    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency,
    }).format(numeric)
  } catch {
    return `${currency} ${normalized}`
  }
}

function formatDate(value) {
  if (!value) {
    return '—'
  }

  const date = new Date(value)

  if (Number.isNaN(date.getTime())) {
    return value
  }

  return date.toLocaleString()
}

function StatusBadge({ status }) {
  const normalized = String(
    status ?? 'UNKNOWN',
  ).toLowerCase()

  return (
    <span className={`status status-${normalized}`}>
      {status ?? 'UNKNOWN'}
    </span>
  )
}

function Field({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
}) {
  return (
    <label className="field">
      <span>{label}</span>

      <input
        type={type}
        value={value}
        onChange={(event) =>
          onChange(event.target.value)
        }
        placeholder={placeholder}
      />
    </label>
  )
}

export default function App() {
  const [form, setForm] = useState(buildDefaults)

  const [payment, setPayment] =
    useState(null)

  const [attempts, setAttempts] =
    useState([])

  const [lookupId, setLookupId] =
    useState('')

  const [busy, setBusy] =
    useState('')

  const [error, setError] =
    useState('')

  const [notice, setNotice] =
    useState('')

  const canProcess =
    payment?.status === 'CREATED'

  const canRetry =
    payment?.status === 'FAILED'

  const latestAttempt = useMemo(
    () => attempts.at(-1) ?? null,
    [attempts],
  )

  function setFormValue(field, value) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))
  }

  async function refreshAttempts(
    paymentId,
  ) {
    if (!paymentId) {
      return
    }

    const data =
      await getAttempts(paymentId)

    setAttempts(
      Array.isArray(data) ? data : [],
    )
  }

  async function applyPayment(
    nextPayment,
    message,
  ) {
    setPayment(nextPayment)

    setLookupId(
      nextPayment?.paymentId ?? '',
    )

    await refreshAttempts(
      nextPayment?.paymentId,
    )

    if (message) {
      setNotice(message)
    }
  }

  async function runAction(
    action,
    callback,
  ) {
    setError('')
    setNotice('')
    setBusy(action)

    try {
      await callback()
    } catch (actionError) {
      setError(
        actionError.message ||
          'Something went wrong.',
      )
    } finally {
      setBusy('')
    }
  }

  function handleCreate() {
    return runAction(
      'create',
      async () => {
        const currency =
          normalizeCurrency(form.currency)

        const amount =
          validateAmount(
            form.amount,
            currency,
          )

        const created =
          await createPayment({
            merchantId:
              form.merchantId.trim(),

            idempotencyKey:
              form.idempotencyKey.trim(),

            orderId:
              form.orderId.trim(),

            customerId:
              form.customerId.trim(),

            amount,
            currency,
          })

        await applyPayment(
          created,
          'Payment created successfully.',
        )
      },
    )
  }

  function handleLoad() {
    return runAction(
      'load',
      async () => {
        const paymentId =
          lookupId.trim()

        if (!paymentId) {
          throw new Error(
            'Enter a payment ID first.',
          )
        }

        const loaded =
          await getPayment(paymentId)

        await applyPayment(
          loaded,
          'Payment loaded.',
        )
      },
    )
  }

  function handleProcess() {
    return runAction(
      'process',
      async () => {
        if (!payment?.paymentId) {
          throw new Error(
            'Create or load a payment first.',
          )
        }

        const processed =
          await processPayment(
            payment.paymentId,
          )

        await applyPayment(
          processed,
          'Payment processed.',
        )
      },
    )
  }

  function handleRetry() {
    return runAction(
      'retry',
      async () => {
        if (!payment?.paymentId) {
          throw new Error(
            'Create or load a payment first.',
          )
        }

        const retried =
          await retryPayment(
            payment.paymentId,
          )

        await applyPayment(
          retried,
          'Payment retry completed.',
        )
      },
    )
  }

  function handleRefresh() {
    return runAction(
      'refresh',
      async () => {
        if (!payment?.paymentId) {
          throw new Error(
            'Create or load a payment first.',
          )
        }

        const loaded =
          await getPayment(
            payment.paymentId,
          )

        await applyPayment(
          loaded,
          'Payment refreshed.',
        )
      },
    )
  }

  function resetForm() {
    setForm(buildDefaults())
    setPayment(null)
    setAttempts([])
    setLookupId('')
    setError('')
    setNotice('Form reset.')
  }

  return (
    <main className="page-shell">
      <header className="hero">
        <div>
          <p className="eyebrow">
            PAYMENT PLATFORM
          </p>

          <h1>
            Payment Operations Console
          </h1>

          <p className="subtitle">
            Exercise the real payment
            lifecycle: create, inspect,
            process, retry, and inspect
            gateway attempts.
          </p>
        </div>

        <div className="hero-meta">
          <span className="env-chip">
            LOCAL
          </span>

          <span className="env-chip subtle">
            API /api/v1/payments
          </span>
        </div>
      </header>

      <div className="layout">
        <section className="panel">
          <div className="panel-header">
            <div>
              <p className="section-kicker">
                1 · Create
              </p>

              <h2>
                Payment request
              </h2>
            </div>

            <button
              className="button ghost"
              type="button"
              onClick={resetForm}
              disabled={Boolean(busy)}
            >
              Reset
            </button>
          </div>

          <div className="form-grid">
            <Field
              label="Merchant ID"
              value={form.merchantId}
              onChange={(value) =>
                setFormValue(
                  'merchantId',
                  value,
                )
              }
            />

            <Field
              label="Idempotency Key"
              value={form.idempotencyKey}
              onChange={(value) =>
                setFormValue(
                  'idempotencyKey',
                  value,
                )
              }
            />

            <Field
              label="Order ID"
              value={form.orderId}
              onChange={(value) =>
                setFormValue(
                  'orderId',
                  value,
                )
              }
            />

            <Field
              label="Customer ID"
              value={form.customerId}
              onChange={(value) =>
                setFormValue(
                  'customerId',
                  value,
                )
              }
            />

            <Field
              label="Amount"
              value={form.amount}
              onChange={(value) =>
                setFormValue(
                  'amount',
                  value,
                )
              }
              placeholder="499.50"
            />

            <Field
              label="Currency"
              value={form.currency}
              onChange={(value) =>
                setFormValue(
                  'currency',
                  value,
                )
              }
              placeholder="INR"
            />
          </div>

          <div className="amount-note">
            Enter the amount in major units,
            for example{' '}
            <strong>
              499.50 INR
            </strong>
            . The backend converts it to
            internal minor units.
          </div>

          <button
            className="button primary full"
            type="button"
            onClick={handleCreate}
            disabled={Boolean(busy)}
          >
            {busy === 'create'
              ? 'Creating…'
              : 'Create Payment'}
          </button>
        </section>

        <section className="panel">
          <div className="panel-header">
            <div>
              <p className="section-kicker">
                2 · Inspect
              </p>

              <h2>
                Payment details
              </h2>
            </div>

            <StatusBadge
              status={payment?.status}
            />
          </div>

          <div className="lookup-row">
            <input
              className="lookup-input"
              value={lookupId}
              onChange={(event) =>
                setLookupId(
                  event.target.value,
                )
              }
              placeholder="Payment ID"
            />

            <button
              className="button secondary"
              type="button"
              onClick={handleLoad}
              disabled={Boolean(busy)}
            >
              {busy === 'load'
                ? 'Loading…'
                : 'View Payment'}
            </button>

            <button
              className="button ghost"
              type="button"
              onClick={handleRefresh}
              disabled={
                Boolean(busy) ||
                !payment?.paymentId
              }
            >
              Refresh
            </button>
          </div>

          {payment ? (
            <div className="detail-grid">
              <div>
                <span>
                  Payment ID
                </span>

                <strong className="mono">
                  {payment.paymentId}
                </strong>
              </div>

              <div>
                <span>
                  Amount
                </span>

                <strong>
                  {formatMoney(
                    payment.amount,
                    payment.currency,
                  )}
                </strong>
              </div>

              <div>
                <span>
                  Currency
                </span>

                <strong>
                  {payment.currency}
                </strong>
              </div>

              <div>
                <span>
                  Merchant
                </span>

                <strong className="mono">
                  {payment.merchantId}
                </strong>
              </div>

              <div>
                <span>
                  Order
                </span>

                <strong className="mono">
                  {payment.orderId}
                </strong>
              </div>

              <div>
                <span>
                  Customer
                </span>

                <strong className="mono">
                  {payment.customerId}
                </strong>
              </div>

              <div>
                <span>
                  Idempotency Key
                </span>

                <strong className="mono">
                  {payment.idempotencyKey}
                </strong>
              </div>

              <div>
                <span>
                  Updated
                </span>

                <strong>
                  {formatDate(
                    payment.updatedAt,
                  )}
                </strong>
              </div>
            </div>
          ) : (
            <div className="empty-state">
              <strong>
                No payment selected.
              </strong>

              <span>
                Create a payment or paste a
                payment ID to inspect one.
              </span>
            </div>
          )}

          <div className="action-row">
            <button
              className="button primary"
              type="button"
              onClick={handleProcess}
              disabled={
                !canProcess ||
                Boolean(busy)
              }
            >
              {busy === 'process'
                ? 'Processing…'
                : 'Process Payment'}
            </button>

            <button
              className="button warning"
              type="button"
              onClick={handleRetry}
              disabled={
                !canRetry ||
                Boolean(busy)
              }
            >
              {busy === 'retry'
                ? 'Retrying…'
                : 'Retry Payment'}
            </button>
          </div>

          {latestAttempt && (
            <div className="latest-attempt">
              <div>
                <span>
                  Latest attempt
                </span>

                <strong>
                  #
                  {
                    latestAttempt.attemptNumber
                  }{' '}
                  ·{' '}
                  {latestAttempt.status}
                </strong>
              </div>

              <div className="latest-attempt-detail">
                {latestAttempt.gatewayTransactionId ||
                  latestAttempt.failureCode ||
                  'No gateway result yet'}
              </div>
            </div>
          )}
        </section>

        <section className="panel attempts-panel">
          <div className="panel-header">
            <div>
              <p className="section-kicker">
                3 · Attempts
              </p>

              <h2>
                Gateway attempts
              </h2>
            </div>

            <span className="attempt-count">
              {attempts.length}{' '}
              attempt
              {attempts.length === 1
                ? ''
                : 's'}
            </span>
          </div>

          {attempts.length ? (
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>Status</th>
                    <th>Gateway</th>
                    <th>Transaction</th>
                    <th>Failure</th>
                    <th>Created</th>
                    <th>Completed</th>
                  </tr>
                </thead>

                <tbody>
                  {attempts.map(
                    (attempt) => (
                      <tr
                        key={
                          attempt.attemptId
                        }
                      >
                        <td>
                          {
                            attempt.attemptNumber
                          }
                        </td>

                        <td>
                          <StatusBadge
                            status={
                              attempt.status
                            }
                          />
                        </td>

                        <td>
                          {attempt.gateway ||
                            '—'}
                        </td>

                        <td className="mono">
                          {
                            attempt.gatewayTransactionId ||
                            '—'
                          }
                        </td>

                        <td>
                          {attempt.failureCode ? (
                            <div className="failure-cell">
                              <strong>
                                {
                                  attempt.failureCode
                                }
                              </strong>

                              <span>
                                {
                                  attempt.failureMessage ||
                                  '—'
                                }
                              </span>
                            </div>
                          ) : (
                            '—'
                          )}
                        </td>

                        <td>
                          {formatDate(
                            attempt.createdAt,
                          )}
                        </td>

                        <td>
                          {formatDate(
                            attempt.completedAt,
                          )}
                        </td>
                      </tr>
                    ),
                  )}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="empty-state compact">
              <strong>
                No attempts to show.
              </strong>

              <span>
                The first attempt appears
                after payment creation.
              </span>
            </div>
          )}
        </section>
      </div>

      {(error || notice) && (
        <div
          className={`toast ${
            error ? 'error' : 'success'
          }`}
          role="status"
        >
          {error || notice}
        </div>
      )}
    </main>
  )
}