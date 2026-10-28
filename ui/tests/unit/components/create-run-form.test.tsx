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

import { cleanup, screen, waitFor, within } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import type { OrtRun, PreconfiguredPluginDescriptor } from '@/api';
import {
  CreateRunForm,
  type CreateRunFormProps,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/create-run-form';
import { getProjectSuggestions } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/project-suggestions';
import {
  createDefaultPlugins,
  createOrtRun,
  createPermissions,
  createPluginDescriptor,
  createPluginSecrets,
  createProject,
} from '../fixtures/create-run';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

const advisorPlugin = createPluginDescriptor({
  id: 'OSV',
  type: 'ADVISOR',
  displayName: 'OSV',
  options: [
    {
      name: 'serverUrl',
      type: 'STRING',
      defaultValue: '',
      description: 'The OSV server URL.',
      isFixed: false,
      isNullable: false,
      isRequired: true,
    },
  ],
});

const scannerPlugin = createPluginDescriptor({
  id: 'ScanCode',
  type: 'SCANNER',
  displayName: 'ScanCode',
});
const packageManagerPlugins = [
  createPluginDescriptor({
    id: 'Maven',
    type: 'PACKAGE_MANAGER',
    displayName: 'Maven',
  }),
  createPluginDescriptor({
    id: 'NPM',
    type: 'PACKAGE_MANAGER',
    displayName: 'NPM',
  }),
  createPluginDescriptor({
    id: 'Gradle',
    type: 'PACKAGE_MANAGER',
    displayName: 'Gradle Legacy',
  }),
  // The 'Unmanaged' package manager is always enabled and must not be selectable.
  createPluginDescriptor({
    id: 'Unmanaged',
    type: 'PACKAGE_MANAGER',
    displayName: 'Unmanaged',
  }),
];
const defaultPlugins = createDefaultPlugins();
const permissions = createPermissions();
const rerun = createOrtRun({ revision: '', path: '', jobConfigs: {} });
const secrets = createPluginSecrets();
const createRunPath =
  '/organizations/1/products/2/repositories/3/create-run' as const;

const renderCreateRunForm = (
  onSubmit = vi.fn(),
  props: Partial<CreateRunFormProps> = {}
) => ({
  onSubmit,
  ...renderInteractiveWithRouter(
    <CreateRunForm
      defaultPlugins={defaultPlugins}
      isSubmitting={false}
      isSuperuser={false}
      onSubmit={onSubmit}
      permissions={permissions}
      plugins={[advisorPlugin, scannerPlugin, ...packageManagerPlugins]}
      rerun={rerun}
      secrets={secrets}
      {...props}
    />,
    {
      path: createRunPath,
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/create-run',
        },
      ],
    }
  ),
});

const renderSwappableForm = (
  initial: PreconfiguredPluginDescriptor[],
  next: PreconfiguredPluginDescriptor[],
  onSubmit = vi.fn(),
  run: OrtRun | null = rerun,
  initiallyLoading = false,
  onConfigContextChange?: (context: string) => void
) => {
  const Harness = () => {
    const [plugins, setPlugins] = useState(initial);
    const [loading, setLoading] = useState(initiallyLoading);
    const [tick, setTick] = useState(0);
    return (
      <>
        <button
          onClick={() => {
            setPlugins(next);
            setLoading(false);
          }}
        >
          Swap plugins
        </button>
        <button onClick={() => setTick(tick + 1)}>Unrelated render</button>
        <CreateRunForm
          defaultPlugins={defaultPlugins}
          isSubmitting={false}
          isSuperuser={false}
          onSubmit={onSubmit}
          onConfigContextChange={onConfigContextChange}
          permissions={permissions}
          plugins={plugins}
          pluginsLoading={loading}
          rerun={run}
          secrets={secrets}
        />
      </>
    );
  };
  return {
    onSubmit,
    ...renderInteractiveWithRouter(<Harness />, {
      path: createRunPath,
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/create-run',
        },
      ],
    }),
  };
};

const enabledPackageManagers = ['Maven', 'NPM', 'Unmanaged'];

