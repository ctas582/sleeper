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
import './NotConfigured.css'

interface Props {
	error: AuthInfo['error']
}

export function NotConfigured({ error }: Props) {
	return (
		<div className="not-configured">
			<h1>Sleeper</h1>
			<p>{error?.message ?? 'This instance has no way for anybody to sign in.'}</p>
			{error?.remediation && <p className="not-configured__remediation">{error.remediation}</p>}
		</div>
	)
}
