import { fetchEventSource } from '@microsoft/fetch-event-source';
import { readAuth } from './http';

const apiBase = () =>
  process.env.NODE_ENV === 'production' ? '/api' : 'http://localhost:8080/api';

/**
 * 启动一次创作助手流式请求（SSE 带 JWT，命名事件分流）。
 * 后端 /ai/lore/chat 契约：
 *   event:intent → onIntent(data) 路由出的意图（WRITE/REVIEW/QUERY/FORESHADOW/CHAT，一条，最先）
 *   event:tool   → onTool(data)   每步 Agent 工具执行结果（多条）
 *   event:answer → onAnswer(data) 最终回答（设定提示卡，一条）
 *   event:error  → onError(err)   业务错误
 *   complete     → onDone()       正常关闭
 *
 * @returns {AbortController|null} 供「停止」使用；未登录返回 null
 */
export function streamAssist(message, { onIntent, onTool, onAnswer, onError, onDone }, chatId) {
  const auth = readAuth();
  if (!auth || !auth.token) {
    onError(new Error('未登录，请先登录'));
    return null;
  }

  // token 塞 header，绝不塞 URL query（2.7 安全叙事）；chatId 用于多轮记忆隔离
  const params = new URLSearchParams({ message });
  if (chatId) params.set('chatId', chatId);
  const url = `${apiBase()}/ai/lore/chat?${params.toString()}`;
  const ctrl = new AbortController();

  // fetch-event-source 在 onclose 后会默认自动重连（把服务器正常关流当意外断线），
  // 必须在 onclose 里抛异常阻止，否则同一 message 会被重发、再跑一次 Agent（重复回答 + 重复落盘）
  let finished = false;

  fetchEventSource(url, {
    method: 'GET',
    headers: {
      Authorization: `Bearer ${auth.token}`,
      Accept: 'text/event-stream'
    },
    signal: ctrl.signal,
    // 连接建立后校验 HTTP 状态与 Content-Type；非 2xx / 非 SSE 直接抛错
    // （兜住「意图路由 LLM 挂 → 500」这类连接前就失败的情况）
    async onopen(response) {
      if (response.ok && (response.headers.get('content-type') || '').includes('text/event-stream')) {
        return;
      }
      throw new Error(`连接失败：HTTP ${response.status}`);
    },
    onmessage(ev) {
      if (ev.event === 'intent') onIntent?.(ev.data);
      else if (ev.event === 'tool') onTool(ev.data);
      else if (ev.event === 'answer') onAnswer(ev.data);
      else if (ev.event === 'error') onError(new Error(ev.data));
      // 其余未知事件忽略
    },
    onclose() {
      finished = true;
      onDone();
      throw new Error('sse-stream-ended');
    },
    onerror(err) {
      if (finished) {
        // 流已正常结束，这里的异常只是「阻止重连」的副作用，静默停止
        throw err;
      }
      onError(err);
      // 禁用自动重连：后端 Agent 是一次性执行，重连会重复跑 + 重复计费
      throw err;
    }
  });

  return ctrl;
}
