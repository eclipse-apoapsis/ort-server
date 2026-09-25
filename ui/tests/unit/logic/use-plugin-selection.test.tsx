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

import { act, renderHook } from '@testing-library/react';
import { useController, useForm } from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import { usePluginSelection } from '@/hooks/use-plugin-selection';
import { createPluginDescriptor } from '../fixtures/create-run';

type FormValues = {
  plugins: string[];
  scopes: Record<string, unknown>;
  mustRunAfter: Record<string, unknown>;
};

const plugins = ['A', 'B', 'C'].map((id) =>
  createPluginDescriptor({ id, displayName: `Plugin ${id}` })
);

type Options = {
  defaultValues?: Partial<FormValues>;
  withScannerScope?: boolean;
  withMustRunAfter?: boolean;
  enableReordering?: boolean;
  showSelectedPluginsFirst?: boolean;
};

const renderSelection = ({
  defaultValues = {},
  withScannerScope = false,
  withMustRunAfter = false,
  enableReordering = false,
  showSelectedPluginsFirst = false,
}: Options = {}) => {
  const { result } = renderHook(() => {
    const form = useForm<FormValues>({
      defaultValues: {
        plugins: [],
        scopes: {},
        mustRunAfter: {},
        ...defaultValues,
      },
    });
    const { field } = useController({
      control: form.control,
      name: 'plugins',
    });
    const selection = usePluginSelection({
      form,
      field,
      plugins,
      scannerScopeName: withScannerScope ? 'scopes' : undefined,
      mustRunAfterName: withMustRunAfter ? 'mustRunAfter' : undefined,
      enableReordering,
      showSelectedPluginsFirst,
    });

    return { form, selection };
  });

  return {
    selection: () => result.current.selection,
    values: () => result.current.form.getValues(),
    displayOrder: () =>
      result.current.selection.pluginsInDisplayOrder.map((plugin) => plugin.id),
  };
};

describe('usePluginSelection', () => {
  describe('setSelected', () => {
    it('stores the selection in click order without reordering', () => {
      const { selection, values } = renderSelection();

      act(() => selection().setSelected('C', true));
      act(() => selection().setSelected('A', true));

      expect(values().plugins).toEqual(['C', 'A']);
      expect(selection().isSelected('A')).toBe(true);
      expect(selection().isSelected('B')).toBe(false);
    });

    it('stores the selection in display order with reordering', () => {
      const { selection, values } = renderSelection({ enableReordering: true });

      act(() => selection().setSelected('C', true));
      act(() => selection().setSelected('A', true));

      expect(values().plugins).toEqual(['A', 'C']);
    });

    it('removes a disabled plugin from the selection', () => {
      const { selection, values } = renderSelection({
        defaultValues: { plugins: ['A', 'B'] },
      });

      act(() => selection().setSelected('A', false));

      expect(values().plugins).toEqual(['B']);
    });

    it('defaults a missing scanner scope to both when enabling', () => {
      const { selection, values } = renderSelection({
        withScannerScope: true,
        defaultValues: { scopes: { B: 'packages' } },
      });

      act(() => selection().setSelected('A', true));
      act(() => selection().setSelected('B', true));

      expect(values().scopes).toEqual({ A: 'both', B: 'packages' });
    });

    it('clears the scanner scope and must-run-after list when disabling', () => {
      const { selection, values } = renderSelection({
        withScannerScope: true,
        withMustRunAfter: true,
        defaultValues: {
          plugins: ['A', 'B'],
          scopes: { A: 'projects', B: 'both' },
          mustRunAfter: { A: ['B'] },
        },
      });

      act(() => selection().setSelected('A', false));

      expect(values().scopes).toEqual({ A: undefined, B: 'both' });
      expect(values().mustRunAfter).toEqual({ A: undefined });
    });
  });

  describe('setAllSelected', () => {
    it('enables all plugins in their original order and sets missing scopes', () => {
      const { selection, values } = renderSelection({
        withScannerScope: true,
        showSelectedPluginsFirst: true,
        defaultValues: { plugins: ['C'], scopes: { C: 'projects' } },
      });

      act(() => selection().setAllSelected(true));

      expect(values().plugins).toEqual(['A', 'B', 'C']);
      expect(values().scopes).toEqual({ A: 'both', B: 'both', C: 'projects' });
    });

    it('enables all plugins in display order with reordering', () => {
      const { selection, values } = renderSelection({
        enableReordering: true,
        showSelectedPluginsFirst: true,
        defaultValues: { plugins: ['C'] },
      });

      act(() => selection().setAllSelected(true));

      expect(values().plugins).toEqual(['C', 'A', 'B']);
    });

    it('disables all plugins and clears their scopes and must-run-after lists', () => {
      const { selection, values } = renderSelection({
        withScannerScope: true,
        withMustRunAfter: true,
        defaultValues: {
          plugins: ['A', 'B', 'C'],
          scopes: { A: 'both', B: 'packages', C: 'projects' },
          mustRunAfter: { A: ['B'], C: ['A'] },
        },
      });

      act(() => selection().setAllSelected(false));

      expect(values().plugins).toEqual([]);
      expect(values().scopes).toEqual({
        A: undefined,
        B: undefined,
        C: undefined,
      });
      expect(values().mustRunAfter).toEqual({
        A: undefined,
        B: undefined,
        C: undefined,
      });
    });
  });

  describe('reorder', () => {
    it('moves a plugin and stores the selection in the new order', () => {
      const { selection, values, displayOrder } = renderSelection({
        enableReordering: true,
        defaultValues: { plugins: ['A', 'C'] },
      });

      act(() => selection().reorder(2, 0));

      expect(displayOrder()).toEqual(['C', 'A', 'B']);
      expect(values().plugins).toEqual(['C', 'A']);
    });

    it('keeps the new order after the selection changes', () => {
      const { selection, values, displayOrder } = renderSelection({
        enableReordering: true,
      });

      act(() => selection().reorder(2, 0));
      act(() => selection().setSelected('B', true));
      act(() => selection().setSelected('C', true));

      expect(displayOrder()).toEqual(['C', 'A', 'B']);
      expect(values().plugins).toEqual(['C', 'B']);
    });
  });

  describe('display order', () => {
    it('keeps the original order by default', () => {
      const { displayOrder } = renderSelection({
        defaultValues: { plugins: ['C', 'A'] },
      });

      expect(displayOrder()).toEqual(['A', 'B', 'C']);
    });

    it('shows selected plugins first in selection order', () => {
      const { displayOrder } = renderSelection({
        showSelectedPluginsFirst: true,
        defaultValues: { plugins: ['C', 'A'] },
      });

      expect(displayOrder()).toEqual(['C', 'A', 'B']);
    });
  });

  describe('allState', () => {
    it.each([
      { selected: [], state: false },
      { selected: ['B'], state: 'indeterminate' },
      { selected: ['A', 'B', 'C'], state: true },
    ])(
      'is $state when $selected.length plugins are enabled',
      ({ selected, state }) => {
        const { selection } = renderSelection({
          defaultValues: { plugins: selected },
        });

        expect(selection().allState).toBe(state);
      }
    );
  });
});
