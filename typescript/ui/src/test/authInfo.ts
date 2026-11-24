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

import type { AuthInfo, Issuer } from '../auth/authInfo'

/** An issuer with everything a browser needs to sign in with it. */
export function issuerForBrowserSignIn(name: string): Issuer {
	return {
		name,
		issuer: `https://idp.internal/realms/${name}`,
		authEndpoint: 'https://idp.internal/authorize',
		tokenEndpoint: 'https://idp.internal/token',
		clientId: `${name}-client`,
	}
}

/** An instance that lets people sign in with any of the given issuers. */
export function instanceTrusting(issuers: Issuer[]): AuthInfo {
	return { configured: true, issuers, iamEnabled: false }
}

/** An instance with no issuers, that can only be reached with AWS IAM credentials. */
export function instanceUsingOnlyIam(): AuthInfo {
	return { configured: true, issuers: [], iamEnabled: true }
}
