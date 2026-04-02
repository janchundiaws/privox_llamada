import { Router } from "express";
import { authMiddleware } from "../middleware/auth.js";
import { Call } from "../models/Call.js";

export const callsRouter = Router();

/**
 * POST /api/calls/start
 * body: { to: "<userId>", meta: { ... } }
 * Crea registro de llamada y devuelve callId.
 */
callsRouter.post("/start", authMiddleware, async (req, res) => {
  try {
    const from = req.user.userId;
    const { to, meta } = req.body;
    if (!to) return res.status(400).json({ error: "missing 'to' field" });

    const callId = `call_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
    const call = await Call.create({
      callId,
      from,
      to,
      status: "ringing",
      meta: meta || {},
    });

    // Nota: la notificación en tiempo real al peer debe hacerse por WebSocket (señalización).
    // Si quieres que el servidor notifique al peer desde aquí, exponemos la map userSockets
    // o una función notifyPeer en src/signaling.js. Por ahora devolvemos callId.
    return res.status(201).json({ callId, call });
  } catch (err) {
    console.error("calls/start error:", err);
    return res.status(500).json({ error: "failed to start call" });
  }
});


/**
 * POST /api/calls/voice
 * body: { to: "<userId>", meta?: { ... } }
 * Crea una llamada solo-voz (sin video) y notifica al receptor vía WebSocket.
 */
callsRouter.post("/voice", authMiddleware, async (req, res) => {
  try {
    const from = req.user.userId;
    const { to, meta } = req.body || {};
    if (!to) return res.status(400).json({ error: "missing 'to' field" });

    const callId = `call_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;

    const mergedMeta = Object.assign({}, meta || {}, { mediaType: "voice", voiceOnly: true });

    const call = await Call.create({
      callId,
      from,
      to,
      status: "ringing",
      meta: mergedMeta,
    });

    // Notificar al peer por WebSocket. Import dinámico para evitar posibles import cycles.
    try {
      const mod = await import("../signaling.js");
      if (mod && typeof mod.notifyUser === "function") {
        mod.notifyUser(to, {
          type: "incoming-call",
          callId,
          from,
          meta: mergedMeta,
        });
      }
    } catch (e) {
      console.warn("calls/voice: failed to notify peer via WS:", e && e.message ? e.message : e);
    }

    return res.status(201).json({ callId, call });
  } catch (err) {
    console.error("calls/voice error:", err);
    return res.status(500).json({ error: "failed to start voice call" });
  }
});