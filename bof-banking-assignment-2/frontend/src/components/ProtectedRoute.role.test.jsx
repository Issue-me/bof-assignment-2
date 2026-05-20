import React from 'react';
import { MemoryRouter, Navigate, Route, Routes } from 'react-router-dom';
import { render, screen } from '@testing-library/react';
import ProtectedRoute from './ProtectedRoute';

jest.mock('../context/AuthContext', () => ({
  useAuth: jest.fn(),
}));

const { useAuth } = require('../context/AuthContext');

const renderWithRole = (role, allowedRoles = ['TELLER', 'ADMIN']) => {
  useAuth.mockReturnValue({
    isAuthenticated: true,
    loading: false,
    user: { role },
  });

  return render(
    <MemoryRouter initialEntries={['/secured']}>
      <Routes>
        <Route
          path="/secured"
          element={
            <ProtectedRoute allowedRoles={allowedRoles}>
              <div>SECURED CONTENT</div>
            </ProtectedRoute>
          }
        />
        <Route path="/access-denied" element={<div>ACCESS DENIED PAGE</div>} />
        <Route path="/dashboard" element={<div>DASHBOARD PAGE</div>} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </MemoryRouter>
  );
};

describe('ProtectedRoute role access', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  test('CUSTOMER cannot access TELLER/ADMIN route', () => {
    renderWithRole('CUSTOMER', ['TELLER', 'ADMIN']);
    expect(screen.getByText('ACCESS DENIED PAGE')).toBeTruthy();
  });

  test('TELLER can access TELLER/ADMIN route', () => {
    renderWithRole('TELLER', ['TELLER', 'ADMIN']);
    expect(screen.getByText('SECURED CONTENT')).toBeTruthy();
  });

  test('ADMIN can access TELLER/ADMIN route', () => {
    renderWithRole('ADMIN', ['TELLER', 'ADMIN']);
    expect(screen.getByText('SECURED CONTENT')).toBeTruthy();
  });

  test('CUSTOMER can access CUSTOMER route', () => {
    renderWithRole('CUSTOMER', ['CUSTOMER']);
    expect(screen.getByText('SECURED CONTENT')).toBeTruthy();
  });
});
