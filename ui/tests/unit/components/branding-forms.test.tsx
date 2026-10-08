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
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { getServerSettingByKeyQueryKey } from '@/api/@tanstack/react-query.gen';
import { ProductNameForm } from '@/routes/admin/content-management/branding/-components/product-name-form';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const productNameKey = getServerSettingByKeyQueryKey({
  path: { key: 'MAIN_PRODUCT_NAME' },
});

/** Render the form with the saved product name already loaded. */
const renderProductNameForm = (value: string) => {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: Infinity } },
  });
  queryClient.setQueryData(productNameKey, { value, isEnabled: true });

  render(
    <QueryClientProvider client={queryClient}>
      <ProductNameForm />
    </QueryClientProvider>
  );

  return queryClient;
};

// The form takes the saved setting as `values` and resets to it, which React
// Hook Form reports as fragile under React Compiler.
describe('ProductNameForm', () => {
  it('is compiled with React Compiler', () => {
    expect(isCompiledByReactCompiler(ProductNameForm)).toBe(true);
  });

  it('restores the saved product name on reset', async () => {
    const user = userEvent.setup();
    renderProductNameForm('Saved Name');
    const input = screen.getByRole('textbox');

    await user.clear(input);
    await user.type(input, 'Edited Name');
    expect(input).toHaveValue('Edited Name');

    await user.click(screen.getByRole('button', { name: 'Reset' }));

    expect(input).toHaveValue('Saved Name');
  });

  it('shows a product name that was loaded again', async () => {
    const queryClient = renderProductNameForm('Saved Name');

    expect(screen.getByRole('textbox')).toHaveValue('Saved Name');

    queryClient.setQueryData(productNameKey, {
      value: 'Changed Name',
      isEnabled: true,
    });

    await waitFor(() =>
      expect(screen.getByRole('textbox')).toHaveValue('Changed Name')
    );
  });
});
