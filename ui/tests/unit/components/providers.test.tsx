/*
 * Copyright (C) 2026 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

// @vitest-environment jsdom

import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { Providers } from '@/components/providers';
import { TOKEN_FLOW_MARKER_KEY } from '@/helpers/token-flow';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const authProvider = vi.hoisted(() => ({
  skipSigninCallback: undefined as boolean | undefined,
}));

vi.mock('react-oidc-context', () => ({
  AuthProvider: ({
    skipSigninCallback,
    children,
  }: {
    skipSigninCallback?: boolean;
    children: ReactNode;
  }) => {
    authProvider.skipSigninCallback = skipSigninCallback;

    return children;
  },
  useAuth: () => ({ user: null }),
}));

const renderAt = (path: string, hasTokenFlowMarker: boolean) => {
  window.history.pushState({}, '', path);
  if (hasTokenFlowMarker) {
    window.sessionStorage.setItem(TOKEN_FLOW_MARKER_KEY, '1');
  }

  render(
    <Providers>
      <span>Page</span>
    </Providers>
  );
};

describe('Providers', () => {
  afterEach(() => {
    authProvider.skipSigninCallback = undefined;
    window.sessionStorage.clear();
    window.history.pushState({}, '', '/');
  });

  it('is compiled by React Compiler', () => {
    expect(isCompiledByReactCompiler(Providers)).toBe(true);
  });

  it.each([
    { path: '/profile/token/callback', marker: true, skips: true },
    { path: '/profile/token/callback/', marker: true, skips: true },
    { path: '/profile/token/callback', marker: false, skips: false },
    { path: '/profile', marker: true, skips: false },
  ])(
    'skips the sign-in callback at $path with marker $marker: $skips',
    ({ path, marker, skips }) => {
      renderAt(path, marker);

      expect(screen.getByText('Page')).toBeInTheDocument();
      expect(authProvider.skipSigninCallback).toBe(skips);
    }
  );
});
