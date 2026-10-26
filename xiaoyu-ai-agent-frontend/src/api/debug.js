import http from './http';

// 意图路由测试
export function debugIntent(message) {
  return http.post('/debug/intent', { message }).then(({ data }) => data);
}

// MCP 连通性探测（doc-parser / database）
export function mcpStatus() {
  return http.get('/debug/mcp/status').then(({ data }) => data);
}

// 真调 parse_document
export function mcpParse(filePath) {
  return http.post('/debug/mcp/parse', { filePath }).then(({ data }) => data);
}

// 真调 query_database
export function mcpQuery(table, fields, conditions) {
  return http.post('/debug/mcp/query', { table, fields, conditions }).then(({ data }) => data);
}
