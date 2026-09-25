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
import { describe, expect, it } from 'vitest';

import { usePluginExpansion } from '@/hooks/use-plugin-expansion';

type Props = Parameters<typeof usePluginExpansion>[0];

const defaultProps: Props = {
  expandableIds: ['A', 'B', 'C'],
  initiallyExpandedIds: [],
  submitCount: 0,
  idsWithErrors: [],
};

const renderExpansion = (props: Partial<Props> = {}) => {
  const { result, rerender } = renderHook(
    (hookProps: Props) => usePluginExpansion(hookProps),
    { initialProps: { ...defaultProps, ...props } }
  );

  return {
    expansion: () => result.current,
    rerender: (newProps: Partial<Props>) =>
      rerender({ ...defaultProps, ...props, ...newProps }),
  };
};

describe('usePluginExpansion', () => {
  it('starts with the initially expanded plugins', () => {
    const { expansion } = renderExpansion({ initiallyExpandedIds: ['B'] });

    expect(expansion().expandedIds).toEqual(['B']);
  });

  it('ignores later changes of the initially expanded plugins', () => {
    const { expansion, rerender } = renderExpansion({
      initiallyExpandedIds: ['B'],
    });

    rerender({ initiallyExpandedIds: ['A', 'C'] });

    expect(expansion().expandedIds).toEqual(['B']);
  });

  describe('setExpanded', () => {
    it('expands and collapses a plugin', () => {
      const { expansion } = renderExpansion();

      act(() => expansion().setExpanded('A', true));
      expect(expansion().expandedIds).toEqual(['A']);

      act(() => expansion().setExpanded('A', false));
      expect(expansion().expandedIds).toEqual([]);
    });

    it('expands a plugin that is not expandable yet', () => {
      const { expansion } = renderExpansion();

      act(() => expansion().setExpanded('D', true));

      expect(expansion().expandedIds).toEqual(['D']);
    });
  });

  describe('setAllExpanded', () => {
    it('expands all plugins with options', () => {
      const { expansion } = renderExpansion({ initiallyExpandedIds: ['B'] });

      act(() => expansion().setAllExpanded(true));

      expect(expansion().expandedIds).toEqual(['A', 'B', 'C']);
      expect(expansion().allExpanded).toBe(true);
    });

    it('collapses all plugins', () => {
      const { expansion } = renderExpansion({
        initiallyExpandedIds: ['A', 'B', 'C'],
      });

      expect(expansion().allExpanded).toBe(true);

      act(() => expansion().setAllExpanded(false));

      expect(expansion().expandedIds).toEqual([]);
      expect(expansion().allExpanded).toBe(false);
    });
  });

  it('is not all expanded when no plugin has options', () => {
    const { expansion } = renderExpansion({ expandableIds: [] });

    expect(expansion().allExpanded).toBe(false);
  });

  it('sets the expanded plugins, as reported by an accordion', () => {
    const { expansion } = renderExpansion();

    act(() => expansion().setExpandedIds(['C', 'A']));

    expect(expansion().expandedIds).toEqual(['C', 'A']);
  });

  describe('on submit', () => {
    it('expands the plugins with errors and keeps the others expanded', () => {
      const { expansion, rerender } = renderExpansion({
        initiallyExpandedIds: ['A'],
      });

      rerender({ submitCount: 1, idsWithErrors: ['C'] });

      expect(expansion().expandedIds).toEqual(['A', 'C']);
    });

    it('does not expand plugins with errors again without a new submit', () => {
      const { expansion, rerender } = renderExpansion();

      rerender({ submitCount: 1, idsWithErrors: ['C'] });
      act(() => expansion().setExpanded('C', false));
      rerender({ submitCount: 1, idsWithErrors: ['B', 'C'] });

      expect(expansion().expandedIds).toEqual([]);
    });

    it('expands the plugins with errors on each new submit', () => {
      const { expansion, rerender } = renderExpansion();

      rerender({ submitCount: 1, idsWithErrors: ['C'] });
      act(() => expansion().setExpanded('C', false));
      rerender({ submitCount: 2, idsWithErrors: ['C'] });

      expect(expansion().expandedIds).toEqual(['C']);
    });
  });
});
