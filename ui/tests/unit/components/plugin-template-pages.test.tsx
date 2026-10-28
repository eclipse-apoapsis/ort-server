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
import type { RouteComponent } from '@tanstack/react-router';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type {
  PluginDescriptor,
  PluginOption,
  PluginOptionTemplate,
  PluginOptionType,
  PluginTemplate,
} from '@/api';
import { getPluginTemplatesQueryKey } from '@/api/@tanstack/react-query.gen';
import { queryClient } from '@/lib/query-client';
import { Route as CreateTemplateRoute } from '@/routes/admin/plugins/$pluginType/$pluginId/create-template/index';
import { Route as EditTemplateRoute } from '@/routes/admin/plugins/$pluginType/$pluginId/edit-template/$templateName/index';
import {
  createTestRouter,
  RouterTestProvider,
} from '../fixtures/router-harness';

const mocks = vi.hoisted(() => ({
  getInstalledPlugins: vi.fn(),
  getPluginTemplate: vi.fn(),
  createPluginTemplate: vi.fn(),
  updatePluginTemplateOptions: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  ...mocks,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: vi.fn(), error: vi.fn() },
  toastError: vi.fn(),
}));

const optionTypes: PluginOptionType[] = [
  'BOOLEAN',
  'ENUM',
  'ENUM_LIST',
  'INTEGER',
  'LONG',
  'SECRET',
  'STRING',
  'STRING_LIST',
];

const optionName = (type: PluginOptionType) => `${type.toLowerCase()}Option`;

const options: PluginOption[] = optionTypes.map((type) => ({
  name: optionName(type),
  type,
  description: `The ${type} option`,
  enumEntries: type === 'ENUM' || type === 'ENUM_LIST' ? ['a', 'b'] : null,
  isNullable: false,
  isRequired: true,
}));

const plugin: PluginDescriptor = {
  id: 'VulnerableCode',
  type: 'ADVISOR',
  displayName: 'VulnerableCode',
  summary: 'An advisor',
  availability: 'ENABLED',
  options,
};

// A stored value for every option type, as the server returns them. A long value is too large
// for a number without losing digits.
const storedValues: Record<PluginOptionType, string> = {
  BOOLEAN: 'true',
  ENUM: 'b',
  ENUM_LIST: 'a, ,b',
  INTEGER: '7',
  LONG: '9007199254740993',
  SECRET: 'secret-name',
  STRING: 'text',
  STRING_LIST: 'first,second',
};

const storedOptions: PluginOptionTemplate[] = optionTypes.map(
  (type, index) => ({
    option: optionName(type),
    type,
    value: storedValues[type],
    isFinal: index % 2 === 0,
  })
);

const template: PluginTemplate = {
  name: 'Default',
  pluginType: 'ADVISOR',
  pluginId: 'VulnerableCode',
  isGlobal: false,
  options: storedOptions,
};

const pluginPath = {
  pluginType: 'ADVISOR' as const,
  pluginId: 'VulnerableCode',
};
const templatesKey = getPluginTemplatesQueryKey({ path: pluginPath });

// The page the pages navigate to, which the tests only need to exist.
const Destination = () => <div>Destination</div>;

// The pages refresh the cache through the application's query client, so they are rendered with
// that client instead of one of their own.
const renderPage = async (
  Page: RouteComponent,
  { path, pagePath }: { path: string; pagePath: string }
) => {
  const router = createTestRouter({
    path,
    routes: [
      { path: pagePath, component: Page },
      { path: '/admin/plugins/$pluginType/$pluginId', component: Destination },
    ],
  });
  const user = userEvent.setup();

  render(
    <QueryClientProvider client={queryClient}>
      <RouterTestProvider router={router} />
    </QueryClientProvider>
  );
  await screen.findByRole('button', { name: /Template$/ });

  return { router, user };
};

const renderEditTemplate = () =>
  renderPage(EditTemplateRoute.options.component!, {
    path: '/admin/plugins/ADVISOR/VulnerableCode/edit-template/Default',
    pagePath:
      '/admin/plugins/$pluginType/$pluginId/edit-template/$templateName/',
  });

const renderCreateTemplate = () =>
  renderPage(CreateTemplateRoute.options.component!, {
    path: '/admin/plugins/ADVISOR/VulnerableCode/create-template',
    pagePath: '/admin/plugins/$pluginType/$pluginId/create-template/',
  });

// The fields of one option: its value, and the checkboxes that make it final or undefined.
const optionFields = (type: PluginOptionType) =>
  within(
    screen
      .getByText(optionName(type))
      .closest<HTMLElement>('[data-slot="form-item"]')!
  );

const expectRefreshedTemplates = async (
  router: Awaited<ReturnType<typeof renderPage>>['router']
) => {
  await waitFor(() =>
    expect(router.state.location.pathname).toBe(
      '/admin/plugins/ADVISOR/VulnerableCode'
    )
  );
  expect(queryClient.getQueryState(templatesKey)?.isInvalidated).toBe(true);
};

describe('plugin template pages', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getInstalledPlugins.mockResolvedValue({ data: [plugin] });

    mocks.getPluginTemplate.mockResolvedValue({ data: template });
    mocks.createPluginTemplate.mockResolvedValue({ data: template });
    mocks.updatePluginTemplateOptions.mockResolvedValue({ data: template });

    queryClient.setQueryData(templatesKey, [template]);
  });

  afterEach(() => {
    queryClient.clear();
  });

  describe('editing', () => {
    it('saves the stored value of every option type unchanged', async () => {
      const { router, user } = await renderEditTemplate();

      await user.click(screen.getByRole('button', { name: 'Update Template' }));

      await expectRefreshedTemplates(router);
      expect(mocks.updatePluginTemplateOptions).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { ...pluginPath, templateName: 'Default' },
          body: storedOptions.map((option) =>
            // Empty entries of an enum list are dropped.
            option.type === 'ENUM_LIST' ? { ...option, value: 'a,b' } : option
          ),
        })
      );
    });

    it('does not send an option that is marked undefined', async () => {
      const { router, user } = await renderEditTemplate();

      await user.click(
        optionFields('STRING').getByRole('checkbox', { name: 'Undefined' })
      );
      await user.click(screen.getByRole('button', { name: 'Update Template' }));

      await expectRefreshedTemplates(router);
      const [{ body }] = mocks.updatePluginTemplateOptions.mock.calls[0]!;
      expect(body.map((option: PluginOptionTemplate) => option.option)).toEqual(
        storedOptions
          .map((option) => option.option)
          .filter((name) => name !== optionName('STRING'))
      );
    });
  });

  describe('creating', () => {
    it('sends the name and only the options that are set', async () => {
      const { router, user } = await renderCreateTemplate();

      await user.type(screen.getByLabelText('Template Name'), 'Default');
      await user.type(optionFields('STRING').getByRole('textbox'), 'text');
      await user.click(screen.getByRole('button', { name: 'Create Template' }));

      await expectRefreshedTemplates(router);
      expect(mocks.createPluginTemplate).toHaveBeenCalledWith(
        expect.objectContaining({
          path: { ...pluginPath, templateName: 'Default' },
          body: [
            {
              option: optionName('STRING'),
              type: 'STRING',
              value: 'text',
              isFinal: false,
            },
          ],
        })
      );
    });
  });
});
