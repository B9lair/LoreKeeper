import http from './http';

// 查看当前作品的分块（type: chapter|setting|foreshadow；chapterId 查某章子块）
export function listChunks({ type, chapterId } = {}) {
  const params = {};
  if (type) params.type = type;
  if (chapterId != null) params.chapterId = chapterId;
  return http.get('/rag/chunks', { params }).then(({ data }) => data);
}

// RAG 检索测试：query → top-k 命中（含 metadata）
export function searchRag(query, topK = 3) {
  return http.post('/rag/search', { query, topK }).then(({ data }) => data);
}
