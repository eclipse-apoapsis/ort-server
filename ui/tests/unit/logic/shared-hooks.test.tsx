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
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getOrganizationInfrastructureServices,
  getOrganizationSecrets,
  getProductInfrastructureServices,
  getProductSecrets,
  getRepositoryInfrastructureServices,
  getRepositorySecrets,
} from '@/api/sdk.gen';
import { useDebounce } from '@/hooks/use-debounce';
import { useInView } from '@/hooks/use-in-view';
import { useInfiniteList } from '@/hooks/use-infinite-list';
import { useInfrastructureServices } from '@/hooks/use-infrastructure-services';
import { useSecrets } from '@/hooks/use-secrets';
import { useTableSizing } from '@/hooks/use-table-sizing';
import {
  OrganizationPermissions,
  ProductPermissions,
  RepositoryPermissions,
} from '@/lib/permissions';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getOrganizationSecrets: vi.fn(),
  getProductSecrets: vi.fn(),
  getRepositorySecrets: vi.fn(),
  getOrganizationInfrastructureServices: vi.fn(),
  getProductInfrastructureServices: vi.fn(),
  getRepositoryInfrastructureServices: vi.fn(),
}));

describe('shared hooks', () => {
  it.each([
    ['useDebounce', useDebounce],
    ['useInView', useInView],
    ['useInfiniteList', useInfiniteList],
    ['useSecrets', useSecrets],
    ['useInfrastructureServices', useInfrastructureServices],
    ['useTableSizing', useTableSizing],
  ])('compiles %s with React Compiler', (_, hook) => {
    expect(isCompiledByReactCompiler(hook)).toBe(true);
  });
});

describe('useDebounce', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('restarts the delay for a new value and passes on only the last one', () => {
    const { result, rerender } = renderHook(
      ({ value }) => useDebounce(value, 100),
      { initialProps: { value: 'a' } }
    );

    rerender({ value: 'b' });
    act(() => vi.advanceTimersByTime(60));
    rerender({ value: 'c' });
    act(() => vi.advanceTimersByTime(60));

    expect(result.current).toBe('a');

    act(() => vi.advanceTimersByTime(40));

    expect(result.current).toBe('c');
  });

  it('applies a changed delay', () => {
    const { result, rerender } = renderHook(
      ({ value, delay }) => useDebounce(value, delay),
      { initialProps: { value: 'a', delay: 100 } }
    );

    rerender({ value: 'b', delay: 300 });
    act(() => vi.advanceTimersByTime(200));

    expect(result.current).toBe('a');

    act(() => vi.advanceTimersByTime(100));

    expect(result.current).toBe('b');
  });
});

describe('useInView', () => {
  const observers: MockIntersectionObserver[] = [];

  class MockIntersectionObserver {
    readonly observed: Element[] = [];
    disconnected = false;

    constructor(readonly callback: IntersectionObserverCallback) {
      observers.push(this);
    }

    observe(element: Element) {
      this.observed.push(element);
    }

    disconnect() {
      this.disconnected = true;
    }

    report(isIntersecting: boolean) {
      this.callback(
        [{ isIntersecting } as IntersectionObserverEntry],
        this as unknown as IntersectionObserver
      );
    }
  }

  beforeEach(() => {
    observers.length = 0;
    vi.stubGlobal('IntersectionObserver', MockIntersectionObserver);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('observes the element the ref moves to and disconnects from the old one', () => {
    const { result } = renderHook(() => useInView());
    const first = document.createElement('div');
    const second = document.createElement('div');

    act(() => result.current.ref(first));
    act(() => observers[0]?.report(true));

    expect(observers[0]?.observed).toEqual([first]);
    expect(result.current.inView).toBe(true);

    act(() => result.current.ref(second));

    expect(observers[0]?.disconnected).toBe(true);
    expect(observers[1]?.observed).toEqual([second]);
  });

  it('is not in view after the ref is detached', () => {
    const { result } = renderHook(() => useInView());

    act(() => result.current.ref(document.createElement('div')));
    act(() => observers[0]?.report(true));
    act(() => result.current.ref(null));

    expect(result.current.inView).toBe(false);
    expect(observers[0]?.disconnected).toBe(true);
  });

  it('keeps its ref and observer when it renders again', () => {
    const { result, rerender } = renderHook(() => useInView());
    const { ref } = result.current;

    act(() => ref(document.createElement('div')));
    rerender();

    expect(result.current.ref).toBe(ref);
    expect(observers).toHaveLength(1);
    expect(observers[0]?.disconnected).toBe(false);
  });
});

/** Answers every request with one item named after the level and its ID. */
const pageOfOne = (level: string) =>
  vi.fn(async (options: { path: Record<string, number> }) => ({
    data: {
      data: [{ name: `${level}-${Object.values(options.path)[0]}` }],
      pagination: { limit: 10, offset: 0, totalCount: 1 },
    },
  }));

const ids = { orgId: '1', productId: '2', repositoryId: '3' };
const permissions = {
  organization: new OrganizationPermissions(1, true, []),
  product: new ProductPermissions(2, true, []),
  repository: new RepositoryPermissions(3, true, []),
};

const renderWithQueryClient = <TProps, TResult>(
  hook: (props: TProps) => TResult,
  initialProps: TProps
) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );

  return renderHook(hook, { initialProps, wrapper });
};

const itemNames = (items: { name: string }[]) => items.map(({ name }) => name);

describe.each([
  {
    name: 'useSecrets',
    use: (props: typeof ids) => useSecrets({ ...props, permissions }),
    requests: {
      organization: getOrganizationSecrets,
      product: getProductSecrets,
      repository: getRepositorySecrets,
    },
  },
  {
    name: 'useInfrastructureServices',
    use: ({ repositoryId, ...props }: typeof ids) =>
      useInfrastructureServices({
        ...props,
        repoId: repositoryId,
        permissions,
      }),
    requests: {
      organization: getOrganizationInfrastructureServices,
      product: getProductInfrastructureServices,
      repository: getRepositoryInfrastructureServices,
    },
  },
])('$name', ({ use, requests }) => {
  beforeEach(() => {
    Object.entries(requests).forEach(([level, request]) =>
      vi
        .mocked(request)
        .mockImplementation(pageOfOne(level) as unknown as typeof request)
    );
  });

  it.each([
    ['organization', 'orgId', 'organization-1'],
    ['product', 'productId', 'product-2'],
    ['repository', 'repositoryId', 'repository-3'],
  ] as const)(
    'reads the items of a changed %s ID',
    async (level, idKey, previousName) => {
      const { result, rerender } = renderWithQueryClient(use, ids);

      await waitFor(() =>
        expect(itemNames(result.current.items)).toEqual([
          'organization-1',
          'product-2',
          'repository-3',
        ])
      );

      rerender({ ...ids, [idKey]: '9' });

      await waitFor(() =>
        expect(itemNames(result.current.items)).toContain(`${level}-9`)
      );
      expect(itemNames(result.current.items)).not.toContain(previousName);
      expect(requests[level]).toHaveBeenLastCalledWith(
        expect.objectContaining({ path: { [`${level}Id`]: 9 } })
      );
    }
  );
});
