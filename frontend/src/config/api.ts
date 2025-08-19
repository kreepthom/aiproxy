// API configuration
// 部署前修改这里的 SERVER_IP 为你的服务器 IP
const SERVER_IP = '51.81.187.171';  // TODO: 修改为你的服务器IP
const SERVER_PORT = '8080';

const isDevelopment = window.location.hostname === 'localhost';

const BASE_URL = isDevelopment 
  ? 'http://localhost:8080'
  : `http://${SERVER_IP}:${SERVER_PORT}`;

export const API_CONFIG = {
  API_BASE_URL: BASE_URL + '/api',
  AUTH_BASE_URL: BASE_URL + '/auth',
  ADMIN_BASE_URL: BASE_URL + '/admin',
  OAUTH_BASE_URL: BASE_URL + '/oauth'
}