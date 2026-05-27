const trimTrailingSlash = (value) => value.replace(/\/+$/, "");

export const API_BASE_URL = trimTrailingSlash(
  import.meta.env.VITE_API_BASE_URL || "/api"
);

export const WS_BASE_URL = trimTrailingSlash(
  import.meta.env.VITE_WS_BASE_URL || "/ws"
);
