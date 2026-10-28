<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="side-brand">
        <div class="brand-mark" aria-hidden="true">L</div>
        <div class="brand-text">
          <span class="brand-name">LoreKeeper</span>
          <span class="brand-sub">设定守护者</span>
        </div>
      </div>

      <div class="work-switcher">
        <select class="work-select" :value="workStore.currentId" @change="onSwitchWork">
          <option v-for="w in workStore.works" :key="w.id" :value="w.id">{{ w.title }}</option>
        </select>
      </div>

      <nav class="side-nav">
        <RouterLink to="/" class="nav-item">
          <span class="nav-icon" aria-hidden="true">◫</span>对话
        </RouterLink>
        <RouterLink to="/rag" class="nav-item">
          <span class="nav-icon" aria-hidden="true">▤</span>知识库
        </RouterLink>
        <RouterLink to="/test" class="nav-item">
          <span class="nav-icon" aria-hidden="true">⌘</span>测试
        </RouterLink>
        <RouterLink to="/data" class="nav-item">
          <span class="nav-icon" aria-hidden="true">☷</span>数据
        </RouterLink>
      </nav>
    </aside>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { RouterLink, RouterView } from 'vue-router';
import { workStore, refreshWorks, switchTo } from './stores/work';

// 应用挂载即拉取作品列表、定位当前作品
refreshWorks();

const onSwitchWork = async (e) => {
  const id = Number(e.target.value);
  if (!id || id === workStore.currentId) return;
  try {
    await switchTo(id);
  } catch (err) {
    // 切换失败保持现状，不打断用户
  }
};
</script>
