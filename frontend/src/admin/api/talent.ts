import { http } from '@/api/http'

/** 性别取值与后端 talent.gender 列保持一致；未填写为 null。 */
export type Gender = 'FEMALE' | 'MALE'

export interface TalentAdminView {
  id: number
  userId: number | null
  nickname: string
  coverUrl: string | null
  intro: string | null
  audioUrl: string | null
  audioSeconds: number | null
  online: boolean
  listed: boolean
  sortOrder: number
  tags: string[]
  gender: Gender | null
  age: number | null
  region: string | null
  /** 0 到 2 条，运营自填。 */
  promoTags: string[]
}

export interface TalentProfilePayload {
  nickname?: string | null
  intro?: string | null
  coverUrl?: string | null
  sortOrder?: number | null
  gender?: Gender | null
  age?: number | null
  region?: string | null
  promoTags?: string[] | null
}

/** 上传结果，与后端 TalentMediaController.UploadResult 一致。 */
export interface MediaUploadResult {
  url: string
  sizeBytes: number
  contentType: string
}

/*
 * 下面四个限制与后端 TalentMediaController 的常量一一对应。
 * 这里重复一份是为了在选中文件的瞬间就拦住，而不是让运营传完 20MB 再看到报错；
 * 真正说了算的仍然是后端——改限制时两边都要改。
 */
export const MAX_COVER_BYTES = 2 * 1024 * 1024
export const MAX_AUDIO_BYTES = 5 * 1024 * 1024
export const COVER_TYPES = ['image/jpeg', 'image/png', 'image/webp']
export const AUDIO_TYPES = [
  'audio/mpeg',
  'audio/mp3',
  'audio/mp4',
  'audio/aac',
  'audio/x-m4a',
  'audio/m4a',
  'audio/wav',
  'audio/x-wav',
]

const BASE = '/api/admin/talents'

export interface ImportRowResult {
  line: number
  ok: boolean
  talentId: number | null
  nickname: string
  message: string
  /**
   * 价格设上了、但粉丝端此刻看不见的档位（所属项目或档位本身已停用），
   * 每项形如「call:m30（项目已停用）」。
   *
   * 后端给的是结构化字段，前端据此提示，不去解析 message 里的中文。
   */
  hiddenSpecs: string[]
}

export interface ImportResult {
  dryRun: boolean
  created: number
  failed: number
  rows: ImportRowResult[]
}

export const talentAdminApi = {
  list: () => http.get<TalentAdminView[]>(BASE),
  detail: (id: number) => http.get<TalentAdminView>(`${BASE}/${id}`),
  create: (payload: TalentProfilePayload) => http.post<TalentAdminView>(BASE, payload),
  update: (id: number, payload: TalentProfilePayload) =>
    http.put<TalentAdminView>(`${BASE}/${id}`, payload),
  setListed: (id: number, listed: boolean) =>
    http.put<TalentAdminView>(`${BASE}/${id}/listed/${listed}`),
  setTags: (id: number, tags: string[]) =>
    http.put<TalentAdminView>(`${BASE}/${id}/tags`, { tags }),

  /**
   * 批量导入花名册。
   *
   * dryRun=true 只校验不写库。几十行里有一个错字，先试算一遍比导进去再收拾划算。
   * 任何一行有问题都整批不导——导到一半最难收拾。
   */
  importCsv: (csv: string, dryRun: boolean) =>
    http.post<ImportResult>(`${BASE}/import`, { csv, dryRun }),

  /** 上传封面图。成功后旧封面文件由后端删除。 */
  uploadCover: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return http.upload<MediaUploadResult>(`${BASE}/${id}/media/cover`, form)
  },

  /**
   * 上传试听片段。时长必须由前端读出后一并提交——后端不解析音频文件。
   */
  uploadAudio: (id: number, file: File, seconds: number) => {
    const form = new FormData()
    form.append('file', file)
    form.append('seconds', String(seconds))
    return http.upload<MediaUploadResult>(`${BASE}/${id}/media/audio`, form)
  },
}
