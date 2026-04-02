import mongoose from "mongoose";

const UserSchema = new mongoose.Schema(
  {
    userId: { type: String, required: true, unique: true, index: true },
    username: { type: String, required: true, unique: true },
    displayName: { type: String },
    pushTokens: [{ type: String }],
    sessionToken: { type: String },
    deviceId: { type: String }//,
    //role: { type: String, enum: ["user", "admin"], default: "user" }, // <-- campo de rol
  },
  { timestamps: true }
);

export const User = mongoose.model("User", UserSchema);