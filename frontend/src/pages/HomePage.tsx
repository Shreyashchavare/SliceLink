import React from 'react';
import { Link } from 'react-router-dom';
import { Button } from '../components/common/Button';

export const HomePage: React.FC = () => {
  return (
    <div className="home-page">
      <section className="hero-section">
        <div className="hero-badge">
          <span>✨</span> Phase 9: React Frontend Foundation
        </div>
        <h1 className="hero-title">
          Shorten Links with <span className="hero-title-highlight">Extreme Scale</span>
        </h1>
        <p className="hero-subtitle">
          High-performance URL shortener backed by Snowflake IDs, Redis caching, Kafka click streaming, and distributed rate limiting.
        </p>
        <div className="hero-actions">
          <Link to="/register">
            <Button variant="primary" size="lg">Get Started Free</Button>
          </Link>
          <Link to="/connectivity">
            <Button variant="secondary" size="lg">Check API Health</Button>
          </Link>
        </div>
      </section>

      <section className="page-container">
        <div className="feature-grid">
          <div className="feature-card">
            <div className="feature-icon">⚡</div>
            <h3 className="feature-title">Ultra-Fast Redirection</h3>
            <p className="feature-description">
              Cache-aside architecture powered by Redis delivers sub-millisecond 302 redirects with automatic PostgreSQL fallbacks.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-icon">📊</div>
            <h3 className="feature-title">Real-Time Analytics</h3>
            <p className="feature-description">
              Asynchronous event streaming over Apache Kafka captures high-throughput click events with idempotent deduplication.
            </p>
          </div>

          <div className="feature-card">
            <div className="feature-icon">🛡️</div>
            <h3 className="feature-title">Abuse Prevention</h3>
            <p className="feature-description">
              Redis-backed rate limiters defend login and URL generation endpoints against brute-force attacks and volumetric spam.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
};

export default HomePage;
