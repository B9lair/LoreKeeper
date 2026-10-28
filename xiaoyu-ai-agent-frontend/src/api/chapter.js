import http from './http';

// 上传章节（纯文本 / TXT / MD 读成字符串），返回 {chapterId, seq, title, newSettings, updatedSettings, conflicts, foreshadows}
// 同步长请求（含向量化 + LLM 抽取），单独放宽超时，避免全局 30s 超时误杀
export function uploadChapter(title, chapterText) {
  return http.post('/lore/chapter/upload', { title, chapterText }, { timeout: 300000 })
    .then(({ data }) => data);
}

// 上传章节文件（multipart，后端本地解析后整本切章、逐章入库）
// 返回 ChapterBatchIngestResult { chapterCount, chapters, totalNewSettings, totalUpdatedSettings, totalConflicts, totalForeshadows }
export function uploadChapterFile(file, title) {
  const form = new FormData();
  form.append('file', file);
  if (title) form.append('title', title);
  return http.post('/lore/chapter/upload-file', form, { timeout: 300000 })
    .then(({ data }) => data);
}

// 列出当前作品的全部章节（按 seq 升序）
export function listChapters() {
  return http.get('/lore/chapter/list').then(({ data }) => data);
}

// 覆盖式替换章节：级联删除旧章（设定/伏笔库也会被完全覆盖）后用原 seq 重新入库
export function replaceChapter(id, title, chapterText) {
  return http.post(`/lore/chapter/${id}/replace`, { title, chapterText }, { timeout: 300000 })
    .then(({ data }) => data);
}

// 删除章节（级联删除原文/向量/设定/伏笔/时间线/审查报告）
export function deleteChapter(id) {
  return http.delete(`/lore/chapter/${id}`);
}
