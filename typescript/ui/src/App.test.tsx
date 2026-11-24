/*
 * Copyright 2026 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { UserManager } from 'oidc-client-ts'
import type { AuthInfo } from './auth/authInfo'
import { App } from './App'
import { instanceTrusting, instanceUsingOnlyIam, issuerForBrowserSignIn } from './test/authInfo'

function serverReturnsAuthInfo(info: AuthInfo): void {
	vi.spyOn(globalThis, 'fetch').mockResolvedValue(
		new Response(JSON.stringify(info), { status: 200 }))
}

/** Stands in for the browser leaving the page, which jsdom will not do. */
function issuerAcceptsRedirect() {
	return vi.spyOn(UserManager.prototype, 'signinRedirect').mockResolvedValue()
}

beforeEach(() => {
	window.sessionStorage.clear()
})

afterEach(() => {
	cleanup()
	vi.restoreAllMocks()
	window.sessionStorage.clear()
})

describe('signing in with a chosen issuer', () => {
	it('redirects to the issuer when the user chooses it', async () => {
		serverReturnsAuthInfo(instanceTrusting([issuerForBrowserSignIn('corp')]))
		const signinRedirect = issuerAcceptsRedirect()
		render(<App />)

		fireEvent.click(await screen.findByRole('button', { name: 'Sign in with corp' }))

		await waitFor(() => {
			expect(signinRedirect).toHaveBeenCalledOnce()
		})
	})

	it('redirects to the chosen issuer, not the first one listed', async () => {
		serverReturnsAuthInfo(instanceTrusting([issuerForBrowserSignIn('corp'), issuerForBrowserSignIn('cognito')]))
		let authority: string | undefined
		vi.spyOn(UserManager.prototype, 'signinRedirect').mockImplementation(async function (this: UserManager) {
			authority = this.settings.authority
		})
		render(<App />)

		fireEvent.click(await screen.findByRole('button', { name: 'Sign in with cognito' }))

		await waitFor(() => {
			expect(authority).toEqual('https://idp.internal/realms/cognito')
		})
	})

	it('shows a redirecting message, not a failure, while the redirect is in progress', async () => {
		// The page stays up while the library works out where to send them. Treating that moment as a sign in
		// that has already come back would tell somebody it had failed while it was still going.
		serverReturnsAuthInfo(instanceTrusting([issuerForBrowserSignIn('corp')]))
		vi.spyOn(UserManager.prototype, 'signinRedirect').mockReturnValue(new Promise(() => { /* never leaves */ }))
		render(<App />)

		fireEvent.click(await screen.findByRole('button', { name: 'Sign in with corp' }))

		expect(await screen.findByText('Taking you to corp to sign in.')).toBeDefined()
		expect(screen.queryByText(/did not finish/)).toBeNull()
	})

	it('does not redirect again when the user returns from the issuer', async () => {
		// The page is loaded again when somebody comes back from the issuer. Sending them out once more would
		// bounce them between the two without ever drawing a page they could act on.
		window.sessionStorage.setItem('sleeper.issuer', 'corp')
		serverReturnsAuthInfo(instanceTrusting([issuerForBrowserSignIn('corp')]))
		const signinRedirect = issuerAcceptsRedirect()

		render(<App />)

		expect(await screen.findByText(/corp/)).toBeDefined()
		expect(signinRedirect).not.toHaveBeenCalled()
	})

	it('offers another issuer when the user returns without having signed in', async () => {
		// Coming back with no tokens and nothing saying why would otherwise leave somebody on a page that waits
		// for a sign in that is not coming.
		window.sessionStorage.setItem('sleeper.issuer', 'corp')
		serverReturnsAuthInfo(instanceTrusting([issuerForBrowserSignIn('corp')]))
		issuerAcceptsRedirect()

		render(<App />)

		expect(await screen.findByRole('button', { name: 'Sign in with a different authentication provider' })).toBeDefined()
	})

	it('shows the error and the issuer choices again when the redirect fails', async () => {
		serverReturnsAuthInfo(instanceTrusting([issuerForBrowserSignIn('corp')]))
		vi.spyOn(UserManager.prototype, 'signinRedirect')
			.mockRejectedValue(new Error('the issuer could not be reached'))
		render(<App />)

		fireEvent.click(await screen.findByRole('button', { name: 'Sign in with corp' }))

		expect(await screen.findByText(/the issuer could not be reached/)).toBeDefined()
		expect(screen.getByRole('button', { name: 'Sign in with corp' })).toBeDefined()
	})

	it('forgets the chosen issuer when the redirect fails', async () => {
		// Remembered, the next load of the page would try to complete a sign in that was never started.
		serverReturnsAuthInfo(instanceTrusting([issuerForBrowserSignIn('corp')]))
		vi.spyOn(UserManager.prototype, 'signinRedirect').mockRejectedValue(new Error('unknown error'))
		render(<App />)

		fireEvent.click(await screen.findByRole('button', { name: 'Sign in with corp' }))

		await waitFor(() => {
			expect(window.sessionStorage.getItem('sleeper.issuer')).toBeNull()
		})
	})
})

describe('when signing in is not possible', () => {
	it('explains that no issuer supports signing in from a browser', async () => {
		serverReturnsAuthInfo(instanceUsingOnlyIam())

		render(<App />)

		expect(await screen.findByText(/no issuer a browser can sign in with/)).toBeDefined()
	})

	it('shows an error when the server fails to return auth info', async () => {
		vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('', { status: 503 }))

		render(<App />)

		expect(await screen.findByText(/could not say how to authenticate/)).toBeDefined()
	})
})
