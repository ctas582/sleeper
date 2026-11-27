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

import type { AuthInfo } from '../auth/authInfo'
import { missingForBrowserSignIn } from '../auth/authInfo'
import './SelectIssuer.css'

interface Props {
	info: AuthInfo
}

export function NoBrowserSignIn({ info }: Props) {
	return (
		<div className="select-issuer">
			<h1>Sleeper</h1>
			<p>This instance has no issuer a browser can sign in with.</p>
			{info.issuers.length > 0 && (
				<>
					<p className="select-issuer__detail">
						To sign in from a browser, an issuer must set authEndpoint, tokenEndpoint and clientId in the
						instance property sleeper.rest.api.auth.oidc.issuers.
					</p>
					<ul className="select-issuer__detail">
						{info.issuers.map(issuer => (
							<li key={issuer.name}>
								{issuer.name} is missing {missingForBrowserSignIn(issuer).join(', ')}
							</li>
						))}
					</ul>
				</>
			)}
			{info.iamEnabled && (
				<p className="select-issuer__detail">
					It accepts AWS credentials, which a browser cannot present. Use the command line.
				</p>
			)}
		</div>
	)
}
