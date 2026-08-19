import React from 'react';
import { NavLink, Outlet } from 'react-router-dom';

export const AppLayout: React.FC = () => {
  return (
    <div className="app-layout">
      <header className="app-header">
        <div className="header-container">
          <NavLink to="/" className="brand-link">
            <span className="brand-logo" aria-hidden="true">⚡</span>
            <span className="brand-title">SliceLink</span>
          </NavLink>

          <nav aria-label="Main Navigation">
            <ul className="nav-links">
              <li>
                <NavLink to="/" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`} end>
                  Home
                </NavLink>
              </li>
              <li>
                <NavLink to="/dashboard" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                  Dashboard
                </NavLink>
              </li>
              <li>
                <NavLink to="/urls" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                  URLs
                </NavLink>
              </li>
              <li>
                <NavLink to="/analytics" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                  Analytics
                </NavLink>
              </li>
              <li>
                <NavLink to="/connectivity" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                  API Health
                </NavLink>
              </li>
              <li>
                <NavLink to="/login" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                  Login
                </NavLink>
              </li>
              <li>
                <NavLink to="/register" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                  Register
                </NavLink>
              </li>
            </ul>
          </nav>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      <footer className="app-footer">
        <p>SliceLink — Scalable URL Shortening Platform • Phase 9 Frontend Foundation</p>
      </footer>
    </div>
  );
};

export default AppLayout;
