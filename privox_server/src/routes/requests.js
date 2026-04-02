import express from "express";
import mongoose from "mongoose";
import { Request } from "../models/Request.js";
import { authMiddleware } from "../middleware/auth.js";
import { User } from "../models/User.js";

const router = express.Router();

router.post("/",authMiddleware, async (req, res) => {
  try {
    const { to, meta } = req.body;

    if (!to) {
      return res.status(400).json({ error: "Campos 'to' son requeridos" });
    }
    // 👇 el "from" viene del token, no del body
    const from = req.user.userId;

    // 🔎 Validar si ya existe una solicitud pendiente entre from y to
    const existingRequest = await Request.findOne({
      from,
      to,
      status: { $in: ["pending", "accepted"] }, 
    });

    if (existingRequest) {
      return res.status(400).json({
        error: "Ya existe una solicitud activa para este usuario",
      });
    }
    // validar que ya exista una solicitud pendiente o aceptada en sentido contrario (to -> from)
    const reverseExistingRequest = await Request.findOne({
      from: to,
      to: from,
      status: { $in: ["pending", "accepted"] }, 
    });

    if (reverseExistingRequest) {
      return res.status(400).json({
        error: "Ya existe una solicitud activa de este usuario hacia ti",
      });
    }

    const existingCancelledRejectedRecRequest = await Request.findOne({
      from,
      to,
      status: { $in: ["cancelled", "rejected"] }, 
    });

    let request;
    if (existingCancelledRejectedRecRequest) {
      // 👇 Actualizamos el registro existente
      existingCancelledRejectedRecRequest.status = "pending";
      existingCancelledRejectedRecRequest.meta = meta;
      await existingCancelledRejectedRecRequest.save();
      request = existingCancelledRejectedRecRequest;
    } else {
      const newRequest = new Request({
        from,
        to,
        meta,
        status: "pending",
      });
      await newRequest.save();
      request = newRequest;
    }


    // 👇 Ajustamos la respuesta al esquema de Swagger
    res.status(201).json({ request: request });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: "Error interno del servidor" });
  }
});

router.get("/", authMiddleware, async (req, res) => {
  try {
    const { direction, status } = req.query;
    const userId = req.user.userId;

    // Construimos el filtro dinámico
    const filter = {};

    if (direction === "incoming") {
      filter.to = userId;
    } else if (direction === "outgoing") {
      filter.from = userId;
    } else {
      // Si no se especifica direction, traemos ambos
      filter.$or = [{ to: userId }, { from: userId }];
    }

    if (status) {
      filter.status = status;
    }

    const requests = await Request.find(filter).lean();

    // Buscar usuarios relacionados
    const userIds = [...requests.map(r => r.from), ...requests.map(r => r.to)];
    const users = await User.find({ userId: { $in: userIds } }).lean();

    const usersMap = Object.fromEntries(users.map(u => [u.userId, u]));

    const enriched = requests.map(r => ({
      ...r,
      from: usersMap[r.from],
      to: usersMap[r.to],
    }));

    res.json({ requests : enriched });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: "Error interno del servidor" });
  }
});

router.patch("/:id", authMiddleware, async (req, res) => {
  try {
    const { id } = req.params;
    const { status } = req.body;
    const userId = req.user.userId;

        // Validar que el string sea un ObjectId válido
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ error: "ID de solicitud inválido" });
    }

    // Validar estado permitido
    const allowedStatuses = ["accepted", "rejected", "cancelled", "pending"];
    if (!allowedStatuses.includes(status)) {
      return res.status(400).json({ error: "Estado inválido" });
    }

    // Buscar la solicitud
    const request = await Request.findById(id);
    if (!request) {
      return res.status(400).json({ error: "Solicitud no encontrada" });
    }

    if ((status == "accepted" || status == "rejected" ) && request.to !== userId) {
      return res.status(403).json({ error: "No tienes permiso para actualizar aceptar/rechazar esta solicitud." });
    }

    if (status == "cancelled" && request.from !== userId) {
      return res.status(403).json({ error: "No tienes permiso para cancelar esta solicitud, solo el emisor la puede cancelar" });
    }

    // Actualizar estado
    request.status = status;
    await request.save();

    res.json({ request });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: "Error interno del servidor" });
  }
});

router.delete("/:id", authMiddleware, async (req, res) => {
  try {
    const { id } = req.params;
    const userId = req.user.userId;

    // Validar que el string sea un ObjectId válido
    if (!mongoose.Types.ObjectId.isValid(id)) {
      return res.status(400).json({ error: "ID de solicitud inválido" });
    }

    // Buscar la solicitud
    const request = await Request.findById(id);
    if (!request) {
      return res.status(400).json({ error: "Solicitud no encontrada" });
    }

    // Solo el emisor o receptor pueden eliminar la solicitud
    if (request.from !== userId && request.to !== userId) {
      return res.status(403).json({ error: "No tienes permiso para eliminar esta solicitud." });
    }

    await Request.findByIdAndDelete(id);

    res.json({ message: "Solicitud eliminada correctamente" });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: "Error interno del servidor" });
  }
});

/* 
  Eliminar contacto
*/
router.delete("/contact/:userId", authMiddleware, async (req, res) => {
  try {
    const { userId } = req.params;
    const currentUserId = req.user.userId;

    // Eliminar la solicitud aceptada que representa la relación de contacto
    const deletedRequest = await Request.findOneAndDelete({
      $or: [
        { from: currentUserId, to: userId, status: "accepted" },
        { from: userId, to: currentUserId, status: "accepted" }
      ]
    });

    if (!deletedRequest) {
      return res.status(404).json({ error: "No se encontró una relación de contacto para eliminar." });
    }

    res.json({ message: "Contacto eliminado correctamente." });
  } catch (error) {
    console.error(error);
    res.status(500).json({ error: "Error interno del servidor" });
  }
});

export const requestsRouter = router;