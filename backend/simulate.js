// backend/simulate.js
const { Pool } = require('pg');
require('dotenv').config();

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

// Simple function to move a coordinate slightly
// 0.0001 lat/lng is roughly 11 meters
const moveCoordinate = (coord) => {
  const movement = (Math.random() - 0.5) * 0.002; // Move between -11m and +11m
  return parseFloat(coord) + movement;
};

const startSimulation = () => {
  console.log('🚌 Bus Simulation Started...');

  setInterval(async () => {
    try {
      // 1. Get all vehicles
      const res = await pool.query('SELECT * FROM vehicles WHERE status = $1', ['MOVING']);
      const vehicles = res.rows;

      // 2. Update each vehicle's position
      for (const bus of vehicles) {
        const newLat = moveCoordinate(bus.current_lat);
        const newLng = moveCoordinate(bus.current_lng);

        await pool.query(
          'UPDATE vehicles SET current_lat = $1, current_lng = $2, last_updated = NOW() WHERE id = $3',
          [newLat, newLng, bus.id]
        );
        
        // Optional: Log movement to console (can be noisy)
        // console.log(`Moved Bus ${bus.vehicle_number} to ${newLat.toFixed(4)}, ${newLng.toFixed(4)}`);
      }
    } catch (err) {
      console.error('Simulation Error:', err.message);
    }
  }, 5000); // Run every 5 seconds
};

module.exports = startSimulation;