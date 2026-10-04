import { useEffect, useState, type ChangeEvent } from "react";
import { getStoredAuthSession } from "../../lib/auth-session";
import { pollCatalogBio, patchCatalogConcert, uploadCatalogBio, type BioJob } from "../../services/organizer-catalog.service";

export function ArtistBioPanel({ concertId, initialBio, readOnly }: { concertId: string; initialBio?: string; readOnly: boolean }) {
  const storageKey = `ticketbox.bio.${getStoredAuthSession()?.user.id}.${concertId}`;
  const [jobId, setJobId] = useState(() => sessionStorage.getItem(storageKey));
  const [job, setJob] = useState<BioJob | null>(null);
  const [bio, setBio] = useState(initialBio ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    if (!jobId) return;
    return pollCatalogBio(concertId, jobId, next => {
      setJob(next);
      if (next.status === "DONE") setMessage("Bio đã tạo xong. Xem nội dung trước khi áp dụng.");
      else if (next.status === "FAILED") setError(next.error_message || "Không thể tạo bio.");
    }, err => setError(err instanceof Error ? err.message : "Không tải được trạng thái bio."));
  }, [concertId, jobId, retry]);

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]; event.target.value = "";
    if (!file || busy) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const next = await uploadCatalogBio(concertId, file);
      sessionStorage.setItem(storageKey, next.id);
      setJob(next); setJobId(next.id);
    } catch (err) { setError(err instanceof Error ? err.message : "Không upload được PDF."); }
    finally { setBusy(false); }
  }
  async function save(value: string, applyGenerated = false) {
    if (busy) return;
    setBusy(true); setError(""); setMessage("");
    try {
      const saved = await patchCatalogConcert(concertId, { artist_bio: value || null });
      setBio(saved.artist_bio ?? "");
      setMessage("Đã lưu bio.");
      if (applyGenerated) { sessionStorage.removeItem(storageKey); setJobId(null); setJob(null); }
    } catch (err) { setError(err instanceof Error ? err.message : "Không lưu được bio."); }
    finally { setBusy(false); }
  }
  return <section className="space-y-3 rounded-2xl border border-white/10 bg-[#111118] p-5">
    <h3 className="font-semibold">Bio nghệ sĩ</h3>
    {error && <p role="alert" className="text-sm text-[#E8315B]">{error}</p>}
    {message && <p role="status" className="text-sm text-[#2DBE6C]">{message}</p>}
    {jobId && <p className="text-sm">Trạng thái: {job?.status ?? "Đang tải"}</p>}
    {error && jobId && <button type="button" onClick={() => { setError(""); setRetry(value => value + 1); }} className="text-sm underline">Thử tải lại trạng thái</button>}
    <fieldset disabled={readOnly || busy} className="space-y-3">
      <label className="grid gap-2 text-sm">Upload PDF (tối đa 10 MiB)
        <input type="file" accept="application/pdf,.pdf" onChange={upload} />
      </label>
      {job?.status === "DONE" && job.generated_bio && <div className="space-y-2">
        <p className="whitespace-pre-wrap text-sm">{job.generated_bio}</p>
        <button type="button" onClick={() => void save(job.generated_bio!, true)} className="rounded-lg bg-[#7B61FF] px-4 py-2">Áp dụng bio</button>
      </div>}
      <label className="grid gap-2 text-sm">Bio hiện tại
        <textarea rows={6} value={bio} onChange={event => setBio(event.target.value)} className="w-full rounded-lg border border-white/10 bg-[#0A0A12] p-3" />
      </label>
      <button type="button" onClick={() => void save(bio)} className="rounded-lg bg-[#7B61FF] px-4 py-2">{busy ? "Đang lưu..." : "Lưu bio"}</button>
    </fieldset>
  </section>;
}
