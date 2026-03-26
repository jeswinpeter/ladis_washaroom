const express = require('express');
const cors = require('cors');
const fs = require('fs');
const path = require('path');
const { exec } = require('child_process');
const archiver = require('archiver');
const AdmZip = require('adm-zip');
const { Pool } = require('pg');
require('dotenv').config();

const startSimulation = require('./simulate');
const { getSunSide, calculateBearing } = require('./sunlogic');
const { calculateFare } = require('./farelogic');

const app = express();
const port = process.env.PORT || 3001;
const OTP_DATA_DIR = path.join(__dirname, '..', 'otp', 'data');

app.use(cors());
app.use(express.json());

const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
});

if (!fs.existsSync(OTP_DATA_DIR)) {
  fs.mkdirSync(OTP_DATA_DIR, { recursive: true });
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1 * Math.PI / 180) *
    Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function deriveStopTimes(stops, tripId, majorStandIds = []) {
  if (!stops || stops.length === 0) return [];
  const anchorMs = new Date(stops[0].time).getTime();
  return stops.map((stop, i) => {
    const offsetSec = Math.round((new Date(stop.time).getTime() - anchorMs) / 1000);
    const toHMS = (sec) => {
      const h = Math.floor(sec / 3600);
      const m = Math.floor((sec % 3600) / 60);
      const s = sec % 60;
      return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
    };
    const isMajor = majorStandIds.includes(stop.stop_id);
    const depOffsetSec = offsetSec + (isMajor ? 240 : 0);
    return {
      trip_id: tripId,
      stop_id: stop.stop_id,
      stop_sequence: i + 1,
      arrival_time: toHMS(offsetSec),
      departure_time: toHMS(depOffsetSec),
      pickup_type: stop.may_stop ? 3 : 0,
      drop_off_type: stop.may_stop ? 3 : 0,
      sample_count: stop.sample_count || 1,
    };
  });
}

function csvEscape(value) {
  const raw = value === null || value === undefined ? '' : String(value);
  if (raw.includes(',') || raw.includes('"') || raw.includes('\n')) {
    return `"${raw.replace(/"/g, '""')}"`;
  }
  return raw;
}

function parseCsvLine(line) {
  const out = [];
  let current = '';
  let inQuotes = false;
  for (let i = 0; i < line.length; i += 1) {
    const ch = line[i];
    if (ch === '"') {
      if (inQuotes && line[i + 1] === '"') {
        current += '"';
        i += 1;
      } else {
        inQuotes = !inQuotes;
      }
    } else if (ch === ',' && !inQuotes) {
      out.push(current);
      current = '';
    } else {
      current += ch;
    }
  }
  out.push(current);
  return out;
}

async function runMigrations() {
  const migrationFile = path.join(__dirname, 'migrations', '001_crowdsource.sql');
  if (!fs.existsSync(migrationFile)) {
    console.warn('Migration file not found:', migrationFile);
    return;
  }
  const sql = fs.readFileSync(migrationFile, 'utf8');
  await pool.query(sql);
  console.log('Migrations applied');
}

