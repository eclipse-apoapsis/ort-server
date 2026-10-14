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

import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { DefaultPlugins, DefaultPluginsCategory } from '@/api';
import { DefaultPluginsPage } from '@/components/default-plugins';
import { toastError } from '@/lib/toast';
import { createPluginDescriptor } from '../fixtures/create-run';

vi.mock('@/lib/toast', () => ({
  toast: { info: vi.fn() },
  toastError: vi.fn(),
}));

const availablePlugins = [
  createPluginDescriptor({
    id: 'ScanCode',
    type: 'SCANNER',
    displayName: 'ScanCode',
  }),
  createPluginDescriptor({
    id: 'Other',
    type: 'SCANNER',
    displayName: 'Other',
  }),
  createPluginDescriptor({
    id: 'Curations',
    type: 'PACKAGE_CURATION_PROVIDER',
    displayName: 'Curations',
  }),
];

/** A request that is answered when the test resolves it. */
type PendingRequest = { resolve: () => void };

/**
 * A fake backend that stores the global defaults. If `deferRequests` is true, requests to change
 * the defaults are only answered when the test resolves them.
 */
class FakeBackend {
  readonly requests: PendingRequest[] = [];

  /** Whether reading the defaults fails. */
  failReads = false;

  private readonly configured: Partial<
    Record<DefaultPluginsCategory, string[]>
  > = {
    SCANNER: ['ScanCode'],
    PACKAGE_CURATION_PROVIDER: ['Curations'],
  };

  constructor(private readonly deferRequests: boolean) {}

  getDefaults(): DefaultPlugins[] {
    if (this.failReads) throw new Error('The defaults are not available.');

    const { PACKAGE_CURATION_PROVIDER, PROJECT_SCANNER, SCANNER } =
      this.configured;

    return [
      {
        category: 'PACKAGE_CURATION_PROVIDER',
        pluginIds: PACKAGE_CURATION_PROVIDER ?? [],
        source: PACKAGE_CURATION_PROVIDER ? 'GLOBAL' : 'BUILT_IN',
      },
      { category: 'SCANNER', pluginIds: SCANNER ?? [], source: 'GLOBAL' },
      PROJECT_SCANNER
        ? {
            category: 'PROJECT_SCANNER',
            pluginIds: PROJECT_SCANNER,
            source: 'GLOBAL',
          }
        : {
            category: 'PROJECT_SCANNER',
            pluginIds: SCANNER ?? [],
            source: 'GLOBAL',
            fallbackCategory: 'SCANNER',
          },
    ];
  }

  setDefaults(category: DefaultPluginsCategory, pluginIds: string[]) {
    return this.respond(() => {
      this.configured[category] = pluginIds;
    });
  }

  resetDefaults(category: DefaultPluginsCategory) {
    return this.respond(() => {
      delete this.configured[category];
    });
  }

  private respond(update: () => void) {
    return new Promise<void>((resolve) => {
      const answer = () => {
        update();
        resolve();
      };

      if (this.deferRequests) {
        this.requests.push({ resolve: answer });
      } else {
        answer();
      }
    });
  }
}

const Page = ({ backend }: { backend: FakeBackend }) => {
  // The key has the shape of the generated query keys, so that saving the defaults invalidates it.
  const { data: defaults } = useQuery({
    queryKey: [{ _id: 'getGlobalDefaultPlugins' }],
    queryFn: () => backend.getDefaults(),
  });

  if (!defaults) return null;

  return (
    <DefaultPluginsPage
      level='GLOBAL'
      description='The global default plugins.'
      defaults={defaults}
      availablePlugins={availablePlugins}
      setDefaults={(category, pluginIds) =>
        backend.setDefaults(category, pluginIds)
      }
      resetDefaults={(category) => backend.resetDefaults(category)}
    />
  );
};

const renderPage = ({ deferRequests = false } = {}) => {
  const backend = new FakeBackend(deferRequests);
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <Page backend={backend} />
    </QueryClientProvider>
  );

  return { backend, requests: backend.requests };
};

/** Get the card with the given title. */
const card = (title: string) => {
  let element: HTMLElement | null = screen.getByText(title);

  while (
    element &&
    !within(element).queryByRole('button', { name: 'Discard Changes' })
  ) {
    element = element.parentElement;
  }

  return within(element!);
};

const checkbox = (cardTitle: string, displayName: string) =>
  card(cardTitle).getByRole('checkbox', { name: `Enable ${displayName}` });

describe('DefaultPluginsPage', () => {
  it('keeps local changes of a category when the defaults it falls back to are saved', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Project Scanners');

    await user.click(checkbox('Project Scanners', 'Other'));

    await user.click(checkbox('Package Scanners', 'ScanCode'));
    await user.click(checkbox('Package Scanners', 'Other'));
    await user.click(
      card('Package Scanners').getByRole('button', { name: 'Save' })
    );

    // The saved defaults are shown without local changes once the request is finished.
    await waitFor(() =>
      expect(
        card('Package Scanners').getByRole('button', {
          name: 'Discard Changes',
        })
      ).toBeDisabled()
    );
    expect(checkbox('Package Scanners', 'ScanCode')).not.toBeChecked();
    expect(checkbox('Package Scanners', 'Other')).toBeChecked();
    expect(checkbox('Project Scanners', 'ScanCode')).toBeChecked();
    expect(checkbox('Project Scanners', 'Other')).toBeChecked();
  });

  it('keeps a category pending until its own request is finished', async () => {
    const user = userEvent.setup();
    const { requests } = renderPage({ deferRequests: true });
    await screen.findByText('Package Scanners');

    await user.click(checkbox('Package Scanners', 'Other'));
    await user.click(
      card('Package Scanners').getByRole('button', { name: 'Save' })
    );
    await user.click(checkbox('Package Curation Providers', 'Curations'));
    await user.click(
      card('Package Curation Providers').getByRole('button', { name: 'Save' })
    );

    expect(checkbox('Package Scanners', 'Other')).toBeDisabled();
    expect(checkbox('Package Curation Providers', 'Curations')).toBeDisabled();

    requests[1]!.resolve();

    await waitFor(() =>
      expect(checkbox('Package Curation Providers', 'Curations')).toBeEnabled()
    );
    expect(checkbox('Package Scanners', 'Other')).toBeDisabled();

    requests[0]!.resolve();

    await waitFor(() =>
      expect(checkbox('Package Scanners', 'Other')).toBeEnabled()
    );
    expect(checkbox('Package Scanners', 'Other')).toBeChecked();
  });

  it('keeps the defaults and local changes if refreshing the defaults fails', async () => {
    const user = userEvent.setup();
    const { backend } = renderPage();
    await screen.findByText('Project Scanners');

    await user.click(checkbox('Project Scanners', 'Other'));

    backend.failReads = true;
    await user.click(checkbox('Package Curation Providers', 'Curations'));
    await user.click(
      card('Package Curation Providers').getByRole('button', { name: 'Save' })
    );

    // The card is pending until the defaults were refreshed, which failed here.
    await waitFor(() =>
      expect(checkbox('Package Curation Providers', 'Curations')).toBeEnabled()
    );
    expect(toastError).toHaveBeenCalledWith(
      'The defaults are not available.',
      expect.any(Error)
    );
    // The saved card keeps its changes instead of showing the outdated defaults.
    expect(
      checkbox('Package Curation Providers', 'Curations')
    ).not.toBeChecked();
    expect(checkbox('Project Scanners', 'ScanCode')).toBeChecked();
    expect(checkbox('Project Scanners', 'Other')).toBeChecked();
  });
});
