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
import { describe, expect, it, vi } from 'vitest';

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
      plugins={[advisorPlugin, scannerPlugin]}
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

const enabledPackageManagers = [
  'Bazel',
  'Bower',
  'Bundler',
  'Cargo',
  'Carthage',
  'CocoaPods',
  'Composer',
  'Conan',
  'Gleam',
  'GoMod',
  'GradleInspector',
  'Maven',
  'NPM',
  'NuGet',
  'OrtProjectFile',
  'PIP',
  'Pipenv',
  'PNPM',
  'Poetry',
  'Pub',
  'SBT',
  'SPDX',
  'SpdxDocumentFile',
  'Stack',
  'SwiftPM',
  'Tycho',
  'Yarn',
  'Yarn2',
  'Unmanaged',
];

const getJobSwitch = (job: string) => {
  const trigger = screen.getByRole('button', { name: job });
  const row = trigger.closest('[data-slot="accordion-item"]')!.parentElement!;

  return within(row).getByRole('switch');
};

describe('CreateRunForm', () => {
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
          createMissingArchives: true,
          skipConcluded: true,
          skipExcluded: true,
          keepAliveWorker: undefined,
          scanners: ['ScanCode'],
          projectScanners: undefined,
          config: {
            ScanCode: {
              options: {},
              secrets: {},
            },
          },
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
});
