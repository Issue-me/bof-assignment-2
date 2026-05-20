import React from "react";
import { Outlet, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { getSidebarItemsForRole } from "../utils/roleUtils";
import NotificationCenter from "../components/NotificationCenter";
import "./Layout.css";
import {
  SidebarProvider,
  Sidebar,
  SidebarHeader,
  SidebarContent,
  SidebarMenu,
  SidebarMenuItem,
  SidebarMenuButton,
  SidebarFooter,
  SidebarTrigger
} from "../components/ui/SideBar";

function MenuIcon({ label }) {
  const icons = {
    Dashboard: (
      <>
        <path d="m3 10 9-7 9 7" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M5 10v10h14V10" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
    Overview: (
      <>
        <path d="m3 10 9-7 9 7" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M5 10v10h14V10" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
    Accounts: <path d="M3 8h18v10H3zM3 12h18" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />,
    "My Transfers": (
      <>
        <path d="M8 4v16" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="m4.5 7 3.5-3.5L11.5 7" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M16 4v16" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="m12.5 17 3.5 3.5 3.5-3.5" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
    Transfer: (
      <>
        <path d="M8 4v16" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="m4.5 7 3.5-3.5L11.5 7" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M16 4v16" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="m12.5 17 3.5 3.5 3.5-3.5" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
    Deposit: <path d="M4 16 10 10l4 4 6-6M16 8h4v4" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />,
    "Transaction History": (
      <>
        <path d="M7 3h8l4 4v14H7z" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M15 3v4h4" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M10 12h6M10 16h6" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
    "Bill Payments": (
      <>
        <path d="M7 3h10v18l-2-1.4L13 21l-1-1.4L11 21l-2-1.4L7 21z" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M12 6.5v11" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M14.8 9.7c0-1.1-1.2-1.9-2.8-1.9s-2.8.8-2.8 1.9 1.2 1.9 2.8 1.9 2.8.8 2.8 1.9-1.2 1.9-2.8 1.9-2.8-.8-2.8-1.9" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
    Loans: (
      <>
        <rect x="6" y="3" width="12" height="18" rx="2" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M9 7h6M9 11h6M9 15h6M3 21h18" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
    Statements: <path d="M3 8h18v10H3zM3 12h18" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />,
    "Bank Statements": <path d="M3 8h18v10H3zM3 12h18" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />,
    "Email Notifications": <path d="M12 21a2 2 0 0 0 2-2H10a2 2 0 0 0 2 2Zm6-6V11a6 6 0 1 0-12 0v4l-2 2v1h16v-1z" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />,
    Profile: (
      <>
        <circle cx="12" cy="8" r="3" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M5 20a7 7 0 0 1 14 0" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
    "Tax Report": <path d="M4 3h10l4 4v14H4zM14 3v5h5M8 12h6M8 16h8" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />,
    "Manage Accounts": <path d="M4 8h16v12H4zM4 12h16M8 4h8" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />,
    "Manage Account Holders": <path d="M8 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6Zm8 0a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM3 21a5 5 0 0 1 10 0M11 21a5 5 0 0 1 10 0" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" />,
    "User Management": <path d="M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9a7 7 0 0 1 14 0" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />,
    "FRCS Report": <path d="M5 20h14M7 16V8m5 8V4m5 12v-6" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />,
    "Manage Interest Rates": (
      <>
        <circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M12 7v10M9 9.5h4.2a1.8 1.8 0 1 1 0 3.6H10.8a1.8 1.8 0 1 0 0 3.6H15" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
    "Transaction Monitoring": <path d="m4 16 5-5 4 4 7-8" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />,
    "Bill Management": <path d="M6 4h12v16H6zM9 8h6M9 12h4" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />,
    "Loan Management": (
      <>
        <rect x="6" y="3" width="12" height="18" rx="2" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
        <path d="M9 7h6M9 11h6M9 15h6M3 21h18" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" />
      </>
    ),
  };

  return (
    <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true">
      {icons[label] || <circle cx="12" cy="12" r="4" fill="currentColor" />}
    </svg>
  );
}

function Layout() {
  const { user, role, logout } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();

  const isRouteActive = (route) => pathname === route || pathname.startsWith(`${route}/`);
  
  const isTransfersActive = 
    pathname === '/transfer' ||
    pathname === '/transfer-limits' ||
    pathname === '/transfer-limit';
  
  const isBillPaymentsActive = 
    pathname === '/pay_bills' ||
    pathname === '/scheduled_bills' ||
    pathname.startsWith('/scheduled_bills/') ||
    pathname === '/bill_payment_history' ||
    pathname === '/schedule_bill_payment';

  const isChildRouteActive = (item) => {
    if (!item.children) {
      return false;
    }
    return item.children.some((child) => isRouteActive(child.path));
  };

  const menuItems = getSidebarItemsForRole(role);

  const pageTitle = (() => {
    if (pathname === "/dashboard") {
      return "Dashboard Overview";
    }

    for (const item of menuItems) {
      if (isRouteActive(item.path)) {
        return item.label;
      }

      if (item.children) {
        const activeChild = item.children.find((child) => isRouteActive(child.path));
        if (activeChild) {
          return activeChild.label;
        }
      }
    }

    return "Dashboard Overview";
  })();

  const userName = [user?.firstName, user?.lastName].filter(Boolean).join(" ") || "User";
  const userInitials = `${user?.firstName?.[0] || ""}${user?.lastName?.[0] || ""}`.trim() || "U";

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <SidebarProvider>
      <div className="layout-root">
        <Sidebar>
          <SidebarHeader>
            <div className="sidebar-brand">
              <img className="sidebar-logo" src="/assets/logo.png" alt="Bank of Fiji logo" />
              <span className="sidebar-bank-name">Bank of Fiji</span>
            </div>
          </SidebarHeader>
          <SidebarContent>
            <SidebarMenu>
              {menuItems.map((item) => (
                <SidebarMenuItem key={item.path} className={item.children ? 'sidebar-menu-group' : ''}>
                  <SidebarMenuButton
                    active={
                      item.children 
                        ? (
                            item.label === 'Bill Payments'
                              ? isBillPaymentsActive || isChildRouteActive(item)
                              : item.label === 'My Transfers'
                                ? isTransfersActive || isChildRouteActive(item)
                                : isChildRouteActive(item)
                          )
                        : isRouteActive(item.path)
                    }
                    onClick={() => navigate(item.path)}
                  >
                    <span className="sidebar-menu-icon"><MenuIcon label={item.label} /></span>
                    <span className="sidebar-menu-label">{item.label}</span>
                  </SidebarMenuButton>
                  {item.children && (
                    <ul className="sidebar-submenu">
                      {item.children.map((child) => (
                        <SidebarMenuItem key={child.path}>
                          <SidebarMenuButton
                            className="sidebar-submenu-button"
                            active={isRouteActive(child.path)}
                            onClick={() => navigate(child.path)}
                          >
                            {child.label}
                          </SidebarMenuButton>
                        </SidebarMenuItem>
                      ))}
                    </ul>
                  )}
                </SidebarMenuItem>
              ))}
            </SidebarMenu>
          </SidebarContent>
          <SidebarFooter>
            <div className="sidebar-user-block">
              <div className="sidebar-user-avatar">{userInitials}</div>
              <div className="sidebar-user-meta">
                <p className="sidebar-user-name">{userName}</p>
                <p className="sidebar-user-email">{user?.email || ""}</p>
              </div>
            </div>
            <button onClick={handleLogout} className="sidebar-footer-logout" title="Logout">
              Logout
            </button>
          </SidebarFooter>
        </Sidebar>
        <div className="layout-main">
          <div className="layout-topbar">
            <div className="layout-topbar-left">
              <SidebarTrigger />
              <h1 className="topbar-title">{pageTitle}</h1>
            </div>
            <div className="layout-topbar-right">
              <NotificationCenter />
              <span className="topbar-user-name">{userName}</span>
            </div>
          </div>
          <div className="layout-main-content">
            <Outlet />
          </div>
        </div>
      </div>
    </SidebarProvider>
  );
}

export default Layout;