import mongoose from "mongoose";

const CallSchema = new mongoose.Schema(
  {
    callId: { type: String, required: true, unique: true, index: true },
    from: { type: String, required: true },
    to: { type: String, required: true },
    status: {
      type: String,
      enum: ["ringing", "in_call", "ended", "missed", "rejected"],
      default: "ringing",
    },
    startedAt: { type: Date },
    endedAt: { type: Date },
    meta: { type: mongoose.Schema.Types.Mixed },
  },
  { timestamps: true }
);

export const Call = mongoose.model("Call", CallSchema);