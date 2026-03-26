const fs = require('fs');
const { Pool } = require('pg');
require('dotenv').config();

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function run() {
    const raw = fs.readFileSync('./scripts/kerala_stops.geojson', 'utf8');
    const data = JSON.parse(raw);
    const nodes = data.features || data.elements || [];
    console.log(`Loaded ${nodes.length} stops from file`);

    let inserted = 0;
    for (const node of nodes) {
        // GeoJSON format from overpass-turbo
        const props = node.properties || node.tags || {};
        const lat = node.geometry?.coordinates[1] ?? node.lat;
        const lon = node.geometry?.coordinates[0] ?? node.lon;
        const stopId = `OSM_${props['@id']?.replace('node/', '') || node.id}`;
        const stopName =
            props['name:en'] || props['name'] || props['name:ml'] ||
            props['description'] || props['ref'] ||
            `Stop @ ${parseFloat(lat).toFixed(5)},${parseFloat(lon).toFixed(5)}`;

        await pool.query(
            `INSERT INTO gtfs_stops (stop_id, stop_name, stop_lat, stop_lon, source, confidence)
       VALUES ($1,$2,$3,$4,'official',5)
       ON CONFLICT (stop_id) DO NOTHING`,
            [stopId, stopName, lat, lon]
        );
        inserted++;
    }

    console.log(`Inserted ${inserted} stops`);
    await pool.end();
}

run().catch(async (err) => {
    console.error('Import failed:', err.message);
    await pool.end();
    process.exit(1);
});