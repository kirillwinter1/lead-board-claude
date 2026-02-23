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

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
