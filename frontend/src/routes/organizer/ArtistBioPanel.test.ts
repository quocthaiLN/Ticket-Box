import { createElement } from "react";
import { renderToStaticMarkup } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ArtistBioPanel } from "./ArtistBioPanel";

describe("ArtistBioPanel PDF picker", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("keeps the PDF picker available when a previous job is pending", () => {
    vi.stubGlobal("localStorage", { getItem: () => JSON.stringify({ accessToken: "token", expiresAt: Date.now() + 60_000, user: { id: "owner" } }) });
    vi.stubGlobal("sessionStorage", { getItem: () => "pending-job" });

    const html = renderToStaticMarkup(createElement(ArtistBioPanel, { concertId: "concert-1", readOnly: false }));

    expect(html).toContain("Trạng thái: Đang tải");
    expect(html).toMatch(/<input[^>]*type="file"[^>]*>/);
    expect(html).not.toMatch(/<fieldset[^>]*disabled/);
    expect(html).not.toMatch(/<input[^>]*type="file"[^>]*disabled/);
  });
});
