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

// 真调 split_chapters（整本切章）
export function mcpSplit(text) {
  return http.post('/debug/mcp/split', { text }).then(({ data }) => data);
}

// 真调 detect_encoding（编码探测）
export function mcpEncoding(base64Content) {
  return http.post('/debug/mcp/encoding', { base64Content }).then(({ data }) => data);
}

// 真调 parse_document_content（内容解析）
export function mcpParseContent(fileName, base64Content) {
  return http.post('/debug/mcp/parse-content', { fileName, base64Content }).then(({ data }) => data);
}
