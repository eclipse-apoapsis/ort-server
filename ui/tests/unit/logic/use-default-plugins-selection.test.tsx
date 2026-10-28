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

import { useDefaultPluginsSelection } from '@/hooks/use-default-plugins-selection';
import { createPluginDescriptor } from '../fixtures/create-run';

type Props = Parameters<typeof useDefaultPluginsSelection>[0];

const defaultProps: Props = {
  pluginType: 'PACKAGE_CURATION_PROVIDER',
  isOrdered: true,
  defaultPluginIds: [],
  availablePlugins: ['A', 'B', 'C'].map((id) =>
    createPluginDescriptor({ id, type: 'PACKAGE_CURATION_PROVIDER' })
  ),
};

const renderSelection = (props: Partial<Props> = {}) => {
  const { result, rerender } = renderHook(
    (hookProps: Props) => useDefaultPluginsSelection(hookProps),
    { initialProps: { ...defaultProps, ...props } }
  );

  return {
    selection: () => result.current,
    shownIds: () =>
      result.current.pluginsInDisplayOrder.map((plugin) => plugin.id),
    rerender: (newProps: Partial<Props>) =>
      rerender({ ...defaultProps, ...props, ...newProps }),
  };
};

describe('useDefaultPluginsSelection', () => {
  it('keeps the order of moved plugins that are not selected', () => {
    const { selection, shownIds } = renderSelection();

    act(() => selection().reorder(2, 0));

    expect(shownIds()).toEqual(['C', 'A', 'B']);
    expect(selection().hasChanges).toBe(false);
  });

  it('selects plugins in the order they were arranged before', () => {
    const { selection, shownIds } = renderSelection();

    act(() => selection().reorder(2, 0));
    act(() => selection().setSelected('A', true));
    act(() => selection().setSelected('C', true));

    expect(shownIds()).toEqual(['C', 'A', 'B']);
    expect(selection().selectedPluginIds).toEqual(['C', 'A']);
  });

  it('shows the defaults in their original order after discarding', () => {
    const { selection, shownIds } = renderSelection();

    act(() => selection().reorder(2, 0));
    act(() => selection().discard());

    expect(shownIds()).toEqual(['A', 'B', 'C']);
  });

  it('shows changed defaults in their order even if the plugins were moved', () => {
    const { selection, shownIds, rerender } = renderSelection({
      defaultPluginIds: ['A', 'C'],
    });

    act(() => selection().reorder(2, 0));
    rerender({ defaultPluginIds: ['C', 'A'] });

    expect(shownIds()).toEqual(['C', 'A', 'B']);
    expect(selection().selectedPluginIds).toEqual(['C', 'A']);
  });
});
