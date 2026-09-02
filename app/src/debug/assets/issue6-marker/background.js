let attempts = 0;
async function report() {
  if (attempts++ >= 6) return;
  const response = await browser.runtime.sendNativeMessage("issue6Marker", { type: "read", slot: "request" });
  if (!response || !["A", "B"].includes(response.slot)) return;
  const old = await browser.storage.local.get("slot");
  const prior = old.slot || response.slot;
  if (!old.slot) await browser.storage.local.set({ slot: response.slot });
  await browser.runtime.sendNativeMessage("issue6Marker", { type: "result", slot: response.slot, prior, current: old.slot || response.slot });
}
report().catch(() => setTimeout(() => report().catch(() => {}), 2000));
