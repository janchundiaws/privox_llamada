#!/bin/sh
# Script para generar turnserver.conf desde el template con variables de entorno

# Si no existe SERVER_HOST, usar localhost por defecto
SERVER_HOST=${SERVER_HOST:-localhost}

echo "🔧 Generando turnserver.conf con SERVER_HOST=$SERVER_HOST"

# Reemplazar ${SERVER_HOST} en el template
envsubst < /etc/coturn/turnserver.conf.template > /etc/coturn/turnserver.conf

echo "✅ Configuración generada"
cat /etc/coturn/turnserver.conf

# Ejecutar coturn con los argumentos pasados
exec "$@"
