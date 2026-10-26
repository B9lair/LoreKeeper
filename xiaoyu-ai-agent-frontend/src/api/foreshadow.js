import http from './http';

// 列出当前作品全部伏笔
export function listForeshadows() {
  return http.get('/foreshadow/list').then(({ data }) => data);
}

// 新建伏笔
export function createForeshadow(content, plantedChapter) {
  return http.post('/foreshadow/create', { content, plantedChapter }).then(({ data }) => data);
}

// 手动回收伏笔（open → closed）
export function resolveForeshadow(id, resolvedChapter) {
  return http.post(`/foreshadow/${id}/resolve`, { resolvedChapter }).then(({ data }) => data);
}

// 删除伏笔（删表 + 删向量索引）
export function deleteForeshadow(id) {
  return http.delete(`/foreshadow/${id}`);
}
