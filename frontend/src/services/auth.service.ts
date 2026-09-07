import {
  apiGet,
  apiPost,
  type ApiResponse,
} from "../lib/api-client";
import {
  clearAuthSession,
  storeAuthSession,
  type AuthUser,
} from "../lib/auth-session";

export type LoginInput = {
  email: string;
  password: string;
};

export type RegisterInput = {
  email: string;
  password: string;
  confirmPassword: string;
  fullName: string;
  phone?: string;
};

export type VerifyOtpInput = {
  email: string;
  otp: string;
};

export type LoginResponse = {
  access_token: string;
  refresh_token?: string;
  expires_in: number;
  user: AuthUser;
  redirect_to?: string;
};

/**
 * Đăng ký tài khoản mới: POST /auth/register
 * Backend lưu user trạng thái PENDING và gửi OTP 6 số qua email.
 */
export async function register(input: RegisterInput): Promise<AuthUser> {
  const payload = {
    email: input.email,
    password: input.password,
    confirm_password: input.confirmPassword,
    full_name: input.fullName,
    phone: input.phone || undefined,
  };

  const response = await apiPost<ApiResponse<AuthUser>>("/auth/register", payload);
  return response.data;
}

/**
 * Xác minh mã OTP: POST /auth/verify-otp
 * Backend kiểm tra mã OTP trong Redis và cập nhật trạng thái user sang ACTIVE.
 */
export async function verifyOtp(input: VerifyOtpInput): Promise<AuthUser> {
  const response = await apiPost<ApiResponse<AuthUser>>("/auth/verify-otp", {
    email: input.email,
    otp: input.otp,
  });
  return response.data;
}

/**
 * Gửi lại mã OTP: POST /auth/resend-otp
 * Backend tạo mã OTP mới và gửi lại email cho user có trạng thái PENDING.
 */
export async function resendOtp(email: string): Promise<string> {
  const response = await apiPost<ApiResponse<string>>("/auth/resend-otp", { email });
  return response.data;
}

/**
 * Đăng nhập: POST /auth/login
 * Nhận access_token và refresh_token (trong body và HttpOnly cookie).
 */
export async function login(input: LoginInput): Promise<LoginResponse> {
  const response = await apiPost<ApiResponse<LoginResponse>>("/auth/login", input, {
    credentials: "include",
  });

  storeAuthSession({
    accessToken: response.data.access_token,
    expiresIn: response.data.expires_in,
    user: response.data.user,
  });

  return response.data;
}

/**
 * Làm mới access token: POST /auth/refresh
 * Gửi refresh_token từ HttpOnly cookie để nhận access token mới.
 */
export async function refreshToken(): Promise<LoginResponse> {
  const response = await apiPost<ApiResponse<LoginResponse>>("/auth/refresh", undefined, {
    credentials: "include",
  });

  storeAuthSession({
    accessToken: response.data.access_token,
    expiresIn: response.data.expires_in,
    user: response.data.user,
  });

  return response.data;
}

/**
 * Lấy thông tin user hiện tại: GET /auth/me
 */
export async function loadCurrentUser(): Promise<AuthUser> {
  const response = await apiGet<ApiResponse<AuthUser>>("/auth/me");
  return response.data;
}

/**
 * Đăng xuất: POST /auth/logout
 * Thu hồi refresh token trên Redis backend và xóa cookie / session ở trình duyệt.
 */
export async function logout(): Promise<void> {
  try {
    await apiPost<void>("/auth/logout", undefined, {
      credentials: "include",
    });
  } finally {
    clearAuthSession();
  }
}
