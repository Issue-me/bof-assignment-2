import api from './api';

/**
 * Notification service — handles notification API calls
 */
const notificationService = {
  /**
   * Get all unread notifications for the current user
   */
  getUnreadNotifications: () => {
    return api.get('/notifications/unread');
  },

  /**
   * Get all notifications for the current user (paginated)
   */
  getNotifications: (page = 0, size = 10) => {
    return api.get(`/notifications?page=${page}&size=${size}`);
  },

  /**
   * Mark a notification as read
   */
  markAsRead: (notificationId) => {
    return api.patch(`/notifications/${notificationId}/read`);
  },

  /**
   * Delete a notification
   */
  deleteNotification: (notificationId) => {
    return api.delete(`/notifications/${notificationId}`);
  },

  /**
   * Clear all notifications
   */
  clearAllNotifications: () => {
    return api.delete('/notifications/clear');
  },
};

export default notificationService;
