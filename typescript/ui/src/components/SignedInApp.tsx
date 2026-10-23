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

import { useAuth } from 'react-oidc-context'
import { WhoAmI } from './WhoAmI'

interface Props {
	issuerName: string
	onChangeIssuer: () => void
}

export function SignedInApp({ issuerName, onChangeIssuer }: Props) {
	const auth = useAuth()

	if (auth.error) {
		return (
			<>
				<h1>Sleeper</h1>
				<p className="error">Signing in with {issuerName} did not work: {auth.error.message}</p>
				<button onClick={() => void auth.signinRedirect()}>Try again</button>
				<button onClick={onChangeIssuer}>Sign in with a different authentication provider</button>
			</>
		)
	}
	if (auth.isLoading) {
		return <p>Signing in with {issuerName}.</p>
	}
	if (!auth.isAuthenticated || !auth.user?.id_token) {
		return (
			<>
				<h1>Sleeper</h1>
				<p>Signing in with {issuerName} did not finish.</p>
				<button onClick={() => void auth.signinRedirect()}>Try again</button>
				<button onClick={onChangeIssuer}>Sign in with a different authentication provider</button>
			</>
		)
	}
	return (
		<WhoAmI
			idToken={auth.user.id_token}
			onSignOut={() => {
				onChangeIssuer()
				void auth.removeUser()
			}}
		/>
	)
}
