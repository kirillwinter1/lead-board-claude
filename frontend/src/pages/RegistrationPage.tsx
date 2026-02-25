import React, { useState, useRef, useCallback, useEffect } from 'react';
import axios from 'axios';
import { setDevTenantSlug } from '../utils/tenant';

export default function RegistrationPage() {
    const [name, setName] = useState('');
    const [slug, setSlug] = useState('');
    const [slugAvailable, setSlugAvailable] = useState<boolean | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');
    const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
    const abortRef = useRef<AbortController | null>(null);

    useEffect(() => {
        return () => {
            if (debounceRef.current) clearTimeout(debounceRef.current);
            abortRef.current?.abort();
        };
    }, []);

    const generateSlug = (companyName: string) => {
        return companyName
            .toLowerCase()
            .replace(/[^a-z0-9\s-]/g, '')
            .replace(/\s+/g, '-')
            .replace(/-+/g, '-')
            .replace(/^-|-$/g, '')
            .substring(0, 63);
    };

    const handleNameChange = (value: string) => {
        setName(value);
        const newSlug = generateSlug(value);
        setSlug(newSlug);
        setSlugAvailable(null);
        debouncedCheckSlug(newSlug);
    };

    const debouncedCheckSlug = useCallback((s: string) => {
        if (debounceRef.current) clearTimeout(debounceRef.current);
        if (s.length < 3) return;
        debounceRef.current = setTimeout(() => checkSlug(s), 300);
    }, []);

    const checkSlug = async (s: string) => {
        abortRef.current?.abort();
        const controller = new AbortController();
        abortRef.current = controller;
        try {
            const { data } = await axios.get(`/api/public/tenants/check-slug?slug=${s}`, {
                signal: controller.signal,
            });
            setSlugAvailable(data.available);
        } catch {
            if (!controller.signal.aborted) setSlugAvailable(null);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError('');

        try {
            const { data } = await axios.post('/api/public/tenants/register', { name, slug });
            // Dev mode (localhost): set tenant slug and redirect to /board
            const host = window.location.hostname;
            if (host === 'localhost' || host === '127.0.0.1') {
                setDevTenantSlug(data.slug);
                window.location.href = '/board';
            } else if (data.redirectUrl) {
                window.location.href = data.redirectUrl;
            }
        } catch (err: unknown) {
            const axiosErr = err as { response?: { data?: { error?: string } } };
            setError(axiosErr.response?.data?.error || 'Registration failed');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: 480, margin: '80px auto', padding: 24 }}>
            <h1 style={{ marginBottom: 8 }}>Create your workspace</h1>
            <p style={{ color: '#666', marginBottom: 32 }}>
                Get started with OneLane. Free 14-day trial.
            </p>

            <form onSubmit={handleSubmit}>
                <div style={{ marginBottom: 16 }}>
                    <label style={{ display: 'block', marginBottom: 4, fontWeight: 500 }}>
                        Company name
                    </label>
                    <input
                        type="text"
                        value={name}
                        onChange={e => handleNameChange(e.target.value)}
                        placeholder="Acme Corporation"
                        required
                        style={{
                            width: '100%', padding: '8px 12px', borderRadius: 6,
                            border: '1px solid #ddd', fontSize: 14
                        }}
                    />
                </div>

                <div style={{ marginBottom: 16 }}>
                    <label style={{ display: 'block', marginBottom: 4, fontWeight: 500 }}>
                        Workspace URL
                    </label>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <input
                            type="text"
                            value={slug}
                            onChange={e => {
                                const val = e.target.value.toLowerCase();
                                setSlug(val);
                                setSlugAvailable(null);
                                debouncedCheckSlug(val);
                            }}
                            placeholder="acme"
                            required
                            minLength={3}
                            maxLength={63}
                            pattern="[a-z][a-z0-9-]{1,61}[a-z0-9]"
                            style={{
                                flex: 1, padding: '8px 12px', borderRadius: 6,
                                border: `1px solid ${slugAvailable === false ? '#e74c3c' : slugAvailable === true ? '#27ae60' : '#ddd'}`,
                                fontSize: 14
                            }}
                        />
                        <span style={{ color: '#888', fontSize: 14 }}>.leadboard.app</span>
                    </div>
                    {slugAvailable === false && (
                        <span style={{ color: '#e74c3c', fontSize: 12 }}>This URL is taken</span>
                    )}
                    {slugAvailable === true && (
                        <span style={{ color: '#27ae60', fontSize: 12 }}>Available</span>
                    )}
                </div>

                {error && (
                    <div style={{ color: '#e74c3c', marginBottom: 16, fontSize: 14 }}>
                        {error}
                    </div>
                )}

                <button
                    type="submit"
                    disabled={loading || !name || !slug || slug.length < 3 || slugAvailable === false}
                    style={{
                        width: '100%', padding: '10px 16px', borderRadius: 6,
                        background: '#2563eb', color: '#fff', border: 'none',
                        fontSize: 14, fontWeight: 500, cursor: 'pointer',
                        opacity: loading ? 0.6 : 1
                    }}
                >
                    {loading ? 'Creating...' : 'Create workspace'}
                </button>
            </form>
        </div>
    );
}
