"use client";

const CURRENT_SESSION_PAYMENT_IDS_KEY =
  "fraud-engine.current-session.payment-ids";
export const CURRENT_SESSION_UPDATED_EVENT =
  "fraud-engine-current-session-updated";

export function clearCurrentSessionPaymentIds() {
  sessionStorage.removeItem(CURRENT_SESSION_PAYMENT_IDS_KEY);
  notifyCurrentSessionUpdated();
}

export function getCurrentSessionPaymentIds() {
  return readPaymentIds();
}

export function recordCurrentSessionPaymentId(paymentId: string) {
  const normalizedPaymentId = paymentId.trim();
  if (!normalizedPaymentId) {
    return;
  }

  const paymentIds = readPaymentIds();
  if (!paymentIds.includes(normalizedPaymentId)) {
    paymentIds.unshift(normalizedPaymentId);
  }

  sessionStorage.setItem(
    CURRENT_SESSION_PAYMENT_IDS_KEY,
    JSON.stringify(paymentIds.slice(0, 100)),
  );
  notifyCurrentSessionUpdated();
}

function notifyCurrentSessionUpdated() {
  window.dispatchEvent(new Event(CURRENT_SESSION_UPDATED_EVENT));
}

function readPaymentIds() {
  const storedValue = sessionStorage.getItem(CURRENT_SESSION_PAYMENT_IDS_KEY);
  if (!storedValue) {
    return [];
  }

  try {
    const parsedValue: unknown = JSON.parse(storedValue);
    return Array.isArray(parsedValue)
      ? parsedValue.filter((value): value is string => typeof value === "string")
      : [];
  } catch {
    return [];
  }
}
