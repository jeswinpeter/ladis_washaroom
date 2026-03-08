// backend/sunlogic.js
const SunCalc = require('suncalc');

function getSunSide(lat, lng, time, heading) {
  const sunPos = SunCalc.getPosition(time, lat, lng);
  
  const sunAzimuth = (sunPos.azimuth * 180 / Math.PI) + 180;
  const sunAltitude = sunPos.altitude * 180 / Math.PI;
  
  if (sunAltitude < 0) {
    return 'NIGHT';
  }
  
  let angleDiff = heading - sunAzimuth;
  
  if (angleDiff > 180) angleDiff -= 360;
  if (angleDiff < -180) angleDiff += 360;
  
  if (Math.abs(angleDiff) < 45) {
    return 'FRONT';
  } else if (Math.abs(angleDiff) > 135) {
    return 'BACK';
  } else if (angleDiff > 0) {
    return 'LEFT';
  } else {
    return 'RIGHT';
  }
}

function calculateBearing(lat1, lon1, lat2, lon2) {
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const lat1Rad = lat1 * Math.PI / 180;
  const lat2Rad = lat2 * Math.PI / 180;
  
  const y = Math.sin(dLon) * Math.cos(lat2Rad);
  const x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);
  
  let bearing = Math.atan2(y, x) * 180 / Math.PI;
  return (bearing + 360) % 360;
}

module.exports = { getSunSide, calculateBearing };