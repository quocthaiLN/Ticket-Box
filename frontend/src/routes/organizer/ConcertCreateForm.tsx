import { useState, type FormEvent } from "react";
import { createCatalogConcert, type CatalogConcert } from "../../services/organizer-catalog.service";

export function ConcertCreateForm({ onCreated, onClose }: { onCreated: (concert: CatalogConcert) => void; onClose: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const data = new FormData(event.currentTarget);
    const value = (key: string) => String(data.get(key) ?? "").trim();
    setBusy(true); setError("");
    try {
      const starts_at = new Date(value("starts_at")).toISOString();
      const ends_at = new Date(value("ends_at")).toISOString();
      if (ends_at <= starts_at) throw new Error("Giờ kết thúc phải sau giờ bắt đầu.");
      onCreated(await createCatalogConcert({ title: value("title"), slug: value("slug"),
        venue: value("venue"), artist_name: value("artist_name"), starts_at, ends_at }));
    } catch (err) { setError(err instanceof Error ? err.message : "Không thể tạo concert."); }
    finally { setBusy(false); }
  }
  return <form onSubmit={submit} className="mb-6 space-y-4 rounded-2xl border border-white/10 bg-[#111118] p-5">
    <h2 className="text-lg font-semibold">Tạo concert</h2>
    {error && <p role="alert" className="text-sm text-[#E8315B]">{error}</p>}
    <fieldset disabled={busy} className="grid gap-4 sm:grid-cols-2">
      {[["title", "Tên concert"], ["slug", "Slug"], ["artist_name", "Nghệ sĩ"], ["venue", "Địa điểm"], ["starts_at", "Bắt đầu"], ["ends_at", "Kết thúc"]].map(([name, label]) =>
        <label key={name} className="grid gap-1 text-sm">{label}
          <input name={name} required maxLength={255} type={name.endsWith("_at") ? "datetime-local" : "text"} className="min-w-0 rounded-lg border border-white/10 bg-[#0A0A12] px-3 py-2" />
        </label>)}
      <button className="rounded-lg bg-[#7B61FF] px-4 py-2 disabled:opacity-50">{busy ? "Đang tạo..." : "Tạo concert"}</button>
      <button type="button" onClick={onClose} className="rounded-lg border border-white/10 px-4 py-2">Đóng</button>
    </fieldset>
  </form>;
}
