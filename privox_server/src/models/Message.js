import mongoose from "mongoose";

const MessageSchema = new mongoose.Schema(
  {
    messageId: { type: String, required: true, unique: true, index: true },
    from: { type: String, ref: "User", required: true, index: true },
    to: { type: String, ref: "User", required: true, index: true },
    content: { type: String, required: true },
    status: { type: String, enum: ["sent", "delivered", "read"], default: "sent" },
    deliveredAt: { type: Date },
    readAt: { type: Date },
  },
  { timestamps: true }
);

// Índices para optimizar el listado de conversaciones y las búsquedas
MessageSchema.index({ from: 1, to: 1 });
MessageSchema.index({ createdAt: -1 });
MessageSchema.index({ content: "text" }); // Soporte para búsqueda por texto

export const Message = mongoose.model("Message", MessageSchema);