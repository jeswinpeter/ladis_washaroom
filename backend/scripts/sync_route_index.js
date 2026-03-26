const { Pool } = require('pg');
const AdmZip = require('adm-zip');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

const pool = new Pool({ connectionString: process.env.DATABASE_URL });
const OTP_DATA_DIR = path.join(__dirname, '..', '..', 'otp', 'data');

function parseCsvLine(line) {
    const out = [];
    let current = '';
    let inQuotes = false;
    for (let i = 0; i < line.length; i++) {
        const ch = line[i];
        if (ch === '"') {
            if (inQuotes && line[i + 1] === '"') { current += '"'; i++; }
            else inQuotes = !inQuotes;
        } else if (ch === ',' && !inQuotes) {
            out.push(current); current = '';
        } else current += ch;
    }
    out.push(current);
    return out;
}

async function syncRouteIndex() {
    if (!fs.existsSync(OTP_DATA_DIR)) {
        console.error('OTP data dir not found:', OTP_DATA_DIR);
        process.exit(1);
    }

    const zipFiles = fs.readdirSync(OTP_DATA_DIR)
        .filter(f => f.toLowerCase().endsWith('.zip'));

    console.log(`Found ${zipFiles.length} zip(s):`, zipFiles);

    for (const zipName of zipFiles) {
        const zipPath = path.join(OTP_DATA_DIR, zipName);
        const zip = new AdmZip(zipPath);
        const entries = zip.getEntries();

        const routesEntry = entries.find(e => e.entryName.endsWith('routes.txt'));
        if (!routesEntry) { console.log(`No routes.txt in ${zipName}, skipping`); continue; }

        const tripsEntry = entries.find(e => e.entryName.endsWith('trips.txt'));
        const stopTimesEntry = entries.find(e => e.entryName.endsWith('stop_times.txt'));
        const stopsEntry = entries.find(e => e.entryName.endsWith('stops.txt'));
        const source = zipName.toLowerCase().includes('crowd') ? 'crowdsourced' : 'official';

        // Build stop name map from this zip's stops.txt
        const stopNameMap = new Map();
        if (stopsEntry) {
            const lines = zip.readAsText(stopsEntry).split(/\r?\n/).filter(Boolean);
            if (lines.length > 1) {
                const headers = parseCsvLine(lines[0]);
                const idIdx = headers.indexOf('stop_id');
                const nameIdx = headers.indexOf('stop_name');
                for (let i = 1; i < lines.length; i++) {
                    const cols = parseCsvLine(lines[i]);
                    if (cols[idIdx]) stopNameMap.set(cols[idIdx], cols[nameIdx] || '');
                }
            }
        }

        // Build trip → route map
        const tripByRoute = new Map();
        if (tripsEntry) {
            const lines = zip.readAsText(tripsEntry).split(/\r?\n/).filter(Boolean);
            if (lines.length > 1) {
                const h = parseCsvLine(lines[0]);
                const ridx = h.indexOf('route_id');
                const tidx = h.indexOf('trip_id');
                for (let i = 1; i < lines.length; i++) {
                    const cols = parseCsvLine(lines[i]);
                    if (cols[ridx] && !tripByRoute.has(cols[ridx]))
                        tripByRoute.set(cols[ridx], cols[tidx]);
                }
            }
        }

        // Build trip → ordered stop IDs map
        const stopSeqByTrip = new Map();
        if (stopTimesEntry) {
            const lines = zip.readAsText(stopTimesEntry).split(/\r?\n/).filter(Boolean);
            if (lines.length > 1) {
                const h = parseCsvLine(lines[0]);
                const tidx = h.indexOf('trip_id');
                const sidx = h.indexOf('stop_id');
                const seqIdx = h.indexOf('stop_sequence');
                for (let i = 1; i < lines.length; i++) {
                    const cols = parseCsvLine(lines[i]);
                    if (!cols[tidx] || !cols[sidx]) continue;
                    if (!stopSeqByTrip.has(cols[tidx])) stopSeqByTrip.set(cols[tidx], []);
                    stopSeqByTrip.get(cols[tidx]).push({ stopId: cols[sidx], seq: Number(cols[seqIdx] || 0) });
                }
            }
        }

        // Process each route
        const routeLines = zip.readAsText(routesEntry).split(/\r?\n/).filter(Boolean);
        const routeHeaders = parseCsvLine(routeLines[0]);
        let count = 0;

        for (let i = 1; i < routeLines.length; i++) {
            const cols = parseCsvLine(routeLines[i]);
            const map = Object.fromEntries(routeHeaders.map((h, idx) => [h, cols[idx] || '']));
            const routeId = map.route_id;
            if (!routeId) continue;

            const tripId = tripByRoute.get(routeId);
            const stopSeq = tripId && stopSeqByTrip.get(tripId)
                ? stopSeqByTrip.get(tripId).sort((a, b) => a.seq - b.seq).map(v => v.stopId)
                : [];

            const originStopId = stopSeq[0] || null;
            const destStopId = stopSeq[stopSeq.length - 1] || null;
            const originName = originStopId ? (stopNameMap.get(originStopId) || null) : null;
            const destName = destStopId ? (stopNameMap.get(destStopId) || null) : null;

            await pool.query(
                `INSERT INTO route_index
          (otp_route_id, short_name, long_name, operator,
           origin_stop_id, origin_name, dest_stop_id, dest_name,
           stop_ids, source, last_synced)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,NOW())
         ON CONFLICT (otp_route_id) DO UPDATE SET
           short_name = EXCLUDED.short_name,
           long_name  = EXCLUDED.long_name,
           origin_name = EXCLUDED.origin_name,
           dest_name   = EXCLUDED.dest_name,
           stop_ids    = EXCLUDED.stop_ids,
           last_synced = NOW()`,
                [
                    routeId,
                    map.route_short_name || null,
                    map.route_long_name || null,
                    map.agency_id || null,
                    originStopId, originName,
                    destStopId, destName,
                    JSON.stringify(stopSeq),
                    source,
                ]
            );
            count++;
        }
        console.log(`Synced ${count} routes from ${zipName}`);
    }

    console.log('Route index sync complete');
    await pool.end();
}

syncRouteIndex().catch(async err => {
    console.error('Sync failed:', err.message);
    await pool.end();
    process.exit(1);
});