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

import { QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { PluginDescriptor, PluginTemplate } from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { queryClient } from '@/lib/query-client';
import { Route } from '@/routes/admin/plugins/$pluginType/$pluginId/index';
import {
  createTestRouter,
  RouterTestProvider,
} from '../fixtures/router-harness';
import { pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getInstalledPlugins: vi.fn(),
  getPluginTemplates: vi.fn(),
  getOrganizations: vi.fn(),
  getOrganization: vi.fn(),
  enableGlobalPluginTemplate: vi.fn(),
  disableGlobalPluginTemplate: vi.fn(),
  deletePluginTemplate: vi.fn(),
  addTemplateToOrganization: vi.fn(),
  removeTemplateFromOrganization: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: vi.fn(), error: vi.fn() },
  toastError: vi.fn(),
}));

const plugin: PluginDescriptor = {
  id: 'VulnerableCode',
  type: 'ADVISOR',
  displayName: 'VulnerableCode',
  summary: 'An advisor',
  availability: 'ENABLED',
  options: [],
};

const createTemplate = (
  name: string,
  overrides: Partial<PluginTemplate>
): PluginTemplate => ({
  name,
  pluginType: 'ADVISOR',
  pluginId: 'VulnerableCode',
  isGlobal: false,
  options: [],
  ...overrides,
});

const templates = [
  createTemplate('Global', { isGlobal: true, organizationIds: [1, 2] }),
  createTemplate('Local', { organizationIds: [] }),
];

const organizationName = (id: number) => `Organization ${id}`;

const templatePath = (templateName: string) => ({
  pluginType: 'ADVISOR',
  pluginId: 'VulnerableCode',
  templateName,
});

const templateMutations = [
  mocks.enableGlobalPluginTemplate,
  mocks.disableGlobalPluginTemplate,
  mocks.deletePluginTemplate,
  mocks.addTemplateToOrganization,
  mocks.removeTemplateFromOrganization,
];

// The page reads the templates through the application's query client, so it is rendered with
// that client instead of one of its own.
const renderTemplates = async () => {
  const router = createTestRouter({
    path: '/admin/plugins/ADVISOR/VulnerableCode',
    routes: [
      {
        path: '/admin/plugins/$pluginType/$pluginId/',
        component: Route.options.component,
      },
    ],
  });
  const user = userEvent.setup();

  render(
    <QueryClientProvider client={queryClient}>
      <TooltipProvider>
        <RouterTestProvider router={router} />
      </TooltipProvider>
    </QueryClientProvider>
  );
  await screen.findByText('VulnerableCode Templates (2)');

  return { user };
};

// The card of the template with the given name.
const templateCard = (name: string) =>
  within(
    screen
      .getByText(name, { selector: '[data-slot="card-title"]' })
      .closest<HTMLElement>('[data-slot="card"]')!
  );

const expectTemplatesFetchedAgain = () =>
  waitFor(() => expect(mocks.getPluginTemplates).toHaveBeenCalledTimes(2));

describe('plugin templates page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // The organization selector asks for the next page when its end scrolls into view.
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        observe() {}
        disconnect() {}
      }
    );
    mocks.getInstalledPlugins.mockResolvedValue({ data: [plugin] });

    mocks.getPluginTemplates.mockResolvedValue({ data: templates });
    mocks.getOrganizations.mockResolvedValue(
      pageOf(
        [1, 2, 3].map((id) => ({ id, name: organizationName(id) })),
        { limit: 20 }
      )
    );
    mocks.getOrganization.mockImplementation(
      async ({ path }: { path: { organizationId: number } }) => ({
        data: {
          id: path.organizationId,
          name: organizationName(path.organizationId),
        },
      })
    );
    for (const mutation of templateMutations) {
      mutation.mockResolvedValue({ data: undefined });
    }
  });

  afterEach(() => {
    queryClient.clear();
    vi.unstubAllGlobals();
  });

  it.each([
    {
      name: 'Global',
      action: 'disables',
      mutation: mocks.disableGlobalPluginTemplate,
    },
    {
      name: 'Local',
      action: 'enables',
      mutation: mocks.enableGlobalPluginTemplate,
    },
  ])('$action the template $name globally', async ({ name, mutation }) => {
    const { user } = await renderTemplates();

    await user.click(templateCard(name).getByRole('switch'));

    await expectTemplatesFetchedAgain();
    expect(mutation).toHaveBeenCalledOnce();
    expect(mutation).toHaveBeenCalledWith(
      expect.objectContaining({ path: templatePath(name) })
    );
  });

  it('deletes a template', async () => {
    const { user } = await renderTemplates();

    await user.click(
      templateCard('Local').getByRole('button', { name: 'Delete' })
    );
    const dialog = await screen.findByRole('alertdialog');
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }));

    await expectTemplatesFetchedAgain();
    expect(mocks.deletePluginTemplate).toHaveBeenCalledOnce();
    expect(mocks.deletePluginTemplate).toHaveBeenCalledWith(
      expect.objectContaining({ path: templatePath('Local') })
    );
  });

  it('assigns a template to an organization', async () => {
    const { user } = await renderTemplates();

    await user.click(templateCard('Global').getByRole('combobox'));
    await user.click(
      await screen.findByRole('option', { name: organizationName(3) })
    );

    await expectTemplatesFetchedAgain();
    expect(mocks.addTemplateToOrganization).toHaveBeenCalledOnce();
    expect(mocks.addTemplateToOrganization).toHaveBeenCalledWith(
      expect.objectContaining({
        path: templatePath('Global'),
        query: { organizationId: 3 },
      })
    );
    expect(mocks.removeTemplateFromOrganization).not.toHaveBeenCalled();
  });

  it('removes a template from an organization', async () => {
    const { user } = await renderTemplates();

    const badge = await templateCard('Global').findByText(organizationName(1));
    await user.click(within(badge).getByRole('button'));

    await expectTemplatesFetchedAgain();
    expect(mocks.removeTemplateFromOrganization).toHaveBeenCalledOnce();
    expect(mocks.removeTemplateFromOrganization).toHaveBeenCalledWith(
      expect.objectContaining({
        path: templatePath('Global'),
        query: { organizationId: 1 },
      })
    );
    expect(mocks.addTemplateToOrganization).not.toHaveBeenCalled();
  });
});
