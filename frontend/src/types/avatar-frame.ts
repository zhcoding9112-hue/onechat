// 头像装饰仅影响展示，不代表主播等级或服务权限。
export type AvatarFrame = 'magic' | 'lucky';
export const avatarFrameNames: Record<AvatarFrame, string> = {
  magic: '魔法奇遇',
  lucky: '好运蝴蝶结',
};
