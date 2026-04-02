
import mongoose from "mongoose";

/**
 * Modelo Request
 * - from: userId de quien realiza la solicitud
 * - to: userId de la persona a la que se dirige la solicitud
 * - status: estado de la solicitud (pending, accepted, rejected, cancelled)
 * - requestedAt: fecha en que se generó la solicitud
 *
 * También utiliza `timestamps` para createdAt/updatedAt.
 */
const RequestSchema = new mongoose.Schema(
	{
		from: { type: String, ref: "User", required: true, index: true },
		to: { type: String, ref: "User", required: true, index: true },
		status: {
			type: String,
			enum: ["pending", "accepted", "rejected", "cancelled"],
			default: "pending",
		},
		requestedAt: { type: Date, default: () => new Date() },
		meta: { type: mongoose.Schema.Types.Mixed },
	},
	{ timestamps: true }
);

export const Request = mongoose.model("Request", RequestSchema);