async function exportGTFSZip() {
  const [stopsRes, routesRes, stopTimesRes, shapesRes] = await Promise.all([
    pool.query('SELECT * FROM gtfs_stops ORDER BY stop_id'),
    pool.query('SELECT * FROM gtfs_routes WHERE status = $1 ORDER BY route_id', ['verified']),
    pool.query('SELECT * FROM gtfs_stop_times ORDER BY trip_id, stop_sequence'),
    pool.query('SELECT * FROM gtfs_shapes ORDER BY shape_id, shape_pt_sequence'),
  ]);

  const now = new Date();
  const startDate = `${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}`;
  const end = new Date(now);
  end.setFullYear(end.getFullYear() + 2);
  const endDate = `${end.getFullYear()}${String(end.getMonth() + 1).padStart(2, '0')}${String(end.getDate()).padStart(2, '0')}`;

  const stopsTxt = [
    'stop_id,stop_name,stop_lat,stop_lon',
    ...stopsRes.rows.map((r) => `${csvEscape(r.stop_id)},${csvEscape(r.stop_name)},${r.stop_lat},${r.stop_lon}`),
  ].join('\n');

  const routesTxt = [
    'route_id,agency_id,route_short_name,route_long_name,route_type',
    ...routesRes.rows.map((r) => `${csvEscape(r.route_id)},CROWD,${csvEscape(r.route_short_name)},${csvEscape(r.route_long_name)},${r.route_type || 3}`),
  ].join('\n');

  const tripMap = new Map();
  stopTimesRes.rows.forEach((r) => {
    if (!tripMap.has(r.trip_id)) {
      const routeId = String(r.trip_id).split('__')[0] || String(r.trip_id).split('_')[0] || String(r.trip_id);
      tripMap.set(r.trip_id, routeId);
    }
  });

  const tripsTxt = [
    'route_id,service_id,trip_id,shape_id',
    ...Array.from(tripMap.entries()).map(([tripId, routeId]) => `${csvEscape(routeId)},DAILY,${csvEscape(tripId)},${csvEscape(tripId)}`),
  ].join('\n');

  const stopTimesTxt = [
    'trip_id,arrival_time,departure_time,stop_id,stop_sequence,pickup_type,drop_off_type',
    ...stopTimesRes.rows.map((r) => `${csvEscape(r.trip_id)},${csvEscape(r.arrival_time)},${csvEscape(r.departure_time)},${csvEscape(r.stop_id)},${r.stop_sequence},${r.pickup_type || 0},${r.drop_off_type || 0}`),
  ].join('\n');

  const shapesTxt = [
    'shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence',
    ...shapesRes.rows.map((r) => `${csvEscape(r.shape_id)},${r.shape_pt_lat},${r.shape_pt_lon},${r.shape_pt_sequence}`),
  ].join('\n');

  const agencyTxt = [
    'agency_id,agency_name,agency_url,agency_timezone',
    'CROWD,NavEz Crowdsourced,https://navez.app,Asia/Kolkata',
  ].join('\n');

  const calendarTxt = [
    'service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date',
    `DAILY,1,1,1,1,1,1,1,${startDate},${endDate}`,
  ].join('\n');

  const tempPath = path.join(OTP_DATA_DIR, 'crowdsourced.zip.tmp');
  const finalPath = path.join(OTP_DATA_DIR, 'crowdsourced.zip');

  await new Promise((resolve, reject) => {
    const output = fs.createWriteStream(tempPath);
    const archive = archiver('zip', { zlib: { level: 9 } });

    output.on('close', resolve);
    archive.on('error', reject);
    archive.pipe(output);

    archive.append(agencyTxt, { name: 'agency.txt' });
    archive.append(calendarTxt, { name: 'calendar.txt' });
    archive.append(stopsTxt, { name: 'stops.txt' });
    archive.append(routesTxt, { name: 'routes.txt' });
    archive.append(tripsTxt, { name: 'trips.txt' });
    archive.append(stopTimesTxt, { name: 'stop_times.txt' });
    if (shapesRes.rows.length > 0) {
      archive.append(shapesTxt, { name: 'shapes.txt' });
    }
    archive.finalize();
  });

  fs.renameSync(tempPath, finalPath);
  console.log('GTFS zip exported to', finalPath);
}

