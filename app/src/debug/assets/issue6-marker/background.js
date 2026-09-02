let attempts = 0;
function connect() {
  if (attempts++ >= 6) return;
  try {
    const port = browser.runtime.connectNative("issue6Marker");
    port.onMessage.addListener(async message => {
      if (!message || message.type !== "read" || !["A", "B"].includes(message.slot)) return;
      const old = await browser.storage.local.get("slot");
      const prior = old.slot || message.slot;
      if (!old.slot) await browser.storage.local.set({ slot: message.slot });
      port.postMessage({ type: "result", slot: message.slot, prior, current: old.slot || message.slot });
    });
    port.onDisconnect.addListener(() => setTimeout(connect, 2000));
  } catch (_) {
    setTimeout(connect, 2000);
  }
}
connect();
