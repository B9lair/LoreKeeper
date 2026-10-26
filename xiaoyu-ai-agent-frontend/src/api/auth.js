import http, { writeAuth } from './http';

// 注册：成功后把后端返回的 {token, userId, workId} 写入 localStorage
export function register(email, password) {
  return http.post('/auth/register', { email, password }).then(({ data }) => {
    writeAuth(data);
    return data;
  });
}

// 登录：成功后把后端返回的 {token, userId, workId} 写入 localStorage
export function login(email, password) {
  return http.post('/auth/login', { email, password }).then(({ data }) => {
    writeAuth(data);
    return data;
  });
}

// demo 演示账号（MVP：登录注册暂时忽略，自动用固定账号登录）
export const DEMO_EMAIL = 'demo@lorekeeper.dev';
export const DEMO_PASSWORD = 'lorekeeper123';

/**
 * 自动 demo 登录：先 login，邮箱不存在（401）则 register，注册冲突（409）再 login。
 * 返回 {token, userId, workId}。
 */
export async function demoLogin() {
  try {
    return await login(DEMO_EMAIL, DEMO_PASSWORD);
  } catch (e) {
    // 登录失败（邮箱未注册等）→ 尝试注册
  }
  try {
    return await register(DEMO_EMAIL, DEMO_PASSWORD);
  } catch (e) {
    // 注册冲突（已存在但密码不同）→ 再登录兜底
    return login(DEMO_EMAIL, DEMO_PASSWORD);
  }
}