const showPayload = async (
  user: ReturnType<typeof renderCreateRunForm>['user']
) => {
  await user.click(await screen.findByRole('switch', { name: 'Show payload' }));
  return screen.getByLabelText(
    'You can copy this payload to use it when triggering runs via the API or CLI.'
  ) as HTMLTextAreaElement;
};

const preview = (textarea: HTMLTextAreaElement) => JSON.parse(textarea.value);

const getJobSwitch = (job: string) => {
  const trigger = screen.getByRole('button', { name: job });
  const row = trigger.closest('[data-slot="accordion-item"]')!.parentElement!;

  return within(row).getByRole('switch');
};

describe('CreateRunForm', () => {
  // The form takes the plugin defaults as `values` and keeps dirty values when
  // they change, which React Hook Form reports as fragile under React Compiler.
  // The tests below run on the compiled form.

  it('previews the complete defaults for new runs and reruns', async () => {
    const { user: newRunUser } = renderSwappableForm(
      [advisorPlugin, scannerPlugin, ...packageManagerPlugins],
      [],
      vi.fn(),
      null
    );
    const newRunPayload = await showPayload(newRunUser);
    expect(preview(newRunPayload)).toMatchObject({
      revision: '',
      path: '',
      jobConfigContext: '',
      jobConfigs: {
        analyzer: { enabledPackageManagers },
      },
    });

    cleanup();
    // Render a rerun with distinct defaults in the same harness.
    const run = createOrtRun({
      revision: 'release',
      path: 'module',
      jobConfigContext: 'config-ref',
    });
    const { user: rerunUser } = renderSwappableForm(
      [advisorPlugin, scannerPlugin, ...packageManagerPlugins],
      [],
      vi.fn(),
      run
    );
    const rerunPayload = await showPayload(rerunUser);
    expect(preview(rerunPayload)).toMatchObject({
      revision: 'release',
      path: 'module',
      jobConfigContext: 'config-ref',
      jobConfigs: { analyzer: { enabledPackageManagers } },
    });
  });

  it('updates the preview and clipboard for edited fields and nested values', async () => {
    const writeText = vi.spyOn(navigator.clipboard, 'writeText');
    const { user } = renderCreateRunForm();
    const payload = await showPayload(user);

    await user.type(screen.getByLabelText('Revision'), 'feature');
    await user.click(getJobSwitch('Advisor'));
    await user.click(screen.getByRole('button', { name: 'Advisor' }));
    await user.type(screen.getByLabelText(/serverUrl/), 'https://osv.dev');
    await user.click(screen.getByRole('button', { name: 'Add parameter' }));
    await user.type(screen.getAllByLabelText('Key').at(-1)!, 'branch');
    await user.type(screen.getAllByLabelText('Value').at(-1)!, 'feature');
    await user.click(screen.getByRole('button', { name: 'Add label' }));
    await user.type(screen.getAllByLabelText('Key').at(-1)!, 'team');
    await user.type(screen.getAllByLabelText('Value').at(-1)!, 'ort');

    expect(preview(payload)).toMatchObject({
      revision: 'feature',
      labels: { team: 'ort' },
      jobConfigs: {
        parameters: { branch: 'feature' },
        advisor: {
          config: { OSV: { options: { serverUrl: 'https://osv.dev' } } },
        },
      },
    });
    await user.click(screen.getByRole('button', { name: 'Copy to clipboard' }));
    expect(writeText).toHaveBeenCalledWith(payload.value);
  }, 10_000);

  it('previews refreshed plugin defaults while preserving edits and pruning removed plugins', async () => {
    const changedOsv = createPluginDescriptor({
      ...advisorPlugin,
      options: [
        { ...advisorPlugin.options[0], defaultValue: 'https://new.example' },
      ],
    });
    const { user } = renderSwappableForm(
      [advisorPlugin, scannerPlugin, packageManagerPlugins[0]!],
      [changedOsv, ...packageManagerPlugins]
    );
    const payload = await showPayload(user);
    await user.type(screen.getByLabelText('Revision'), 'feature');
    await user.click(getJobSwitch('Advisor'));
    await user.click(getJobSwitch('Scanner'));
    expect(preview(payload).jobConfigs.scanner.scanners).toEqual(['ScanCode']);

    await user.click(screen.getByRole('button', { name: 'Swap plugins' }));
    await waitFor(() => {
      expect(preview(payload)).toMatchObject({
        revision: 'feature',
        jobConfigs: {
          analyzer: { enabledPackageManagers },
          advisor: { advisors: ['OSV'] },
          scanner: { skipConcluded: true },
        },
      });
    });
    expect(preview(payload).jobConfigs.scanner.scanners).toEqual([]);
    expect(preview(payload).jobConfigs.advisor).not.toHaveProperty('config');
    await user.click(screen.getByRole('button', { name: 'Advisor' }));
    await user.click(screen.getByRole('button', { name: /^OSV/ }));
    expect(screen.getByLabelText(/serverUrl/)).toHaveValue(
      'https://new.example'
    );
  });

  it('debounces configuration context changes, including after plugins reload', async () => {
    const onContextChange = vi.fn();
    const { user } = renderSwappableForm(
      [advisorPlugin, scannerPlugin],
      [advisorPlugin, scannerPlugin, ...packageManagerPlugins],
      vi.fn(),
      rerun,
      false,
      onContextChange
    );
    await waitFor(() => expect(onContextChange).toHaveBeenCalledWith(''));
    onContextChange.mockClear();

    await user.type(screen.getByLabelText('Configuration context'), 'first');
    await user.clear(screen.getByLabelText('Configuration context'));
    await user.type(screen.getByLabelText('Configuration context'), 'latest');
    expect(onContextChange).not.toHaveBeenCalled();
    await waitFor(() => expect(onContextChange).toHaveBeenCalledWith('latest'));
    expect(onContextChange).toHaveBeenCalledTimes(1);

    await user.click(screen.getByRole('button', { name: 'Swap plugins' }));
    onContextChange.mockClear();
    await user.clear(screen.getByLabelText('Configuration context'));
    await user.type(
      screen.getByLabelText('Configuration context'),
      'after-reload'
    );
    expect(onContextChange).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(onContextChange).toHaveBeenCalledWith('after-reload')
    );
    expect(onContextChange).toHaveBeenCalledTimes(1);
  });

  it('keeps basic inputs editable and blocks creation while plugins load', async () => {
    const loadedAdvisor = createPluginDescriptor({
      ...advisorPlugin,
      options: [
        { ...advisorPlugin.options[0], defaultValue: 'https://osv.dev' },
      ],
    });
    const { user, onSubmit } = renderSwappableForm(
      [],
      [loadedAdvisor, scannerPlugin, ...packageManagerPlugins],
      vi.fn(),
      rerun,
      true
    );
    const button = await screen.findByRole('button', {
      name: 'Loading available plugins...',
    });
    expect(button).toBeDisabled();
    expect(within(button).queryByText('Creating run...')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Create' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Advisor' })).toBeNull();
    expect(screen.getByRole('switch', { name: 'Show payload' })).toBeDisabled();

    await user.type(screen.getByLabelText('Revision'), 'feature');
    await user.click(screen.getByRole('button', { name: 'Swap plugins' }));
    expect(screen.getByLabelText('Revision')).toHaveValue('feature');
    expect(screen.queryByText('Loading available plugins...')).toBeNull();
    expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        revision: 'feature',
        jobConfigs: expect.objectContaining({
          analyzer: expect.objectContaining({ enabledPackageManagers }),
        }),
      })
    );
  });

  it('blocks creation if the plugins could not be loaded', async () => {
    const onSubmit = vi.fn();
    const { user } = renderInteractiveWithRouter(
      <CreateRunForm
        defaultPlugins={defaultPlugins}
        isSubmitting={false}
        isSuperuser={false}
        onSubmit={onSubmit}
        permissions={permissions}
        plugins={[]}
        pluginsLoadFailed
        rerun={rerun}
        secrets={secrets}
      />,
      {
        path: createRunPath,
        routes: [
          {
            path: '/organizations/$orgId/products/$productId/repositories/$repoId/create-run',
          },
        ],
      }
    );

    const button = await screen.findByRole('button', {
      name: 'The plugins could not be loaded',
    });
    expect(button).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Create' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Advisor' })).toBeNull();
    expect(screen.getByRole('switch', { name: 'Show payload' })).toBeDisabled();

    await user.type(screen.getByLabelText('Revision'), 'feature');
    await user.click(button);
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('submits the payload produced from entered form values', async () => {
    const { onSubmit, user } = renderCreateRunForm();

    await user.type(await screen.findByLabelText('Revision'), 'main');
    await user.type(screen.getByLabelText('Path'), 'src');
    await user.click(getJobSwitch('Advisor'));
    await user.click(screen.getByRole('button', { name: 'Advisor' }));
    await user.type(screen.getByLabelText(/serverUrl/), 'https://osv.dev');
    await user.click(getJobSwitch('Scanner'));
    await user.click(screen.getByRole('button', { name: 'Create' }));

    expect(onSubmit).toHaveBeenCalledOnce();
    expect(onSubmit).toHaveBeenCalledWith({
      revision: 'main',
      path: 'src',
      jobConfigs: {
        analyzer: {
          allowDynamicVersions: true,
          repositoryConfigPath: undefined,
          skipExcluded: true,
          enabledPackageManagers,
          packageManagerOptions: undefined,
          packageCurationProviders: [],
          keepAliveWorker: undefined,
          keepAlivePhases: undefined,
        },
        advisor: {
          skipExcluded: true,
          // Default plugins that are not available are not enabled.
          advisors: ['OSV'],
          config: {
            OSV: {
              options: { serverUrl: 'https://osv.dev' },
              secrets: {},
            },
          },
          keepAliveWorker: undefined,
        },
        scanner: {
          skipConcluded: true,
          skipExcluded: true,
          keepAliveWorker: undefined,
          scanners: ['ScanCode'],
          projectScanners: undefined,
          config: undefined,
        },
        evaluator: undefined,
        reporter: undefined,
        notifier: undefined,
        parameters: {},
      },
      labels: {},
      jobConfigContext: '',
      environmentConfigPath: '',
    });
  });

  it('shows validation errors without submitting an invalid form', async () => {
    const { onSubmit, user } = renderCreateRunForm();

    await user.click(await screen.findByRole('button', { name: 'Create' }));

    expect(
      await screen.findByText(
        'Required option "serverUrl" is missing for "OSV".'
      )
    ).toBeVisible();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('applies new plugin defaults without replacing user edits', async () => {
    const newAdvisor = createPluginDescriptor({
      id: 'NewAdvisor',
      type: 'ADVISOR',
      displayName: 'New Advisor',
      options: [
        {
          name: 'token',
          type: 'STRING',
          defaultValue: 'configured',
          description: '',
          isFixed: false,
          isNullable: false,
          isRequired: true,
        },
      ],
    });
    const changedOsv = createPluginDescriptor({
      ...advisorPlugin,
      options: [
        { ...advisorPlugin.options[0], defaultValue: 'https://new.example' },
      ],
    });
    const { user, onSubmit } = renderSwappableForm(
      [advisorPlugin, scannerPlugin, ...packageManagerPlugins],
      [changedOsv, newAdvisor, scannerPlugin, ...packageManagerPlugins]
    );
    await user.type(await screen.findByLabelText('Revision'), 'feature');
    await user.type(screen.getByLabelText('Configuration context'), 'ctx');
    await user.click(screen.getByRole('button', { name: 'Add label' }));
    await user.type(screen.getAllByLabelText('Key').at(-1)!, 'team');
    await user.type(screen.getAllByLabelText('Value').at(-1)!, 'ort');
    await user.click(getJobSwitch('Advisor'));
    await user.click(screen.getByRole('button', { name: 'Swap plugins' }));
    await user.click(screen.getByRole('button', { name: 'Advisor' }));
    // OSV has its default options, so they start collapsed.
    await user.click(screen.getByRole('button', { name: /^OSV/ }));
    expect(screen.getByLabelText(/serverUrl/)).toHaveValue(
      'https://new.example'
    );
    await user.click(
      screen.getByRole('checkbox', { name: 'Enable New Advisor' })
    );
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(onSubmit).toHaveBeenCalledOnce();
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        revision: 'feature',
        jobConfigContext: 'ctx',
        labels: { team: 'ort' },
        jobConfigs: expect.objectContaining({
          advisor: expect.objectContaining({
            advisors: expect.arrayContaining(['NewAdvisor']),
            config: undefined,
          }),
        }),
      })
    );
  });

  it('keeps typed option overrides and clears obsolete validation errors on a plugin change', async () => {
    const changed = createPluginDescriptor({
      ...advisorPlugin,
      options: [
        { ...advisorPlugin.options[0], defaultValue: 'https://new.example' },
      ],
    });
    const { user, onSubmit } = renderSwappableForm(
      [advisorPlugin, scannerPlugin],
      [changed, scannerPlugin]
    );
    await screen.findByRole('button', { name: 'Advisor' });
    await user.click(getJobSwitch('Advisor'));
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(
      await screen.findByText(
        'Required option "serverUrl" is missing for "OSV".'
      )
    ).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Unrelated render' }));
    expect(
      screen.getByText('Required option "serverUrl" is missing for "OSV".')
    ).toBeVisible();
    await user.click(screen.getByRole('button', { name: 'Swap plugins' }));
    expect(
      screen.queryByText('Required option "serverUrl" is missing for "OSV".')
    ).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it('preserves a typed option when its plugin default changes', async () => {
    const changed = createPluginDescriptor({
      ...advisorPlugin,
      options: [
        { ...advisorPlugin.options[0], defaultValue: 'https://new.example' },
      ],
    });
    const { user, onSubmit } = renderSwappableForm(
      [advisorPlugin, scannerPlugin],
      [changed, scannerPlugin]
    );
    await screen.findByRole('button', { name: 'Advisor' });
    await user.click(getJobSwitch('Advisor'));
    await user.click(screen.getByRole('button', { name: 'Advisor' }));
    await user.type(
      screen.getByLabelText(/serverUrl/),
      'https://custom.example'
    );
    await user.click(screen.getByRole('button', { name: 'Swap plugins' }));
    expect(screen.getByLabelText(/serverUrl/)).toHaveValue(
      'https://custom.example'
    );
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        jobConfigs: expect.objectContaining({
          advisor: expect.objectContaining({
            config: {
              OSV: {
                options: { serverUrl: 'https://custom.example' },
                secrets: {},
              },
            },
          }),
        }),
      })
    );
  });

  it('prunes unavailable base and dirty plugin selections and dependencies', async () => {
    const { user, onSubmit } = renderSwappableForm(
      [advisorPlugin, scannerPlugin, ...packageManagerPlugins],
      []
    );
    await screen.findByRole('button', { name: 'Advisor' });
    await user.click(getJobSwitch('Advisor'));
    await user.click(getJobSwitch('Scanner'));
    await user.click(
      await screen.findByRole('button', { name: 'Swap plugins' })
    );
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        jobConfigs: expect.objectContaining({
          analyzer: expect.objectContaining({
            enabledPackageManagers: ['Unmanaged'],
          }),
          advisor: expect.objectContaining({ advisors: [], config: undefined }),
          scanner: expect.objectContaining({
            scanners: [],
            config: undefined,
          }),
        }),
      })
    );
  });

  it('removes unavailable rerun plugins, scopes and package-manager dependencies', async () => {
    const provider = createPluginDescriptor({
      id: 'Curator',
      type: 'PACKAGE_CURATION_PROVIDER',
    });
    const configProvider = createPluginDescriptor({
      id: 'Config',
      type: 'PACKAGE_CONFIGURATION_PROVIDER',
    });
    const reporter = createPluginDescriptor({
      id: 'CycloneDX',
      type: 'REPORTER',
    });
    const extraScanner = createPluginDescriptor({
      id: 'ExtraScanner',
      type: 'SCANNER',
    });
    const run = createOrtRun({
      jobConfigs: {
        analyzer: {
          enabledPackageManagers: ['Maven', 'NPM'],
          packageManagerOptions: {
            Maven: { mustRunAfter: ['NPM'] },
            NPM: { mustRunAfter: ['Maven'] },
          },
          packageCurationProviders: [{ type: 'Curator' }],
        },
        advisor: {
          advisors: ['OSV'],
          config: { OSV: { options: { serverUrl: 'https://old.example' } } },
        },
        scanner: {
          scanners: ['ScanCode', 'ExtraScanner'],
          projectScanners: ['ExtraScanner'],
        },
        evaluator: { packageConfigurationProviders: [{ type: 'Config' }] },
        reporter: {
          formats: ['CycloneDX'],
          packageConfigurationProviders: [{ type: 'Config' }],
        },
      },
    });
    const { user, onSubmit } = renderSwappableForm(
      [
        advisorPlugin,
        scannerPlugin,
        extraScanner,
        reporter,
        provider,
        configProvider,
        ...packageManagerPlugins,
      ],
      [packageManagerPlugins[0]!],
      vi.fn(),
      run
    );
    await user.click(
      await screen.findByRole('button', { name: 'Swap plugins' })
    );
    await user.click(screen.getByRole('button', { name: 'Create' }));
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        jobConfigs: expect.objectContaining({
          analyzer: expect.objectContaining({
            enabledPackageManagers: ['Maven', 'Unmanaged'],
            packageCurationProviders: [],
            packageManagerOptions: undefined,
          }),
          advisor: expect.objectContaining({ advisors: [], config: undefined }),
          scanner: expect.objectContaining({
            scanners: [],
            projectScanners: undefined,
            config: undefined,
          }),
          evaluator: expect.objectContaining({
            packageConfigurationProviders: [],
          }),
          reporter: expect.objectContaining({
            formats: [],
            packageConfigurationProviders: undefined,
            config: undefined,
          }),
        }),
      })
    );
  });

  it('clears the Gradle conflict when the package managers are reset to the defaults', async () => {
    const gradleInspectorPlugin = createPluginDescriptor({
      id: 'GradleInspector',
      type: 'PACKAGE_MANAGER',
      displayName: 'Gradle',
    });
    const { onSubmit, user } = renderCreateRunForm(vi.fn(), {
      plugins: [
        advisorPlugin,
        scannerPlugin,
        ...packageManagerPlugins,
        gradleInspectorPlugin,
      ],
    });
    const conflict =
      '"Gradle Legacy" and "Gradle" cannot be enabled at the same time.';

    await user.click(await screen.findByRole('button', { name: 'Analyzer' }));
    await user.click(screen.getByLabelText('Enable Gradle Legacy'));
    await user.click(screen.getByLabelText('Enable Gradle'));
    await user.click(screen.getByRole('button', { name: 'Create' }));
    // The conflict is shown at the field and in the list of all errors.
    expect(await screen.findAllByText(conflict)).toHaveLength(2);

    const packageManagers = screen
      .getByText('Enabled package managers')
      .closest<HTMLElement>('[data-slot="form-item"]')!;
    await user.click(
      within(packageManagers).getByRole('button', { name: 'Reset to defaults' })
    );

    await waitFor(() =>
      expect(screen.queryAllByText(conflict)).toHaveLength(0)
    );
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('does not offer the always enabled "Unmanaged" package manager for selection', async () => {
    const { user } = renderCreateRunForm();

    await user.click(await screen.findByRole('button', { name: 'Analyzer' }));

    const packageManagers = screen
      .getByText('Enabled package managers')
      .closest<HTMLElement>('[data-slot="form-item"]')!;

    expect(within(packageManagers).getByText('Maven')).toBeVisible();
    expect(within(packageManagers).queryByText('Unmanaged')).toBeNull();
  });

  it('submits a scope exclude added from a typed pattern', async () => {
    const { onSubmit, user } = renderCreateRunForm();

    await user.click(await screen.findByRole('button', { name: /^Scopes/ }));
    await user.click(screen.getByRole('button', { name: 'Add scope pattern' }));
    await user.type(
      screen.getByPlaceholderText(/type a regular expression/),
      'test.*'
    );
    await user.click(
      screen.getByRole('option', { name: 'Exclude scopes matching test.*' })
    );

    expect(screen.getByLabelText('Pattern')).toHaveValue('test.*');
    expect(screen.getByRole('button', { name: /^Scopes/ })).toHaveTextContent(
      '1 excluded'
    );

    await user.click(screen.getByRole('button', { name: 'Advisor' }));
    await user.type(screen.getByLabelText(/serverUrl/), 'https://osv.dev');
    await user.click(screen.getByRole('button', { name: 'Create' }));

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        scopes: {
          excludes: [{ pattern: 'test.*', reason: 'TEST_DEPENDENCY_OF' }],
          includes: [],
        },
      })
    );
  });

  it('opens the scopes when one of their entries is invalid', async () => {
    const { onSubmit, user } = renderCreateRunForm();
    const scopes = await screen.findByRole('button', { name: /^Scopes/ });

    await user.click(scopes);
    await user.click(screen.getByRole('button', { name: 'Add scope pattern' }));
    await user.type(
      screen.getByPlaceholderText(/type a regular expression/),
      'runtime'
    );
    await user.click(
      screen.getByRole('option', { name: 'Exclude scopes matching runtime' })
    );
    await user.click(scopes);
    await user.click(screen.getByRole('button', { name: 'Create' }));

    expect(await screen.findAllByText('Choose a reason.')).not.toHaveLength(0);
    expect(scopes).toHaveAttribute('aria-expanded', 'true');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('offers a typed pattern in the direction that no suggestion has', async () => {
    const { user } = renderCreateRunForm();

    await user.click(await screen.findByRole('button', { name: /^Scopes/ }));
    await user.click(screen.getByRole('button', { name: 'Add scope pattern' }));
    // The Maven and SBT suggestions exclude "test".
    await user.type(
      screen.getByPlaceholderText(/type a regular expression/),
      'test'
    );

    expect(
      screen.getByRole('option', { name: 'Include only scopes matching test' })
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('option', { name: 'Exclude scopes matching test' })
    ).toBeNull();
  });

  it('adds a suggested pattern with its reason and removes it when picked again', async () => {
    const { user } = renderCreateRunForm();

    await user.click(await screen.findByRole('button', { name: /^Scopes/ }));
    await user.click(screen.getByRole('button', { name: 'Add scope pattern' }));
    const getPreset = () =>
      screen.getByRole('option', { name: /^Unit and instrumented tests/ });
    await user.click(getPreset());

    expect(screen.getByLabelText('Pattern')).toHaveValue(
      '.*(UnitTest|AndroidTest).*'
    );
    expect(screen.getByLabelText('Reason')).toHaveTextContent(
      'Test dependency of'
    );

    // The list stays open, so that the same preset can be picked again to remove it.
    await user.click(getPreset());

    expect(screen.queryByLabelText('Pattern')).toBeNull();
  });

  it('suggests the presets and scopes of the projects of the last analyzed run first', async () => {
    const { user } = renderCreateRunForm(vi.fn(), {
      projectSuggestions: getProjectSuggestions({
        runIndex: 12,
        projects: [
          createProject({ type: 'NPM', scopeNames: ['devDependencies'] }),
        ],
        capped: false,
      }),
    });

    await user.click(await screen.findByRole('button', { name: /^Scopes/ }));
    await user.click(screen.getByRole('button', { name: 'Add scope pattern' }));

    const headings = Array.from(
      document.querySelectorAll('[cmdk-group-heading]'),
      (heading) => heading.textContent
    );
    expect(headings.slice(0, 2)).toEqual([
      'Suggested for NPM',
      'Scopes in run #12',
    ]);
  });

  it('warns when the excludes leave no scope of the last analyzed run', async () => {
    const { user } = renderCreateRunForm(vi.fn(), {
      projectSuggestions: getProjectSuggestions({
        runIndex: 12,
        projects: [
          createProject({ type: 'NPM', scopeNames: ['devDependencies'] }),
        ],
        capped: false,
      }),
    });

    await user.click(await screen.findByRole('button', { name: /^Scopes/ }));
    await user.click(screen.getByRole('button', { name: 'Add scope pattern' }));
    // The first one is suggested for the NPM project found.
    await user.click(
      screen.getAllByRole('option', { name: /^Development dependencies/ })[0]!
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This pattern would exclude the only scope found in run #12, leaving no dependencies to analyze.'
    );
  });
});
