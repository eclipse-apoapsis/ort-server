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

import { screen, within } from '@testing-library/react';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';

import type { PreconfiguredPluginDescriptor } from '@/api';
import { CreateRunForm } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components/create-run-form';
import {
  createOrtRun,
  createPermissions,
  createPluginDescriptor,
  createPluginSecrets,
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
const permissions = createPermissions();
const rerun = createOrtRun({ revision: '', path: '', jobConfigs: {} });
const secrets = createPluginSecrets();
const createRunPath =
  '/organizations/1/products/2/repositories/3/create-run' as const;

const renderCreateRunForm = (onSubmit = vi.fn()) => ({
  onSubmit,
  ...renderInteractiveWithRouter(
    <CreateRunForm
      isSubmitting={false}
      isSuperuser={false}
      onSubmit={onSubmit}
      permissions={permissions}
      plugins={[advisorPlugin, scannerPlugin, ...packageManagerPlugins]}
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
  ),
});

const renderSwappableForm = (
  initial: PreconfiguredPluginDescriptor[],
  next: PreconfiguredPluginDescriptor[],
  onSubmit = vi.fn(),
  run = rerun,
  initiallyLoading = false
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
          isSubmitting={false}
          isSuperuser={false}
          onSubmit={onSubmit}
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

const getJobSwitch = (job: string) => {
  const trigger = screen.getByRole('button', { name: job });
  const row = trigger.closest('[data-slot="accordion-item"]')!.parentElement!;

  return within(row).getByRole('switch');
};

describe('CreateRunForm', () => {
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
      name: 'Plugins loading...',
    });
    expect(button).toBeDisabled();
    expect(within(button).queryByText('Creating run...')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Create' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Advisor' })).toBeNull();
    expect(screen.getByText('Loading available plugins...')).toBeVisible();
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
          packageCurationProviders: undefined,
          keepAliveWorker: undefined,
          keepAlivePhases: undefined,
        },
        advisor: {
          skipExcluded: true,
          advisors: ['OSV', 'VulnerableCode'],
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
    expect(screen.getByLabelText(/serverUrl/)).toHaveValue(
      'https://new.example'
    );
    await user.click(screen.getByText('New Advisor'));
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
            scanners: undefined,
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
            packageCurationProviders: undefined,
            packageManagerOptions: undefined,
          }),
          advisor: expect.objectContaining({ advisors: [], config: undefined }),
          scanner: expect.objectContaining({
            scanners: undefined,
            projectScanners: undefined,
            config: undefined,
          }),
          evaluator: expect.objectContaining({
            packageConfigurationProviders: undefined,
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

  it('does not offer the always enabled "Unmanaged" package manager for selection', async () => {
    const { user } = renderCreateRunForm();

    await user.click(await screen.findByRole('button', { name: 'Analyzer' }));

    const packageManagers = screen
      .getByText('Enabled package managers')
      .closest<HTMLElement>('[data-slot="form-item"]')!;

    expect(within(packageManagers).getByText('Maven')).toBeVisible();
    expect(within(packageManagers).queryByText('Unmanaged')).toBeNull();
  });
});
