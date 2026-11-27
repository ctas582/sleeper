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

import type { ApiError } from '../api/apiError'
import { errorFromResponse } from '../api/apiError'

export interface Issuer {
	name: string
	issuer: string
	tokenEndpoint?: string
	authEndpoint?: string
	logoutEndpoint?: string
	clientId?: string
}

export interface AuthInfo {
	configured: boolean
	issuers: Issuer[]
	iamEnabled: boolean
	error?: ApiError
}

export async function fetchAuthInfo(): Promise<AuthInfo> {
	const response = await fetch('/api/v1/auth/info')
	if (!response.ok) {
		throw await errorFromResponse(response, 'The instance could not say how to authenticate with it')
	}
	return (await response.json()) as AuthInfo
}

/** Lists the settings an issuer is missing before a browser can sign in with it, by their names in the configuration. */
export function missingForBrowserSignIn(issuer: Issuer): string[] {
	const required: [string, string | undefined][] = [
		['authEndpoint', issuer.authEndpoint],
		['tokenEndpoint', issuer.tokenEndpoint],
		['clientId', issuer.clientId],
	]
	return required.filter(([, value]) => !value).map(([name]) => name)
}

export function canSignInFromBrowser(issuer: Issuer): boolean {
	return missingForBrowserSignIn(issuer).length === 0
}

export function issuersToSignInWith(info: AuthInfo): Issuer[] {
	return info.issuers.filter(canSignInFromBrowser)
}
