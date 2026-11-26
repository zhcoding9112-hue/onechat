import { reactive, ref } from 'vue';
import type { FeedCommentView, FeedPostView } from '@/api/feed';
import { previewTalents, visualDemoEnabled } from './marketplace';

export type DisplayFeedPost = FeedPostView & {
  demo?: boolean;
  demoTiles?: number[];
};
const stories: { content: string; tiles: number[] }[] = [
  {
    content: '今天的快乐是一杯热拿铁。第一口留给自己，第二口想分享给你 ☕',
    tiles: [0],
  },
  {
    content: '出门晒了二十分钟太阳，感觉整个人都充上电啦。你今天有好好休息吗？',
    tiles: [1],
  },
  {
    content: '路过花店，给普通的一天买了一点浪漫。粉色真的会让心情变好 🌷',
    tiles: [2],
  },
  {
    content: '发现一家很好吃的草莓蛋糕店！甜度刚刚好，快乐也是。',
    tiles: [3, 0],
  },
  {
    content: '下雨的下午适合慢一点。翻几页书，喝口热茶，把烦恼先放一放。',
    tiles: [4],
  },
  {
    content: '赶上了今天最后一抹晚霞。想把这片天空装进口袋，送给晚归的你。',
    tiles: [5],
  },
  {
    content:
      '今日份小确幸：睡了个好觉、听到喜欢的歌、收到一句“在吗”。\n你的小确幸是什么？',
    tiles: [],
  },
  {
    content: '周末计划很简单：找个靠窗的位置，吃点甜的，再散散步。',
    tiles: [0, 3, 1],
  },
  {
    content: '散步的时候把耳机摘下来，才发现风吹树叶的声音也很好听。',
    tiles: [1, 5],
  },
  {
    content: '给自己买花不需要理由。也祝刷到这条的你，今天能遇见一件好事。',
    tiles: [2],
  },
  {
    content: '甜食是我的快乐开关。草莓蛋糕和水果茶，你先选哪一个？🍓',
    tiles: [3],
  },
  {
    content: '今晚想听你分享一首歌。最近一直循环的那首也可以，我会认真听。',
    tiles: [],
  },
  {
    content: '雨声、茶香、一本还没看完的书。一个人的下午也可以很充实。',
    tiles: [4, 2],
  },
  { content: '今天的落日像橘子汽水，连路上的风都是温柔的。', tiles: [5] },
  {
    content: '把手机放下，去公园走一圈。走着走着，就没有那么多想不通的事了。',
    tiles: [1],
  },
  {
    content: '咖啡续命，甜点治愈。认真生活的人，值得拥有一点小奖励。',
    tiles: [0, 3],
  },
  {
    content: '收集到三种喜欢的颜色：花的粉、树的绿、晚霞的橙。今天很圆满。',
    tiles: [2, 1, 5],
  },
  {
    content:
      '如果今天有一点点累，就允许自己早点休息。明天的事，明天再慢慢来。晚安 🌙',
    tiles: [],
  },
  {
    content: '窗外在下雨，杯子里是热茶。这样的天气，你会选择出门还是宅家？',
    tiles: [4],
  },
  {
    content: '没什么特别的事，只是今天的花开得很好看，想给你看看。',
    tiles: [2, 0],
  },
  {
    content: '散步走到了河边，刚好遇见天空换颜色。原来慢一点也能有惊喜。',
    tiles: [1, 5],
  },
  {
    content: '今天认真吃饭了，也认真开心了。你呢？记得给自己留一点时间。',
    tiles: [3],
  },
  {
    content:
      '分享一个小习惯：睡前写下今天值得感谢的三件小事。平凡的日子也会发光。',
    tiles: [],
  },
  {
    content: '把这一周喜欢的瞬间存起来。下一周，也要继续遇见美好。',
    tiles: [0, 2, 5],
  },
];
const now = Date.now();
export const previewFeedPosts = reactive<DisplayFeedPost[]>(
  visualDemoEnabled
    ? stories.map((story, i) => {
        const talent = previewTalents[i % previewTalents.length]!;
        return {
          id: -20001 - i,
          talentId: talent.id,
          nickname: talent.nickname,
          coverUrl: null,
          content: story.content,
          images: [],
          demo: true,
          demoTiles: story.tiles,
          liked: false,
          likeCount: 8 + ((i * 13) % 59),
          commentCount: 0,
          createdAt: new Date(now - (i * 43 + 5) * 60000).toISOString(),
        };
      })
    : [],
);

// 模拟互动仅保存在当前页面会话，与真实接口、消息和资金流程隔离。
export const previewFeedFollowing = ref<number[]>([]);
export const previewFeedComments = reactive<Record<number, FeedCommentView[]>>(
  {},
);
export function getPreviewPost(id: number) {
  return previewFeedPosts.find((p) => p.id === id);
}
export function togglePreviewFollow(id: number) {
  if (!previewTalents.some((t) => t.id === id)) return;
  previewFeedFollowing.value = previewFeedFollowing.value.includes(id)
    ? previewFeedFollowing.value.filter((value) => value !== id)
    : [...previewFeedFollowing.value, id];
}
export function togglePreviewLike(post: FeedPostView) {
  const item = getPreviewPost(post.id);
  if (!item) return;
  item.liked = !item.liked;
  item.likeCount += item.liked ? 1 : -1;
}
let commentSequence = 0;
export function addPreviewComment(postId: number, content: string) {
  const post = getPreviewPost(postId);
  if (!post) return;
  const comment: FeedCommentView = {
    id: -30001 - commentSequence++,
    userId: -1,
    nickname: '我（模拟）',
    avatarUrl: '/images/avatar-placeholder.svg',
    author: false,
    level: null,
    levelName: null,
    content,
    createdAt: new Date().toISOString(),
  };
  (previewFeedComments[postId] ??= []).push(comment);
  post.commentCount = previewFeedComments[postId]!.length;
}
