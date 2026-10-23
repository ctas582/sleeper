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

import type { AuthProviderNoUserManagerProps } from 'react-oidc-context'
import type { UserManagerSettings } from 'oidc-client-ts'
import { UserManager, WebStorageStateStore } from 'oidc-client-ts'
import type { Issuer } from './authInfo'

function storagePrefix(issuer: Issuer): string {
	return `oidc.${issuer.issuer}.`
}

export function userManagerSettingsFor(issuer: Issuer): UserManagerSettings {
	if (!issuer.authEndpoint || !issuer.tokenEndpoint || !issuer.clientId) {
		throw new Error(`${issuer.name} did not say how a browser signs in with it.`)
	}
	return {
		authority: issuer.issuer,
		client_id: issuer.clientId,
		redirect_uri: window.location.origin + '/',
		post_logout_redirect_uri: window.location.origin + '/',
		response_type: 'code',
		scope: 'openid profile',
		metadata: {
			issuer: issuer.issuer,
			authorization_endpoint: issuer.authEndpoint,
			token_endpoint: issuer.tokenEndpoint,
		},
		automaticSilentRenew: true,
		userStore: new WebStorageStateStore({ store: window.sessionStorage, prefix: storagePrefix(issuer) }),
		stateStore: new WebStorageStateStore({ store: window.sessionStorage, prefix: storagePrefix(issuer) }),
	}
}

export function oidcConfigFor(issuer: Issuer): AuthProviderNoUserManagerProps {
	return {
		...userManagerSettingsFor(issuer),
		// Takes the sign-in code out of the address bar, otherwise it would stay in the browser history and break token renewals
		onSigninCallback: () => {
			window.history.replaceState({}, document.title, window.location.pathname)
		},
	}
}

export async function redirectToIssuer(issuer: Issuer): Promise<void> {
	return new UserManager({
		...userManagerSettingsFor(issuer),
		automaticSilentRenew: false,
	}).signinRedirect()
}
