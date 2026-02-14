// backend/index.js
const express = require('express');
const cors = require('cors');
const { Pool } = require('pg');
require('dotenv').config();

const app = express();
const port = process.env.PORT || 3001;

// Middleware
app.use(cors()); // Allow Android emulator to access localhost
app.use(express.json());

// Database Connection
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

// Test DB Connection on Startup
pool.connect((err, client, release) => {
  if (err) {
    return console.error('❌ Error acquiring client', err.stack);
  }
  console.log('✅ Connected to PostgreSQL database');
  release();
});

// --- API ENDPOINTS ---

// 1. GET /api/routes - Returns all bus routes
app.get('/api/routes', async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM routes');
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

// 2. GET /api/vehicles - Returns live bus locations
app.get('/api/vehicles', async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM vehicles');
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

// 3. POST /api/sos - Handles SOS alerts (Mock implementation)
app.post('/api/sos', async (req, res) => {
  const { lat, lng, user_id } = req.body;
  console.log(`🚨 SOS RECEIVED from User ${user_id} at ${lat}, ${lng}`);
  
  // In a real app, this would trigger SMS/Email via Twilio/SendGrid
  res.json({ status: 'success', message: 'Emergency contacts notified' });
});

// Start Server
app.listen(port, () => {
  console.log(`🚀 Server running on http://localhost:${port}`);
});