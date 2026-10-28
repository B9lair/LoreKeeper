import http from './http';

// MCP 连通性探测（database）
export function mcpStatus() {
  return http.get('/debug/mcp/status').then(({ data }) => data);
}

// 真调 query_database
export function mcpQuery(table, fields, conditions) {
  return http.post('/debug/mcp/query', { table, fields, conditions }).then(({ data }) => data);
}
