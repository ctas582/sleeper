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

export interface GrantedPermission {
	permission: string
	tables?: string[]
}

export interface ClientIdentity {
	principal: string
	authenticatedBy: string
	admin: boolean
	permissions: GrantedPermission[]
}

export async function fetchIdentity(idToken: string): Promise<ClientIdentity> {
	const response = await fetch('/api/v1/auth/me', {
		headers: { Authorization: `Bearer ${idToken}` },
	})
	if (response.status === 401) {
		throw new Error('The instance did not accept this token. It may have expired, so try signing in again.')
	}
	if (!response.ok) {
		throw new Error(`The instance would not say who this token belongs to (${response.status}).`)
	}
	return (await response.json()) as ClientIdentity
}
