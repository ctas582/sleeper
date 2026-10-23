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

import type { Issuer } from '../auth/authInfo'
import './SelectIssuer.css'

interface Props {
	issuers: Issuer[]
	onSelect: (issuer: Issuer) => void
}

export function SelectIssuer({ issuers, onSelect }: Props) {
	return (
		<div className="select-issuer">
			<h1>Sleeper</h1>

			{issuers.map(issuer => (
				<button
					key={issuer.name}
					className="select-issuer__button"
					onClick={() => onSelect(issuer)}
				>
					Sign in with {issuer.name}
				</button>
			))}
		</div>
	)
}
