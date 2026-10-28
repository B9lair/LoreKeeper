import http from './http';

// 列出当前作品已有角色
export function listCharacters() {
  return http.get('/character/list').then(({ data }) => data);
}

// 回填候选：world_setting 实体名（尚未成为角色，含地点等需人工裁决）
export function listCharacterCandidates() {
  return http.get('/character/candidates').then(({ data }) => data);
}

// 人工确认导入角色，返回实际新增数量
export function backfillCharacters(characters) {
  return http.post('/character/backfill', { characters }).then(({ data }) => data);
}

// 已忽略的候选实体名列表
export function listIgnoredCharacters() {
  return http.get('/character/ignored').then(({ data }) => data);
}

// 忽略候选实体（确认为非人物，从候选隐藏）
export function ignoreCharacterCandidate(entity) {
  return http.post('/character/ignore', { entity }).then(({ data }) => data);
}

// 恢复被忽略的候选实体
export function restoreCharacterCandidate(entity) {
  return http.post('/character/restore', { entity }).then(({ data }) => data);
}
