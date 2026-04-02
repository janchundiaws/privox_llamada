db = db.getSiblingDB('ghox'); // Cambia 'ghox' por el nombre de tu base de datos

// Usuario admin
db.users.updateOne(
  { username: "admin" },
  {
    $setOnInsert: {
      userId: "000000001",
      username: "admin",
      displayName: "Administrador",
      role: "admin",
      createdAt: new Date(),
      updatedAt: new Date()
      // password: "admin123" // Si quieres agregar contraseña, pon el hash aquí
    }
  },
  { upsert: true }
);

// Roles (ejemplo)
const roles = [
  { name: "ORGANIZATION_MANAGER", description: "ORGANIZATION_MANAGER", is_default: true },
  { name: "ORGANIZATION_ADMIN", description: "ORGANIZATION_ADMIN", is_default: true },
  { name: "ORGANIZATION_USER", description: "ORGANIZATION_USER", is_default: true }
];

roles.forEach(r => {
  db.roles.updateOne(
    { name: r.name },
    { $setOnInsert: r },
    { upsert: true }
  );
});