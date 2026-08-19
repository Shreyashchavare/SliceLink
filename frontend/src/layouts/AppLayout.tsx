import React from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/common/Button';

export const AppLayout: React.FC = () => {
  const { user, isAuthenticated, logout } = useAuth();
  const { showInfo } = useToast();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    showInfo('You have been signed out.');
    navigate('/login');
  };

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

              {isAuthenticated ? (
                <>
                  <li>
                    <NavLink to="/dashboard" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                      Dashboard
                    </NavLink>
                  </li>
                  <li>
                    <NavLink to="/urls" className={({ isActive }) => `nav-link ${isActive ? 'active' : ''}`}>
                      My URLs
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
                    <div className="user-nav-badge" title={user?.email || ''}>
                      <span className="user-avatar" aria-hidden="true">
                        {user?.name ? user.name.charAt(0).toUpperCase() : 'U'}
                      </span>
                      <span>{user?.name || 'User'}</span>
                    </div>
                  </li>
                  <li>
                    <Button variant="ghost" size="sm" onClick={handleLogout} title="Sign Out">
                      Sign Out
                    </Button>
                  </li>
                </>
              ) : (
                <>
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
                </>
              )}
            </ul>
          </nav>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>

      <footer className="app-footer">
        <p>SliceLink — Scalable URL Shortening Platform • Phase 10 Authentication & URL Management</p>
      </footer>
    </div>
  );
};

export default AppLayout;
