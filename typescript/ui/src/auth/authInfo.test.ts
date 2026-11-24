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

import { describe, expect, it } from 'vitest'
import type { Issuer } from './authInfo'
import { issuersToSignInWith } from './authInfo'
import { oidcConfigFor } from './oidcConfig'
import { instanceTrusting, instanceUsingOnlyIam, issuerForBrowserSignIn } from '../test/authInfo'

function names(issuers: Issuer[]): string[] {
	return issuers.map(issuer => issuer.name)
}

describe('choosing which issuers to offer for sign in', () => {
	it('includes an issuer that has everything a browser needs to sign in', () => {
		expect(names(issuersToSignInWith(instanceTrusting([issuerForBrowserSignIn('cognito')])))).toEqual(['cognito'])
	})

	it('excludes an issuer with no endpoints or client ID', () => {
		expect(issuersToSignInWith(instanceTrusting([{
			name: 'corp',
			issuer: 'https://idp.internal/realms/sleeper',
		}]))).toEqual([])
	})

	it('excludes an issuer with no client ID', () => {
		expect(issuersToSignInWith(instanceTrusting([{
			...issuerForBrowserSignIn('corp'),
			clientId: undefined,
		}]))).toEqual([])
	})

	it('excludes an issuer with no token endpoint', () => {
		// A user would be sent to sign in, come back with a code, and the sign in could not be completed.
		expect(issuersToSignInWith(instanceTrusting([{
			...issuerForBrowserSignIn('corp'),
			tokenEndpoint: undefined,
		}]))).toEqual([])
	})

	it('includes every issuer that supports browser sign in, not just the first', () => {
		expect(names(issuersToSignInWith(instanceTrusting([issuerForBrowserSignIn('corp'), issuerForBrowserSignIn('cognito')]))))
			.toEqual(['corp', 'cognito'])
	})

	it('excludes an unsupported issuer listed before a supported one', () => {
		expect(names(issuersToSignInWith(instanceTrusting([
			{ name: 'machines-only', issuer: 'https://idp.internal/realms/machines' },
			issuerForBrowserSignIn('people'),
		])))).toEqual(['people'])
	})

	it('returns nothing when the instance has no issuers', () => {
		expect(issuersToSignInWith(instanceUsingOnlyIam())).toEqual([])
	})

	it('only includes issuers that can be turned into an OIDC config', () => {
		const partial = { ...issuerForBrowserSignIn('partial'), tokenEndpoint: undefined }
		const found = issuersToSignInWith(instanceTrusting([partial, issuerForBrowserSignIn('complete')]))

		expect(found).not.toEqual([])
		for (const issuer of found) {
			expect(() => oidcConfigFor(issuer)).not.toThrow()
		}
	})
})
