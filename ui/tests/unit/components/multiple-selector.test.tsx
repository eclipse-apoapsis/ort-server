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

import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import MultipleSelector from '@/components/ui/multiple-selector';

const inputProps = { 'aria-label': 'Search options' };
const commandProps = { shouldFilter: false };

describe('MultipleSelector search callbacks', () => {
  it('uses a new synchronous callback while open, without repeating an unchanged search', async () => {
    const user = userEvent.setup();
    const first = vi.fn(() => [{ value: 'first', label: 'First result' }]);
    const second = vi.fn(() => [{ value: 'second', label: 'Second result' }]);
    const { rerender } = render(
      <MultipleSelector
        inputProps={inputProps}
        commandProps={commandProps}
        onSearchSync={first}
        triggerSearchOnFocus
      />
    );

    await user.click(screen.getByRole('combobox'));
    expect(await screen.findByText('First result')).toBeVisible();
    expect(first).toHaveBeenCalledExactlyOnceWith('');

    rerender(
      <MultipleSelector
        inputProps={inputProps}
        commandProps={commandProps}
        onSearchSync={second}
        triggerSearchOnFocus
      />
    );
    expect(await screen.findByText('Second result')).toBeVisible();
    expect(screen.queryByText('First result')).toBeNull();
    expect(second).toHaveBeenCalledExactlyOnceWith('');

    rerender(
      <MultipleSelector
        inputProps={inputProps}
        commandProps={commandProps}
        onSearchSync={second}
        triggerSearchOnFocus
      />
    );
    expect(second).toHaveBeenCalledTimes(1);
  });

  it('uses a new asynchronous callback while open and stops loading', async () => {
    const user = userEvent.setup();
    const first = vi.fn(async () => [
      { value: 'first', label: 'First result' },
    ]);
    const second = vi.fn(async () => [
      { value: 'second', label: 'Second result' },
    ]);
    const { rerender } = render(
      <MultipleSelector
        inputProps={inputProps}
        commandProps={commandProps}
        onSearch={first}
        triggerSearchOnFocus
        loadingIndicator={<span>Searching...</span>}
      />
    );

    await user.click(screen.getByRole('combobox'));
    expect(await screen.findByText('First result')).toBeVisible();
    expect(first).toHaveBeenCalledExactlyOnceWith('');

    rerender(
      <MultipleSelector
        inputProps={inputProps}
        commandProps={commandProps}
        onSearch={second}
        triggerSearchOnFocus
        loadingIndicator={<span>Searching...</span>}
      />
    );
    expect(await screen.findByText('Second result')).toBeVisible();
    expect(screen.queryByText('First result')).toBeNull();
    expect(screen.queryByText('Searching...')).toBeNull();
    expect(second).toHaveBeenCalledExactlyOnceWith('');

    rerender(
      <MultipleSelector
        inputProps={inputProps}
        commandProps={commandProps}
        onSearch={second}
        triggerSearchOnFocus
      />
    );
    expect(second).toHaveBeenCalledTimes(1);
  });

  it.each(['sync', 'async'] as const)(
    'searches after debounced typing in %s mode, but not while closed',
    async (mode) => {
      const user = userEvent.setup();
      const first = vi.fn((term: string) => [
        { value: term, label: 'First result' },
      ]);
      const second = vi.fn((term: string) => [
        { value: term, label: 'Second result' },
      ]);
      const props = (search: typeof first) =>
        mode === 'sync'
          ? { onSearchSync: search }
          : { onSearch: async (term: string) => search(term) };
      const { rerender } = render(
        <MultipleSelector
          inputProps={inputProps}
          commandProps={commandProps}
          {...props(first)}
        />
      );
      const input = screen.getByRole('combobox');
      await user.click(input);
      expect(first).not.toHaveBeenCalled();
      await user.type(input, 'query');
      await waitFor(() => expect(first).toHaveBeenCalledWith('query'));
      expect(await screen.findByText('First result')).toBeVisible();

      await user.tab();
      rerender(
        <MultipleSelector
          inputProps={inputProps}
          commandProps={commandProps}
          {...props(second)}
        />
      );
      expect(second).not.toHaveBeenCalled();
      await user.click(input);
      await waitFor(() => expect(second).toHaveBeenCalledWith('query'));
      expect(await screen.findByText('Second result')).toBeVisible();
    }
  );
});

describe('MultipleSelector command filter', () => {
  const defaultOptions = [
    { value: 'apple', label: 'Apple' },
    { value: 'banana', label: 'Banana' },
  ];
  const onlyValue = (kept: string) => (value: string) =>
    value === kept ? 1 : 0;

  it('filters with a new filter passed in the command props', async () => {
    const user = userEvent.setup();
    const { rerender } = render(
      <MultipleSelector
        inputProps={inputProps}
        defaultOptions={defaultOptions}
        commandProps={{ filter: onlyValue('Apple') }}
      />
    );

    const input = screen.getByRole('combobox');
    await user.click(input);
    await user.type(input, 'a');
    expect(await screen.findByRole('option', { name: 'Apple' })).toBeVisible();
    expect(screen.queryByRole('option', { name: 'Banana' })).toBeNull();

    rerender(
      <MultipleSelector
        inputProps={inputProps}
        defaultOptions={defaultOptions}
        commandProps={{ filter: onlyValue('Banana') }}
      />
    );
    await user.type(input, 'n');
    expect(await screen.findByRole('option', { name: 'Banana' })).toBeVisible();
    expect(screen.queryByRole('option', { name: 'Apple' })).toBeNull();
  });
});