async function syncRouteIndex() {
  if (!fs.existsSync(OTP_DATA_DIR)) return;
  const zipFiles = fs.readdirSync(OTP_DATA_DIR)
    .filter((f) => f.toLowerCase().endsWith('.zip'));

  for (const zipName of zipFiles) {
    const zipPath = path.join(OTP_DATA_DIR, zipName);
    const zip = new AdmZip(zipPath);
    const entries = zip.getEntries();
    const routesEntry = entries.find((e) => e.entryName.endsWith('routes.txt'));
    if (!routesEntry) continue;

    const tripsEntry = entries.find((e) => e.entryName.endsWith('trips.txt'));
    const stopTimesEntry = entries.find((e) => e.entryName.endsWith('stop_times.txt'));

    const source = zipName.toLowerCase().includes('crowd') ? 'crowdsourced' : 'official';
    const routeLines = zip.readAsText(routesEntry).split(/\r?\n/).filter(Boolean);
    if (routeLines.length < 2) continue;
    const routeHeaders = parseCsvLine(routeLines[0]);

    const tripByRoute = new Map();
    if (tripsEntry) {
      const tripLines = zip.readAsText(tripsEntry).split(/\r?\n/).filter(Boolean);
      if (tripLines.length > 1) {
        const th = parseCsvLine(tripLines[0]);
        const ridx = th.indexOf('route_id');
        const tidx = th.indexOf('trip_id');
        for (let i = 1; i < tripLines.length; i += 1) {
          const cols = parseCsvLine(tripLines[i]);
          const routeId = cols[ridx];
          const tripId = cols[tidx];
          if (routeId && tripId && !tripByRoute.has(routeId)) {
            tripByRoute.set(routeId, tripId);
          }
        }
      }
    }

    const stopSeqByTrip = new Map();
    if (stopTimesEntry) {
      const stLines = zip.readAsText(stopTimesEntry).split(/\r?\n/).filter(Boolean);
      if (stLines.length > 1) {
        const sh = parseCsvLine(stLines[0]);
        const tidx = sh.indexOf('trip_id');
        const sidx = sh.indexOf('stop_id');
        const seqIdx = sh.indexOf('stop_sequence');
        for (let i = 1; i < stLines.length; i += 1) {
          const cols = parseCsvLine(stLines[i]);
          const tripId = cols[tidx];
          const stopId = cols[sidx];
          const seq = Number(cols[seqIdx] || 0);
          if (!tripId || !stopId) continue;
          if (!stopSeqByTrip.has(tripId)) stopSeqByTrip.set(tripId, []);
          stopSeqByTrip.get(tripId).push({ stopId, seq });
        }
      }
    }

    for (let i = 1; i < routeLines.length; i += 1) {
      const cols = parseCsvLine(routeLines[i]);
      const map = Object.fromEntries(routeHeaders.map((h, idx) => [h, cols[idx] || '']));
      const routeId = map.route_id;
      if (!routeId) continue;

      const tripId = tripByRoute.get(routeId);
      const stopSeq = tripId && stopSeqByTrip.get(tripId)
        ? stopSeqByTrip.get(tripId).sort((a, b) => a.seq - b.seq).map((v) => v.stopId)
        : [];

      const originStopId = stopSeq.length > 0 ? stopSeq[0] : null;
      const destStopId = stopSeq.length > 0 ? stopSeq[stopSeq.length - 1] : null;

      let originName = null;
      let destName = null;
      if (originStopId || destStopId) {
        const names = await pool.query(
          'SELECT stop_id, stop_name FROM gtfs_stops WHERE stop_id = ANY($1::varchar[])',
          [[originStopId, destStopId].filter(Boolean)]
        );
        const nameMap = new Map(names.rows.map((r) => [r.stop_id, r.stop_name]));
        originName = originStopId ? nameMap.get(originStopId) || null : null;
        destName = destStopId ? nameMap.get(destStopId) || null : null;
      }

      await pool.query(
        `INSERT INTO route_index
          (otp_route_id, short_name, long_name, operator, origin_stop_id, origin_name, dest_stop_id, dest_name, stop_ids, source, last_synced)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,NOW())
         ON CONFLICT (otp_route_id)
         DO UPDATE SET
          short_name = EXCLUDED.short_name,
          long_name = EXCLUDED.long_name,
          operator = EXCLUDED.operator,
          origin_stop_id = EXCLUDED.origin_stop_id,
          origin_name = EXCLUDED.origin_name,
          dest_stop_id = EXCLUDED.dest_stop_id,
          dest_name = EXCLUDED.dest_name,
          stop_ids = EXCLUDED.stop_ids,
          source = EXCLUDED.source,
          last_synced = NOW()`,
        [
          routeId,
          map.route_short_name || null,
          map.route_long_name || null,
          map.agency_id || null,
          originStopId,
          originName,
          destStopId,
          destName,
          JSON.stringify(stopSeq),
          source,
        ]
      );
    }
  }

  console.log('Route index sync complete');
}

function triggerOTPRebuild() {
  console.log('Triggering OTP graph rebuild...');
  exec('docker restart otp_server', (err) => {
    if (err) {
      console.error('OTP restart failed:', err.message);
    } else {
      console.log('OTP rebuild triggered successfully');
      setTimeout(() => {
        syncRouteIndex().catch((e) => {
          console.error('Route index sync failed:', e);
        });
      }, 360000);
    }
  });
}

