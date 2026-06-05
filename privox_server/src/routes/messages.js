import { Router } from "express";
import { authMiddleware } from "../middleware/auth.js";
import { Message } from "../models/Message.js";
import { User } from "../models/User.js";

export const messagesRouter = Router();


messagesRouter.get("/conversations", authMiddleware, async (req, res) => {
  try {
    const currentUserId = req.user.userId;

    const conversations = await Message.aggregate([
      {
        $match: {
          $or: [{ from: currentUserId }, { to: currentUserId }]
        }
      },
      { $sort: { createdAt: -1 } },
      {
        $group: {
          _id: {
            $cond: [{ $eq: ["$from", currentUserId] }, "$to", "$from"]
          },
          lastMessage: { $first: "$$ROOT" },
          unreadCount: {
            $sum: {
              $cond: [
                { $and: [{ $eq: ["$to", currentUserId] }, { $ne: ["$status", "read"] }] },
                1,
                0
              ]
            }
          }
        }
      }
    ]);

    // Poblar detalles del usuario
    const userIds = conversations.map(c => c._id);
    const users = await User.find({ userId: { $in: userIds } }, { userId: 1, displayName: 1, username: 1 }).lean();
    const userMap = Object.fromEntries(users.map(u => [u.userId, u]));

    const result = conversations.map(c => ({
      contact: userMap[c._id] || { userId: c._id, displayName: "Usuario Desconocido" },
      lastMessage: c.lastMessage,
      unreadCount: c.unreadCount
    }));

    res.json({ conversations: result });
  } catch (error) {
    console.error("Error en /conversations:", error);
    res.status(500).json({ error: "Error interno del servidor" });
  }
});


messagesRouter.get("/history/:userId", authMiddleware, async (req, res) => {
  try {
    const currentUserId = req.user.userId;
    const targetUserId = req.params.userId;
    const limit = parseInt(req.query.limit) || 50;
    const offset = parseInt(req.query.offset) || 0;

    const messages = await Message.find({
      $or: [
        { from: currentUserId, to: targetUserId },
        { from: targetUserId, to: currentUserId }
      ]
    })
      .sort({ createdAt: -1 })
      .skip(offset)
      .limit(limit)
      .lean();

    res.json({ messages });
  } catch (error) {
    console.error("Error en /history:", error);
    res.status(500).json({ error: "Error interno del servidor" });
  }
});


messagesRouter.get("/unread-count", authMiddleware, async (req, res) => {
  try {
    const currentUserId = req.user.userId;
    const count = await Message.countDocuments({
      to: currentUserId,
      status: { $ne: "read" }
    });
    res.json({ count });
  } catch (error) {
    res.status(500).json({ error: "Error interno del servidor" });
  }
});


messagesRouter.get("/search", authMiddleware, async (req, res) => {
  try {
    const currentUserId = req.user.userId;
    const { q } = req.query;
    if (!q) return res.status(400).json({ error: "Término de búsqueda 'q' requerido" });

    const messages = await Message.find({ $text: { $search: q }, $or: [{ from: currentUserId }, { to: currentUserId }] }).sort({ createdAt: -1 }).limit(50).lean();
    res.json({ messages });
  } catch (error) {
    res.status(500).json({ error: "Error interno del servidor" });
  }
});

//Endpoint para eliminar todo los mensajes historicos que yo e enviado a un chat en especifico
messagesRouter.delete("/delete-messages/:toId", authMiddleware, async (req, res) => {
  try {

    const currentUserId = req.user.userId;
    const toId = req.params.toId;

    await Message.deleteMany({ from: currentUserId, to: toId });
    res.json({ success: true });

  } catch (error) {
    res.status(500).json({ error: "Error interno del servidor" });
  }
});

//Endpoint para eliminar un mensaje en especifico
messagesRouter.delete("/delete-message/:messageId", authMiddleware, async (req, res) => {
  try {

    const currentUserId = req.user.userId;
    const messageId = req.params.messageId;

    const message = await Message.findOne({ messageId });
    if (!message) return res.status(404).json({ error: "Mensaje no encontrado" });
    if (message.from !== currentUserId && message.to !== currentUserId) {
      return res.status(403).json({ error: "No tienes permiso para eliminar este mensaje" });
    }

    await Message.findOneAndDelete({ messageId });
    res.json({ success: true });

  } catch (error) {
    console.log(error);
    res.status(500).json({ error: "Error interno del servidor" });
  }
});

//Ednpoint para eliminar una conversación completa entre dos usuarios
messagesRouter.delete("/delete-conversation/:contactId", authMiddleware, async (req, res) => {
  try {

    const currentUserId = req.user.userId;
    const contactId = req.params.contactId;

    await Message.deleteMany({
      $or: [
        { from: currentUserId, to: contactId },
        { from: contactId, to: currentUserId }
      ]
    });

    res.json({ success: true });

  } catch (error) {
    console.log(error);
    res.status(500).json({ error: "Error interno del servidor" });
  }
});