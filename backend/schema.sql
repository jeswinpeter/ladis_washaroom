CREATE TABLE routes (
    id SERIAL PRIMARY KEY,
    route_name VARCHAR(100) NOT NULL, 
    start_point VARCHAR(100),
    end_point VARCHAR(100),
    stops JSONB, 
    polyline TEXT 
);

CREATE TABLE vehicles (
    id SERIAL PRIMARY KEY,
    vehicle_number VARCHAR(20) NOT NULL,
    route_id INTEGER REFERENCES routes(id),
    current_lat DECIMAL(10, 8),
    current_lng DECIMAL(10, 8),
    status VARCHAR(20) DEFAULT 'MOVING', 
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);