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
import { describe, expect, it, vi } from 'vitest';

import type {
  DefaultPlugins,
  PluginType,
  PreconfiguredPluginDescriptor,
} from '@/api';
import { DefaultPluginsCard } from '@/components/default-plugins/default-plugins-card';
import { createPluginDescriptor } from '../fixtures/create-run';

const pluginA = createPluginDescriptor({
  id: 'A',
  type: 'PACKAGE_CURATION_PROVIDER',
  displayName: 'Plugin A',
});
const pluginB = createPluginDescriptor({
  id: 'B',
  type: 'PACKAGE_CURATION_PROVIDER',
  displayName: 'Plugin B',
});
const pluginC = createPluginDescriptor({
  id: 'C',
  type: 'PACKAGE_CURATION_PROVIDER',
  displayName: 'Plugin C',
});

const renderCard = ({
  defaults = {
    category: 'PACKAGE_CURATION_PROVIDER',
    pluginIds: ['B'],
    source: 'GLOBAL',
  },
  pluginType = 'PACKAGE_CURATION_PROVIDER',
  availablePlugins = [pluginA, pluginB, pluginC],
  isOrdered = true,
}: {
  defaults?: DefaultPlugins;
  pluginType?: PluginType;
  availablePlugins?: PreconfiguredPluginDescriptor[];
  isOrdered?: boolean;
} = {}) => {
  const onSave = vi.fn();
  const onReset = vi.fn();

  render(
    <DefaultPluginsCard
      title='Package Curation Providers'
      description='The default package curation providers.'
      pluginType={pluginType}
      isOrdered={isOrdered}
      level='GLOBAL'
      defaults={defaults}
      availablePlugins={availablePlugins}
      isPending={false}
      onSave={onSave}
      onReset={onReset}
    />
  );

  return { onSave, onReset };
};

const checkbox = (displayName: string) =>
  screen.getByRole('checkbox', { name: `Enable ${displayName}` });

const pluginNames = () =>
  screen
    .getAllByRole('checkbox')
    .map((element) => element.getAttribute('aria-label'));

describe('DefaultPluginsCard', () => {
  it('shows the category the defaults fall back to and does not treat them as customized', () => {
    renderCard({
      defaults: {
        category: 'PROJECT_SCANNER',
        pluginIds: ['B'],
        source: 'GLOBAL',
        fallbackCategory: 'SCANNER',
      },
    });

    expect(screen.getByText('Same as Global Package Scanners')).toBeVisible();
    expect(
      screen.getByRole('button', { name: 'Reset to Built-in Defaults' })
    ).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
  });

  it('shows the selected plugins first if the order matters', () => {
    renderCard();

    expect(pluginNames()).toEqual([
      'Enable Plugin B',
      'Enable Plugin A',
      'Enable Plugin C',
    ]);
    expect(checkbox('Plugin B')).toBeChecked();
    expect(checkbox('Plugin A')).not.toBeChecked();
  });

  it('keeps the original order for plugin types whose order does not matter', () => {
    renderCard({ isOrdered: false });

    expect(pluginNames()).toEqual([
      'Enable Plugin A',
      'Enable Plugin B',
      'Enable Plugin C',
    ]);
    expect(checkbox('Plugin B')).toBeChecked();
  });

  it('saves the selected plugins in the original order if the order does not matter', async () => {
    const user = userEvent.setup();
    const { onSave } = renderCard({ isOrdered: false });

    await user.click(checkbox('Plugin C'));
    await user.click(checkbox('Plugin A'));

    expect(pluginNames()).toEqual([
      'Enable Plugin A',
      'Enable Plugin B',
      'Enable Plugin C',
    ]);

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(onSave).toHaveBeenCalledWith(['A', 'B', 'C']);
  });

  it('saves the selected plugins in display order without moving them', async () => {
    const user = userEvent.setup();
    const { onSave } = renderCard();

    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

    await user.click(checkbox('Plugin C'));
    await user.click(checkbox('Plugin A'));

    expect(pluginNames()).toEqual([
      'Enable Plugin B',
      'Enable Plugin A',
      'Enable Plugin C',
    ]);

    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(onSave).toHaveBeenCalledWith(['B', 'A', 'C']);
  });

  it('discards the changes', async () => {
    const user = userEvent.setup();
    renderCard();

    await user.click(checkbox('Plugin A'));
    await user.click(screen.getByRole('button', { name: 'Discard Changes' }));

    expect(checkbox('Plugin A')).not.toBeChecked();
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
  });

  it('resets customized defaults', async () => {
    const user = userEvent.setup();
    const { onReset } = renderCard();

    expect(screen.getByText('Customized')).toBeInTheDocument();

    await user.click(
      screen.getByRole('button', { name: 'Reset to Built-in Defaults' })
    );

    expect(onReset).toHaveBeenCalled();
  });

  it('allows to save inherited defaults, but not to reset them', () => {
    renderCard({
      defaults: {
        category: 'PACKAGE_CURATION_PROVIDER',
        pluginIds: [],
        source: 'BUILT_IN',
      },
    });

    expect(screen.getByText('Built-in defaults')).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Reset to Built-in Defaults' })
    ).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
  });

  it('shows configured plugins that are not available', () => {
    renderCard({
      defaults: {
        category: 'PACKAGE_CURATION_PROVIDER',
        pluginIds: ['Removed'],
        source: 'GLOBAL',
      },
    });

    expect(checkbox('Removed')).toBeChecked();
    expect(
      screen.getByText(/This plugin is not available\./)
    ).toBeInTheDocument();
  });

  it('matches configured plugin ids case-insensitively', async () => {
    const user = userEvent.setup();
    const webApp = createPluginDescriptor({
      id: 'webApp',
      type: 'REPORTER',
      displayName: 'Web App Definition',
    });
    const { onSave } = renderCard({
      defaults: {
        category: 'REPORTER',
        pluginIds: ['WebApp'],
        source: 'GLOBAL',
      },
      pluginType: 'REPORTER',
      availablePlugins: [webApp],
      isOrdered: false,
    });

    expect(pluginNames()).toEqual(['Enable Web App Definition']);
    expect(checkbox('Web App Definition')).toBeChecked();
    expect(
      screen.queryByText(/This plugin is not available\./)
    ).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

    await user.click(checkbox('Web App Definition'));
    await user.click(checkbox('Web App Definition'));
    expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

    await user.click(checkbox('Web App Definition'));
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(onSave).toHaveBeenCalledWith([]);
  });

  it('allows to reorder only plugin types whose order matters', () => {
    renderCard({ isOrdered: false });

    expect(
      screen.queryByRole('button', { name: 'Reorder A' })
    ).not.toBeInTheDocument();
  });

  it('shows drag handles for plugin types whose order matters', () => {
    renderCard();

    expect(
      screen.getByRole('button', { name: 'Reorder A' })
    ).toBeInTheDocument();
  });
});
