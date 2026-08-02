import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { TrainFront, Mail, Lock, Eye, EyeOff, ArrowRight } from 'lucide-react';
import { cn } from '@/lib/cn';

export function LoginPage() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('arjun.mehta@email.com');
  const [password, setPassword] = useState('password');
  const [showPassword, setShowPassword] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const validate = () => {
    const errs: Record<string, string> = {};
    if (!email.trim()) errs.email = 'Email is required';
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) errs.email = 'Enter a valid email address';
    if (!password) errs.password = 'Password is required';
    else if (password.length < 6) errs.password = 'Password must be at least 6 characters';
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

          <h1 className="mt-8 text-24 font-bold text-navy-600">Welcome back</h1>
          <p className="mt-1 text-14 text-navy-400">Sign in to manage your trips and get live disruption alerts.</p>

          <form onSubmit={handleSubmit} className="mt-8 space-y-5">
            <div>
              <label className="label-text mb-1.5 block">Email Address</label>
              <div className="relative">
                <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className={cn('input-field pl-10', errors.email && 'border-status-cancelled focus:border-status-cancelled focus:ring-status-cancelled/10')}
                  placeholder="you@email.com"
                />
              </div>
              {errors.email && <p className="mt-1 text-12 text-status-cancelled">{errors.email}</p>}
            </div>

            <div>
              <label className="label-text mb-1.5 block">Password</label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-navy-300" />
                <input
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className={cn('input-field pl-10 pr-10', errors.password && 'border-status-cancelled focus:border-status-cancelled focus:ring-status-cancelled/10')}
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

            <div className="flex items-center justify-between text-14">
              <label className="flex cursor-pointer items-center gap-2 text-navy-500">
                <input type="checkbox" className="h-4 w-4 rounded border-navy-200 text-accent-500 focus:ring-accent-100" />
                Remember me
              </label>
              <span className="text-14 text-navy-400 cursor-pointer hover:text-accent-600">Forgot password?</span>
            </div>

            <button type="submit" className="btn-accent w-full">
              Sign In
              <ArrowRight className="h-4 w-4" />
            </button>
          </form>

          <p className="mt-6 text-center text-14 text-navy-400">
            Don't have an account?{' '}
            <Link to="/register" className="font-semibold text-accent-600 hover:text-accent-700">
              Create one
            </Link>
          </p>
        </div>

        <p className="mt-4 text-center text-12 text-navy-300">
          This is a demo. No real authentication is performed.
        </p>
      </div>
    </div>
  );
}
