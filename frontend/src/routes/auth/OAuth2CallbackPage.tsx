import { CircleAlert, LoaderCircle } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { AuthRole } from "../../lib/auth-session";
import { refreshToken } from "../../services/auth.service";

export function OAuth2CallbackPage() {
  const navigate = useNavigate();
  const started = useRef(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (started.current) return;
    started.current = true;

    refreshToken()
      .then((auth) => navigate(nextPathForRole(auth.user.role), { replace: true }))
      .catch(() => setError("Không thể hoàn tất đăng nhập Google. Vui lòng thử lại."));
  }, [navigate]);

  return (
    <main className="flex min-h-screen items-center justify-center bg-[#080E14] px-4" style={{ fontFamily: "'Plus Jakarta Sans', sans-serif" }}>
      <section className="w-full max-w-md rounded-3xl border border-white/10 bg-[#111118] p-8 text-center shadow-2xl">
        {error ? (
          <>
            <CircleAlert className="mx-auto mb-4 h-10 w-10 text-[#E8315B]" />
            <h1 className="text-xl font-semibold text-[#F0EDEB]">Đăng nhập không thành công</h1>
            <p className="mt-2 text-sm text-[#8585A0]">{error}</p>
            <button type="button" onClick={() => navigate("/login", { replace: true })} className="mt-6 rounded-xl bg-[#E8315B] px-5 py-3 text-sm font-semibold text-white">
              Quay lại đăng nhập
            </button>
          </>
        ) : (
          <>
            <LoaderCircle className="mx-auto mb-4 h-10 w-10 animate-spin text-[#F5C842]" />
            <h1 className="text-xl font-semibold text-[#F0EDEB]">Đang hoàn tất đăng nhập</h1>
            <p className="mt-2 text-sm text-[#8585A0]">TicketBox đang xác thực tài khoản Google của bạn.</p>
          </>
        )}
      </section>
    </main>
  );
}

function nextPathForRole(role: AuthRole) {
  if (role === "ADMIN") return "/admin";
  if (role === "ORGANIZER") return "/organizer";
  if (role === "CHECKER") return "/checker";
  return "/";
}
