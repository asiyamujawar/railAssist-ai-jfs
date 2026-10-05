import React from 'react';

const SkeletonLoader = ({ count = 3, height = 'h-16', className = '' }) => {
  return (
    <div className={`space-y-3 w-full ${className}`}>
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          className={`w-full bg-slate-200 animate-pulse rounded-xl ${height}`}
        />
      ))}
    </div>
  );
};

export default SkeletonLoader;
