// backend/seed.js
const { Pool } = require('pg');
require('dotenv').config();

// Connect to the database using the URL from .env
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

const seedData = async () => {
  try {
    console.log('🌱 Seeding database...');

    // 1. Reset Tables (Drop if exists, then Create)
    await pool.query(`
      DROP TABLE IF EXISTS vehicles;
      DROP TABLE IF EXISTS routes;
      
      CREATE TABLE routes (
          id SERIAL PRIMARY KEY,
          route_name VARCHAR(100),
          start_point VARCHAR(100),
          end_point VARCHAR(100),
          stops JSONB
      );
      
      CREATE TABLE vehicles (
          id SERIAL PRIMARY KEY,
          vehicle_number VARCHAR(20),
          route_id INTEGER REFERENCES routes(id),
          current_lat DECIMAL(10, 8),
          current_lng DECIMAL(10, 8),
          status VARCHAR(20),
          last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP -- <--- ADDED THIS LINE
      );
    `);

    // 2. Insert Kerala Routes
    const routeRes = await pool.query(`
      INSERT INTO routes (route_name, start_point, end_point, stops)
      VALUES 
      ('Aluva - Vyttila', 'Aluva', 'Vyttila', '["Aluva", "Companypady", "Kalamassery", "Palarivattom", "Vyttila"]'),
      ('Fort Kochi - Menaka', 'Fort Kochi', 'Menaka', '["Fort Kochi", "Thoppumpady", "Naval Base", "Menaka"]'),
      ('Kakkanad - Infopark', 'Kakkanad', 'Infopark', '["Kakkanad", "CSEZ", "Infopark Phase 1", "Infopark Phase 2"]')
      RETURNING *;
    `);
    
    console.log(`✅ Created ${routeRes.rowCount} routes.`);

    // 3. Insert "Live" Vehicles linked to these routes
    await pool.query(`
      INSERT INTO vehicles (vehicle_number, route_id, current_lat, current_lng, status)
      VALUES 
      ('KL-07-AV-001', ${routeRes.rows[0].id}, 10.0500, 76.3300, 'MOVING'),
      ('KL-07-FK-002', ${routeRes.rows[1].id}, 9.9600, 76.2400, 'MOVING'),
      ('KL-07-KI-003', ${routeRes.rows[2].id}, 10.0100, 76.3600, 'STOPPED');
    `);

    console.log('✅ Vehicles positioned.');
    process.exit(0);
  } catch (err) {
    console.error('❌ Seeding failed:', err);
    process.exit(1);
  }
};

seedData();