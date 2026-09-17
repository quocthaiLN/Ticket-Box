import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { getPayment } from "../../services/order.service";
import {
  clearPendingCheckout,
  readHeldCheckouts,
  readPendingCheckout,
  writePendingCheckout,
} from "../audience/checkout-storage";

type PaymentResultState = "PROCESSING" | "SUCCEEDED" | "FAILED" | "UNAVAILABLE";

// VNPAY Return URL chỉ đưa browser quay lại đây. Trạng thái hiển thị luôn lấy
// từ payment đã được backend cập nhật bằng IPN, không lấy từ query string VNPAY.
export function PaymentResultPage() {
  const [checkout] = useState(() => readPendingCheckout());
  const [resultState, setResultState] = useState<PaymentResultState>(
    checkout?.paymentId ? "PROCESSING" : "UNAVAILABLE",
  );
  const [failureReason, setFailureReason] = useState("");
  const [pollError, setPollError] = useState("");
  const [remainingHeldCount, setRemainingHeldCount] = useState(0);
  const orderId = checkout?.orderId ?? "";

  useEffect(() => {
    if (!checkout?.paymentId) return;

    let stopped = false;
    let timeoutId: number | undefined;
    let pollAttempt = 0;

    const scheduleNextPoll = () => {
      const delays = [3_000, 5_000, 10_000];
      const delay = delays[Math.min(pollAttempt, delays.length - 1)];
      pollAttempt += 1;
      timeoutId = window.setTimeout(() => void poll(), delay);
    };

    const poll = async () => {
      try {
        const payment = await getPayment(checkout.paymentId!);
        if (stopped) return;
        setPollError("");

        if (payment.status === "SUCCEEDED") {
          clearPendingCheckout(payment.order_id);
          const remaining = readHeldCheckouts().filter((item) => item.expiresAt > Date.now());
          const nextCheckout = remaining[0];
          if (nextCheckout) writePendingCheckout(nextCheckout);
          setRemainingHeldCount(remaining.length);
          setResultState("SUCCEEDED");
          return;
        }

        if (["FAILED", "CANCELLED", "REFUNDED"].includes(payment.status)) {
          setFailureReason(payment.failure_reason ?? "Giao dịch chưa hoàn tất.");
          setRemainingHeldCount(readHeldCheckouts().filter((item) => item.expiresAt > Date.now()).length);
          setResultState("FAILED");
          return;
        }

        scheduleNextPoll();
      } catch {
        if (!stopped) {
          setPollError("Chưa thể đọc trạng thái thanh toán. Hệ thống đang thử lại.");
          scheduleNextPoll();
        }
      }
    };

    void poll();
    return () => {
      stopped = true;
      if (timeoutId !== undefined) window.clearTimeout(timeoutId);
    };
  }, [checkout]);

  const success = resultState === "SUCCEEDED";
  const processing = resultState === "PROCESSING";

  return (
    <div className="mx-auto flex max-w-md flex-col items-center gap-4 px-4 py-16 text-center">
      {processing ? (
        <Loader2 className="h-16 w-16 animate-spin text-primary" />
      ) : success ? (
        <CheckCircle2 className="h-16 w-16 text-green-500" />
      ) : (
        <XCircle className="h-16 w-16 text-red-500" />
      )}

      <h1 className="text-2xl font-semibold">
        {processing ? "Đang xác nhận thanh toán" : success ? "Thanh toán thành công" : "Thanh toán thất bại"}
      </h1>

      <p className="text-muted-foreground">
        {processing
          ? "VNPAY đã đưa bạn về ứng dụng. Hệ thống đang chờ xác nhận chính thức từ cổng thanh toán."
          : success
          ? "Vé của bạn đã được xác nhận. Kiểm tra trong My Tickets."
          : failureReason || "Giao dịch chưa hoàn tất. Bạn có thể thử thanh toán lại."}
      </p>

      {processing && pollError && (
        <p className="text-sm text-destructive">{pollError}</p>
      )}

      {orderId && (
        <p className="text-sm text-muted-foreground">
          Mã đơn: <span className="font-mono">{orderId}</span>
        </p>
      )}

      {remainingHeldCount > 0 && (
        <p className="text-sm text-muted-foreground">
          Bạn còn {remainingHeldCount} đơn vé đang được giữ và chưa thanh toán.
        </p>
      )}

      <div className="mt-4 flex flex-wrap justify-center gap-3">
        {remainingHeldCount > 0 && (
          <Link
            to="/checkout"
            className="rounded-md border-2 border-primary bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90"
          >
            Thanh toán đơn tiếp theo
          </Link>
        )}
        <Link
          to="/events"
          className="rounded-md border px-4 py-2 text-sm font-medium hover:bg-accent"
        >
          Tiếp tục mua vé
        </Link>
        <Link
          to="/"
          className={
            remainingHeldCount > 0
              ? "rounded-md border px-4 py-2 text-sm font-medium hover:bg-accent"
              : "rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground hover:opacity-90"
          }
        >
          Về trang chủ
        </Link>
      </div>
    </div>
  );
}
