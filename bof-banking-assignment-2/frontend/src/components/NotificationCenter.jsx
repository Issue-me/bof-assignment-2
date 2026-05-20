import React, { useEffect, useMemo, useRef, useState } from "react";
import notificationService from "../services/notificationService";
import "./NotificationCenter.css";

function normalizeNotifications(payload) {
  if (Array.isArray(payload)) {
    return payload;
  }

  if (Array.isArray(payload?.content)) {
    return payload.content;
  }

  return [];
}

function NotificationCenter() {
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const panelRef = useRef(null);

  const hasUnread = unreadCount > 0;

  const refreshNotifications = async () => {
    setLoading(true);
    try {
      const [notificationsResponse, unreadResponse] = await Promise.all([
        notificationService.getNotifications(0, 20),
        notificationService.getUnreadNotifications(),
      ]);

      setNotifications(normalizeNotifications(notificationsResponse?.data));
      setUnreadCount(normalizeNotifications(unreadResponse?.data).length);
    } catch (error) {
      if (error?.response?.status === 403) {
        setNotifications([]);
        setUnreadCount(0);
        return;
      }
      console.error("Failed to load notifications", error);
      setNotifications([]);
      setUnreadCount(0);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshNotifications();
    const timer = setInterval(refreshNotifications, 60000);
    return () => clearInterval(timer);
  }, []);

  useEffect(() => {
    const onClickOutside = (event) => {
      if (panelRef.current && !panelRef.current.contains(event.target)) {
        setOpen(false);
      }
    };

    if (open) {
      document.addEventListener("mousedown", onClickOutside);
    }

    return () => document.removeEventListener("mousedown", onClickOutside);
  }, [open]);

  const unreadIdsInView = useMemo(
    () => notifications.filter((item) => !item.read).map((item) => item.id),
    [notifications]
  );

  const handleToggle = async () => {
    const nextOpen = !open;
    setOpen(nextOpen);
    if (nextOpen) {
      await refreshNotifications();
    }
  };

  const handleMarkRead = async (id) => {
    try {
      await notificationService.markAsRead(id);
      setNotifications((current) =>
        current.map((item) =>
          item.id === id ? { ...item, read: true, readAt: new Date().toISOString() } : item
        )
      );
      setUnreadCount((current) => Math.max(0, current - 1));
    } catch (error) {
      console.error("Failed to mark notification as read", error);
    }
  };

  const handleMarkAllRead = async () => {
    if (unreadIdsInView.length === 0) {
      return;
    }

    try {
      await Promise.all(unreadIdsInView.map((id) => notificationService.markAsRead(id)));
      setNotifications((current) => current.map((item) => ({ ...item, read: true, readAt: item.readAt || new Date().toISOString() })));
      setUnreadCount(0);
    } catch (error) {
      console.error("Failed to mark all notifications as read", error);
    }
  };

  const handleClearAll = async () => {
    try {
      await notificationService.clearAllNotifications();
      setNotifications([]);
      setUnreadCount(0);
    } catch (error) {
      console.error("Failed to clear notifications", error);
    }
  };

  const formatType = (value) => {
    if (!value) {
      return "Notification";
    }
    return value.replaceAll("_", " ");
  };

  const formatDate = (value) => {
    if (!value) {
      return "";
    }
    return new Date(value).toLocaleString();
  };

  return (
    <div className="notification-center" ref={panelRef}>
      <button type="button" className="notification-bell" onClick={handleToggle} aria-label="View notifications">
        <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
          <path
            d="M12 3a6 6 0 0 0-6 6v3.46L4.29 15.3a1 1 0 0 0 .71 1.7h14a1 1 0 0 0 .71-1.7L18 12.46V9a6 6 0 0 0-6-6Zm0 19a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 22Z"
            fill="currentColor"
          />
        </svg>
        {hasUnread && <span className="notification-badge">{unreadCount > 99 ? "99+" : unreadCount}</span>}
      </button>

      {open && (
        <div className="notification-panel" role="dialog" aria-label="Notification center">
          <div className="notification-panel-header">
            <div>
              <h3>Notifications</h3>
              <p>{unreadCount} unread</p>
            </div>
            <div className="notification-actions">
              <button type="button" onClick={handleMarkAllRead} disabled={unreadIdsInView.length === 0}>
                Mark all read
              </button>
              <button type="button" onClick={handleClearAll} disabled={notifications.length === 0}>
                Clear
              </button>
            </div>
          </div>

          <div className="notification-list">
            {loading ? (
              <p className="notification-empty">Loading notifications...</p>
            ) : notifications.length === 0 ? (
              <p className="notification-empty">No notifications yet.</p>
            ) : (
              notifications.map((notification) => (
                <div
                  key={notification.id}
                  className={`notification-item ${notification.read ? "read" : "unread"}`}
                >
                  <div className="notification-item-head">
                    <span className="notification-type">{formatType(notification.type)}</span>
                    <span className={`notification-status ${notification.read ? "status-read" : "status-unread"}`}>
                      {notification.read ? "Read" : "Unread"}
                    </span>
                  </div>
                  <p className="notification-title">{notification.title}</p>
                  <p className="notification-message">{notification.message}</p>
                  <div className="notification-footer">
                    <span>{formatDate(notification.createdAt)}</span>
                    {!notification.read && (
                      <button type="button" onClick={() => handleMarkRead(notification.id)}>
                        Mark read
                      </button>
                    )}
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export default NotificationCenter;
