import http from './http';

// 列出当前用户全部作品
export function listWorks() {
  return http.get('/work/list').then(({ data }) => data);
}

// 新建作品，返回完整作品记录
export function createWork(title, genre) {
  return http.post('/work/create', { title, genre }).then(({ data }) => data);
}

// 切换作品，返回 {token, userId, workId}（页面层 writeAuth 换 token 后 reload）
export function switchWork(targetWorkId) {
  return http.post('/work/switch', { targetWorkId }).then(({ data }) => data);
}

// 更新作品信息（标题/类型/简介/作者），返回更新后的完整作品记录
export function updateWork(id, payload) {
  return http.put(`/work/${id}`, payload).then(({ data }) => data);
}

// 删除作品（级联删除该作品全部章节/设定/伏笔/时间线/向量）
export function deleteWork(id) {
  return http.delete(`/work/${id}`);
}
