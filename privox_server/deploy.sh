#!/bin/bash
set -e

echo "🚀 Iniciando deployment de Ghox Server..."

# Verificar Docker Compose V2
if ! command -v docker &> /dev/null; then
    echo "❌ Docker no está instalado"
    exit 1
fi

if ! docker compose version &> /dev/null; then
    echo "📦 Instalando Docker Compose V2..."
    sudo apt update
    sudo apt install docker-compose-plugin -y
fi

# Detener servicios existentes si existen
if [ -f docker-compose.yml ]; then
    echo "🛑 Deteniendo servicios existentes..."
    docker compose down
fi

# Generar certificados para coturn
echo "🔐 Generando certificados para TURN..."
docker compose up -d certs_gen
docker compose logs certs_gen

# Levantar solo producción con sus dependencias
echo "🐳 Levantando producción (app, mongo, coturn, nginx)..."
docker compose -f docker-compose.yml up -d --build --force-recreate app nginx

# Verificar estado
echo "✅ Verificando servicios..."
docker compose ps

echo ""
echo "✨ Deployment completado!"
echo "📊 Ver logs: docker compose logs -f"
echo "🔍 Estado: docker compose ps"
