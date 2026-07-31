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

import { renderToStaticMarkup } from 'react-dom/server';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ProductItemCounts } from '@/routes/organizations/$orgId/-components/product-item-counts';

type Statistics = {
  issuesCount: number;
  vulnerabilitiesCount: number;
  ruleViolationsCount: number;
};

type ItemCountsProps = {
  statistics: Statistics;
  compact: boolean;
  link?: unknown;
  tooltip: (label: string, count: number) => string;
};

const mocks = vi.hoisted(() => ({
  statistics: {
    issuesCount: 3,
    vulnerabilitiesCount: 5,
    ruleViolationsCount: 7,
  } as Statistics,
  suspend: false,
  itemCountsProps: undefined as ItemCountsProps | undefined,
  pendingPromise: new Promise<never>(() => undefined),
  useSuspenseQuery: vi.fn(),
}));

vi.mock('@tanstack/react-query', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@tanstack/react-query')>();

  return {
    ...actual,
    useSuspenseQuery: mocks.useSuspenseQuery,
  };
});

vi.mock('@/components/item-counts', () => ({
  ItemCounts: (props: ItemCountsProps) => {
    mocks.itemCountsProps = props;
    return <div>Item counts</div>;
  },
  ItemCountsSkeleton: () => <div data-slot='item-counts-skeleton' />,
}));

const renderItemCounts = () =>
  renderToStaticMarkup(<ProductItemCounts productId={2} />);

describe('ProductItemCounts', () => {
  beforeEach(() => {
    mocks.suspend = false;
    mocks.itemCountsProps = undefined;
    mocks.useSuspenseQuery.mockReset();
    mocks.useSuspenseQuery.mockImplementation(() => {
      if (mocks.suspend) throw mocks.pendingPromise;

      return { data: mocks.statistics };
    });
  });

  it('renders the product statistics without links', () => {
    const markup = renderItemCounts();

    expect(markup).toContain('Item counts');
    expect(mocks.itemCountsProps).toMatchObject({
      statistics: mocks.statistics,
      compact: true,
    });
    expect(mocks.itemCountsProps?.link).toBeUndefined();
    expect(mocks.itemCountsProps?.tooltip('issues', 3)).toBe(
      '3 issues in total'
    );
  });

  it('renders a skeleton while the statistics are loading', () => {
    mocks.suspend = true;

    const markup = renderItemCounts();

    expect(markup).toContain('data-slot="item-counts-skeleton"');
    expect(markup).toContain('Loading data...');
    expect(mocks.itemCountsProps).toBeUndefined();
  });
});
