# Deployment Guide - Ghox Server

## Requisitos previos

- Ubuntu 20.04+ o Debian 11+
- Docker y Docker Compose V2
- Certificados SSL de Let's Encrypt (para producción)
- Dominio apuntando al servidor

## Primera instalación en servidor nuevo

### 1. Instalar Docker

```bash
# Actualizar sistema
sudo apt update && sudo apt upgrade -y

# Instalar Docker
curl -fsSL https://get.docker.com -o get-docker.sh
sudo sh get-docker.sh

# Agregar usuario al grupo docker
sudo usermod -aG docker $USER
newgrp docker

# Instalar Docker Compose V2
sudo apt install docker-compose-plugin -y

# Verificar instalación
docker --version
docker compose version
```

### 2. Configurar Nginx (si migras desde nginx de sistema)

```bash
# Detener nginx del sistema
sudo systemctl stop nginx
sudo systemctl disable nginx

# Verificar puertos libres
sudo ss -tulpn | grep ':80\|:443'
```

### 3. Obtener certificados SSL (Let's Encrypt)

```bash
# Instalar certbot
sudo apt install certbot -y

# Generar certificados (nginx debe estar detenido)
sudo certbot certonly --standalone -d ghox-api.tech-services-explore.com

# Los certificados quedarán en: /etc/letsencrypt/live/ghox-api.tech-services-explore.com/
```

### 4. Clonar proyecto

```bash
cd ~
git clone <tu-repo-url> ghox_server
cd ghox_server
```

### 5. Configurar variables de entorno

```bash
# Copiar archivos de ejemplo
cp .env.example .env
cp .env.dev.example .env.dev
cp .env.staging.example .env.staging

# Editar con tus valores
nano .env
```

Variables requeridas en `.env`:
```
PORT=3003
MONGO_URI=mongodb://admin:adminpassword@mongo_ghox:27017/ghox?authSource=admin
JWT_SECRET=<tu-secreto-seguro>
TURN_SERVER_URL=turn:ghox-api.tech-services-explore.com:3478
TURN_USERNAME=<usuario-turn>
TURN_PASSWORD=<password-turn>
```

### 6. Deployment

```bash
# Opción 1: Usar script automatizado
chmod +x deploy.sh
./deploy.sh

# Opción 2: Manual
docker compose up -d

# Ver logs
docker compose logs -f
```

### 7. Configurar renovación automática de SSL

```bash
# Crear hook para recargar nginx después de renovación
sudo mkdir -p /etc/letsencrypt/renewal-hooks/deploy
sudo nano /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh
```

Contenido del archivo:
```bash
#!/bin/bash
docker exec ghox-nginx nginx -s reload
```

```bash
# Hacer ejecutable
sudo chmod +x /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

# Probar renovación
sudo certbot renew --dry-run
```

## Comandos útiles

```bash
# Ver estado de servicios
docker compose ps

# Ver logs
docker compose logs -f
docker compose logs -f app
docker compose logs -f nginx

# Reiniciar un servicio
docker compose restart app

# Detener todo
docker compose down

# Actualizar código y reiniciar
git pull
docker compose up -d --build

# Limpiar recursos no usados
docker system prune -a
```

## Arquitectura

```
Internet (80/443) → Nginx Container → Node.js App Container (3003)
                                   → MongoDB Container (27017)
                                   → Coturn Container (3478, 5349)
```

## Troubleshooting

### Contenedores no arrancan
```bash
docker compose down
docker compose up -d
docker compose logs -f
```

### Problemas con certificados SSL
```bash
# Verificar que existan
ls -la /etc/letsencrypt/live/ghox-api.tech-services-explore.com/

# Verificar permisos
sudo chmod -R 755 /etc/letsencrypt/live/
sudo chmod -R 755 /etc/letsencrypt/archive/
```

### Error de conexión a MongoDB
```bash
# Verificar que mongo esté corriendo
docker compose ps mongo_ghox

# Ver logs de mongo
docker compose logs mongo_ghox

# Reiniciar mongo
docker compose restart mongo_ghox
```

### WebSocket no conecta
```bash
# Verificar que nginx tenga configuración de upgrade
docker exec ghox-nginx cat /etc/nginx/conf.d/default.conf

# Verificar logs
docker compose logs nginx
docker compose logs app
```

## Seguridad

- Cambiar todas las contraseñas por defecto en producción
- Usar JWT_SECRET fuerte y único
- Configurar firewall (UFW)
- Habilitar fail2ban
- Mantener Docker actualizado

```bash
# Configurar firewall básico
sudo ufw allow 22
sudo ufw allow 80
sudo ufw allow 443
sudo ufw allow 3478
sudo ufw allow 5349
sudo ufw enable
```
