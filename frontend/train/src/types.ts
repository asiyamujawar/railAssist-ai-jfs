export type TrainClassStatus = 'available' | 'limited' | 'waitlist';

export interface TrainClass {
  code: string;
  name: string;
  fare: number;
  available: number;
  status: TrainClassStatus;
}

export interface Train {
  id: string;
  name: string;
  number: string;
  type: string;
  from: string;
  fromName: string;
  to: string;
  toName: string;
  departure: string;
  arrival: string;
  duration: string;
  distance: number;
  daysOfWeek: string[];
  classes: TrainClass[];
  rating: number;
  onTime: number;
}

export interface ScheduleStop {
  station: string;
  code: string;
  arrival: string;
  departure: string;
  day: number;
  distance: number;
}

export interface Station {
  code: string;
  name: string;
  city: string;
}

export interface Passenger {
  id: string;
  name: string;
  age: number;
  gender: string;
  berthPreference: string;
}

export interface BookedPassenger {
  name: string;
  age: number;
  gender: string;
  berth: string;
  status: string;
}

export interface AlternativeTrain {
  trainNumber: string;
  trainName: string;
  departure: string;
  arrival: string;
  class: string;
  fare: number;
  available: number;
  arrivesEarlier: boolean;
  savesMinutes: number;
}

export interface Disruption {
  detected: boolean;
  reason: string;
  delayMinutes: number;
  alternativeTrain?: AlternativeTrain;
  rebooked?: boolean;
  rebookedTo?: string;
}

export interface Booking {
  id: string;
  pnr: string;
  trainId: string;
  trainNumber: string;
  trainName: string;
  trainType: string;
  from: string;
  fromCode: string;
  to: string;
  toCode: string;
  departure: string;
  arrival: string;
  duration: string;
  journeyDate: string;
  class: string;
  className: string;
  fare: number;
  passengers: BookedPassenger[];
  totalFare: number;
  bookingDate: string;
  status: string;
  tripType: string;
  hasDisruption: boolean;
  disruption?: Disruption;
}

export interface Hotel {
  id: string;
  bookingId: string;
  name: string;
  city: string;
  address: string;
  checkIn: string;
  checkOut: string;
  nights: number;
  roomType: string;
  guests: number;
  originalCheckInTime: string;
  updatedCheckInTime: string;
  status: string;
  before: { checkInTime: string; note: string };
  after: { checkInTime: string; note: string };
  price: number;
  image: string;
}

export interface Cab {
  id: string;
  bookingId: string;
  provider: string;
  driver: { name: string; phone: string; rating: number; trips: number };
  vehicle: { model: string; plate: string; color: string; type: string };
  pickup: string;
  destination: string;
  estimatedFare: number;
  status: string;
  before: { pickupTime: string; note: string };
  after: { pickupTime: string; note: string };
}

export interface NotificationItem {
  id: string;
  category: string;
  title: string;
  message: string;
  timestamp: string;
  severity: string;
  read: boolean;
  bookingId: string | null;
}

export interface TimelineStep {
  step: number;
  label: string;
  detail: string;
  time: string;
  status: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  phone: string;
  memberSince: string;
  tripsTaken: number;
  tier: string;
  avatar: string;
}
