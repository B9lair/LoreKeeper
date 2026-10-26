import { reactive } from 'vue';
import { readAuth, writeAuth, clearAuth } from '../api/http';
import { listWorks, createWork, switchWork, deleteWork } from '../api/work';

// 全局作品上下文：works = 作品列表，currentId = 当前 workId，current = 当前作品对象。
// version 是「作品变了」的自增广播信号，各页面 watch 它重新拉数据，替代 window.location.reload()。
export const workStore = reactive({
  works: [],
  currentId: null,
  current: null,
  version: 0
});

// 拉取作品列表并定位当前作品
export async function refreshWorks() {
  try {
    workStore.works = await listWorks();
  } catch (e) {
    workStore.works = [];
  }
  workStore.currentId = readAuth()?.workId ?? null;
  workStore.current = workStore.works.find((w) => w.id === workStore.currentId) || null;
}

// 切换作品：换 token（重签 JWT）+ 广播版本号，不再全站 reload
export async function switchTo(id) {
  const data = await switchWork(id); // 后端重签 JWT，返回 {token, userId, workId}
  writeAuth(data);
  workStore.version++;
  await refreshWorks();
}

// 新建作品并自动切换过去（减少一步操作）
export async function createAndSwitch(title, genre) {
  const work = await createWork(title, genre);
  await switchTo(work.id);
  return work;
}

// 删除作品（后端级联删除全部章节/设定/伏笔/向量）
// 删当前作品时自动切到剩余第一个；全删光则重建登录态（demo 自动登录会重建默认作品）
export async function removeWork(id) {
  await deleteWork(id);
  const wasCurrent = id === workStore.currentId;
  await refreshWorks();
  if (wasCurrent && workStore.works.length > 0) {
    await switchTo(workStore.works[0].id);
  } else if (wasCurrent) {
    clearAuth();
    window.location.reload();
  }
  return wasCurrent;
}
