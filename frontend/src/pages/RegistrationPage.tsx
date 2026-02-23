import React, { useState } from 'react';
import axios from 'axios';

export default function RegistrationPage() {
    const [name, setName] = useState('');
    const [slug, setSlug] = useState('');
    const [slugAvailable, setSlugAvailable] = useState<boolean | null>(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState('');

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
        if (newSlug.length >= 3) {
            checkSlug(newSlug);
        }
    };

    const checkSlug = async (s: string) => {
        try {
            const { data } = await axios.get(`/api/public/tenants/check-slug?slug=${s}`);
            setSlugAvailable(data.available);
        } catch {
            setSlugAvailable(null);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        setError('');

        try {
            const { data } = await axios.post('/api/public/tenants/register', { name, slug });
            // Redirect to tenant subdomain
            if (data.redirectUrl) {
                window.location.href = data.redirectUrl;
            }
        } catch (err: any) {
            setError(err.response?.data?.error || 'Registration failed');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: 480, margin: '80px auto', padding: 24 }}>
            <h1 style={{ marginBottom: 8 }}>Create your workspace</h1>
            <p style={{ color: '#666', marginBottom: 32 }}>
                Get started with Lead Board. Free 14-day trial.
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
                                setSlug(e.target.value.toLowerCase());
                                setSlugAvailable(null);
                                if (e.target.value.length >= 3) checkSlug(e.target.value.toLowerCase());
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
