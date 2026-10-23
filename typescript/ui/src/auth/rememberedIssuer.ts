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

const KEY = 'sleeper.issuer'

/**
 * Remembers which issuer somebody chose to sign in with.
 *
 * Signing in leaves the page for the issuer and comes back to it, so the choice cannot be held in memory. It is
 * kept beside the tokens in session storage, so that it is discarded with them when the tab closes, and so that
 * the page returned to knows which issuer to complete the sign in with.
 */
export function rememberIssuer(name: string): void {
	window.sessionStorage.setItem(KEY, name)
}

export function rememberedIssuer(): string | undefined {
	return window.sessionStorage.getItem(KEY) ?? undefined
}

export function forgetIssuer(): void {
	window.sessionStorage.removeItem(KEY)
}
