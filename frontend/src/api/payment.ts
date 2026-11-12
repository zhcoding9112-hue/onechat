import { http } from './http'

export interface ProviderOption {
  code: string
  displayName: string
}

export interface PaymentInstruction {
  provider: string
  kind: 'DISPLAY_AND_UPLOAD' | 'INVOKE_SDK'
  /** 形状随 kind 变化，由各分支自行解读。 */
  payload: Record<string, unknown>
}

export interface ProofView {
  id: number
  url: string
  uploadedAt: string
}

export interface PaymentPage {
  orderRef: string
  payableCents: number
  status: string
  statusLabel: string
  providers: ProviderOption[]
  instruction: PaymentInstruction | null
  proofs: ProofView[]
}

/** 人工收款的 payload 形状。 */
export interface ManualPayload {
  orderRef: string
  payableCents: number
  accountName: string | null
  accountNo: string | null
  bankName: string | null
  qrUrl: string | null
  tips: string | null
  ready: boolean
}

export const paymentApi = {
  page: (orderId: number, provider?: string) =>
    http.get<PaymentPage>(
      `/api/orders/${orderId}/payment${provider ? `?provider=${encodeURIComponent(provider)}` : ''}`,
    ),

  /** 凭证走 multipart，不能用统一的 JSON 封装。 */
  uploadProof: async (orderId: number, file: File): Promise<ProofView> => {
    const form = new FormData()
    form.append('file', file)
    const res = await fetch(`/api/orders/${orderId}/payment/proofs`, {
      method: 'POST',
      credentials: 'include',
      body: form,
    })
    const payload = await res.json()
    if (!res.ok || !payload.success) {
      throw new Error(payload.message ?? '上传失败')
    }
    return payload.data as ProofView
  },
}
