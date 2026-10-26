import axios from 'axios';

// 根据环境变量设置 API 基础 URL
const API_BASE_URL = process.env.NODE_ENV === 'production'
  ? '/api' // 生产环境使用相对路径，适用于前后端部署在同一域名下
  : 'http://localhost:8080/api'; // 开发环境指向本地后端服务

// 统一 storage key（供 auth.js 复用，避免 key 字符串散落两处）
export const AUTH_STORAGE_KEY = 'lorekeeper_auth';

// 从 localStorage 读取登录态（隐私模式 / 损坏 JSON 时返回 null）
export function readAuth() {
  try {
    return JSON.parse(localStorage.getItem(AUTH_STORAGE_KEY) || 'null');
  } catch {
    return null;
  }
}

export function writeAuth(auth) {
  try {
    localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth));
  } catch {
    // 隐私模式下写失败，静默忽略
  }
}

export function clearAuth() {
  try {
    localStorage.removeItem(AUTH_STORAGE_KEY);
  } catch {
    // 忽略
  }
}

const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000
});

// 请求拦截器：自动注入 Authorization: Bearer <token>
http.interceptors.request.use((config) => {
  const auth = readAuth();
  if (auth && auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`;
  }
  return config;
});

// 响应拦截器：401 时清空登录态并回首页重新触发 demo 自动登录（无独立登录页）
http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      clearAuth();
      if (window.location.pathname !== '/') {
        window.location.href = '/';
      }
    }
    return Promise.reject(error);
  }
);

export default http;
