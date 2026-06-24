export type PaymentMethod =
  | "CARD"
  | "BANK_TRANSFER"
  | "DIGITAL_WALLET"
  | "ACH"
  | "UPI";

export type FraudStatus = "SAFE" | "REVIEW" | "BLOCK";

export interface RiskPaymentEvent {
  paymentId: string;
  amount: string;
  currency: string;
  accountId: string;
  merchantId: string;
  destinationAccountId: string;
  paymentMethod: PaymentMethod;
  occurredAt: string;
  status: FraudStatus;
  aiReasoning: string;
  correlationId: string;
  riskScore?: number;
  rulesTriggered?: string;
}

export interface GeminiDecisionJson {
  decision: FraudStatus;
  reasoning: string;
}
