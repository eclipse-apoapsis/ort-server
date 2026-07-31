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

import type { ReactNode } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { OrganizationProductsStatisticsCard } from '@/routes/organizations/$orgId/-components/organization-products-statistics-card';

const mocks = vi.hoisted(() => ({
  canCreateProduct: true,
  useOrganizationPermission: vi.fn(),
  useSuspenseQuery: vi.fn(),
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...actual,
    useSuspenseQuery: mocks.useSuspenseQuery,
  };
});

vi.mock('@tanstack/react-router', () => ({
  Link: ({
    children,
    className,
    disabled,
    params,
  }: {
    children: ReactNode;
    className?: string;
    disabled?: boolean;
    params: { orgId: string };
  }) => (
    <a
      className={className}
      href={`/organizations/${params.orgId}/create-product`}
      aria-disabled={disabled}
    >
      {children}
    </a>
  ),
}));

vi.mock('@/hooks/use-authorization', () => ({
  useOrganizationPermission: mocks.useOrganizationPermission,
}));

const renderCard = () =>
  renderToStaticMarkup(
    <OrganizationProductsStatisticsCard orgId='42' className='col-span-2' />
  );

describe('OrganizationProductsStatisticsCard', () => {
  beforeEach(() => {
    mocks.canCreateProduct = true;
    mocks.useOrganizationPermission.mockReset();
    mocks.useOrganizationPermission.mockImplementation(() => ({
      isAllowed: mocks.canCreateProduct,
    }));
    mocks.useSuspenseQuery.mockReset();
    mocks.useSuspenseQuery.mockReturnValue({
      data: { pagination: { totalCount: 12 } },
    });
  });

  it('renders the product total and add action', () => {
    const markup = renderCard();

    expect(markup).toContain('Products');
    expect(markup).toContain('>12<');
    expect(markup).toContain('Add product');
    expect(markup).toContain('href="/organizations/42/create-product"');
    expect(markup).toContain('col-span-2');
    expect(mocks.useOrganizationPermission).toHaveBeenCalledWith(
      42,
      'CREATE_PRODUCT'
    );
  });

  it('disables the add action without permission', () => {
    mocks.canCreateProduct = false;

    const markup = renderCard();

    expect(markup).toContain('aria-disabled="true"');
  });
});
