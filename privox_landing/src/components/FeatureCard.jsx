import React from 'react';

export default function FeatureCard({ icon: Icon, title, description }) {
  return (
    <div className="glass feature-card">
      <div className="feature-icon-wrapper">
        <Icon size={28} />
      </div>
      <h3>{title}</h3>
      <p>{description}</p>
    </div>
  );
}
