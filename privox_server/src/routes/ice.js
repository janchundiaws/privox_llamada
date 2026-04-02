import { Router } from "express";
export const iceRouter = Router();

// Configuración normal de ICE servers
iceRouter.get("/", async (req, res) => {
  try {
    if (process.env.TURN_URL) {
      return res.json({
        iceServers: [
          { 
            urls: process.env.TURN_URL, 
            username: process.env.TURN_USER || "", 
            credential: process.env.TURN_PASS || "" 
          }
        ]
      });
    }
    
    console.error("No TURN server configured. Set TURN_URL, TURN_USER, and TURN_PASS in .env");
    return res.status(500).json({ 
      error: "No TURN server configured",
      iceServers: [] 
    });
  } catch (err) {
    console.error("ice-config error:", err);
    return res.status(500).json({ 
      error: "Failed to get ICE servers",
      iceServers: [] 
    });
  }
});

// Endpoint compatible para cliente web (mismo contenido que "/")
iceRouter.get("/config", async (req, res) => {
  try {
    if (process.env.TURN_URL) {
      return res.json({
        iceServers: [
          { 
            urls: process.env.TURN_URL, 
            username: process.env.TURN_USER || "", 
            credential: process.env.TURN_PASS || "" 
          }
        ]
      });
    }
    
    console.error("No TURN server configured. Set TURN_URL, TURN_USER, and TURN_PASS in .env");
    return res.status(500).json({ 
      error: "No TURN server configured",
      iceServers: [] 
    });
  } catch (err) {
    console.error("ice-config error:", err);
    return res.status(500).json({ 
      error: "Failed to get ICE servers",
      iceServers: [] 
    });
  }
});

// Endpoint para forzar TURN only (ya no necesario con servidor propio)
iceRouter.get("/turn-only", async (req, res) => {
  // Redirigir al endpoint principal ya que solo usamos nuestro servidor
  return res.redirect("/api/ice");
});