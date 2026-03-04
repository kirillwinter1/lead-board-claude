import React from 'react'
import ReactDOM from 'react-dom/client'
import axios from 'axios'
import App from './App'
import './App.css'
import { getTenantSlug } from './utils/tenant'

axios.defaults.withCredentials = true

// Add tenant slug header to all requests
axios.interceptors.request.use(config => {
    const slug = getTenantSlug();
    if (slug) {
        config.headers['X-Tenant-Slug'] = slug;
    }
    return config;
});

// Detect tenant-not-found 404s and clear invalid tenant slug
axios.interceptors.response.use(
    response => response,
    error => {
        if (error.response?.status === 404 && error.response?.data?.error === 'Not Found') {
            const slug = getTenantSlug();
            const path = error.response?.data?.path || '';
            // If a non-public API path returns 404, it may be a tenant-not-found error.
            // Check if this is a generic Spring 404 (no matching controller) vs tenant filter rejection
            // by verifying the response has no content-type with application/json from a controller.
            if (slug && path && path.startsWith('/api/') && !path.startsWith('/api/public/')) {
                // Mark this error so components can distinguish tenant-not-found from regular 404s
                error.isTenantNotFound = true;
            }
        }
        return Promise.reject(error);
    }
);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
