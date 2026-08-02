import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { TrainFront, Mail, Lock, User, Phone, Eye, EyeOff, ArrowRight } from 'lucide-react';
import { cn } from '@/lib/cn';

export function RegisterPage() {
  const navigate = useNavigate();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [agree, setAgree] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = () => {
    const errs: Record<string, string> = {};
    if (!name.trim()) errs.name = 'Full name is required';
    else if (name.trim().length < 3) errs.name = 'Name must be at least 3 characters';
    if (!email.trim()) errs.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errs.email = 'Enter a valid email address';
    if (!phone.trim()) errs.phone = 'Phone number is required';
    else if (!/^[+]?[\d\s-]{10,15}$/.test(phone)) errs.phone = 'Enter a valid phone number';
    if (!password) errs.password = 'Password is required';
    else if (password.length < 6) errs.password = 'Password must be at least 6 characters';
    if (password !== confirmPassword) errs.confirmPassword = 'Passwords do not match';
    if (!agree) errs.agree = 'Please accept the terms to continue';
    return errs;
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const errs = validate();
    setErrors(errs);
    if (Object.keys(errs).length === 0) {
      navigate('/dashboard');
    }
  };

  return (
    <div className="flex min-h-[calc(100vh-4rem)] items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        <div className="card p-8">
          <Link to="/" className="flex items-center gap-2.5">
            <div className="flex h-10 w-10 items-center justify-center rounded-btn bg-navy-600">
              <TrainFront className="h-5 w-5 text-white" strokeWidth={2} />
            </div>
            <span className="text-24 font-bold text-navy-600">TrainMate</span>
          </Link>

          <h1 className="mt-8 text-24 font-bold text-navy-600">Create your account</h1>
          <p className="mt-1 text-14 text-navy-400">Join TrainMate to book trains and get AI-powered disruption alerts.</p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-5">
            <div>
              <label className="label-text mb-1.5 block">Full Name</label>
              <div className="relative">
                <User className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className={cn('input-field pl-10', errors.name && 'border-status-cancelled')}
                  placeholder="Arjun Mehta"
                />
              </div>
              {errors.name && <p className="mt-1 text-12 text-status-cancelled">{errors.name}</p>}
            </div>

            <div>
              <label className="label-text mb-1.5 block">Email Address</label>
              <div className="relative">
                <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className={cn('input-field pl-10', errors.email && 'border-status-cancelled')}
                  placeholder="you@email.com"
                />
              </div>
              {errors.email && <p className="mt-1 text-12 text-status-cancelled">{errors.email}</p>}
            </div>

            <div>
              <label className="label-text mb-1.5 block">Phone Number</label>
              <div className="relative">
                <Phone className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className={cn('input-field pl-10', errors.phone && 'border-status-cancelled')}
                  placeholder="+91 98765 43210"
                />
              </div>
              {errors.phone && <p className="mt-1 text-12 text-status-cancelled">{errors.phone}</p>}
            </div>

            <div>
              <label className="label-text mb-1.5 block">Password</label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className={cn('input-field pl-10 pr-10', errors.password && 'border-status-cancelled')}
                  placeholder="••••••••"
                />
                <button
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-navy-300 hover:text-navy-500"
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {errors.password && <p className="mt-1 text-12 text-status-cancelled">{errors.password}</p>}
            </div>

            <div>
              <label className="label-text mb-1.5 block">Confirm Password</label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  className={cn('input-field pl-10', errors.confirmPassword && 'border-status-cancelled')}
                  placeholder="••••••••"
                />
              </div>
              {errors.confirmPassword && <p className="mt-1 text-12 text-status-cancelled">{errors.confirmPassword}</p>}
            </div>

            <div>
              <label className="flex cursor-pointer items-start gap-2 text-14 text-navy-500">
                <input
                  type="checkbox"
                  checked={agree}
                  onChange={(e) => setAgree(e.target.checked)}
                  className="mt-0.5 h-4 w-4 rounded border-navy-200 text-accent-500 focus:ring-accent-100"
                />
                <span>I agree to the <span className="text-accent-600">Terms of Service</span> and <span className="text-accent-600">Privacy Policy</span></span>
              </label>
              {errors.agree && <p className="mt-1 text-12 text-status-cancelled">{errors.agree}</p>}
            </div>

            <button type="submit" className="btn-accent w-full">
              Create Account
              <ArrowRight className="h-4 w-4" />
            </button>
          </form>

          <p className="mt-6 text-center text-14 text-navy-400">
            Already have an account?{' '}
            <Link to="/login" className="font-semibold text-accent-600 hover:text-accent-700">
              Sign in
            </Link>
          </p>
        </div>

        <p className="mt-4 text-center text-12 text-navy-300">
          This is a demo. No real account is created.
        </p>
      </div>
    </div>
  );
}
