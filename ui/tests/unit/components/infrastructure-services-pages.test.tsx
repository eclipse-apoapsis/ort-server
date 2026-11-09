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

import { screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { InfrastructureService } from '@/api';
import { Route as OrganizationRoute } from '@/routes/organizations/$orgId/infrastructure-services';
import { Route as ProductRoute } from '@/routes/organizations/$orgId/products/$productId/infrastructure-services';
import { Route as RepositoryRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/infrastructure-services';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { hierarchyLevels, pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getOrganization: vi.fn(),
  getProduct: vi.fn(),
  getRepository: vi.fn(),
  getOrganizationInfrastructureServices: vi.fn(),
  getProductInfrastructureServices: vi.fn(),
  getRepositoryInfrastructureServices: vi.fn(),
  deleteOrganizationInfrastructureService: vi.fn(),
  deleteProductInfrastructureService: vi.fn(),
  deleteRepositoryInfrastructureService: vi.fn(),
}));

const permissions = vi.hoisted(() => ({
  useOrganizationPermission: vi.fn(),
  useProductPermission: vi.fn(),
  useRepositoryPermission: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

vi.mock('@/hooks/use-authorization', () => permissions);

// Answers the permission hooks of all levels with the given permissions.
const grant = (granted: string[]) => {
  for (const hook of Object.values(permissions)) {
    hook.mockImplementation((_id: number, permission: string) => ({
      isAllowed: granted.includes(permission),
      isPending: false,
      error: null,
    }));
  }
};

const [organization, product, repository] = hierarchyLevels(
  'infrastructure-services'
);
const levels = [
  {
    ...organization,
    route: OrganizationRoute,
    services: mocks.getOrganizationInfrastructureServices,
    remove: mocks.deleteOrganizationInfrastructureService,
  },
  {
    ...product,
    route: ProductRoute,
    services: mocks.getProductInfrastructureServices,
    remove: mocks.deleteProductInfrastructureService,
  },
  {
    ...repository,
    route: RepositoryRoute,
    services: mocks.getRepositoryInfrastructureServices,
    remove: mocks.deleteRepositoryInfrastructureService,
  },
];

const services: InfrastructureService[] = [
  {
    name: 'repo',
    description: 'The artifact repository',
    url: 'https://repo.example.com',
    usernameSecretRef: 'repo-user',
    passwordSecretRef: 'repo-password',
    credentialsTypes: ['NETRC_FILE', 'GIT_CREDENTIALS_FILE'],
  },
  {
    name: 'git',
    url: 'https://git.example.com',
    usernameSecretRef: 'git-user',
    passwordSecretRef: 'git-password',
  },
];

const renderServices = (level: (typeof levels)[number], search = '') => {
  const ServicesComponent = level.route.options.component!;

  return renderInteractiveWithRouter(<ServicesComponent />, {
    path: `${level.path}${search}`,
    routes: level.routes,
    withQueryClient: true,
  });
};

const getRow = (name: string) => screen.getByText(name).closest('tr')!;

// The router link renders `aria-disabled` and no target only when it is disabled.
const expectLinkEnabled = (link: HTMLElement, enabled: boolean) => {
  if (enabled) {
    expect(link).not.toHaveAttribute('aria-disabled');
    expect(link).toHaveAttribute('href');
  } else {
    expect(link).toHaveAttribute('aria-disabled', 'true');
    expect(link).not.toHaveAttribute('href');
  }
};

const expectActions = (enabled: boolean) => {
  expectLinkEnabled(
    screen.getByText('Add infrastructure service').closest('a')!,
    enabled
  );

  for (const name of ['repo', 'git']) {
    const row = within(getRow(name));

    expectLinkEnabled(row.getByText('Edit').closest('a')!, enabled);
    if (enabled)
      expect(row.getByRole('button', { name: 'Delete' })).toBeEnabled();
    else expect(row.getByRole('button', { name: 'Delete' })).toBeDisabled();
  }
};

describe('infrastructure services pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mocks.getOrganization.mockResolvedValue({
      data: { id: 1, name: 'Organization' },
    });
    mocks.getProduct.mockResolvedValue({
      data: { id: 2, organizationId: 1, name: 'Product' },
    });
    mocks.getRepository.mockResolvedValue({
      data: {
        id: 3,
        organizationId: 1,
        productId: 2,
        type: 'GIT',
        url: 'https://example.com/repository.git',
      },
    });

    for (const level of levels) {
      level.services.mockResolvedValue(pageOf(services));
      level.remove.mockResolvedValue({ data: undefined });
    }
    grant(['READ', 'WRITE']);
  });

  it.each(levels)(
    'shows the services of the $level level to readers with the actions disabled',
    async (level) => {
      // Writing secrets does not allow changing infrastructure services.
      grant(['READ', 'WRITE_SECRETS']);
      renderServices(level);

      await screen.findByText('repo');

      expect(screen.getByText('https://git.example.com')).toBeVisible();
      expectActions(false);
    }
  );

  it.each(levels)(
    'enables the actions on the $level level for users who may write',
    async (level) => {
      renderServices(level);

      await screen.findByText('repo');

      expectActions(true);
    }
  );

  it.each(levels)(
    'pages the services of the $level level from the URL',
    async (level) => {
      level.services.mockResolvedValue(pageOf(services, { totalCount: 12 }));
      renderServices(level, '?page=2&pageSize=5');

      expect(await screen.findByText('of 3')).toBeVisible();
      expect(level.services).toHaveBeenCalledWith(
        expect.objectContaining({
          path: level.hierarchyId,
          query: { limit: 5, offset: 5 },
        })
      );
    }
  );

  it.each(levels)(
    'shows the details, secrets and credential files of a service at the $level level',
    async (level) => {
      renderServices(level);

      await screen.findByText('repo');
      const row = within(getRow('repo'));

      for (const text of [
        'The artifact repository',
        'https://repo.example.com',
        'repo-user',
        'repo-password',
        'Netrc File, Git Credentials File',
      ]) {
        expect(row.getByText(text)).toBeVisible();
      }
    }
  );

  it.each(levels)(
    'deletes a service of the $level level after confirmation and refreshes the services',
    async (level) => {
      const { user } = renderServices(level);

      await screen.findByText('repo');
      await user.click(
        within(getRow('repo')).getByRole('button', { name: 'Delete' })
      );
      await user.click(
        within(await screen.findByRole('alertdialog')).getByRole('button', {
          name: 'Delete',
        })
      );

      expect(level.remove).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { ...level.hierarchyId, serviceName: 'repo' },
        })
      );
      await waitFor(() => expect(level.services).toHaveBeenCalledTimes(2));
    }
  );
});
