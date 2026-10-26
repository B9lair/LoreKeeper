import http from './http';

// 列出当前用户的历史会话（按最近更新倒序）
export function listConversations() {
  return http.get('/chat/conversations').then(({ data }) => data);
}

// 读取某会话的完整历史消息 [{ role, content }]
export function getConversation(chatId) {
  return http.get(`/chat/conversations/${encodeURIComponent(chatId)}`).then(({ data }) => data);
}

// 删除某会话
export function deleteConversation(chatId) {
  return http.delete(`/chat/conversations/${encodeURIComponent(chatId)}`).then(({ data }) => data);
}
