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
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import {
  afterEach,
  beforeEach,
  describe,
  expect,
  it,
  vi,
  type Mock,
} from 'vitest';

import {
  getOrganizationInfrastructureServices,
  getOrganizationSecrets,
} from '@/api/sdk.gen';
import { InfrastructureServiceSelect } from '@/components/infrastructure-service-select';
import { SecretSelect } from '@/components/secret-select';
import { Form, FormField, FormItem } from '@/components/ui/form';
import MultipleSelector from '@/components/ui/multiple-selector';
import { OrganizationPermissions } from '@/lib/permissions';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const permissions = vi.hoisted(() => ({
  organization: undefined as unknown,
  product: undefined,
  repository: undefined,
}));

vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ orgId: '1' }),
  useRouter: () => ({ options: { context: { permissions } } }),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getOrganizationSecrets: vi.fn(),
  getOrganizationInfrastructureServices: vi.fn(),
}));

describe('selectors', () => {
  it.each([
    ['MultipleSelector', MultipleSelector],
    ['SecretSelect', SecretSelect],
    ['InfrastructureServiceSelect', InfrastructureServiceSelect],
  ])('compiles %s with React Compiler', (_, component) => {
    expect(isCompiledByReactCompiler(component)).toBe(true);
  });
});

describe('MultipleSelector', () => {
  it('passes a selected option to the latest change handler', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const second = vi.fn();
    const selector = (onChange: typeof first) => (
      <MultipleSelector
        inputProps={{ 'aria-label': 'Search options' }}
        defaultOptions={[{ value: 'apple', label: 'Apple' }]}
        onChange={onChange}
      />
    );
    const { rerender } = render(selector(first));

    rerender(selector(second));
    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByRole('option', { name: 'Apple' }));

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith([{ value: 'apple', label: 'Apple' }]);
  });
});

/** Renders a select inside the form field it expects, with a query client. */
const renderInForm = (select: (field: { value: string }) => ReactNode) => {
  const Harness = () => {
    const form = useForm<{ name: string }>({ defaultValues: { name: '' } });

    return (
      <Form {...form}>
        <FormField
          control={form.control}
          name='name'
          render={({ field }) => <FormItem>{select(field)}</FormItem>}
        />
      </Form>
    );
  };
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <Harness />
    </QueryClientProvider>
  );

  return queryClient;
};

const pageOf = (...names: string[]) => ({
  data: {
    data: names.map((name) => ({ name })),
    pagination: { limit: 10, offset: 0, totalCount: names.length },
  },
});

describe.each([
  {
    name: 'SecretSelect',
    request: getOrganizationSecrets,
    select: ({ value }: { value: string }) => (
      <SecretSelect
        value={value}
        onChange={() => {}}
        placeholder='Select a secret'
        orgId='1'
        permissions={{
          organization: new OrganizationPermissions(1, true, []),
          product: undefined,
          repository: undefined,
        }}
      />
    ),
  },
  {
    name: 'InfrastructureServiceSelect',
    request: getOrganizationInfrastructureServices,
    select: ({ value }: { value: string }) => (
      <InfrastructureServiceSelect
        value={value}
        onChange={() => {}}
        placeholder='Select a service'
      />
    ),
  },
])('$name', ({ request, select }) => {
  beforeEach(() => {
    permissions.organization = new OrganizationPermissions(1, true, []);
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('offers an item that was added when the list is read again', async () => {
    const user = userEvent.setup();
    const respondWith = (...names: string[]) =>
      (vi.mocked(request) as Mock).mockResolvedValue(pageOf(...names));
    respondWith('first');
    const queryClient = renderInForm(select);

    await user.click(screen.getByRole('combobox'));

    expect(
      await screen.findByRole('option', { name: 'first (Organization)' })
    ).toBeInTheDocument();

    respondWith('first', 'second');
    await queryClient.invalidateQueries();

    expect(
      await screen.findByRole('option', { name: 'second (Organization)' })
    ).toBeInTheDocument();
  });
});
