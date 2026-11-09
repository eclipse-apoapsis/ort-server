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

import type { Secret } from '@/api';
import { Route as RepositoryRoute } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/secrets';
import { Route as ProductRoute } from '@/routes/organizations/$orgId/products/$productId/secrets';
import { Route as OrganizationRoute } from '@/routes/organizations/$orgId/secrets';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { hierarchyLevels, pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getOrganization: vi.fn(),
  getProduct: vi.fn(),
  getRepository: vi.fn(),
  getOrganizationSecrets: vi.fn(),
  getProductSecrets: vi.fn(),
  getRepositorySecrets: vi.fn(),
  deleteOrganizationSecret: vi.fn(),
  deleteProductSecret: vi.fn(),
  deleteRepositorySecret: vi.fn(),
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

const [organization, product, repository] = hierarchyLevels('secrets');
const levels = {
  organization: { ...organization, route: OrganizationRoute },
  product: { ...product, route: ProductRoute },
  repository: { ...repository, route: RepositoryRoute },
};

const createSecrets = (prefix: string, count: number): Secret[] =>
  Array.from({ length: count }, (_, index) => ({
    name: `${prefix}-${index + 1}`,
    description: `Secret ${index + 1} of the ${prefix}`,
  }));

// The secrets of a level, of which the server answers with the first `count` in one page.
const serveSecrets = (
  secretsMock: typeof mocks.getOrganizationSecrets,
  prefix: string,
  count: number
) =>
  secretsMock.mockResolvedValue(
    pageOf(createSecrets(prefix, Math.min(count, 2)), { totalCount: count })
  );

type Permission = 'READ' | 'WRITE_SECRETS';

// Grants the given permissions on each level, which the permission hooks then answer with.
const grant = ({
  organization = ['READ', 'WRITE_SECRETS'],
  product = ['READ', 'WRITE_SECRETS'],
  repository = ['READ', 'WRITE_SECRETS'],
}: {
  organization?: Permission[];
  product?: Permission[];
  repository?: Permission[];
}) => {
  for (const [hook, granted] of [
    [permissions.useOrganizationPermission, organization],
    [permissions.useProductPermission, product],
    [permissions.useRepositoryPermission, repository],
  ] as const) {
    hook.mockImplementation((_id: number, permission: Permission) => ({
      isAllowed: granted.includes(permission),
      isPending: false,
      error: null,
    }));
  }
};

const renderSecrets = (
  level: (typeof levels)[keyof typeof levels],
  search = ''
) => {
  const SecretsComponent = level.route.options.component!;

  return renderInteractiveWithRouter(<SecretsComponent />, {
    path: `${level.path}${search}`,
    routes: level.routes,
    withQueryClient: true,
  });
};

type Card = ReturnType<typeof within>;

// The card with the given title, which holds one of the secrets tables of the page.
const findCard = async (title: string): Promise<Card> =>
  within(
    (
      await screen.findByText(title, { selector: '[data-slot="card-title"]' })
    ).closest<HTMLElement>('[data-slot="card"]')!
  );

const expectQuery = (
  secretsMock: typeof mocks.getOrganizationSecrets,
  query: { limit: number; offset: number }
) =>
  expect(secretsMock).toHaveBeenCalledWith(expect.objectContaining({ query }));

// Deletes the secret through the dialog of its row in the given card.
const deleteSecret = async (
  user: ReturnType<typeof renderSecrets>['user'],
  card: Card,
  name: string
) => {
  const row = card.getByRole('cell', { name }).closest('tr')!;
  await user.click(within(row).getByRole('button', { name: 'Delete' }));
  await user.click(
    within(await screen.findByRole('alertdialog')).getByRole('button', {
      name: 'Delete',
    })
  );
};

const expectNoActions = (card: Card) => {
  expect(card.getByRole('table')).toBeVisible();
  expect(card.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument();
  expect(
    card.queryByRole('button', { name: 'Delete' })
  ).not.toBeInTheDocument();
};

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

const expectManageLink = (card: Card, name: string, allowed: boolean) =>
  expectLinkEnabled(card.getByText(name).closest('a')!, allowed);

// The actions on the secrets of the level itself, which need permission to write secrets.
const expectOwnActions = (card: Card, enabled: boolean) => {
  expectLinkEnabled(card.getByText('Add secret').closest('a')!, enabled);

  for (const edit of card.getAllByText('Edit')) {
    expectLinkEnabled(edit.closest('a')!, enabled);
  }
  for (const remove of card.getAllByRole('button', { name: 'Delete' })) {
    if (enabled) expect(remove).toBeEnabled();
    else expect(remove).toBeDisabled();
  }
};

describe('secrets pages', () => {
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
    serveSecrets(mocks.getOrganizationSecrets, 'organization', 2);
    serveSecrets(mocks.getProductSecrets, 'product', 2);
    serveSecrets(mocks.getRepositorySecrets, 'repository', 2);
    mocks.deleteOrganizationSecret.mockResolvedValue({ data: undefined });
    mocks.deleteProductSecret.mockResolvedValue({ data: undefined });
    mocks.deleteRepositorySecret.mockResolvedValue({ data: undefined });
    grant({});
  });

  describe('organization', () => {
    const level = levels.organization;

    it('pages the secrets from the URL', async () => {
      serveSecrets(mocks.getOrganizationSecrets, 'organization', 12);
      renderSecrets(level, '?page=2&pageSize=5');

      const card = await findCard('Secrets');

      expect(card.getByText('of 3')).toBeVisible();
      expectQuery(mocks.getOrganizationSecrets, { limit: 5, offset: 5 });
    });

    it('deletes a secret after confirmation and refreshes the secrets', async () => {
      const { user } = renderSecrets(level);

      await deleteSecret(user, await findCard('Secrets'), 'organization-1');

      expect(mocks.deleteOrganizationSecret).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { organizationId: 1, secretName: 'organization-1' },
        })
      );
      await waitFor(() =>
        expect(mocks.getOrganizationSecrets).toHaveBeenCalledTimes(2)
      );
    });
  });

  describe('product', () => {
    const level = levels.product;

    it('pages its own and the organization secrets from the URL', async () => {
      serveSecrets(mocks.getProductSecrets, 'product', 6);
      serveSecrets(mocks.getOrganizationSecrets, 'organization', 6);
      renderSecrets(level, '?page=3&pageSize=2&orgPage=2&orgPageSize=4');

      const productCard = await findCard('Product Secrets');
      const organizationCard = await findCard('Organization Secrets');

      expect(productCard.getByText('of 3')).toBeVisible();
      expectQuery(mocks.getProductSecrets, { limit: 2, offset: 4 });
      expect(organizationCard.getByText('of 2')).toBeVisible();
      expectQuery(mocks.getOrganizationSecrets, { limit: 4, offset: 4 });
    });

    it('deletes a secret after confirmation and refreshes the secrets', async () => {
      const { user } = renderSecrets(level);

      await deleteSecret(user, await findCard('Product Secrets'), 'product-1');

      expect(mocks.deleteProductSecret).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { productId: 2, secretName: 'product-1' },
        })
      );
      await waitFor(() =>
        expect(mocks.getProductSecrets).toHaveBeenCalledTimes(2)
      );
    });

    it('lists the organization secrets without actions', async () => {
      renderSecrets(level);

      const card = await findCard('Organization Secrets');

      expect(card.getByText('organization-1')).toBeVisible();
      expectNoActions(card);
    });

    it.each([true, false])(
      'offers to manage the organization secrets if allowed to write them: %s',
      async (allowed) => {
        grant({ organization: allowed ? ['READ', 'WRITE_SECRETS'] : ['READ'] });
        renderSecrets(level);

        const card = await findCard('Organization Secrets');

        expectManageLink(card, 'Manage organization secrets', allowed);
      }
    );
  });

  describe('repository', () => {
    const level = levels.repository;

    // Each table reads its own page and page size, which all differ here, so a table that
    // reads those of another table shows another offset or page count.
    it('pages its own and the inherited secrets from the URL', async () => {
      serveSecrets(mocks.getRepositorySecrets, 'repository', 6);
      serveSecrets(mocks.getProductSecrets, 'product', 6);
      serveSecrets(mocks.getOrganizationSecrets, 'organization', 6);
      renderSecrets(
        level,
        '?page=2&pageSize=3&productPage=3&productPageSize=2'
      );

      const repositoryCard = await findCard('Repository Secrets');
      const productCard = await findCard('Product Secrets');
      const organizationCard = await findCard('Organization Secrets');

      expect(repositoryCard.getByText('of 2')).toBeVisible();
      expectQuery(mocks.getRepositorySecrets, { limit: 3, offset: 3 });
      expect(productCard.getByText('of 3')).toBeVisible();
      expectQuery(mocks.getProductSecrets, { limit: 2, offset: 4 });
      expect(organizationCard.getByText('of 2')).toBeVisible();
      expectQuery(mocks.getOrganizationSecrets, { limit: 5, offset: 0 });
    });

    it('deletes a secret after confirmation and refreshes the secrets', async () => {
      const { user } = renderSecrets(level);

      await deleteSecret(
        user,
        await findCard('Repository Secrets'),
        'repository-1'
      );

      expect(mocks.deleteRepositorySecret).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { repositoryId: 3, secretName: 'repository-1' },
        })
      );
      await waitFor(() =>
        expect(mocks.getRepositorySecrets).toHaveBeenCalledTimes(2)
      );
    });

    it('lists the product and organization secrets without actions', async () => {
      renderSecrets(level);

      const productCard = await findCard('Product Secrets');
      const organizationCard = await findCard('Organization Secrets');

      expect(productCard.getByText('product-1')).toBeVisible();
      expectNoActions(productCard);
      expect(organizationCard.getByText('organization-1')).toBeVisible();
      expectNoActions(organizationCard);
    });

    it.each([
      { product: true, organization: false },
      { product: false, organization: true },
    ])(
      'offers to manage the inherited secrets of each level it may write: $product for the product, $organization for the organization',
      async (allowed) => {
        grant({
          product: allowed.product ? ['READ', 'WRITE_SECRETS'] : ['READ'],
          organization: allowed.organization
            ? ['READ', 'WRITE_SECRETS']
            : ['READ'],
        });
        renderSecrets(level);

        const productCard = await findCard('Product Secrets');
        const organizationCard = await findCard('Organization Secrets');

        expectManageLink(
          productCard,
          'Manage product secrets',
          allowed.product
        );
        expectManageLink(
          organizationCard,
          'Manage organization secrets',
          allowed.organization
        );
      }
    );
  });

  describe.each([
    { ...levels.organization, title: 'Secrets' },
    { ...levels.product, title: 'Product Secrets' },
    { ...levels.repository, title: 'Repository Secrets' },
  ])('$level', (level) => {
    it('shows the secrets to readers with the actions disabled', async () => {
      grant({ [level.level]: ['READ'] });
      renderSecrets(level);

      const card = await findCard(level.title);

      expect(card.getByText(`${level.level}-1`)).toBeVisible();
      expect(card.getByText(`Secret 1 of the ${level.level}`)).toBeVisible();
      expectOwnActions(card, false);
    });

    it('enables the actions for users who may write secrets', async () => {
      renderSecrets(level);

      expectOwnActions(await findCard(level.title), true);
    });
  });
});
