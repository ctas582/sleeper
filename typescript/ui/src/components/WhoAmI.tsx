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
import type { ClientIdentity } from '../api/identity'
import { fetchIdentity } from '../api/identity'
import './WhoAmI.css'

interface Props {
	idToken: string
	onSignOut: () => void
}

type Loading =
	| { state: 'loading' }
	| { state: 'loaded'; identity: ClientIdentity }
	| { state: 'failed'; message: string }

export function WhoAmI({ idToken, onSignOut }: Props) {
	const [loading, setLoading] = useState<Loading>({ state: 'loading' });

	useEffect(() => {
		fetchIdentity(idToken)
			.then((identity) => setLoading({ state: 'loaded', identity }))
			.catch((error: unknown) => setLoading({
				state: 'failed',
				message: error instanceof Error ? error.message : String(error),
			}))
	}, [idToken])

	if (loading.state === 'loading') {
		return <p>Asking the instance who you are.</p>
	}
	if (loading.state === 'failed') {
		return (
			<>
				<p className="error">{loading.message}</p>
				<button onClick={onSignOut}>Sign out</button>
			</>
		)
	}

	const identity = loading.identity
	return (
		<div className="who-am-i">
			<h1>Signed in</h1>
			<dl className="who-am-i__facts">
				<dt>You are</dt>
				<dd>{identity.principal}</dd>
				<dt>Signed in with</dt>
				<dd>{identity.authenticatedBy}</dd>
				<dt>Administrator</dt>
				<dd>{identity.admin ? 'Yes' : 'No'}</dd>
			</dl>
			<h2>What you may do</h2>
			{identity.permissions.length > 0 ? (
				<ul className="who-am-i__permissions">
					{identity.permissions.map((granted) => (
						<li key={granted.permission}>
							{granted.permission}
							{granted.tables?.length ? ` on ${granted.tables.join(', ')}` : ' on every table'}
						</li>
					))}
				</ul>
			) : (
				<p className="who-am-i__nothing">
					Nothing yet. Signing in establishes who you are. Access is granted separately, by adding you to
					the instance's list of principals.
				</p>
			)}
			<button onClick={onSignOut}>Sign out</button>
		</div>
	)
}
