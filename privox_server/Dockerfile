FROM node:18-alpine

# Create app directory
WORKDIR /usr/src/app

# Install dependencies (copy package files first for better caching)
COPY package*.json ./
RUN npm install --production

# Copy application source
COPY . ./

# Use production environment
ENV NODE_ENV=production

# Expose the port the app listens on (use PORT env var in your app)
EXPOSE 3003

# Start the app
CMD ["npm", "start"]
