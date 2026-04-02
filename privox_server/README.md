# privox Server

Servidor Node.js para la aplicación privox.

Este repositorio contiene el backend (Express + Socket.IO + MongoDB) y configuraciones para ejecutar en tres ambientes: desarrollo, staging y producción usando Docker.

**Archivos Docker**
- `Dockerfile.dev` — imagen para desarrollo (instala dependencias de desarrollo, monta el código y ejecuta `npm run dev`).
- `Dockerfile.staging` — imagen para staging (instala dependencias de producción, `NODE_ENV=staging`).
- `Dockerfile` — imagen para producción (instala dependencias de producción, `NODE_ENV=production`).

**Prerequisitos**
- Docker & Docker Compose
- Node (sólo para desarrollo local sin contenedor)

Estructura relevante:

- `src/` — código fuente del servidor
- `package.json` — scripts: `dev` (node --watch src/index.js) y `start` (node src/index.js)
- `docker-compose.yml` — configura servicios `mongo`, `app_dev`, `app_staging`, `app` (producción)

Variables de entorno
--------------------
Se recomiendan archivos separados para cada ambiente:

- `.env.dev`
- `.env.staging`
- `.env.prod`

Ejemplos mínimos (no incluyas secretos reales en el repo):

`.env.dev`
```
PORT=3000
MONGO_URI=mongodb://admin:adminpassword@mongo:27017/privox?authSource=admin
JWT_SECRET=dev-secret
TWILIO_ACCOUNT_SID=
TWILIO_AUTH_TOKEN=
```

`.env.staging`
```
PORT=3000
MONGO_URI=mongodb://admin:adminpassword@mongo:27017/privox_staging?authSource=admin
JWT_SECRET=staging-secret
```

`.env`
```
PORT=3000
MONGO_URI=mongodb://<user>:<password>@mongo:27017/privox_prod?authSource=admin
JWT_SECRET=change-this-secret-to-a-strong-value
```

Cómo usar (Docker Compose)
--------------------------

Desde la raíz `privox_server`:

Desarrollo (monta el código, permite hot-reload):
```bash
docker compose up --build app_dev
```

Staging:
```bash
docker compose up --build app_staging
```

Producción (imagen optimizada):
```bash
docker compose up --build app
```

Comandos útiles
---------------
- Construir una imagen manualmente:
```bash
docker build -t privox_server:latest -f Dockerfile .
```

- Ejecutar contenedor conectando `.env`:
```bash
docker run --env-file .env.prod -p 3000:3000 privox_server:latest
```

Notas y recomendaciones
----------------------
- No incluyas archivos `.env` con secretos en el repositorio; añade una plantilla `.env.example` si quieres.
- Para desarrollo local puedes correr `npm install` y `npm run dev` sin Docker si prefieres.
- Considera usar `profiles` en `docker-compose` para activar sólo el servicio del ambiente necesario.
- Ajusta el puerto expuesto en los Dockerfiles y `docker-compose.yml` si tu app usa otro puerto (por defecto se expone `3000`).

Contacto
--------
Si quieres, puedo:
- Crear archivos `.env.example` en este repo.
- Añadir `profiles` a `docker-compose.yml`.
- Añadir un servicio `nginx` para manejar TLS / reverse proxy en producción.

---
Generado por el asistente — adapta los valores de entorno y secretos antes de desplegar.
