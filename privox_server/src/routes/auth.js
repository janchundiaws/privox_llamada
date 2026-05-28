import { Router } from "express";
import jwt from "jsonwebtoken";
import { User } from "../models/User.js";
import { authMiddleware } from "../middleware/auth.js";
import { generateUserId , generateDeviceId} from "../utils/generateId.js";

export const authRouter = Router();

/**
 * POST /api/auth/register
 * body: { username, displayName? }
 * Crea un usuario nuevo con un ID único de 9 dígitos
 */
authRouter.post("/register", async (req, res) => {
  const { username, displayName } = req.body || {};
  if (!username) return res.status(400).json({ error: "username requerido" });

  // validar que no exista username
  const exists = await User.findOne({ username });
  if (exists) return res.status(400).json({ error: "username ya en uso" });

  const userId = generateUserId();
  const deviceId = generateDeviceId();

  const token = jwt.sign(
    { userId: userId, username: username },
    process.env.JWT_SECRET,
    { expiresIn: "30d" }
  );

  const user = await User.create({
    userId,
    username,
    displayName: displayName || "",
    sessionToken: token,
    deviceId: deviceId//req.body.deviceId || "",
  });

  res.json({
    token,
    user: { id: user.userId, username: user.username, displayName: user.displayName, deviceId: user.deviceId, sessionToken: user.sessionToken}
  });
});

/**
 * PATCH /api/auth/display-name
 * body: { newDisplayName }
 * Requiere autenticación (JWT)
 * Cambia el displayName del usuario autenticado
 */
authRouter.patch("/display-name",authMiddleware, async (req, res) => {
  const authHeader = req.headers.authorization;
  if (!authHeader) return res.status(401).json({ error: "Token requerido" });

  const token = authHeader.split(" ")[1];

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    const user = await User.findOne({ userId: decoded.userId });
    if (!user) return res.status(404).json({ error: "Usuario no encontrado" });

    const { displayName } = req.body || {};

    if (!displayName) return res.status(400).json({ error: "displayName requerido" });

    const existingDisplayName = await User.findOne({ displayName });
    if (existingDisplayName && existingDisplayName.userId !== user.userId) {
      return res.status(400).json({ error: "displayName ya en uso" });
    }

    user.displayName = displayName;
    await user.save();

    res.json({ user: { id: user.userId, username: user.username, displayName: user.displayName } });
  } catch (err) {
    return res.status(401).json({ error: "Token inválido" });
  }
});


/**
 * POST /api/auth/login
 * body: { username }
 * Devuelve JWT si el usuario existe
 */
authRouter.post("/login", async (req, res) => {
  const { username , deviceId } = req.body || {};
  if (!username) return res.status(400).json({ error: "username requerido" });

  if (!deviceId) return res.status(400).json({ error: "deviceId requerido" });

  const user = await User.findOne({ username, deviceId });
  if (!user) return res.status(404).json({ error: "usuario no encontrado" });

  const token = jwt.sign(
    { userId: user.userId, username: user.username },
    process.env.JWT_SECRET,
    { expiresIn: "30d" }
  );

  res.json({
    token,
    user: { id: user.userId, username: user.username, displayName: user.displayName }
  });
});
