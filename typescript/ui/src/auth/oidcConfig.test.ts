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

import { afterEach, describe, expect, it, vi } from 'vitest'
import type { UserManagerSettings } from 'oidc-client-ts'
import { UserManager } from 'oidc-client-ts'
import { oidcConfigFor, redirectToIssuer } from './oidcConfig'
import { issuerForBrowserSignIn } from '../test/authInfo'

afterEach(() => {
	vi.restoreAllMocks()
})

const COGNITO = issuerForBrowserSignIn('cognito')
const CORP = issuerForBrowserSignIn('corp')

describe('storing sign-in data separately for each issuer', () => {
	it('stores each issuer\'s token under a separate key', async () => {
		// The library names every issuer's keys the same by default, so the last to be used would otherwise
		// overwrite the token of the one before it.
		const cognito = oidcConfigFor(COGNITO)
		const corp = oidcConfigFor(CORP)

		await cognito.userStore?.set('user', 'cognito-token')
		await corp.userStore?.set('user', 'corp-token')

		expect(await cognito.userStore?.get('user')).toEqual('cognito-token')
		expect(await corp.userStore?.get('user')).toEqual('corp-token')
	})

	it('stores each issuer\'s sign-in state under a separate key', async () => {
		// Completing a sign-in matches what comes back against what was stored when it started. Shared keys
		// would have a request to one issuer answered by the state of another.
		const cognito = oidcConfigFor(COGNITO)
		const corp = oidcConfigFor(CORP)

		await cognito.stateStore?.set('state', 'cognito-state')
		await corp.stateStore?.set('state', 'corp-state')

		expect(await cognito.stateStore?.get('state')).toEqual('cognito-state')
		expect(await corp.stateStore?.get('state')).toEqual('corp-state')
	})
})

describe('building the OIDC config for an issuer', () => {
	it('uses the authorization code flow', () => {
		expect(oidcConfigFor(COGNITO).response_type).toEqual('code')
	})

	it('uses the issuer\'s client ID', () => {
		expect(oidcConfigFor(COGNITO).client_id).toEqual(COGNITO.clientId)
	})

	it('sets the redirect URI to the root of the UI', () => {
		expect(oidcConfigFor(COGNITO).redirect_uri).toEqual(`${window.location.origin}/`)
	})

	it('uses the issuer\'s endpoints instead of OIDC discovery', () => {
		const metadata = oidcConfigFor(COGNITO).metadata

		expect(metadata?.authorization_endpoint).toEqual(COGNITO.authEndpoint)
		expect(metadata?.token_endpoint).toEqual(COGNITO.tokenEndpoint)
		expect(metadata?.issuer).toEqual(COGNITO.issuer)
	})

	it('sets its own token and sign-in state stores', () => {
		// Session storage is discarded with the tab, where local storage would leave the token on the machine.
		const config = oidcConfigFor(COGNITO)

		expect(config.userStore).toBeDefined()
		expect(config.stateStore).toBeDefined()
	})

	it('enables automatic token renewal', () => {
		expect(oidcConfigFor(COGNITO).automaticSilentRenew).toBe(true)
	})

	it('sets a callback to run after sign in', () => {
		expect(oidcConfigFor(COGNITO).onSigninCallback).toBeDefined()
	})

	it('throws for an issuer with no endpoints or client ID', () => {
		expect(() => oidcConfigFor({ name: 'corp', issuer: 'https://idp.internal' }))
			.toThrow('did not say how a browser signs in')
	})
})

describe('redirecting to an issuer to sign in', () => {
	it('starts a sign-in redirect', async () => {
		const signinRedirect = vi.spyOn(UserManager.prototype, 'signinRedirect').mockResolvedValue()

		await redirectToIssuer(CORP)

		expect(signinRedirect).toHaveBeenCalledOnce()
	})

	it('stores sign-in state under the same keys as the OIDC config', async () => {
		// The browser leaves and the library here is lost with the page. The one that comes back reads what this
		// one stored, so both must use the same keys or the sign in cannot be completed.
		let settings: UserManagerSettings | undefined
		vi.spyOn(UserManager.prototype, 'signinRedirect').mockImplementation(async function (this: UserManager) {
			settings = this.settings
		})

		await redirectToIssuer(COGNITO)

		expect(settings?.userStore).toBeDefined()
		await settings?.stateStore?.set('state', 'started-here')
		expect(await oidcConfigFor(COGNITO).stateStore?.get('state')).toEqual('started-here')
	})

	it('disables automatic token renewal for the redirect', async () => {
		let settings: UserManagerSettings | undefined
		vi.spyOn(UserManager.prototype, 'signinRedirect').mockImplementation(async function (this: UserManager) {
			settings = this.settings
		})

		await redirectToIssuer(COGNITO)

		expect(settings?.automaticSilentRenew).toBe(false)
		expect(oidcConfigFor(COGNITO).automaticSilentRenew).toBe(true)
	})

	it('rejects an issuer with no endpoints or client ID', async () => {
		await expect(redirectToIssuer({ name: 'corp', issuer: 'https://idp.internal' }))
			.rejects.toThrow('did not say how a browser signs in')
	})
})
