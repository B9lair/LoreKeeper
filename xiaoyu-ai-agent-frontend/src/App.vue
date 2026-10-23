<template>
  <div class="app-container">
    <header class="app-header">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true"></div>
        <div class="brand-text">
          <h1>小渝 AI 应用</h1>
          <div class="brand-subtitle">SSE · Stream · Agents</div>
        </div>
      </div>
      <nav class="nav-links">
        <RouterLink to="/">主页</RouterLink>
        <RouterLink to="/love-app">AI 恋爱大师</RouterLink>
        <RouterLink to="/manus">AI 超级智能体</RouterLink>
      </nav>
    </header>

    <main class="app-main">
      <RouterView />
    </main>

    <footer class="app-footer">
      <div class="footer-inner">
        <div class="footer-left">
          <span class="footer-brand">小渝 AI 应用</span>
          <span class="footer-sep">·</span>
          <span>© {{ year }} Xiaoyu</span>
          <span class="footer-sep">·</span>
          <span class="footer-note">All rights reserved</span>
        </div>
        <div class="footer-right">
          <span class="footer-note">Powered by SSE · Vue3</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { RouterLink, RouterView } from 'vue-router';

const year = new Date().getFullYear();
</script>

