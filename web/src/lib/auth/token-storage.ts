const ACCESS_TOKEN_KEY = "hadivo.accessToken";
const REFRESH_TOKEN_KEY = "hadivo.refreshToken";

function canUseStorage() {
  return typeof window !== "undefined" && typeof window.sessionStorage !== "undefined";
}

export const tokenStorage = {
  getAccessToken() {
    if (!canUseStorage()) return null;
    return window.sessionStorage.getItem(ACCESS_TOKEN_KEY);
  },
  getRefreshToken() {
    if (!canUseStorage()) return null;
    return window.sessionStorage.getItem(REFRESH_TOKEN_KEY);
  },
  setTokens(accessToken: string, refreshToken?: string) {
    if (!canUseStorage()) return;
    window.sessionStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    if (refreshToken) window.sessionStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },
  clear() {
    if (!canUseStorage()) return;
    window.sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    window.sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  },
};
