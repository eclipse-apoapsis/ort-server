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

import { screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { Route } from '@/routes/admin/index';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { getLinkTarget, pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getOrganizations: vi.fn(),
  getRuns: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

const renderOverview = () => {
  const OverviewComponent = Route.options.component!;

  return renderInteractiveWithRouter(<OverviewComponent />, {
    path: '/admin',
    routes: [{ path: '/admin/' }],
    withQueryClient: true,
  });
};

const getCountLink = (title: string) => screen.getByText(title).closest('a')!;

describe('admin overview', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.getOrganizations.mockResolvedValue(pageOf([], { totalCount: 3 }));
  });

  it('shows each count as soon as its own query has loaded', async () => {
    // The runs never load.
    mocks.getRuns.mockReturnValue(new Promise(() => {}));

    renderOverview();

    expect(await screen.findByText('3')).toBeVisible();
    expect(getLinkTarget(getCountLink('Organizations')).pathname).toBe(
      '/organizations'
    );
    expect(screen.getByText('Loading runs...')).toBeInTheDocument();
    expect(screen.getByText('Loading active runs...')).toBeInTheDocument();
  });

  it('shows a retry button for a failed count and the other counts with their links', async () => {
    mocks.getRuns.mockImplementation(({ query }) =>
      query.status
        ? Promise.reject(new Error('Server error'))
        : Promise.resolve(pageOf([], { totalCount: 12 }))
    );

    renderOverview();

    expect(await screen.findByRole('button', { name: /retry/i })).toBeVisible();
    expect(screen.getByText('Active runs').closest('a')).toBeNull();
    expect(screen.getByText('3')).toBeVisible();
    expect(screen.getByText('12')).toBeVisible();
    expect(getLinkTarget(getCountLink('Runs'))).toEqual({
      pathname: '/admin/runs',
      search: {},
    });
  });
});
