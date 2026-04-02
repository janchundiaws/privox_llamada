import { Router } from "express";
import { authMiddleware } from "../middleware/auth.js";
import { User } from "../models/User.js";
import { Request } from "../models/Request.js";

export const usersRouter = Router();

/** GET /api/users/me */
usersRouter.get("/me", authMiddleware, async (req, res) => {
  const u = await User.findOne({ userId: req.user.userId }).lean();
  if (!u) return res.status(404).json({ error: "user not found" });
  res.json({ id: u.userId, username: u.username, displayName: u.displayName });
});

/** POST /api/users/find
 * body: { usernames: ["israel_q","ghostman"] }
 * Devuelve los usuarios que existen
 */
usersRouter.post("/find", authMiddleware, async (req, res) => {
  const { usernames } = req.body || {};
  if (!Array.isArray(usernames) || !usernames.length) {
    return res.status(400).json({ error: "usernames requerido" });
  }
  const users = await User.find({ username: { $in: usernames } }, { userId: 1, username: 1, displayName: 1 }).lean();
  res.json({ found: users });
});

/** GET /api/users
 * Devuelve todos los usuarios (solo campos públicos)
 */
usersRouter.get("/", authMiddleware, async (req, res) => {
  // exclude the current user and the admin user (if ADMIN_USERNAME is set)
  const filter = { userId: { $ne: req.user.userId } };
  if (process.env.ADMIN_USERNAME) {
    filter.username = { $ne: process.env.ADMIN_USERNAME };
  }

  const users = await User.find(filter, { userId: 1, username: 1, displayName: 1 }).lean();
  res.json({ users });
});

/** GET /api/usersAdd
 * Devuelve todos los usuarios a los que puedo agregar (no están Agregados a mi cuenta)
 */
usersRouter.get("/usersadd", authMiddleware, async (req, res) => {

  try {

    // Buscar requests donde el usuario actual sea 'from' o 'to'
    const requests = await Request.find({
      status: { $in: ["pending", "accepted"] },
      $or: [{ from: req.user.userId }, { to: req.user.userId }]
    }).lean();

    // Extraer todos los userIds involucrados
    const userIds = requests.flatMap(r => [r.from, r.to]);

    // Filtrar usuarios
    const filter = { userId: { $nin: userIds, $ne: req.user.userId } };
    if (process.env.ADMIN_USERNAME) {
      filter.username = { $ne: process.env.ADMIN_USERNAME };
    }

    const users = await User.find(filter, { userId: 1, username: 1, displayName: 1 }).lean();
    res.json({ users });

  } catch (err) {
    console.error("❌ Error en /usersadd:", err);
    return res.status(500).json({ error: "Error al obtener usuarios" });
  }
});

/** GET /api/users
 * Devuelve todos los usuarios que tengo agregado a mi cuenta
 */
usersRouter.get("/usersaccount", authMiddleware, async (req, res) => {
  try {
    // Buscar requests donde el usuario actual sea 'from' o 'to'
    const requests = await Request.find({
      status: "accepted",
      $or: [{ from: req.user.userId }, { to: req.user.userId }]
    }).lean();

    // Extraer todos los userIds involucrados
    const userIds = requests.flatMap(r => [r.from, r.to]);

    // Filtrar usuarios
    const filter = { userId: { $in: userIds, $ne: req.user.userId } };
    if (process.env.ADMIN_USERNAME) {
      filter.username = { $ne: process.env.ADMIN_USERNAME };
    }

    const users = await User.find(filter, { userId: 1, username: 1, displayName: 1 }).lean();
    res.json({ users });
  } catch (err) {
    console.error("❌ Error en /usersaccount:", err);
    res.status(500).json({ error: "Error al obtener usuarios" });
  }
});


/** GET /api/users/:id
 * Devuelve un usuario público por userId
 */
usersRouter.get("/:id", authMiddleware, async (req, res) => {
  const { id } = req.params;
  if (!id) return res.status(400).json({ error: "id requerido" });

  const u = await User.findOne(
    { userId: id },
    { userId: 1, username: 1, displayName: 1 }
  ).lean();

  if (!u) return res.status(404).json({ error: "user not found" });

  res.json({ user: { id: u.userId, username: u.username, displayName: u.displayName } });
});

/** DELETE /api/users/me
 * Elimina el usuario autenticado (según token)
 */
usersRouter.delete("/me", authMiddleware, async (req, res) => {
  try {
    const userId = req.user.userId; // 🔑 viene del token gracias a authMiddleware

    if (userId === "000000001") {
      return res.status(404).json({ error: "Usuario no puede ser eliminado" });
    }

    // Buscar usuario
    const user = await User.findOne({ userId });
    if (!user) {
      return res.status(404).json({ error: "Usuario no encontrado" });
    }

    // Eliminar usuario
    await User.deleteOne({ userId });

    // Eliminar requests relacionados
    await Request.deleteMany({ $or: [{ from: userId }, { to: userId }] });

    res.json({ success: true, message: `Usuario ${userId} eliminado correctamente` });
  } catch (err) {
    console.error("❌ Error al eliminar usuario:", err);
    res.status(500).json({ error: "Error interno al eliminar usuario" });
  }
});