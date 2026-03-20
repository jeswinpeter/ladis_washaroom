const { Pool } = require('pg');
require('dotenv').config();

const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
});

const OVERPASS_URL = 'https://overpass-api.de/api/interpreter?data=[out:json][timeout:60];node[highway=bus_stop](8.18,74.85,12.77,77.42);out body;';

async function run() {
    console.log('Fetching OSM bus stops from Overpass...');
    const response = await fetch(OVERPASS_URL);
    if (!response.ok) {
        throw new Error(`Overpass request failed: ${response.status}`);
    }

    const payload = await response.json();
    const nodes = Array.isArray(payload.elements) ? payload.elements : [];
    console.log(`Received ${nodes.length} nodes`);

    let inserted = 0;
    for (const node of nodes) {
        const stopId = `OSM_${node.id}`;
        const stopName = (node.tags && (node.tags.name || node.tags['name:en'])) || 'Unnamed Stop';
        await pool.query(
            `INSERT INTO gtfs_stops (stop_id, stop_name, stop_lat, stop_lon, source, confidence)
       VALUES ($1,$2,$3,$4,'official',5)
       ON CONFLICT (stop_id) DO NOTHING`,
            [stopId, stopName, node.lat, node.lon]
        );
        inserted += 1;
    }

    console.log(`Processed ${inserted} stops`);
    await pool.end();
}

run().catch(async (err) => {
    console.error('Import failed:', err.message);
    await pool.end();
    process.exit(1);
});
