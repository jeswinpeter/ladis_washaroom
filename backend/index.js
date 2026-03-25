// backend/index.js
const express = require('express');
const cors = require('cors');
const { Pool } = require('pg');
require('dotenv').config();
const startSimulation = require('./simulate');

const app = express();
const port = process.env.PORT || 3001;

const { getSunSide, calculateBearing } = require('./sunlogic');

const { calculateFare } = require('./farelogic');

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

// Start the background simulation
startSimulation();

// // === NEW SHADE ENDPOINT ===
// app.get('/api/shade-recommendation', async (req, res) => {
//   const { route_id, departure_time } = req.query;

//   try {
//     const routeResult = await pool.query('SELECT * FROM routes WHERE id = $1', [route_id]);
//     if (routeResult.rows.length === 0) return res.status(404).json({ error: 'Route not found' });

//     const route = routeResult.rows[0];
//     const travelTime = departure_time ? new Date(departure_time) : new Date();

//     // Hardcoded coordinates for MVP (match these to your seed data)
//     let startLat, startLng, endLat, endLng;

//     if (route_id == 1) { // Aluva - Vyttila
//       startLat = 10.0500; startLng = 76.3300;
//       endLat = 9.9800; endLng = 76.2900;
//     } else if (route_id == 2) { // Fort Kochi - Menaka
//       startLat = 9.9600; startLng = 76.2400;
//       endLat = 9.9400; endLng = 76.2600;
//     } else { // Kakkanad - Infopark
//       startLat = 10.0100; startLng = 76.3500;
//       endLat = 10.0300; endLng = 76.3800;
//     }

//     const bearing = calculateBearing(startLat, startLng, endLat, endLng);
//     const sunSide = getSunSide(startLat, startLng, travelTime, bearing);

//     let advice = '';
//     if (sunSide === 'LEFT') advice = 'Sit on the RIGHT side of the bus (Sun is on the left)';
//     else if (sunSide === 'RIGHT') advice = 'Sit on the LEFT side of the bus (Sun is on the right)';
//     else if (sunSide === 'FRONT') advice = 'Wear sunglasses, sun is in your face';
//     else if (sunSide === 'BACK') advice = 'Sun is behind you, you are good!';
//     else advice = 'It is night time, no sun concerns.';

//     res.json({
//       route: route.route_name,
//       time: travelTime.toISOString(),
//       sun_position: sunSide,
//       recommendation: advice
//     });

//   } catch (err) {
//     console.error(err);
//     res.status(500).send('Server Error');
//   }
// });

// === AUTORICKSHAW FARE CALCULATOR ===
app.post('/api/fare', (req, res) => {
  const {
    distanceKm,      // Distance in kilometers (required)
    isOneway,       // true/false (optional, default: false)
    waitingMinutes,// Minutes (optional, default: 0)
    startTime       // ISO string or use current time (optional)
  } = req.body;

  if (!distanceKm || distanceKm <= 0) {
    return res.status(400).json({ error: 'Valid distance in km is required' });
  }

  const travelTime = startTime ? new Date(startTime) : new Date();

  const result = calculateFare(distanceKm, {
    isOneway: isOneway || false,
    waitingMinutes: waitingMinutes || 0,
    startTime: travelTime
  });

  res.json(result);
});

// API endpont for sit-in-shade. Returns advice
app.get('/api/sun-side', (req, res) => {
  const { originLat, originLon, destLat, destLon, time } = req.query;

  if (!originLat || !originLon || !destLat || !destLon) {
    return res.status(400).json({ error: 'originLat, originLon, destLat, destLon are required' });
  }

  const travelTime = time ? new Date(time) : new Date();
  const bearing = calculateBearing(
    parseFloat(originLat), parseFloat(originLon),
    parseFloat(destLat), parseFloat(destLon)
  );
  const sunSide = getSunSide(parseFloat(originLat), parseFloat(originLon), travelTime, bearing);

  let advice = '';
  if (sunSide === 'LEFT') advice = 'Sit on the RIGHT side of the bus (Sun is on the left)';
  else if (sunSide === 'RIGHT') advice = 'Sit on the LEFT side of the bus (Sun is on the right)';
  else if (sunSide === 'FRONT') advice = 'Wear sunglasses, sun is in your face';
  else if (sunSide === 'BACK') advice = 'Sun is behind you, you are good!';
  else advice = 'It is night time, no sun concerns.';

  const sitSide = sunSide === 'LEFT' ? 'R'
    : sunSide === 'RIGHT' ? 'L'
      : (sunSide === 'BACK' || sunSide === 'NIGHT') ? 'L/R'
        : '–';

  res.json({ advice, sit_side: sitSide });
});

// === START SERVER ===
app.listen(port, () => {
  console.log(`🚀 Server running on http://localhost:${port}`);
});