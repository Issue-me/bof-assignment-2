import api from './api';

const userService = {
  async getAllUsers() {
    const response = await api.get('/users');
    return response.data;
  },

  async activateUser(userId) {
    await api.post(`/users/${userId}/activate`);
  },

  async deactivateUser(userId) {
    await api.post(`/users/${userId}/deactivate`);
  },
};

export default userService;
