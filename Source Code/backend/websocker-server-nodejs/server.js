require('dotenv').config();
const express = require('express');
const http = require('http');
const cors = require('cors');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 4000;
const CLIENT_ORIGINS = (process.env.CLIENT_ORIGINS || '*')
  .split(',')
  .map((origin) => origin.trim())
  .filter(Boolean);

const app = express();
app.use(express.json());
app.use(
  cors({
    origin: CLIENT_ORIGINS.length ? CLIENT_ORIGINS : '*',
    credentials: true,
  })
);

const server = http.createServer(app);
const wss = new WebSocketServer({ server, path: '/ws' });

let latestEsp32Data = null;
let latestEsp32Image = null;

const broadcast = (message, exceptSocket) => {
  wss.clients.forEach((client) => {
    if (client === exceptSocket || client.readyState !== client.OPEN) return;
    client.send(JSON.stringify(message));
  });
};

wss.on('connection', (socket, req) => {
  const peer = req.socket.remoteAddress;
  console.log(`WebSocket connected: ${peer}`);

  // Immediately send the latest reading so dashboards sync up fast
  if (latestEsp32Data) {
    socket.send(
      JSON.stringify({ type: 'server:data', payload: latestEsp32Data })
    );
  }

  socket.on('message', (rawMessage) => {
    try {
      const { type, payload } = JSON.parse(rawMessage.toString());

      if (type === 'esp32:data') {
        latestEsp32Data = { ...payload, receivedAt: Date.now() };
        console.log('ESP32 data:', latestEsp32Data);
        broadcast({ type: 'server:data', payload: latestEsp32Data }, socket);
        return;
      }

      if (type === 'esp32:image') {
        const { filename, contentType, data } = payload || {};

        if (!filename || !contentType || !data) {
          socket.send(
            JSON.stringify({
              type: 'server:error',
              payload: 'Missing filename/contentType/data for esp32:image',
            })
          );
          return;
        }

        latestEsp32Image = {
          filename,
          contentType,
          data,
          receivedAt: Date.now(),
          size: Buffer.byteLength(data, 'base64'),
        };

        console.log(
          `ESP32 image: ${filename} (${latestEsp32Image.size} bytes base64)`
        );

        socket.send(
          JSON.stringify({
            type: 'server:image:ack',
            payload: { filename, receivedAt: latestEsp32Image.receivedAt },
          })
        );

        broadcast({ type: 'server:image', payload: latestEsp32Image }, socket);
        return;
      }

      if (type === 'esp32:ping') {
        socket.send(JSON.stringify({ type: 'server:pong', payload: Date.now() }));
        return;
      }

      console.warn('Unknown message type:', type);
    } catch (error) {
      console.error('Failed to parse message:', error);
      socket.send(
        JSON.stringify({
          type: 'server:error',
          payload: 'Invalid JSON payload',
        })
      );
    }
  });

  socket.on('close', (code, reason) => {
    console.log(`WebSocket closed (${peer}) code=${code} reason=${reason}`);
  });

  socket.on('error', (error) => {
    console.error('WebSocket error:', error.message);
  });
});

// Simple REST endpoint for debugging or uptime checks
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: Date.now(),
    latestEsp32Data,
    latestEsp32Image: latestEsp32Image
      ? {
          filename: latestEsp32Image.filename,
          contentType: latestEsp32Image.contentType,
          receivedAt: latestEsp32Image.receivedAt,
          size: latestEsp32Image.size,
        }
      : null,
  });
});

server.listen(PORT, () => {
  console.log(`Socket server listening on port ${PORT}`);
});

