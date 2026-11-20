import { http } from '@/api/http'

/** GRID 是九宫格服务入口，BANNER 是首页横幅轮播。 */
export type HomeEntryType = 'GRID' | 'BANNER'

export interface HomeEntry {
  id: number
  entryType: HomeEntryType
  title: string
  subtitle: string | null
  imageUrl: string | null
  targetUrl: string | null
  sortOrder: number
  enabled: boolean
  /** 生效窗口，两端都可为空表示长期有效。 */
  startAt: string | null
  endAt: string | null
  createdAt: string
  updatedAt: string
}

export interface HomeEntryPayload {
  entryType: HomeEntryType
  title: string
  subtitle?: string | null
  imageUrl?: string | null
  targetUrl?: string | null
  sortOrder?: number | null
  enabled?: boolean | null
  startAt?: string | null
  endAt?: string | null
}

export interface ImageUploadResult {
  url: string
  sizeBytes: number
  contentType: string
}

/* 与后端 AdminImageUploadController 的常量对应，改限制时两边都要改。 */
export const MAX_IMAGE_BYTES = 2 * 1024 * 1024
export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']

const BASE = '/api/admin/home-entries'

export const homeEntryAdminApi = {
  list: () => http.get<HomeEntry[]>(BASE),

  create: (payload: HomeEntryPayload) => http.post<HomeEntry>(BASE, payload),

  update: (id: number, payload: HomeEntryPayload) =>
    http.put<HomeEntry>(`${BASE}/${id}`, payload),

  remove: (id: number) => http.delete<void>(`${BASE}/${id}`),

  /** 上传图片，拿到可以直接填进 imageUrl 的地址。 */
  uploadImage: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    form.append('category', 'home-entry')
    return http.upload<ImageUploadResult>('/api/admin/uploads/image', form)
  },
}
