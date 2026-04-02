import { Router } from "express";
import { authMiddleware } from "../middleware/auth.js";
import { getPresenceInfo, userSockets } from "../signaling.js";

export const presenceRouter = Router();

/**
 * GET /api/presence
 * Devuelve la lista de usuarios con su estado de presencia
 */
presenceRouter.get("/", authMiddleware, (req, res) => {
  const presence = getPresenceInfo();
  const currentUserId = req.user.userId;
  
  // Filter out current user and add online status
  const filteredPresence = presence
    .filter(p => p.userId !== currentUserId)
    .map(p => ({
      ...p,
      isOnline: userSockets.has(p.userId) && p.status === 'online'
    }));

  res.json({ presence: filteredPresence });
});

/**
 * GET /api/presence/online
 * Devuelve solo usuarios actualmente conectados
 */
presenceRouter.get("/online", authMiddleware, (req, res) => {
  const currentUserId = req.user.userId;
  const onlineUsers = Array.from(userSockets.keys())
    .filter(userId => userId !== currentUserId)
    .map(userId => {
      const presence = getPresenceInfo().find(p => p.userId === userId);
      return {
        userId,
        displayName: presence?.displayName || 'Unknown',
        status: 'online',
        lastSeen: presence?.lastSeen || new Date()
      };
    });

  res.json({ users: onlineUsers, count: onlineUsers.length });
});