// Existing endpoints
app.get('/api/routes', async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM routes');
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.get('/api/vehicles', async (req, res) => {
  try {
    const result = await pool.query('SELECT * FROM vehicles');
    res.json(result.rows);
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.post('/api/sos', async (req, res) => {
  const { lat, lng, user_id } = req.body;
  console.log(`SOS RECEIVED from User ${user_id} at ${lat}, ${lng}`);
  res.json({ status: 'success', message: 'Emergency contacts notified' });
});

app.post('/api/fare', (req, res) => {
  const { distanceKm, isOneway, waitingMinutes, startTime } = req.body;

  if (!distanceKm || distanceKm <= 0) {
    return res.status(400).json({ error: 'Valid distance in km is required' });
  }

  const travelTime = startTime ? new Date(startTime) : new Date();
  const result = calculateFare(distanceKm, {
    isOneway: isOneway || false,
    waitingMinutes: waitingMinutes || 0,
    startTime: travelTime,
  });
  res.json(result);
});

// Fixed route bug here.
app.get('/api/sun-side', (req, res) => {
  const {
    originLat,
    originLon,
    destLat,
    destLon,
    time,
  } = req.query;

  if (!originLat || !originLon || !destLat || !destLon) {
    return res.status(400).json({ error: 'originLat, originLon, destLat, destLon are required' });
  }

  const travelTime = time ? new Date(time) : new Date();
  const bearing = calculateBearing(
    parseFloat(originLat),
    parseFloat(originLon),
    parseFloat(destLat),
    parseFloat(destLon)
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

  res.json({ sun_side: sunSide, advice, sit_side: sitSide });
});

// New endpoints
app.get('/api/routes/search', async (req, res) => {
  try {
    const {
      q,
      originStopId,
      destStopId,
      lat,
      lng,
    } = req.query;

    const conditions = [];
    const values = [];

    if (q) {
      values.push(q);
      const qIdx = values.length;
      conditions.push(`(
        to_tsvector('english', COALESCE(short_name,'') || ' ' || COALESCE(long_name,'') || ' ' || COALESCE(operator,''))
          @@ plainto_tsquery('english', $${qIdx})
        OR short_name ILIKE $${qIdx + 1}
      )`);
      values.push(`%${q}%`);
    }

    if (originStopId) {
      values.push(originStopId);
      conditions.push(`origin_stop_id = $${values.length}`);
    }

    if (destStopId) {
      values.push(destStopId);
      conditions.push(`dest_stop_id = $${values.length}`);
    }

    if (lat && lng) {
      values.push(parseFloat(lat));
      values.push(parseFloat(lng));
      const latIdx = values.length - 1;
      const lngIdx = values.length;
      conditions.push(`EXISTS (
        SELECT 1
        FROM gtfs_stops s
        WHERE (
          6371000 * acos(
            LEAST(1, cos(radians($${latIdx})) * cos(radians(s.stop_lat)) *
            cos(radians(s.stop_lon) - radians($${lngIdx})) +
            sin(radians($${latIdx})) * sin(radians(s.stop_lat)))
          )
        ) <= 300
        AND route_index.stop_ids ? s.stop_id
      )`);
    }

    let query;
    if (conditions.length === 0) {
      query = `SELECT id, otp_route_id, short_name, long_name, operator, origin_name, dest_name, source, stop_ids
               FROM route_index
               ORDER BY last_synced DESC
               LIMIT 20`;
    } else {
      query = `SELECT id, otp_route_id, short_name, long_name, operator, origin_name, dest_name, source, stop_ids
               FROM route_index
               WHERE ${conditions.join(' AND ')}
               ORDER BY last_synced DESC
               LIMIT 50`;
    }

    const result = await pool.query(query, values);
    res.json({ routes: result.rows });
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.get('/api/stops/nearby', async (req, res) => {
  try {
    const lat = parseFloat(req.query.lat);
    const lng = parseFloat(req.query.lng);
    const radiusM = req.query.radiusM ? parseFloat(req.query.radiusM) : 100;

    if (Number.isNaN(lat) || Number.isNaN(lng)) {
      return res.status(400).json({ error: 'lat and lng are required' });
    }

    const result = await pool.query(
      `SELECT
          stop_id,
          stop_name,
          stop_lat AS lat,
          stop_lon AS lng,
          confidence,
          (
            6371000 * acos(
              LEAST(1, cos(radians($1)) * cos(radians(stop_lat)) *
                cos(radians(stop_lon) - radians($2)) +
                sin(radians($1)) * sin(radians(stop_lat))
              )
            )
          ) AS distance_m
        FROM gtfs_stops
        WHERE (
          6371000 * acos(
            LEAST(1, cos(radians($1)) * cos(radians(stop_lat)) *
              cos(radians(stop_lon) - radians($2)) +
              sin(radians($1)) * sin(radians(stop_lat))
            )
          )
        ) <= $3
        ORDER BY distance_m ASC
        LIMIT 5`,
      [lat, lng, radiusM]
    );

    res.json({ stops: result.rows, count: result.rows.length });
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.post('/api/stops', async (req, res) => {
  try {
    const lat = parseFloat(req.body.lat);
    const lng = parseFloat(req.body.lng);
    let name = (req.body.name || '').trim();

    if (Number.isNaN(lat) || Number.isNaN(lng)) {
      return res.status(400).json({ error: 'lat and lng are required' });
    }

    if (name.length < 2) {
      name = `Stop @ ${lat.toFixed(5)},${lng.toFixed(5)}`;
    }

    const nearby = await pool.query(
      `SELECT stop_id, stop_name, stop_lat AS lat, stop_lon AS lng, confidence,
       (
         6371000 * acos(
           LEAST(1, cos(radians($1)) * cos(radians(stop_lat)) *
             cos(radians(stop_lon) - radians($2)) +
             sin(radians($1)) * sin(radians(stop_lat))
           )
         )
       ) AS distance_m
       FROM gtfs_stops
       WHERE (
         6371000 * acos(
           LEAST(1, cos(radians($1)) * cos(radians(stop_lat)) *
             cos(radians(stop_lon) - radians($2)) +
             sin(radians($1)) * sin(radians(stop_lat))
           )
         )
       ) <= 50
       ORDER BY distance_m ASC
       LIMIT 1`,
      [lat, lng]
    );

    if (nearby.rows.length > 0) {
      return res.json({ created: false, stop: nearby.rows[0] });
    }

    const stopId = `CROWD_${Date.now()}_${Math.floor(1000 + Math.random() * 9000)}`;
    const inserted = await pool.query(
      `INSERT INTO gtfs_stops (stop_id, stop_name, stop_lat, stop_lon, source, confidence)
       VALUES ($1, $2, $3, $4, 'crowdsourced', 1)
       RETURNING stop_id, stop_name, stop_lat AS lat, stop_lon AS lng, confidence`,
      [stopId, name, lat, lng]
    );

    res.json({ created: true, stop: inserted.rows[0] });
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.get('/api/stops/corridor', async (req, res) => {
  try {
    const lat1 = parseFloat(req.query.lat1);
    const lon1 = parseFloat(req.query.lon1);
    const lat2 = parseFloat(req.query.lat2);
    const lon2 = parseFloat(req.query.lon2);

    if ([lat1, lon1, lat2, lon2].some(Number.isNaN)) {
      return res.status(400).json({ error: 'lat1, lon1, lat2, lon2 are required' });
    }

    const minLat = Math.min(lat1, lat2) - 0.005;
    const maxLat = Math.max(lat1, lat2) + 0.005;
    const minLon = Math.min(lon1, lon2) - 0.005;
    const maxLon = Math.max(lon1, lon2) + 0.005;

    const result = await pool.query(
      `WITH segment AS (
         SELECT
           $1::float8 AS lat1,
           $2::float8 AS lon1,
           $3::float8 AS lat2,
           $4::float8 AS lon2,
           (($3 - $1)^2 + ($4 - $2)^2) AS seg_len2
       )
       SELECT * FROM (
         SELECT
           s.stop_id,
           s.stop_name,
           s.stop_lat,
           s.stop_lon,
           CASE
             WHEN seg.seg_len2 = 0 THEN 0
             ELSE (((s.stop_lat - seg.lat1) * (seg.lat2 - seg.lat1)) + ((s.stop_lon - seg.lon1) * (seg.lon2 - seg.lon1))) / seg.seg_len2
           END AS proj_frac,
           CASE
             WHEN seg.seg_len2 = 0 THEN 999
             ELSE abs((s.stop_lat - seg.lat1) * (seg.lon2 - seg.lon1) - (s.stop_lon - seg.lon1) * (seg.lat2 - seg.lat1)) / sqrt(seg.seg_len2)
           END AS perp_dist
         FROM gtfs_stops s
         CROSS JOIN segment seg
         WHERE s.stop_lat BETWEEN $5 AND $6
           AND s.stop_lon BETWEEN $7 AND $8
       ) q
       WHERE q.perp_dist < 0.0014
         AND q.proj_frac BETWEEN 0.05 AND 0.95
       ORDER BY q.proj_frac ASC
       LIMIT 10`,
      [lat1, lon1, lat2, lon2, minLat, maxLat, minLon, maxLon]
    );

    res.json({ stops: result.rows, count: result.rows.length });
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.post('/api/trips', async (req, res) => {
  try {
    const {
      deviceId,
      busName,
      mode,
      otpRouteId,
      declaredFinalStopId,
      declaredFinalStopName,
      stops,
      shapePoints,
    } = req.body;

    if (!busName || String(busName).trim().length === 0) {
      return res.status(400).json({ error: 'busName is required' });
    }

    if (!Array.isArray(stops) || stops.length === 0 || !stops.some((s) => s.confirmed)) {
      return res.status(400).json({ error: 'At least one confirmed stop is required' });
    }

    for (let i = 1; i < stops.length; i += 1) {
      const prev = stops[i - 1];
      const curr = stops[i];
      if ([prev.lat, prev.lng, curr.lat, curr.lng].some((v) => Number.isNaN(parseFloat(v)))) {
        continue;
      }
      const gapKm = haversineKm(parseFloat(prev.lat), parseFloat(prev.lng), parseFloat(curr.lat), parseFloat(curr.lng));
      if (gapKm > 50) {
        return res.status(400).json({ error: `Consecutive stops too far apart at index ${i}` });
      }
    }

    const firstStop = stops[0];
    const firstStopId = firstStop.stop_id || '';
    const firstStopTime = firstStop.time;

    let duplicateGapMinutes = null;
    let duplicateExistingId = null;
    if (firstStopId && firstStopTime) {
      const duplicateRes = await pool.query(
        `SELECT id,
                ABS(EXTRACT(EPOCH FROM (((stops->0->>'time')::timestamptz - $3::timestamptz)))/60.0) AS gap_minutes
         FROM trip_submissions
         WHERE bus_name = $1
           AND status IN ('pending', 'accepted')
           AND COALESCE(stops->0->>'stop_id', '') = $2
           AND ABS(EXTRACT(EPOCH FROM (((stops->0->>'time')::timestamptz - $3::timestamptz)))/60.0) < 15
         ORDER BY gap_minutes ASC
         LIMIT 1`,
        [busName, firstStopId, firstStopTime]
      );
      if (duplicateRes.rows.length > 0) {
        duplicateGapMinutes = Number(duplicateRes.rows[0].gap_minutes);
        duplicateExistingId = duplicateRes.rows[0].id;
      }
    }

    const trustRes = await pool.query('SELECT trust_score FROM contributors WHERE device_id = $1', [deviceId || '']);
    const trustScore = trustRes.rows[0] ? Number(trustRes.rows[0].trust_score) : 0.5;

    const insertRes = await pool.query(
      `INSERT INTO trip_submissions
       (device_id, bus_name, mode, otp_route_id, declared_final_stop_id, declared_final_stop_name, stops, shape_points, trust_score_at_submit, status)
       VALUES ($1,$2,$3,$4,$5,$6,$7::jsonb,$8::jsonb,$9,'pending')
       RETURNING id`,
      [
        deviceId || null,
        busName,
        mode || 'new_route',
        otpRouteId || null,
        declaredFinalStopId || null,
        declaredFinalStopName || null,
        JSON.stringify(stops),
        JSON.stringify(shapePoints || []),
        trustScore,
      ]
    );

    if (deviceId) {
      await pool.query(
        `INSERT INTO contributors (device_id, submissions)
         VALUES ($1, 1)
         ON CONFLICT (device_id)
         DO UPDATE SET submissions = contributors.submissions + 1`,
        [deviceId]
      );
    }

    res.json({
      id: insertRes.rows[0].id,
      duplicate_gap_minutes: duplicateGapMinutes,
      duplicate_existing_id: duplicateExistingId,
    });
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.patch('/api/trips/:id/merge', async (req, res) => {
  try {
    const newSubmissionId = parseInt(req.params.id, 10);
    const { existingId, newStops } = req.body;

    if (!existingId) {
      return res.status(400).json({ error: 'existingId is required' });
    }

    const existingRes = await pool.query('SELECT id, stops FROM trip_submissions WHERE id = $1', [existingId]);
    if (existingRes.rows.length === 0) {
      return res.status(404).json({ error: 'Existing submission not found' });
    }

    let incomingStops = Array.isArray(newStops) ? newStops : null;
    if (!incomingStops) {
      const newRes = await pool.query('SELECT stops FROM trip_submissions WHERE id = $1', [newSubmissionId]);
      incomingStops = newRes.rows[0] ? newRes.rows[0].stops : [];
    }

    const existingStops = existingRes.rows[0].stops || [];
    const incomingMap = new Map((incomingStops || []).map((s) => [s.stop_id, s]));

    const merged = existingStops.map((s) => {
      const m = incomingMap.get(s.stop_id);
      if (!m || !s.time || !m.time) return s;
      const t1 = new Date(s.time).getTime();
      const t2 = new Date(m.time).getTime();
      const avg = new Date(Math.round((t1 + t2) / 2)).toISOString();
      return {
        ...s,
        time: avg,
        sample_count: (s.sample_count || 1) + 1,
      };
    });

    await pool.query('UPDATE trip_submissions SET stops = $1::jsonb WHERE id = $2', [JSON.stringify(merged), existingId]);
    if (!Number.isNaN(newSubmissionId)) {
      await pool.query('DELETE FROM trip_submissions WHERE id = $1', [newSubmissionId]);
    }

    res.json({ merged: true, existing_id: existingId });
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.get('/api/admin/submissions', async (req, res) => {
  try {
    const result = await pool.query(
      `SELECT ts.*, COALESCE(c.trust_score, 0.5) AS contributor_trust_score
       FROM trip_submissions ts
       LEFT JOIN contributors c ON c.device_id = ts.device_id
       WHERE ts.status = 'pending'
       ORDER BY ts.submitted_at DESC`
    );
    res.json({ submissions: result.rows });
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

app.post('/api/admin/promote/:id', async (req, res) => {
  const submissionId = parseInt(req.params.id, 10);
  const client = await pool.connect();
  let submission = null;

  try {
    await client.query('BEGIN');
    const subRes = await client.query('SELECT * FROM trip_submissions WHERE id = $1 FOR UPDATE', [submissionId]);
    if (subRes.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Submission not found' });
    }

    submission = subRes.rows[0];
    if (submission.status !== 'pending') {
      await client.query('ROLLBACK');
      return res.status(404).json({ error: 'Submission already reviewed' });
    }

    const routeId = submission.otp_route_id || `CROWD_${submission.id}`;
    await client.query(
      `INSERT INTO gtfs_routes (route_id, route_short_name, route_long_name, status, confirmation_count, source)
       VALUES ($1, $2, $3, 'verified', 1, 'crowdsourced')
       ON CONFLICT (route_id)
       DO UPDATE SET
         confirmation_count = gtfs_routes.confirmation_count + 1,
         status = 'verified'`,
      [routeId, submission.bus_name, submission.declared_final_stop_name || submission.bus_name]
    );

    const stops = submission.stops || [];
    const confirmedStops = stops.filter((s) => !s.may_stop);
    for (const stop of confirmedStops) {
      if (!stop.stop_id) continue;
      await client.query(
        `INSERT INTO gtfs_stops (stop_id, stop_name, stop_lat, stop_lon, source, confidence)
         VALUES ($1,$2,$3,$4,'crowdsourced',1)
         ON CONFLICT (stop_id)
         DO UPDATE SET confidence = gtfs_stops.confidence + 1`,
        [stop.stop_id, stop.name || stop.stop_id, stop.lat, stop.lng]
      );
    }

    const stopIds = stops.map((s) => s.stop_id).filter(Boolean);
    const majorRes = stopIds.length > 0
      ? await client.query('SELECT stop_id FROM gtfs_stops WHERE is_major_stand = true AND stop_id = ANY($1::varchar[])', [stopIds])
      : { rows: [] };
    const majorStandIds = majorRes.rows.map((r) => r.stop_id);

    const tripId = `${routeId}__${submission.id}`;
    const stopTimeRows = deriveStopTimes(stops, tripId, majorStandIds);
    for (const row of stopTimeRows) {
      await client.query(
        `INSERT INTO gtfs_stop_times
         (trip_id, stop_id, stop_sequence, arrival_time, departure_time, pickup_type, drop_off_type, sample_count)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
         ON CONFLICT (trip_id, stop_sequence)
         DO UPDATE SET
          arrival_time = EXCLUDED.arrival_time,
          departure_time = EXCLUDED.departure_time,
          pickup_type = EXCLUDED.pickup_type,
          drop_off_type = EXCLUDED.drop_off_type,
          sample_count = EXCLUDED.sample_count`,
        [
          row.trip_id,
          row.stop_id,
          row.stop_sequence,
          row.arrival_time,
          row.departure_time,
          row.pickup_type,
          row.drop_off_type,
          row.sample_count,
        ]
      );
    }

    if (Array.isArray(submission.shape_points) && submission.shape_points.length > 0) {
      for (let i = 0; i < submission.shape_points.length; i += 1) {
        const p = submission.shape_points[i];
        await client.query(
          `INSERT INTO gtfs_shapes (shape_id, shape_pt_lat, shape_pt_lon, shape_pt_sequence)
           VALUES ($1,$2,$3,$4)
           ON CONFLICT (shape_id, shape_pt_sequence)
           DO NOTHING`,
          [tripId, p.lat, p.lng, i + 1]
        );
      }
    }

    await client.query(
      `UPDATE trip_submissions
       SET status = 'accepted', reviewed_at = NOW(), reviewer_note = COALESCE($1, reviewer_note)
       WHERE id = $2`,
      [req.body.reviewer_note || null, submissionId]
    );

    if (submission.device_id) {
      await client.query(
        `UPDATE contributors
         SET accepted = accepted + 1
         WHERE device_id = $1`,
        [submission.device_id]
      );
    }

    await client.query('COMMIT');

    setImmediate(async () => {
      try {
        await exportGTFSZip();
        await syncRouteIndex();
        triggerOTPRebuild();
      } catch (e) {
        console.error('Post-promotion tasks failed:', e);
      }
    });

    res.json({ promoted: true, id: submissionId });
  } catch (err) {
    await client.query('ROLLBACK');
    console.error(err);
    res.status(500).send('Server Error');
  } finally {
    client.release();
  }
});

app.post('/api/admin/reject/:id', async (req, res) => {
  const submissionId = parseInt(req.params.id, 10);
  try {
    const updateRes = await pool.query(
      `UPDATE trip_submissions
       SET status = 'rejected', reviewed_at = NOW(), reviewer_note = $1
       WHERE id = $2
       RETURNING device_id`,
      [req.body.reviewer_note || null, submissionId]
    );

    if (updateRes.rows.length === 0) {
      return res.status(404).json({ error: 'Submission not found' });
    }

    const deviceId = updateRes.rows[0].device_id;
    if (deviceId) {
      await pool.query('UPDATE contributors SET flagged = flagged + 1 WHERE device_id = $1', [deviceId]);
    }

    res.json({ rejected: true, id: submissionId });
  } catch (err) {
    console.error(err);
    res.status(500).send('Server Error');
  }
});

pool.connect((err, client, release) => {
  if (err) {
    return console.error('Error acquiring client', err.stack);
  }
  console.log('Connected to PostgreSQL database');
  release();
});

app.listen(port, () => {
  console.log(`Server running on http://localhost:${port}`);
  runMigrations()
    .then(() => {
      startSimulation();
    })
    .catch((err) => {
      console.error('Migration error:', err);
      startSimulation();
    });
});