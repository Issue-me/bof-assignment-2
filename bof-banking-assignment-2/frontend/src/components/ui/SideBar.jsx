import React, { createContext, useContext, useState } from "react";
import "./sidebar.css";

const SidebarContext = createContext();

export function useSidebar() {
  return useContext(SidebarContext);
}

export function SidebarProvider({ children }) {
  const [open, setOpen] = useState(true);

  const toggleSidebar = () => {
    setOpen(!open);
  };

  return (
    <SidebarContext.Provider value={{ open, toggleSidebar }}>
      <div className="sidebar-wrapper">{children}</div>
    </SidebarContext.Provider>
  );
}

export function Sidebar({ children }) {
  const { open } = useSidebar();

  return (
    <div className={`sidebar ${open ? "expanded" : "collapsed"}`}>
      {children}
    </div>
  );
}

export function SidebarTrigger() {
  const { open, toggleSidebar } = useSidebar();

  return (
    <button className="sidebar-trigger" onClick={toggleSidebar} aria-label="toggle sidebar">
      <svg width="20" height="20" viewBox="0 0 24 24">
        {open ? (
          <path d="M14.5 6 8.5 12l6 6" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        ) : (
          <path d="m9.5 6 6 6-6 6" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        )}
      </svg>
    </button>
  );
}

export function SidebarMenu({ children }) {
  return <ul className="sidebar-menu">{children}</ul>;
}

export function SidebarMenuItem({ children, className = "" }) {
  return <li className={`sidebar-menu-item ${className}`.trim()}>{children}</li>;
}

export function SidebarMenuButton({ children, onClick, active = false, className = "" }) {
  const buttonClassName = ["sidebar-menu-button", active ? "active" : "", className]
    .filter(Boolean)
    .join(" ");

  return (
    <button type="button" className={buttonClassName} onClick={onClick}>
      {children}
    </button>
  );
}

export function SidebarContent({ children }) {
  return <div className="sidebar-content">{children}</div>;
}

export function SidebarHeader({ children }) {
  return <div className="sidebar-header">{children}</div>;
}

export function SidebarFooter({ children }) {
  return <div className="sidebar-footer">{children}</div>;
}