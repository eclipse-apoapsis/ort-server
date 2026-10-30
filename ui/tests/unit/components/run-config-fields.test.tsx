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

import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ConfigFields } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config/-components/config-fields';
import { PluginDescriptorsContext } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config/-components/config-utils';
import { JobConfigurations } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/config/-components/job-configurations';
import { createOrtRun, createPluginDescriptor } from '../fixtures/create-run';

/** The row of the setting with the given label. */
const row = (label: string) => {
  const term = screen.getByText(label).closest('dt');

  if (!term?.parentElement) throw new Error(`No row for "${label}".`);

  return within(term.parentElement);
};

/** The text of the secret reference that shows the given secret name. */
const secretReference = (name: string) =>
  screen.getByText(name).parentElement?.textContent;

describe('ConfigFields', () => {
  const plugin = (
    type: 'REPORTER' | 'SCANNER',
    id: string,
    displayName: string
  ) => createPluginDescriptor({ id, type, displayName, options: [] });

  it('shows switched-off settings, switched-on settings and empty lists', () => {
    render(
      <ConfigFields
        value={{
          allowDynamicVersions: false,
          skipExcluded: true,
          enabledPackageManagers: [],
        }}
        path={['analyzer']}
      />
    );

    expect(row('Allow dynamic versions').getByText('Off')).toBeInTheDocument();
    expect(row('Skip excluded').getByText('On')).toBeInTheDocument();
    expect(
      row('Enabled package managers').getByText('None')
    ).toBeInTheDocument();
  });

  it('shows a setting without a shared label under a label made from its key', () => {
    render(
      <ConfigFields
        value={{ assetFilesGroups: ['docs'] }}
        path={['reporter']}
      />
    );

    expect(row('Asset files groups').getByText('docs')).toBeInTheDocument();
  });

  it('shows secrets as references to the secret', () => {
    render(
      <>
        <ConfigFields
          value={{
            config: {
              ScanOSS: { options: {}, secrets: { apiKey: 'scanossToken' } },
            },
          }}
          path={['scanner']}
        />
        <ConfigFields
          value={{
            environmentConfig: {
              environmentVariables: [{ name: 'TOKEN', secretName: 'dosToken' }],
              infrastructureServices: [
                {
                  name: 'Artifactory',
                  url: 'https://artifactory.example.com',
                  usernameSecretRef: 'artifactoryUser',
                  passwordSecretRef: 'artifactoryPassword',
                },
              ],
            },
          }}
          path={['analyzer']}
        />
      </>
    );

    expect(secretReference('scanossToken')).toBe('SecretscanossToken');
    expect(secretReference('dosToken')).toBe('SecretdosToken');
    expect(secretReference('artifactoryPassword')).toBe(
      'SecretartifactoryPassword'
    );
  });

  it('shows each scanner once, with the scope it runs on', () => {
    render(
      <ConfigFields
        value={{ scanners: ['DOS'], projectScanners: ['DOS', 'SCANOSS'] }}
        path={['scanner']}
      />
    );

    const scanners = row('Enabled scanners');

    expect(scanners.getByText('DOS').parentElement).toHaveTextContent(
      'DOS Both'
    );
    expect(scanners.getByText('SCANOSS').parentElement).toHaveTextContent(
      'SCANOSS Projects only'
    );
    expect(screen.queryByText('Project scanners')).not.toBeInTheDocument();
  });

  it.each([
    ['unset', undefined, 'Both'],
    ['empty', [], 'Packages only'],
  ])(
    'shows the package scanners with project scanners %s as scanning %s',
    (_, projectScanners, scope) => {
      render(
        <ConfigFields
          value={{ scanners: ['ScanCode'], projectScanners }}
          path={['scanner']}
        />
      );

      expect(
        row('Enabled scanners').getByText('ScanCode').parentElement
      ).toHaveTextContent(`ScanCode ${scope}`);
    }
  );

  it('shows the configuration of a scanner that only scans projects', () => {
    render(
      <ConfigFields
        value={{
          scanners: [],
          projectScanners: ['ScanCode'],
          config: { ScanCode: { options: { timeout: '300' }, secrets: {} } },
        }}
        path={['scanner']}
      />
    );

    expect(row('timeout').getByText('300')).toBeInTheDocument();
  });

  it('names and explains plugin options from the plugin descriptors', async () => {
    render(
      <PluginDescriptorsContext.Provider
        value={[
          createPluginDescriptor({
            id: 'CycloneDX',
            type: 'REPORTER',
            displayName: 'CycloneDX SBOM',
            options: [
              {
                name: 'outputFileFormats',
                description: 'The formats of the generated files.',
              },
            ],
          }),
        ]}
      >
        <ConfigFields
          value={{
            config: {
              CycloneDX: {
                options: { outputFileFormats: 'json' },
                secrets: {},
              },
              Removed: { options: { someOption: 'value' }, secrets: {} },
            },
          }}
          path={['reporter']}
        />
      </PluginDescriptorsContext.Provider>
    );

    expect(screen.getByText('CycloneDX SBOM')).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole('button', { name: 'About outputFileFormats' })
    );

    expect(await screen.findByRole('tooltip')).toHaveTextContent(
      'The formats of the generated files.'
    );
    expect(screen.getByText('Removed')).toBeInTheDocument();
    expect(row('someOption').getByText('value')).toBeInTheDocument();
  });

  it('names plugins in lists as on the create-run form, with their id as a fallback', () => {
    render(
      <PluginDescriptorsContext.Provider
        value={[
          plugin('REPORTER', 'SpdxDocument', 'SPDX'),
          plugin('SCANNER', 'DOS', 'Double Open Server'),
          plugin('REPORTER', 'DOS', 'Not a scanner'),
        ]}
      >
        <ConfigFields
          value={{ formats: ['SpdxDocument', 'Gone'] }}
          path={['reporter']}
        />
        <ConfigFields value={{ scanners: ['DOS'] }} path={['scanner']} />
      </PluginDescriptorsContext.Provider>
    );

    expect(row('Report formats').getByText('SPDX')).toBeInTheDocument();
    expect(row('Report formats').getByText('Gone')).toBeInTheDocument();
    expect(
      row('Enabled scanners').getByText(/^Double Open Server/)
    ).toBeInTheDocument();
  });

  it('shows the environment configuration in the position of the create-run form', () => {
    render(
      <ConfigFields
        value={{
          keepAliveWorker: false,
          environmentConfig: {
            environmentVariables: [{ name: 'TOKEN', value: 'x' }],
          },
          skipExcluded: true,
        }}
        path={['analyzer']}
      />
    );

    const labels = screen.getAllByRole('term').map((term) => term.textContent);

    expect(labels.indexOf('Environment configuration')).toBe(
      labels.indexOf('Skip excluded') + 1
    );
    expect(labels.indexOf('Keep worker alive')).toBeGreaterThan(
      labels.indexOf('Environment configuration')
    );
  });
});

