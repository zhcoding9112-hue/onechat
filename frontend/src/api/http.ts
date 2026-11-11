import { showToast } from 'vant'
import { ErrorCode, type ApiFieldError, type ApiResponse } from './types'

/** 微信网页授权入口。未认证时由前端跳转至此，由后端发起授权。 */
export const AUTH_ENTRY = '/api/auth/wechat/authorize'

/** 业务请求失败。调用方若需自行处理，可捕获本异常。 */
export class ApiError extends Error {
  readonly code: string
  readonly httpStatus: number
  readonly errors?: ApiFieldError[]

  constructor(code: string, message: string, httpStatus: number, errors?: ApiFieldError[]) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.httpStatus = httpStatus
    this.errors = errors
  }
}

export interface RequestOptions extends Omit<RequestInit, 'body'> {
  /** JSON 请求体，自动序列化并带上 Content-Type。 */
  body?: unknown
  /** 失败时是否自动弹出提示，默认 true。 */
  toastOnError?: boolean
  /** 未认证时是否自动跳转重新授权，默认 true。 */
  redirectOnUnauthenticated?: boolean
}

/** 同一时刻只允许触发一次授权跳转，避免并发请求连环跳。 */
let redirecting = false

export function redirectToAuth(): void {
  if (redirecting) return
  redirecting = true
  const back = window.location.pathname + window.location.search
  window.location.assign(`${AUTH_ENTRY}?redirect=${encodeURIComponent(back)}`)
}

export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const {
    body,
    headers,
    toastOnError = true,
    redirectOnUnauthenticated = true,
    ...rest
  } = options

  const init: RequestInit = {
    // 会话走 Cookie，必须带上凭证。
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...headers,
    },
    ...rest,
  }
  if (body !== undefined) {
    init.body = JSON.stringify(body)
  }

  let res: Response
  try {
    res = await fetch(url, init)
  } catch {
    const err = new ApiError(ErrorCode.INTERNAL_ERROR, '网络连接失败，请检查网络后重试', 0)
    if (toastOnError) showToast(err.message)
    throw err
  }

  let payload: ApiResponse<T> | null = null
  try {
    payload = (await res.json()) as ApiResponse<T>
  } catch {
    payload = null
  }

  if (res.ok && payload?.success) {
    return payload.data as T
  }

  const code = payload?.code ?? ErrorCode.INTERNAL_ERROR
  const message = payload?.message ?? '服务暂时不可用，请稍后再试'
  const err = new ApiError(code, message, res.status, payload?.errors)

  if (code === ErrorCode.UNAUTHENTICATED) {
    // 未认证不弹错误提示，直接重新发起授权，避免用户看到无意义的报错。
    if (redirectOnUnauthenticated) redirectToAuth()
    throw err
  }

  if (toastOnError) {
    // 参数校验失败优先展示第一条字段提示，比通用文案更有用。
    showToast(err.errors?.[0]?.message ?? message)
  }
  throw err
}

export const http = {
  get: <T>(url: string, options?: RequestOptions) => request<T>(url, { ...options, method: 'GET' }),
  post: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>(url, { ...options, method: 'POST', body }),
}
