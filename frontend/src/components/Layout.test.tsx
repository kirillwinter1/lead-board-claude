import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import axios from 'axios'
import { Layout } from './Layout'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

// Mock the logo import
vi.mock('../icons/logo.png', () => ({ default: 'logo.png' }))

describe('Layout', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    // Default: not authenticated
    mockedAxios.get.mockResolvedValue({
      data: { authenticated: false, user: null },
    })
  })

  const renderLayout = (route = '/board') => {
    return render(
      <MemoryRouter initialEntries={[route]}>
        <Layout />
      </MemoryRouter>
    )
  }

  describe('Navigation', () => {
    it('should render all navigation tabs', async () => {
      renderLayout()

      await waitFor(() => {
        expect(screen.getByText('Board')).toBeInTheDocument()
        expect(screen.getByText('Timeline')).toBeInTheDocument()
        expect(screen.getByText('Metrics')).toBeInTheDocument()
        expect(screen.getByText('Data Quality')).toBeInTheDocument()
        expect(screen.getByText('Poker')).toBeInTheDocument()
        expect(screen.getByText('Teams')).toBeInTheDocument()
      })
    })

    it('should render logo', async () => {
      renderLayout()

      await waitFor(() => {
        const logo = screen.getByAltText('Lead Board')
        expect(logo).toBeInTheDocument()
      })
    })

    it('should preserve teamId in navigation links', async () => {
      renderLayout('/board?teamId=5')

      await waitFor(() => {
        const boardLink = screen.getByText('Board').closest('a')
        expect(boardLink).toHaveAttribute('href', '/board?teamId=5')

        const timelineLink = screen.getByText('Timeline').closest('a')
        expect(timelineLink).toHaveAttribute('href', '/board/timeline?teamId=5')
      })
    })
  })

  describe('Authentication - Not logged in', () => {
    it('should show login button when not authenticated', async () => {
      renderLayout()

      await waitFor(() => {
        expect(screen.getByText('Login with Atlassian')).toBeInTheDocument()
      })
    })

    it('should redirect to OAuth on login click', async () => {
      // Mock window.location.href
      const originalLocation = window.location
      Object.defineProperty(window, 'location', {
        writable: true,
        value: { href: '' },
      })

      renderLayout()

      await waitFor(() => {
        expect(screen.getByText('Login with Atlassian')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Login with Atlassian'))

      expect(window.location.href).toBe('/oauth/atlassian/authorize')

      // Restore
      Object.defineProperty(window, 'location', {
        writable: true,
        value: originalLocation,
      })
    })
  })

  describe('Authentication - Logged in', () => {
    beforeEach(() => {
      mockedAxios.get.mockResolvedValue({
        data: {
          authenticated: true,
          user: {
            id: 1,
            accountId: 'acc-123',
            displayName: 'John Doe',
            email: 'john@example.com',
            avatarUrl: 'https://example.com/avatar.jpg',
          },
        },
      })
    })

    it('should show user info when authenticated', async () => {
      renderLayout()

      await waitFor(() => {
        expect(screen.getByText('John Doe')).toBeInTheDocument()
      })
    })

    it('should show user avatar when available', async () => {
      const { container } = renderLayout()

      await waitFor(() => {
        const avatar = container.querySelector('.user-avatar')
        expect(avatar).toBeInTheDocument()
        expect(avatar).toHaveAttribute('src', 'https://example.com/avatar.jpg')
      })
    })

    it('should show logout button when authenticated', async () => {
      renderLayout()

      await waitFor(() => {
        expect(screen.getByText('Logout')).toBeInTheDocument()
      })
    })

    it('should call logout API on logout click', async () => {
      mockedAxios.post.mockResolvedValueOnce({})
      renderLayout()

      await waitFor(() => {
        expect(screen.getByText('Logout')).toBeInTheDocument()
      })

      fireEvent.click(screen.getByText('Logout'))

      await waitFor(() => {
        expect(mockedAxios.post).toHaveBeenCalledWith('/oauth/atlassian/logout')
      })
    })
  })

  describe('Authentication - No avatar', () => {
    beforeEach(() => {
      mockedAxios.get.mockResolvedValue({
        data: {
          authenticated: true,
          user: {
            id: 1,
            accountId: 'acc-123',
            displayName: 'Jane Doe',
            email: 'jane@example.com',
            avatarUrl: null,
          },
        },
      })
    })

    it('should not show avatar when not available', async () => {
      renderLayout()

      await waitFor(() => {
        expect(screen.getByText('Jane Doe')).toBeInTheDocument()
      })

      const avatars = screen.queryAllByRole('img', { name: '' })
      // Only logo should be present, not user avatar
      expect(avatars.filter(img => img.classList.contains('user-avatar'))).toHaveLength(0)
    })
  })

  describe('API error handling', () => {
    it('should show login button on auth status error', async () => {
      mockedAxios.get.mockRejectedValue(new Error('Network error'))
      renderLayout()

      await waitFor(() => {
        expect(screen.getByText('Login with Atlassian')).toBeInTheDocument()
      })
    })
  })

  describe('Structure', () => {
    it('should have correct CSS classes', async () => {
      const { container } = renderLayout()

      await waitFor(() => {
        expect(container.querySelector('.app')).toBeInTheDocument()
        expect(container.querySelector('.header')).toBeInTheDocument()
        expect(container.querySelector('.header-left')).toBeInTheDocument()
        expect(container.querySelector('.header-right')).toBeInTheDocument()
        expect(container.querySelector('.nav-tabs')).toBeInTheDocument()
      })
    })
  })
})