describe('JobConfigurations', () => {
  it.each([
    [true, false],
    [false, true],
  ])(
    'shows the package configuration providers of the reporter only without an evaluator (evaluator: %s)',
    (hasEvaluator, shown) => {
      const dos = { type: 'DOS', id: 'DOS', enabled: true };
      const jobConfigs = {
        analyzer: {},
        ...(hasEvaluator
          ? { evaluator: { packageConfigurationProviders: [dos] } }
          : {}),
        reporter: { formats: ['WebApp'], packageConfigurationProviders: [dos] },
      };

      render(
        <JobConfigurations
          run={createOrtRun({
            status: 'FINISHED',
            jobs: {},
            jobConfigs,
            resolvedJobConfigs: jobConfigs,
          })}
          plugins={[]}
          openSections={['reporter']}
          onOpenSectionsChange={() => {}}
        />
      );

      expect(
        screen.queryByText('Package configuration providers') !== null
      ).toBe(shown);
    }
  );

  it('shows the requested settings of a run whose configuration is not resolved', () => {
    render(
      <JobConfigurations
        run={createOrtRun({
          jobs: {},
          jobConfigs: { analyzer: { allowDynamicVersions: true } },
          resolvedJobConfigs: null,
        })}
        plugins={[]}
        openSections={['analyzer']}
        onOpenSectionsChange={() => {}}
      />
    );

    expect(row('Allow dynamic versions').getByText('On')).toBeInTheDocument();
  });
});
