import { useState } from 'react';
import { useParams, useSearchParams, Link, useNavigate } from 'react-router-dom';
import { ArrowLeft, ArrowRight, Check, CreditCard, User, Plus, Trash2, CheckCircle2, Ticket, Home } from 'lucide-react';
import trainsData from '@/data/trains.json';
import usersData from '@/data/users.json';
import type { Train } from '@/types';
import { formatINR } from '@/lib/format';
import { cn } from '@/lib/cn';

type Step = 'passengers' | 'summary' | 'payment' | 'confirmed';

interface PassengerForm {
  name: string;
  age: string;
  gender: string;
  berth: string;
}

export function BookingPage() {
  const { trainId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const date = searchParams.get('date') || '2026-08-05';
  const classCode = searchParams.get('class') || '2A';

  const train = (trainsData.trains as Train[]).find((t) => t.id === trainId);
  const savedPassengers = usersData.passengers;

  const [step, setStep] = useState<Step>('passengers');
  const [passengers, setPassengers] = useState<PassengerForm[]>(
    savedPassengers.slice(0, 2).map((p) => ({
      name: p.name,
      age: String(p.age),
      gender: p.gender,
      berth: p.berthPreference,
    })),
  );
  const [contactName, setContactName] = useState(usersData.user.name);
  const [contactPhone, setContactPhone] = useState(usersData.user.phone);
  const [contactEmail, setContactEmail] = useState(usersData.user.email);
  const [cardNumber, setCardNumber] = useState('4242 4242 4242 4242');
  const [cardExpiry, setCardExpiry] = useState('12/28');
  const [cardCvv, setCardCvv] = useState('123');
  const [generatedPnr, setGeneratedPnr] = useState('');

  if (!train) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-16 lg:px-8">
        <p className="text-16 text-navy-400">Train not found.</p>
        <Link to="/search" className="btn-primary mt-4">Back to Search</Link>
      </div>
    );
  }

  const classObj = train.classes.find((c) => c.code === classCode) || train.classes[0];
  const baseFare = classObj.fare;
  const totalFare = baseFare * passengers.length;
  const taxes = Math.round(totalFare * 0.05);
  const grandTotal = totalFare + taxes;

  const addPassenger = () => {
    if (passengers.length >= 6) return;
    setPassengers([...passengers, { name: '', age: '', gender: 'Male', berth: 'No Preference' }]);
  };

  const removePassenger = (idx: number) => {
    setPassengers(passengers.filter((_, i) => i !== idx));
  };

  const updatePassenger = (idx: number, field: keyof PassengerForm, value: string) => {
    setPassengers(passengers.map((p, i) => (i === idx ? { ...p, [field]: value } : p)));
  };

  const passengersValid = passengers.every((p) => p.name.trim() && p.age.trim() && Number(p.age) > 0 && Number(p.age) < 125);
  const contactValid = contactName.trim() && contactPhone.trim() && contactEmail.trim();
  const paymentValid = cardNumber.replace(/\s/g, '').length >= 12 && cardExpiry && cardCvv.length >= 3;

  const handleProceedToSummary = () => {
    if (passengersValid && contactValid) setStep('summary');
  };

  const handleProceedToPayment = () => setStep('payment');

  const handlePay = () => {
    if (!paymentValid) return;
    const pnr = String(Math.floor(1000000000 + Math.random() * 8999999999));
    setGeneratedPnr(pnr);
    setStep('confirmed');
  };

  const steps: { key: Step; label: string }[] = [
    { key: 'passengers', label: 'Passengers' },
    { key: 'summary', label: 'Fare Summary' },
    { key: 'payment', label: 'Payment' },
    { key: 'confirmed', label: 'Confirmed' },
  ];
  const currentStepIdx = steps.findIndex((s) => s.key === step);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 lg:px-8">
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-24 font-bold text-navy-600">Book {train.name}</h1>
          <p className="text-14 text-navy-400">{train.number} · {train.fromName} → {train.toName} · {date}</p>
        </div>
        <Link to={`/trains/${train.id}`} className="btn-outline">
          <ArrowLeft className="h-4 w-4" />
          Back
        </Link>
      </div>

      {/* Step Indicator */}
      <div className="mb-8 flex items-center gap-2">
        {steps.map((s, idx) => (
          <div key={s.key} className="flex flex-1 items-center gap-2">
            <div className={cn(
              'flex h-8 w-8 items-center justify-center rounded-full text-12 font-bold transition-colors',
              idx <= currentStepIdx ? 'bg-accent-500 text-white' : 'bg-navy-50 text-navy-300',
            )}>
              {idx < currentStepIdx ? <Check className="h-4 w-4" /> : idx + 1}
            </div>
            <span className={cn('text-12 font-medium', idx <= currentStepIdx ? 'text-navy-600' : 'text-navy-300')}>
              {s.label}
            </span>
            {idx < steps.length - 1 && <div className={cn('h-px flex-1', idx < currentStepIdx ? 'bg-accent-500' : 'bg-navy-100')} />}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="lg:col-span-2">
          {/* Step: Passengers */}
          {step === 'passengers' && (
            <div className="card p-6">
              <h3 className="text-16 font-semibold text-navy-600">Passenger Details</h3>
              <p className="mt-1 text-14 text-navy-400">Add up to 6 passengers per booking.</p>

              <div className="mt-6 space-y-4">
                {passengers.map((p, idx) => (
                  <div key={idx} className="rounded-btn border border-navy-100 p-4">
                    <div className="mb-3 flex items-center justify-between">
                      <span className="flex items-center gap-2 text-14 font-semibold text-navy-600">
                        <User className="h-4 w-4 text-navy-400" />
                        Passenger {idx + 1}
                      </span>
                      {passengers.length > 1 && (
                        <button onClick={() => removePassenger(idx)} className="text-navy-300 hover:text-status-cancelled">
                          <Trash2 className="h-4 w-4" />
                        </button>
                      )}
                    </div>
                    <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
                      <div className="col-span-2 sm:col-span-2">
                        <label className="label-text mb-1 block">Full Name</label>
                        <input
                          type="text"
                          value={p.name}
                          onChange={(e) => updatePassenger(idx, 'name', e.target.value)}
                          placeholder="As per ID proof"
                          className="input-field"
                        />
                      </div>
                      <div>
                        <label className="label-text mb-1 block">Age</label>
                        <input
                          type="number"
                          value={p.age}
                          onChange={(e) => updatePassenger(idx, 'age', e.target.value)}
                          placeholder="Age"
                          className="input-field"
                        />
                      </div>
                      <div>
                        <label className="label-text mb-1 block">Gender</label>
                        <select
                          value={p.gender}
                          onChange={(e) => updatePassenger(idx, 'gender', e.target.value)}
                          className="input-field"
                        >
                          <option>Male</option>
                          <option>Female</option>
                          <option>Other</option>
                        </select>
                      </div>
                      <div className="col-span-2 sm:col-span-4">
                        <label className="label-text mb-1 block">Berth Preference</label>
                        <select
                          value={p.berth}
                          onChange={(e) => updatePassenger(idx, 'berth', e.target.value)}
                          className="input-field"
                        >
                          <option>No Preference</option>
                          <option>Lower</option>
                          <option>Upper</option>
                          <option>Side Lower</option>
                          <option>Side Upper</option>
                          <option>Middle</option>
                        </select>
                      </div>
                    </div>
                  </div>
                ))}
              </div>

              {passengers.length < 6 && (
                <button onClick={addPassenger} className="btn-outline mt-4 w-full">
                  <Plus className="h-4 w-4" />
                  Add Passenger
                </button>
              )}

              <div className="mt-8 border-t border-navy-100 pt-6">
                <h4 className="text-14 font-semibold text-navy-600">Contact Details</h4>
                <div className="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-3">
                  <div>
                    <label className="label-text mb-1 block">Full Name</label>
                    <input value={contactName} onChange={(e) => setContactName(e.target.value)} className="input-field" />
                  </div>
                  <div>
                    <label className="label-text mb-1 block">Phone</label>
                    <input value={contactPhone} onChange={(e) => setContactPhone(e.target.value)} className="input-field" />
                  </div>
                  <div>
                    <label className="label-text mb-1 block">Email</label>
                    <input value={contactEmail} onChange={(e) => setContactEmail(e.target.value)} className="input-field" />
                  </div>
                </div>
              </div>

              <button
                onClick={handleProceedToSummary}
                disabled={!passengersValid || !contactValid}
                className={cn('mt-6 w-full', (!passengersValid || !contactValid) ? 'btn-outline opacity-50 cursor-not-allowed' : 'btn-accent')}
              >
                Continue to Fare Summary
                <ArrowRight className="h-4 w-4" />
              </button>
            </div>
          )}

          {/* Step: Summary */}
          {step === 'summary' && (
            <div className="card p-6">
              <h3 className="text-16 font-semibold text-navy-600">Fare Summary</h3>

              <div className="mt-6 space-y-3">
                {passengers.map((p, idx) => (
                  <div key={idx} className="flex items-center justify-between rounded-btn border border-navy-100 p-3">
                    <div>
                      <p className="text-14 font-medium text-navy-600">{p.name}</p>
                      <p className="text-12 text-navy-400">{p.age} yrs · {p.gender} · {p.berth}</p>
                    </div>
                    <span className="text-14 font-semibold text-navy-600">{formatINR(baseFare)}</span>
                  </div>
                ))}
              </div>

              <div className="mt-6 space-y-2 border-t border-navy-100 pt-4">
                <div className="flex justify-between text-14">
                  <span className="text-navy-400">Base fare ({passengers.length} × {formatINR(baseFare)})</span>
                  <span className="text-navy-600">{formatINR(totalFare)}</span>
                </div>
                <div className="flex justify-between text-14">
                  <span className="text-navy-400">IRCTC convenience fee + GST</span>
                  <span className="text-navy-600">{formatINR(taxes)}</span>
                </div>
                <div className="flex justify-between border-t border-navy-100 pt-2">
                  <span className="text-16 font-bold text-navy-600">Total</span>
                  <span className="text-20 font-bold text-navy-600">{formatINR(grandTotal)}</span>
                </div>
              </div>

              <div className="mt-4 rounded-btn bg-navy-50 p-4 text-14 text-navy-400">
                <p className="font-medium text-navy-600">Train: {train.name} ({train.number})</p>
                <p>{train.fromName} → {train.toName} · {date}</p>
                <p>Class: {classObj.name} ({classObj.code})</p>
                <p>Departure: {train.departure} · Arrival: {train.arrival}</p>
              </div>

              <div className="mt-6 flex gap-3">
                <button onClick={() => setStep('passengers')} className="btn-outline flex-1">
                  <ArrowLeft className="h-4 w-4" />
                  Back
                </button>
                <button onClick={handleProceedToPayment} className="btn-accent flex-1">
                  Proceed to Payment
                  <ArrowRight className="h-4 w-4" />
                </button>
              </div>
            </div>
          )}

          {/* Step: Payment */}
          {step === 'payment' && (
            <div className="card p-6">
              <h3 className="text-16 font-semibold text-navy-600">Payment</h3>
              <p className="mt-1 text-14 text-navy-400">This is a mock payment — no real charge will be made.</p>

              <div className="mt-6 space-y-4">
                <div>
                  <label className="label-text mb-1.5 block">Card Number</label>
                  <div className="relative">
                    <CreditCard className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                    <input
                      value={cardNumber}
                      onChange={(e) => setCardNumber(e.target.value)}
                      className="input-field pl-10"
                      placeholder="0000 0000 0000 0000"
                    />
                  </div>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="label-text mb-1.5 block">Expiry</label>
                    <input value={cardExpiry} onChange={(e) => setCardExpiry(e.target.value)} className="input-field" placeholder="MM/YY" />
                  </div>
                  <div>
                    <label className="label-text mb-1.5 block">CVV</label>
                    <input type="password" value={cardCvv} onChange={(e) => setCardCvv(e.target.value)} className="input-field" placeholder="•••" />
                  </div>
                </div>
              </div>

              <div className="mt-6 rounded-btn bg-accent-50 p-4">
                <div className="flex items-center justify-between">
                  <span className="text-14 text-navy-400">Amount Payable</span>
                  <span className="text-24 font-bold text-navy-600">{formatINR(grandTotal)}</span>
                </div>
              </div>

              <div className="mt-6 flex gap-3">
                <button onClick={() => setStep('summary')} className="btn-outline flex-1">
                  <ArrowLeft className="h-4 w-4" />
                  Back
                </button>
                <button
                  onClick={handlePay}
                  disabled={!paymentValid}
                  className={cn('flex-1', !paymentValid ? 'btn-outline opacity-50 cursor-not-allowed' : 'btn-accent')}
                >
                  <CreditCard className="h-4 w-4" />
                  Pay {formatINR(grandTotal)}
                </button>
              </div>
            </div>
          )}

          {/* Step: Confirmed */}
          {step === 'confirmed' && (
            <div className="card p-8 text-center">
              <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-status-ontime/10">
                <CheckCircle2 className="h-8 w-8 text-status-ontime" />
              </div>
              <h3 className="mt-4 text-24 font-bold text-navy-600">Booking Confirmed!</h3>
              <p className="mt-2 text-14 text-navy-400">Your ticket has been booked successfully. TrainMate AI will monitor your journey for disruptions.</p>

              <div className="mx-auto mt-8 max-w-md rounded-card border border-navy-100 bg-navy-50 p-6 text-left">
                <div className="flex items-center justify-between border-b border-navy-100 pb-4">
                  <div>
                    <p className="text-12 text-navy-400">PNR Number</p>
                    <p className="text-24 font-bold text-navy-600">{generatedPnr}</p>
                  </div>
                  <Ticket className="h-8 w-8 text-accent-500" />
                </div>
                <div className="mt-4 space-y-2 text-14">
                  <div className="flex justify-between">
                    <span className="text-navy-400">Train</span>
                    <span className="font-medium text-navy-600">{train.name} ({train.number})</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-navy-400">Route</span>
                    <span className="font-medium text-navy-600">{train.fromName} → {train.toName}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-navy-400">Date</span>
                    <span className="font-medium text-navy-600">{date}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-navy-400">Class</span>
                    <span className="font-medium text-navy-600">{classObj.name}</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-navy-400">Passengers</span>
                    <span className="font-medium text-navy-600">{passengers.length}</span>
                  </div>
                  <div className="flex justify-between border-t border-navy-100 pt-2">
                    <span className="text-navy-400">Total Paid</span>
                    <span className="font-bold text-navy-600">{formatINR(grandTotal)}</span>
                  </div>
                </div>
              </div>

              <div className="mt-8 flex flex-col gap-3 sm:flex-row sm:justify-center">
                <Link to="/trips" className="btn-primary">
                  <Ticket className="h-4 w-4" />
                  View My Trips
                </Link>
                <Link to="/dashboard" className="btn-accent">
                  <Home className="h-4 w-4" />
                  Go to Dashboard
                </Link>
              </div>
            </div>
          )}
        </div>

        {/* Right sidebar: train info */}
        <div className="lg:col-span-1">
          <div className="card sticky top-20 p-6">
            <h3 className="text-14 font-semibold text-navy-600">Trip Details</h3>
            <div className="mt-4 space-y-3 text-14">
              <div className="flex justify-between">
                <span className="text-navy-400">Train</span>
                <span className="font-medium text-navy-600">{train.name}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-navy-400">Number</span>
                <span className="font-medium text-navy-600">{train.number}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-navy-400">From</span>
                <span className="font-medium text-navy-600">{train.fromName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-navy-400">To</span>
                <span className="font-medium text-navy-600">{train.toName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-navy-400">Departure</span>
                <span className="font-medium text-navy-600">{train.departure}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-navy-400">Arrival</span>
                <span className="font-medium text-navy-600">{train.arrival}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-navy-400">Duration</span>
                <span className="font-medium text-navy-600">{train.duration}</span>
              </div>
              <div className="flex justify-between border-t border-navy-100 pt-3">
                <span className="text-navy-400">Class</span>
                <span className="font-medium text-navy-600">{classObj.name}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-navy-400">Fare/person</span>
                <span className="font-medium text-navy-600">{formatINR(baseFare)}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-navy-400">Passengers</span>
                <span className="font-medium text-navy-600">{passengers.length}</span>
              </div>
              <div className="flex justify-between border-t border-navy-100 pt-3">
                <span className="text-16 font-bold text-navy-600">Total</span>
                <span className="text-20 font-bold text-navy-600">{formatINR(grandTotal)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
