const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? ''

const REQUEST_TIMEOUT_MS = 15000

async function request(path, options = {}) {
  const controller = new AbortController()

  const timeoutId = window.setTimeout(() => {
    controller.abort()
  }, REQUEST_TIMEOUT_MS)

  try {
    const response = await fetch(
      `${API_BASE_URL}${path}`,
      {
        ...options,
        signal: controller.signal,
        headers: {
          Accept: 'application/json',

          ...(options.body
            ? {
                'Content-Type': 'application/json',
              }
            : {}),

          ...(options.headers ?? {}),
        },
      },
    )

    const text = await response.text()

    let body = null

    try {
      body = text
        ? JSON.parse(text)
        : null
    } catch {
      body = text || null
    }

    if (!response.ok) {
      const message =
        body?.message ||
        body?.error ||
        body?.detail ||
        (typeof body === 'string'
          ? body
          : null) ||
        `Request failed with HTTP ${response.status}`

      const error = new Error(message)

      error.status = response.status
      error.body = body

      throw error
    }

    return body
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error(
        'The backend request timed out.',
        {
          cause: error,
        },
      )
    }

    throw error
  } finally {
    window.clearTimeout(timeoutId)
  }
}

export function createPayment({
  merchantId,
  idempotencyKey,
  orderId,
  customerId,
  amount,
  currency,
}) {
  return request(
    '/api/v1/payments',
    {
      method: 'POST',

      headers: {
        'X-Merchant-Id': merchantId,
        'Idempotency-Key': idempotencyKey,
      },

      body: JSON.stringify({
        orderId,
        customerId,

        // Amount is sent in major units.
        // Backend converts BigDecimal to minor units.
        amount,

        currency,
      }),
    },
  )
}

export function getPayment(paymentId) {
  return request(
    `/api/v1/payments/${encodeURIComponent(
      paymentId,
    )}`,
  )
}

export function getAttempts(paymentId) {
  return request(
    `/api/v1/payments/${encodeURIComponent(
      paymentId,
    )}/attempts`,
  )
}

export function processPayment(paymentId) {
  return request(
    `/api/v1/payments/${encodeURIComponent(
      paymentId,
    )}/process`,
    {
      method: 'POST',
    },
  )
}

export function retryPayment(paymentId) {
  return request(
    `/api/v1/payments/${encodeURIComponent(
      paymentId,
    )}/retry`,
    {
      method: 'POST',
    },
  )
}