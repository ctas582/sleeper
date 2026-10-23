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

export interface Issuer {
	name: string
	issuer: string
	tokenEndpoint?: string
	authEndpoint?: string
	clientId?: string
}

export interface AuthInfo {
	configured: boolean
	issuers: Issuer[]
	iamEnabled: boolean
	error?: {
		code: string
		message: string
		remediation?: string
	}
}

export async function fetchAuthInfo(): Promise<AuthInfo> {
	const response = await fetch('/api/v1/auth/info')
	if (!response.ok) {
		throw new Error(`The instance could not say how to authenticate with it (${response.status}).`)
	}
	return (await response.json()) as AuthInfo
}

export function canSignInFromBrowser(issuer: Issuer): boolean {
	return Boolean(issuer.authEndpoint && issuer.tokenEndpoint && issuer.clientId)
}

export function issuersToSignInWith(info: AuthInfo): Issuer[] {
	return info.issuers.filter(canSignInFromBrowser)
}
