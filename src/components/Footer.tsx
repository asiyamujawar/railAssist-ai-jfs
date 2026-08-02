import { Link } from 'react-router-dom';
import { TrainFront, Mail, Phone, MapPin } from 'lucide-react';

export function Footer() {
  return (
    <footer className="border-t border-navy-100 bg-white">
      <div className="mx-auto max-w-7xl px-4 py-12 lg:px-8">
        <div className="grid grid-cols-2 gap-8 md:grid-cols-4 lg:grid-cols-5">
          <div className="col-span-2 lg:col-span-2">
            <Link to="/" className="flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-btn bg-navy-600">
                <TrainFront className="h-5 w-5 text-white" strokeWidth={2} />
              </div>
              <span className="text-20 font-bold text-navy-600">TrainMate</span>
            </Link>
            <p className="mt-4 max-w-xs text-14 text-navy-400">
              AI-powered disruption concierge for Indian Railways. Search, book, and travel with confidence — we handle the rest.
            </p>
          </div>

          <div>
            <h4 className="label-text mb-3">Platform</h4>
            <ul className="space-y-2 text-14 text-navy-400">
              <li><Link to="/search" className="hover:text-navy-600">Search Trains</Link></li>
              <li><Link to="/trips" className="hover:text-navy-600">My Trips</Link></li>
              <li><Link to="/dashboard" className="hover:text-navy-600">Live Dashboard</Link></li>
              <li><Link to="/notifications" className="hover:text-navy-600">Notifications</Link></li>
            </ul>
          </div>

          <div>
            <h4 className="label-text mb-3">Account</h4>
            <ul className="space-y-2 text-14 text-navy-400">
              <li><Link to="/login" className="hover:text-navy-600">Sign In</Link></li>
              <li><Link to="/register" className="hover:text-navy-600">Create Account</Link></li>
              <li><Link to="/profile" className="hover:text-navy-600">Profile</Link></li>
            </ul>
          </div>

          <div>
            <h4 className="label-text mb-3">Contact</h4>
            <ul className="space-y-2 text-14 text-navy-400">
              <li className="flex items-center gap-2"><Mail className="h-3.5 w-3.5" /> support@trainmate.in</li>
              <li className="flex items-center gap-2"><Phone className="h-3.5 w-3.5" /> 1800-123-4567</li>
              <li className="flex items-center gap-2"><MapPin className="h-3.5 w-3.5" /> New Delhi, India</li>
            </ul>
          </div>
        </div>

        <div className="mt-12 flex flex-col items-center justify-between gap-4 border-t border-navy-100 pt-6 sm:flex-row">
          <p className="text-12 text-navy-300">© 2026 TrainMate Technologies Pvt. Ltd. All rights reserved.</p>
          <div className="flex gap-6 text-12 text-navy-300">
            <a href="#" className="hover:text-navy-500">Privacy</a>
            <a href="#" className="hover:text-navy-500">Terms</a>
            <a href="#" className="hover:text-navy-500">Refund Policy</a>
          </div>
        </div>
      </div>
    </footer>
  );
}
