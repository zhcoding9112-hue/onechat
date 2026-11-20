import { http } from '@/api/http'

/** 某主播的评分聚合。无评分时 average 为 null，不是 0——两者不是一回事。 */
export interface SurveyAggregate {
  count: number
  average: number | null
  /** 1 到 5 各分值的人数，键是分数的字符串形式。 */
  distribution: Record<string, number>
}

/** 运营可见的逐单评分。粉丝只给出 id，要找人去订单管理里按订单号查。 */
export interface SurveyDetailEntry {
  orderId: number
  orderRef: string
  fanUserId: number
  score: number
  createdAt: string
}

export interface TalentSurveyDetail {
  aggregate: SurveyAggregate
  details: SurveyDetailEntry[]
}

export const surveyAdminApi = {
  /**
   * 全部主播的聚合评分，键是主播 id。
   *
   * 接口不带昵称，页面自己拿主播列表join——改接口会动到已有断言，
   * 而这个 join 在前端一行就够。
   */
  overview: () => http.get<Record<string, SurveyAggregate>>('/api/admin/survey/overview'),

  talentDetail: (talentId: number) =>
    http.get<TalentSurveyDetail>(`/api/admin/talents/${talentId}/survey`),
}
