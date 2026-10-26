// 意图元信息（对话台 / 测试台共用）
export const intentMeta = {
  WRITE: { label: '设定提示', cls: 'intent-write' },
  REVIEW: { label: '一致性审查', cls: 'intent-review' },
  QUERY: { label: '世界观问答', cls: 'intent-query' },
  FORESHADOW: { label: '伏笔追踪', cls: 'intent-foreshadow' },
  CHAT: { label: '闲聊', cls: 'intent-chat' }
};

export function intentLabel(i) {
  return intentMeta[i]?.label || i;
}

export function intentCls(i) {
  return intentMeta[i]?.cls || 'intent-chat';
}
