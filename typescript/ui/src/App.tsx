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

import { useEffect, useState } from 'react'
import { AuthProvider } from 'react-oidc-context'
import type { AuthInfo, Issuer } from './auth/authInfo'
import { fetchAuthInfo, issuersToSignInWith } from './auth/authInfo'
import { oidcConfigFor, redirectToIssuer } from './auth/oidcConfig'
import { forgetIssuer, rememberIssuer, rememberedIssuer } from './auth/rememberedIssuer'
import { NotConfigured } from './components/NotConfigured'
import { NoBrowserSignIn } from './components/NoBrowserSignIn'
import { SelectIssuer } from './components/SelectIssuer'
import { SignedInApp } from './components/SignedInApp'
import './App.css'

type Loading =
	| { state: 'loading' }
	| { state: 'loaded'; info: AuthInfo }
	| { state: 'failed'; message: string }

export function App() {
	const [loading, setLoading] = useState<Loading>({ state: 'loading' })
	const [selectedIssuer, setSelectedIssuer] = useState<string | undefined>(rememberedIssuer)
	const [leavingFor, setLeavingFor] = useState<string | undefined>(undefined)
	const [failedToLeave, setFailedToLeave] = useState<string | undefined>(undefined)

	useEffect(() => {
		fetchAuthInfo()
			.then(info => setLoading({ state: 'loaded', info }))
			.catch((error: unknown) => setLoading({
				state: 'failed',
				message: error instanceof Error ? error.message : String(error),
			}))
	}, [])

	if (loading.state === 'loading') {
		return <main className="app"><p>Loading.</p></main>
	}
	if (loading.state === 'failed') {
		return <main className="app"><p className="error">{loading.message}</p></main>
	}
	if (!loading.info.configured) {
		return <main className="app"><NotConfigured error={loading.info.error} /></main>
	}

	const issuers = issuersToSignInWith(loading.info)
	if (issuers.length === 0) {
		return <main className="app"><NoBrowserSignIn info={loading.info} /></main>
	}

	const onIssuerSelected = (issuer: Issuer) => {
		rememberIssuer(issuer.name)
		setLeavingFor(issuer.name)
		setFailedToLeave(undefined)
		redirectToIssuer(issuer).catch((error: unknown) => {
			forgetIssuer()
			setLeavingFor(undefined)
			setFailedToLeave(error instanceof Error ? error.message : String(error))
		})
	}

	if (leavingFor) {
		return <main className="app"><p>Taking you to {leavingFor} to sign in.</p></main>
	}

	const chosen = issuers.find(issuer => issuer.name === selectedIssuer)
	if (!chosen) {
		return (
			<main className="app">
				{failedToLeave && <p className="error">Could not send you to sign in: {failedToLeave}</p>}
				<SelectIssuer issuers={issuers} onSelect={onIssuerSelected} />
			</main>
		)
	}

	return (
		<AuthProvider key={chosen.issuer} {...oidcConfigFor(chosen)}>
			<main className="app">
				<SignedInApp
					issuerName={chosen.name}
					onChangeIssuer={() => {
						forgetIssuer()
						setSelectedIssuer(undefined)
					}}
				/>
			</main>
		</AuthProvider>
	)
}
