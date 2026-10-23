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
import './SelectIssuer.css'

interface Props {
	info: AuthInfo
}

export function NoBrowserSignIn({ info }: Props) {
	return (
		<div className="select-issuer">
			<h1>Sleeper</h1>
			<p>This instance has no issuer a browser can sign in with.</p>
			<p className="select-issuer__detail">
				{info.iamEnabled
					? 'It accepts AWS credentials, which a browser cannot present. Use the command line.'
					: 'Its issuers did not say where to send somebody to sign in.'}
			</p>
		</div>
	)
}
