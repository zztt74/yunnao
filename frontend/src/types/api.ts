export interface ApiResponse<T> {
  code: string
  message: string
  data: T
  traceId: string
}

export interface PageResponse<T> {
  items: T[]
  page: number
  pageSize: number
  total: number
  totalPages: number
}
