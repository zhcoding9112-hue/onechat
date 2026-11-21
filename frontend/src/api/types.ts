/** 与后端 ApiResponse 一一对应。 */
export interface ApiFieldError {
  field: string
  message: string
}

export interface ApiResponse<T> {
  success: boolean
  code: string
  message: string
  data?: T
  errors?: ApiFieldError[]
}

/** 与后端 ErrorCode 一一对应。 */
export const ErrorCode = {
  UNAUTHENTICATED: 'UNAUTHENTICATED',
  FORBIDDEN: 'FORBIDDEN',
  INVALID_PARAM: 'INVALID_PARAM',
  BUSINESS_REJECTED: 'BUSINESS_REJECTED',
  NOT_FOUND: 'NOT_FOUND',
  INTERNAL_ERROR: 'INTERNAL_ERROR',
} as const

export type ErrorCodeValue = (typeof ErrorCode)[keyof typeof ErrorCode]

/** 分页响应。与后端 common.api.PageResponse 一致。 */
export interface PageResponse<T> {
  items: T[]
  /** 符合条件的总条数，不受分页影响。 */
  total: number
  hasMore: boolean
}
