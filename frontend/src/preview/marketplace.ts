import { withDemoVoice } from './audio';
import type { TalentCard } from '@/api/talent';
import type { AvatarFrame } from '@/types/avatar-frame';
import type { EntryView, MarqueeItem } from '@/api/home';

// 仅用于开发预览；生产构建始终关闭，不写入业务库或发送交易请求。
export const visualDemoEnabled =
  import.meta.env.DEV && import.meta.env.VITE_VISUAL_DEMO === 'true';

// 仅供首页通知条演示，不代表真实打赏或账户流水。
export const previewTipMarquee: MarqueeItem[] = visualDemoEnabled
  ? [
      { nickname: '星禾', amountCents: 5200 },
      { nickname: '泡泡', amountCents: 520 },
      { nickname: '鹿眠', amountCents: 10000 },
      { nickname: '橙子', amountCents: 6600 },
      { nickname: '晚柠', amountCents: 8800 },
    ]
  : [];
export type PreviewTalent = TalentCard & {
  demo: true;
  avatarFrame: AvatarFrame | null;
  intro: string;
};
const frameStyles: (AvatarFrame | null)[] = ['lucky', 'magic', 'lucky', null];
const names = [
  '星禾',
  '鹿眠',
  '晚柠',
  '知夏',
  '阿屿',
  '南川',
  '小满',
  '梨涡',
  '桃酥',
  '听澜',
  '予安',
  '林深',
  '月白',
  '橙子',
];
const regions = ['江苏', '上海', '浙江', '广东', '四川', '福建'];
const services = [
  ['文字语音条', '语音连麦', '哄睡'],
  ['文字语音条', '叫醒', '买断专属'],
  ['语音连麦', '哄睡', '叫醒'],
];

export const previewTalents: PreviewTalent[] = visualDemoEnabled
  ? names.map((nickname, i) =>
      withDemoVoice<PreviewTalent>({
        // 排列 ID 使占位头像的男女与模拟资料一致，同时保持唯一负数。
        id: -(1200 + Math.floor(i / 6) * 6 + [0, 1, 3, 5, 2, 4][i % 6]!),
        nickname,
        coverUrl: null,
        online: i % 5 !== 4,
        gender: i % 6 === 4 || i % 6 === 5 ? 'MALE' : 'FEMALE',
        age: 22 + (i % 7),
        region: regions[i % regions.length]!,
        level: (i % 6) + 1,
        tags: services[i % services.length]!,
        promoTags: i % 2 ? ['温柔倾听', '轻松有趣'] : ['声音治愈', '认真陪伴'],
        audioUrl: null,
        audioSeconds: null,
        demo: true as const,
        avatarFrame: frameStyles[i % frameStyles.length]!,
        intro:
          '这是一份用于预览排版和头像装饰的模拟资料。可以浏览和收藏，不提供真实服务。',
      }),
    )
  : [];

export const previewBanners: EntryView[] = visualDemoEnabled
  ? [
      {
        id: -201,
        title: '把今天的心事，说给 TA 听',
        subtitle: '温柔陪伴 · 从一句你好开始',
        imageUrl: null,
        targetUrl: '/match',
      },
      {
        id: -202,
        title: '听见让你心动的声音',
        subtitle: '先听声音，再遇见喜欢的 TA',
        imageUrl: null,
        targetUrl: '/audition',
      },
      {
        id: -203,
        title: '每一个日常，都值得分享',
        subtitle: '去动态广场，发现有趣的灵魂',
        imageUrl: null,
        targetUrl: '/feed',
      },
    ]
  : [];
