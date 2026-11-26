import { demoVoices } from './voice-manifest';

const enabled =
  import.meta.env.DEV && import.meta.env.VITE_VISUAL_DEMO === 'true';

/** 示例录音只覆盖当前视觉演示的对应资料，不修改运营上传记录。 */
export function demoVoiceFor(id: number, nickname: string) {
  if (!enabled) return undefined;
  return demoVoices.find(
    (voice) => voice.id === id && voice.nickname === nickname,
  );
}

export function withDemoVoice<
  T extends {
    id: number;
    nickname: string;
    audioUrl: string | null;
    audioSeconds: number | null;
  },
>(talent: T): T {
  const voice = demoVoiceFor(talent.id, talent.nickname);
  return voice
    ? { ...talent, audioUrl: voice.url, audioSeconds: voice.seconds }
    : talent;
}
