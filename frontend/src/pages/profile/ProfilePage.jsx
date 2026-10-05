import React from 'react';
import { User, Mail, Shield, Phone, LogOut, CheckCircle2 } from 'lucide-react';
import { useAuth } from '../../context/AuthContext';

const ProfilePage = () => {
  const { user, isAdmin, logout } = useAuth();

  return (
    <div className="space-y-8 max-w-4xl mx-auto animate-fade-in">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-extrabold text-slate-900 tracking-tight">Account Profile & Settings</h1>
        <p className="text-sm text-slate-500 mt-1">Manage your passenger profile details and active security roles.</p>
      </div>

      <div className="bg-white p-8 rounded-3xl border border-slate-200/80 shadow-xs space-y-8">
        {/* Avatar & User Info */}
        <div className="flex items-center gap-6 border-b border-slate-100 pb-8">
          <div className="w-20 h-20 rounded-3xl bg-blue-600 text-white font-extrabold text-3xl flex items-center justify-center shadow-lg shadow-blue-600/30">
            {user?.firstName ? user.firstName[0] : 'U'}
          </div>

          <div>
            <h2 className="text-xl font-extrabold text-slate-900">
              {user?.firstName} {user?.lastName}
            </h2>
            <p className="text-sm text-slate-500">{user?.email}</p>
            <div className="mt-2 flex items-center gap-2">
              <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold bg-blue-50 text-blue-700 border border-blue-200">
                <CheckCircle2 className="w-3.5 h-3.5" /> Authenticated Passenger
              </span>
              {isAdmin && (
                <span className="inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold bg-purple-50 text-purple-700 border border-purple-200">
                  <Shield className="w-3.5 h-3.5" /> Administrator
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Profile Details List */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 text-sm">
          <div className="p-4 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span className="text-xs text-slate-400 font-semibold uppercase block">First Name</span>
            <span className="font-bold text-slate-900">{user?.firstName || 'N/A'}</span>
          </div>

          <div className="p-4 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span className="text-xs text-slate-400 font-semibold uppercase block">Last Name</span>
            <span className="font-bold text-slate-900">{user?.lastName || 'N/A'}</span>
          </div>

          <div className="p-4 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span className="text-xs text-slate-400 font-semibold uppercase block">Email Address</span>
            <span className="font-bold text-slate-900">{user?.email || 'N/A'}</span>
          </div>

          <div className="p-4 bg-slate-50 rounded-2xl border border-slate-100 space-y-1">
            <span className="text-xs text-slate-400 font-semibold uppercase block">Account Role</span>
            <span className="font-bold text-slate-900">{user?.role || 'ROLE_USER'}</span>
          </div>
        </div>

        {/* Account Actions */}
        <div className="pt-6 border-t border-slate-100 flex items-center justify-between">
          <span className="text-xs text-slate-500">Security: Session token managed securely via JWT Bearer headers.</span>
          <button
            onClick={logout}
            className="px-5 py-2.5 bg-rose-600 hover:bg-rose-700 text-white font-bold text-xs rounded-xl shadow-md transition-colors flex items-center gap-2"
          >
            <LogOut className="w-4 h-4" /> Sign Out
          </button>
        </div>
      </div>
    </div>
  );
};

export default ProfilePage;
