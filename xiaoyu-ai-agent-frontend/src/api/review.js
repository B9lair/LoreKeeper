import http from './http';

// 一致性审查，返回 {conflicts, foreshadowReminders}（含 LLM 抽取 + 归并，长请求，放宽超时）
export function reviewChapter(chapterNo, chapterText) {
  return http.post('/ai/lore/review', { chapterNo, chapterText }, { timeout: 300000 })
    .then(({ data }) => data);
}

// 待确认队列（只含 pending）
export function listPending() {
  return http.get('/pending/list').then(({ data }) => data);
}

// 采纳新值（后端更新设定 + 重建索引）
export function acceptPending(id) {
  return http.post(`/pending/${id}/accept`).then(({ data }) => data);
}

// 驳回（保留旧值）
export function rejectPending(id) {
  return http.post(`/pending/${id}/reject`).then(({ data }) => data);
}
