const BASE_MIN_KM = 1.5;
const MIN_FARE = 30;
const PER_KM_RATE = 15;
const NIGHT_MULTIPLIER = 1.5; // 50% extra
const ONEWAY_MULTIPLIER = 0.5; // 50% of (fare - min)

function calculateFare(distanceKm, options = {}) {
  const {
    isOneway = false,
    waitingMinutes = 0,
    startTime = new Date()
  } = options;

  // 1. Calculate Base Fare
  let fare = 0;
  if (distanceKm <= BASE_MIN_KM) {
    fare = MIN_FARE;
  } else {
    fare = MIN_FARE + (distanceKm - BASE_MIN_KM) * PER_KM_RATE;
  }

  // 2. Add Waiting Charge (Assuming ₹2 per minute - typical for Kerala)
  const waitingCharge = waitingMinutes * 2;
  fare += waitingCharge;

  // 3. Night Charge (10 PM - 5 AM)
  const hours = startTime.getHours();
  const isNight = hours >= 22 || hours < 5;
  if (isNight) {
    fare = fare * NIGHT_MULTIPLIER;
  }

  // 4. One-way Extra Charge
  // Formula: Extra = 50% of (fare - minimum charge)
  if (isOneway) {
    const extra = (fare - MIN_FARE) * ONEWAY_MULTIPLIER;
    fare = fare + extra;
  }

  return {
    baseFare: Math.round(fare * 100) / 100,
    breakdown: {
      distanceKm: distanceKm,
      minimumCharge: MIN_FARE,
      distanceFare: distanceKm > BASE_MIN_KM ? (distanceKm - BASE_MIN_KM) * PER_KM_RATE : 0,
      waitingMinutes: waitingMinutes,
      waitingCharge: waitingCharge,
      nightCharge: isNight ? Math.round((fare / NIGHT_MULTIPLIER) * 0.5 * 100) / 100 : 0,
      onewayCharge: isOneway ? Math.round((fare - MIN_FARE) * ONEWAY_MULTIPLIER * 100) / 100 : 0,
      isNight: isNight,
      isOneway: isOneway
    }
  };
}

module.exports = { calculateFare };