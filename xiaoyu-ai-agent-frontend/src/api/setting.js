import http from './http';

// 列出当前作品全部设定条目
export function listSettings() {
  return http.get('/setting/list').then(({ data }) => data);
}

// 保存设定（id 为 null = 新建，非 null = 更新），返回 {id, version, conflicts}
// 触发冲突扫描（LLM 比对章节库），长请求，放宽超时
export function saveSetting(payload) {
  return http.post('/setting/save', payload, { timeout: 300000 }).then(({ data }) => data);
}

// 删除设定
export function deleteSetting(id) {
  return http.delete(`/setting/${id}`);
}
