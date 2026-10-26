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

      <div class="side-foot">
        <button type="button" class="nav-item ghost" @click="reset">退出演示</button>
      </div>
    </aside>

    <main class="app-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { RouterLink, RouterView } from 'vue-router';
import { clearAuth } from './api/http';

const reset = () => {
  clearAuth();
  window.location.href = '/';
};
</script>
