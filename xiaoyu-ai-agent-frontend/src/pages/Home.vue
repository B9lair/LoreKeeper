<template>
  <div class="home-hero">
    <div class="hero-grid" aria-hidden="true"></div>
    <div class="hero-glow" aria-hidden="true"></div>

    <div class="hero-inner">
      <div class="hero-left">
        <div class="kicker">XIAOYU · AI APPS</div>
        <h2 class="hero-title">
          极客入口，
          <span class="hero-title-accent">一键进入</span>
          <br />
          流式 AI 聊天宇宙
        </h2>
        <p class="hero-subtitle">
          SSE 实时输出 · 多应用切换 · 统一聊天室体验
        </p>

        <div class="hero-actions">
          <RouterLink to="/manus" class="neo-button primary">进入超级智能体</RouterLink>
          <RouterLink to="/lore-keeper" class="neo-button ghost">进入设定守护者</RouterLink>
        </div>
      </div>

      <div class="hero-right">
        <div class="apps-panel">
          <div class="panel-header">
            <span class="panel-dot red" aria-hidden="true"></span>
            <span class="panel-dot yellow" aria-hidden="true"></span>
            <span class="panel-dot green" aria-hidden="true"></span>
            <span class="panel-title">apps://launcher</span>
          </div>

          <div class="panel-body">
            <RouterLink to="/lore-keeper" class="app-tile lore">
              <div class="tile-top">
                <span class="chip">LORE</span>
                <span class="chip subtle">SSE</span>
              </div>
              <div class="tile-title">LoreKeeper 设定守护者</div>
              <div class="tile-desc">设定一致性 · 伏笔追踪 · 流式对话</div>
            </RouterLink>

            <RouterLink to="/manus" class="app-tile manus">
              <div class="tile-top">
                <span class="chip">MANUS</span>
                <span class="chip subtle">STEP</span>
              </div>
              <div class="tile-title">AI 超级智能体</div>
              <div class="tile-desc">多步骤推理 · 每步独立气泡输出</div>
            </RouterLink>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { RouterLink } from 'vue-router';
</script>

