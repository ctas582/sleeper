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

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { SelectIssuer } from './SelectIssuer'
import { issuerForBrowserSignIn } from '../test/authInfo'

afterEach(cleanup)

describe('the sign in page', () => {
	it('offers the only issuer when the instance trusts one', () => {
		render(<SelectIssuer issuers={[issuerForBrowserSignIn('cognito')]} onSelect={vi.fn()} />)

		expect(screen.getByRole('button', { name: 'Sign in with cognito' })).toBeDefined()
	})

	it('offers every issuer when the instance trusts several', () => {
		render(<SelectIssuer issuers={[issuerForBrowserSignIn('corp'), issuerForBrowserSignIn('cognito')]} onSelect={vi.fn()} />)

		expect(screen.getAllByRole('button').map(button => button.textContent))
			.toEqual(['Sign in with corp', 'Sign in with cognito'])
	})

	it('signs in with the issuer that was chosen, not the first offered', () => {
		const onSelect = vi.fn()
		const cognito = issuerForBrowserSignIn('cognito')
		render(<SelectIssuer issuers={[issuerForBrowserSignIn('corp'), cognito]} onSelect={onSelect} />)

		fireEvent.click(screen.getByRole('button', { name: 'Sign in with cognito' }))

		expect(onSelect).toHaveBeenCalledWith(cognito)
	})

	it('offers each issuer as a button, so a keyboard reaches them', () => {
		render(<SelectIssuer issuers={[issuerForBrowserSignIn('corp'), issuerForBrowserSignIn('cognito')]} onSelect={vi.fn()} />)

		for (const button of screen.getAllByRole('button')) {
			expect(button.tagName).toEqual('BUTTON')
		}
	})

	it('signs in with one issuer only, however many are offered', () => {
		const onSelect = vi.fn()
		const corp = issuerForBrowserSignIn('corp')
		render(<SelectIssuer issuers={[corp, issuerForBrowserSignIn('cognito')]} onSelect={onSelect} />)

		fireEvent.click(screen.getByRole('button', { name: 'Sign in with corp' }))

		expect(onSelect).toHaveBeenCalledTimes(1)
		expect(onSelect).toHaveBeenCalledWith(corp)
	})

	it('offers nothing when no issuer can be used, rather than an empty button', () => {
		render(<SelectIssuer issuers={[]} onSelect={vi.fn()} />)

		expect(screen.queryAllByRole('button')).toEqual([])
	})
})
