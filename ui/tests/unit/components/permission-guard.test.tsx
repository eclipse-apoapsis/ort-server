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

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { UserInfo } from '@/api';
import { getUserInfo } from '@/api/sdk.gen';
import { PermissionGuard } from '@/components/authorization/permission-guard';
import {
  RequireOrganizationPermission,
  RequireProductPermission,
  RequireRepositoryPermission,
} from '@/components/authorization/require-permission';
import { RequireSuperuser } from '@/components/authorization/require-superuser';
import {
  useIsSuperuser,
  useOrganizationPermission,
  useProductPermission,
  useRepositoryPermission,
} from '@/hooks/use-authorization';
import { toastError } from '@/lib/toast';
import {
  compiledFunctionNames,
  isCompiledByReactCompiler,
} from '../fixtures/react-compiler';

const navigate = vi.hoisted(() => vi.fn());

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => navigate }));

vi.mock('@/lib/toast', () => ({ toastError: vi.fn() }));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getUserInfo: vi.fn(),
}));

afterEach(() => {
  vi.clearAllMocks();
});

describe('authorization', () => {
  it.each([
    ['PermissionGuard', PermissionGuard],
    ['RequireOrganizationPermission', RequireOrganizationPermission],
    ['RequireProductPermission', RequireProductPermission],
    ['RequireRepositoryPermission', RequireRepositoryPermission],
    ['RequireSuperuser', RequireSuperuser],
    ['useIsSuperuser', useIsSuperuser],
    ['useOrganizationPermission', useOrganizationPermission],
    ['useProductPermission', useProductPermission],
    ['useRepositoryPermission', useRepositoryPermission],
  ])('compiles %s with React Compiler', (_, component) => {
    expect(isCompiledByReactCompiler(component)).toBe(true);
  });

  it('compiles useEntityPermission, which is not exported', () => {
    expect(compiledFunctionNames('src/hooks/use-authorization.ts')).toContain(
      'useEntityPermission'
    );
  });
});

describe('PermissionGuard', () => {
  const guard = (props: {
    isAllowed?: boolean;
    isLoading?: boolean;
    error?: unknown;
  }) => (
    <PermissionGuard
      isAllowed={props.isAllowed}
      isLoading={props.isLoading ?? false}
      error={props.error}
    >
      Protected
    </PermissionGuard>
  );

  it('shows a loading indicator while loading', () => {
    render(guard({ isLoading: true }));

    expect(screen.getByText('Loading data...')).toBeInTheDocument();
    expect(screen.queryByText('Protected')).toBeNull();
  });

  it('redirects once when the permission is denied after loading', () => {
    const { rerender } = render(guard({ isLoading: true }));

    rerender(guard({ isAllowed: false }));
    rerender(guard({ isAllowed: false }));

    expect(screen.queryByText('Protected')).toBeNull();
    expect(navigate).toHaveBeenCalledExactlyOnceWith({
      replace: true,
      to: '/403',
    });
  });

  it('reports an error that arrives later once', () => {
    const error = new Error('Forbidden');
    const { rerender } = render(guard({ isAllowed: true }));

    expect(screen.getByText('Protected')).toBeInTheDocument();

    rerender(guard({ isAllowed: true, error }));
    rerender(guard({ isAllowed: true, error }));

    expect(toastError).toHaveBeenCalledExactlyOnceWith(
      'Unable to load permissions',
      error
    );
    expect(navigate).not.toHaveBeenCalled();
  });
});

describe('RequireOrganizationPermission', () => {
  it('follows the permission of a changed organization', async () => {
    vi.mocked(getUserInfo).mockImplementation((async (options: {
      query: { organizationId: number };
    }) => ({
      data: {
        isSuperuser: false,
        organizationPermissions:
          options.query.organizationId === 1 ? ['READ'] : [],
      } as Partial<UserInfo>,
    })) as unknown as typeof getUserInfo);
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const tree = (organizationId: number): ReactNode => (
      <QueryClientProvider client={queryClient}>
        <RequireOrganizationPermission
          organizationId={organizationId}
          permission='READ'
        >
          Protected
        </RequireOrganizationPermission>
      </QueryClientProvider>
    );
    const { rerender } = render(tree(1));

    expect(await screen.findByText('Protected')).toBeInTheDocument();

    rerender(tree(2));

    await vi.waitFor(() =>
      expect(navigate).toHaveBeenCalledWith({ replace: true, to: '/403' })
    );
    expect(screen.queryByText('Protected')).toBeNull();
    expect(getUserInfo).toHaveBeenLastCalledWith(
      expect.objectContaining({ query: { organizationId: 2 } })
    );
  });
});
