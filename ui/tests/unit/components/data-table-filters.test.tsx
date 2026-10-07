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

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeAll, describe, expect, it, vi } from 'vitest';

import { FilterInfiniteMultiSelect } from '@/components/data-table/filter-infinite-multi-select';
import { FilterMultiSelect } from '@/components/data-table/filter-multi-select';
import { FilterRegex } from '@/components/data-table/filter-regex';
import { FilterSingleSelect } from '@/components/data-table/filter-single-select';
import { FilterText } from '@/components/data-table/filter-text';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const openFilter = async (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button'));

describe('table filters', () => {
  it.each([
    ['FilterText', FilterText],
    ['FilterRegex', FilterRegex],
    ['FilterSingleSelect', FilterSingleSelect],
    ['FilterMultiSelect', FilterMultiSelect],
    ['FilterInfiniteMultiSelect', FilterInfiniteMultiSelect],
  ])('compiles %s with React Compiler', (_, component) => {
    expect(isCompiledByReactCompiler(component)).toBe(true);
  });
});

describe('FilterText', () => {
  it('shows a filter value that changed from outside', async () => {
    const user = userEvent.setup();
    const { rerender } = render(
      <FilterText filterValue='first' setFilterValue={() => {}} />
    );

    rerender(<FilterText filterValue='second' setFilterValue={() => {}} />);
    await openFilter(user);

    expect(screen.getByRole('textbox')).toHaveValue('second');
  });

  it('passes the typed value to the latest handler', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(
      <FilterText filterValue='' setFilterValue={first} />
    );

    rerender(<FilterText filterValue='' setFilterValue={second} />);
    await openFilter(user);
    await user.type(screen.getByRole('textbox'), 'core{Enter}');

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith('core');
  });
});

describe('FilterRegex', () => {
  it('passes the typed expression to the latest handler', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(
      <FilterRegex filterValue='' setFilterValue={first} />
    );

    rerender(<FilterRegex filterValue='' setFilterValue={second} />);
    await openFilter(user);
    await user.type(screen.getByRole('textbox'), '^core{Enter}');

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith('^core');
  });
});

const optionsOf = (...labels: string[]) =>
  labels.map((label) => ({ label, value: label.toLowerCase() }));

describe('FilterSingleSelect', () => {
  it('offers changed options and passes a choice to the latest handler', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(
      <FilterSingleSelect
        title='Status'
        options={optionsOf('Open')}
        setSelected={first}
      />
    );

    rerender(
      <FilterSingleSelect
        title='Status'
        options={optionsOf('Open', 'Closed')}
        setSelected={second}
      />
    );
    await openFilter(user);
    await user.click(screen.getByRole('radio', { name: 'Closed' }));

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith('closed');
  });
});

describe('FilterMultiSelect', () => {
  beforeAll(() => {
    // cmdk scrolls the selected option into view, which jsdom does not implement.
    Element.prototype.scrollIntoView = () => {};
  });

  it('offers changed options and passes a choice to the latest handler', async () => {
    const user = userEvent.setup();
    const first = vi.fn();
    const second = vi.fn();
    const { rerender } = render(
      <FilterMultiSelect
        options={optionsOf('Open')}
        selected={['open']}
        setSelected={first}
      />
    );

    rerender(
      <FilterMultiSelect
        options={optionsOf('Open', 'Closed')}
        selected={['open']}
        setSelected={second}
      />
    );
    await openFilter(user);
    await user.click(screen.getByRole('option', { name: 'Closed' }));

    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledWith(['open', 'closed']);
  });
});
