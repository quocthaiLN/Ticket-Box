import { Loader2, QrCode } from "lucide-react";
import QRCode from "qrcode";
import { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { ApiClientError } from "../../lib/api-client";
import { getMyTicketQr } from "../../services/ticket.service";

export function TicketQrPage() {
  const { ticketId } = useParams<{ ticketId: string }>();
  const location = useLocation();
  const [qrImageUrl, setQrImageUrl] = useState<string | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "auth-required" | "error">("loading");

  useEffect(() => {
    if (!ticketId) {
      setStatus("error");
      return;
    }
    let active = true;
    getMyTicketQr(ticketId)
      .then((ticketQr) => QRCode.toDataURL(ticketQr.content, { width: 320, margin: 1 }))
      .then((imageUrl) => {
        if (!active) return;
        setQrImageUrl(imageUrl);
        setStatus("ready");
      })
      .catch((error: unknown) => {
        if (!active) return;
        if (error instanceof ApiClientError && error.status === 401) {
          sessionStorage.setItem("ticketbox.redirectAfterLogin", location.pathname);
          setStatus("auth-required");
          return;
        }
        setStatus("error");
      });
    return () => {
      active = false;
    };
  }, [location.pathname, ticketId]);

  return (
    <main className="min-h-screen bg-[#080E14] px-4 pb-12 pt-24 text-[#F0EDEB] sm:px-6">
      <section className="mx-auto max-w-md rounded-2xl border border-white/10 bg-white/[0.03] p-6 text-center shadow-2xl">
        <QrCode className="mx-auto h-9 w-9 text-[#F5C842]" />
        <h1 className="mt-4 text-3xl font-bold">Mã QR vé</h1>
        <p className="mt-2 text-sm text-[#8585A0]">Xuất trình mã này tại cổng soát vé.</p>

        {status === "loading" && <Loader2 className="mx-auto my-10 h-8 w-8 animate-spin text-[#F5C842]" />}
        {status === "ready" && qrImageUrl && <img className="mx-auto my-8 rounded-xl bg-white p-3" src={qrImageUrl} alt="Mã QR vé" />}
        {status === "auth-required" && <p className="my-8 text-sm text-[#E8315B]">Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại để mở vé này.</p>}
        {status === "error" && <p className="my-8 text-sm text-[#E8315B]">Không thể tải mã QR của vé. Vui lòng thử lại sau.</p>}
        {status === "auth-required" ? (
          <Link className="inline-block rounded-lg bg-[#E8315B] px-4 py-2 text-sm font-semibold text-white" to="/login">
            Đăng nhập lại
          </Link>
        ) : (
          <Link className="inline-block rounded-lg bg-[#E8315B] px-4 py-2 text-sm font-semibold text-white" to="/my-tickets">
            Xem tất cả vé
          </Link>
        )}
      </section>
    </main>
  );
}
