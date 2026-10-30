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
import { describe, expect, it, vi } from 'vitest';

import type { OrtRun } from '@/api';
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
  const renderJobConfigurations = (run: Parameters<typeof createOrtRun>[0]) =>
    render(
      <JobConfigurations
        run={createOrtRun(run)}
        plugins={[]}
        openSections={['analyzer', 'advisor']}
        onOpenSectionsChange={() => {}}
      />
    );

  it('shows a job that was not enabled as such, without a way to expand it', () => {
    renderJobConfigurations({
      status: 'FINISHED',
      jobs: { analyzer: { status: 'FINISHED' } },
      jobConfigs: { analyzer: {} },
      resolvedJobConfigs: { analyzer: {} },
    });

    const advisor = screen.getByRole('button', { name: /^Advisor/ });

    expect(advisor).toHaveTextContent('Not enabled for this run');
    expect(advisor).toBeDisabled();
  });

  it('shows the configuration of a job that has not started', () => {
    renderJobConfigurations({
      status: 'ACTIVE',
      jobs: { analyzer: { status: 'RUNNING' } },
      jobConfigs: { analyzer: {}, advisor: { advisors: ['OSV'] } },
      resolvedJobConfigs: { analyzer: {}, advisor: { advisors: ['OSV'] } },
    });

    const advisor = screen.getByRole('button', { name: /^Advisor/ });

    expect(advisor).toHaveTextContent('Not started');
    expect(advisor).toBeEnabled();
    expect(row('Enabled advisors').getByText('OSV')).toBeInTheDocument();
  });

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

  it.each([
    ['ACTIVE', 'The server has not resolved this configuration yet.'],
    ['FAILED', 'The server could not resolve this configuration.'],
  ] as const)(
    'shows the requested settings of a %s run whose configuration is not resolved',
    (status, notice) => {
      renderJobConfigurations({
        status,
        jobs: {},
        jobConfigs: { analyzer: { allowDynamicVersions: true } },
        resolvedJobConfigs: null,
      });

      expect(screen.getByRole('status')).toHaveTextContent(notice);
      expect(row('Allow dynamic versions').getByText('On')).toBeInTheDocument();
      expect(
        screen.getByRole('button', { name: /^Advisor/ })
      ).toHaveTextContent('Not requested');
    }
  );
});

describe('settings the server changed', () => {
  const renderChanges = (
    jobConfigs: OrtRun['jobConfigs'],
    resolvedJobConfigs: OrtRun['jobConfigs'],
    openSections: string[]
  ) =>
    render(
      <JobConfigurations
        run={createOrtRun({
          status: 'FINISHED',
          jobs: {},
          jobConfigs,
          resolvedJobConfigs,
        })}
        plugins={[]}
        openSections={openSections}
        onOpenSectionsChange={() => {}}
      />
    );

  const allChanges = () => screen.queryAllByText(/by the server$/);

  it('shows a value the server changed with its old and new value', () => {
    renderChanges(
      { analyzer: {}, scanner: { skipConcluded: false, skipExcluded: true } },
      { analyzer: {}, scanner: { skipConcluded: true, skipExcluded: true } },
      ['scanner']
    );

    const skipConcluded = row('Skip concluded');

    expect(
      skipConcluded.getByText('Changed by the server')
    ).toBeInTheDocument();
    expect(skipConcluded.getByText('Off').closest('del')).not.toBeNull();
    expect(skipConcluded.getByText('On').closest('del')).toBeNull();
    expect(allChanges()).toHaveLength(1);
  });

  it.each([
    ['unset', { skipExcluded: undefined }],
    ['an empty list', { scanners: [] as string[] }],
  ])('shows a value that was %s as added', (_, requested) => {
    const key = Object.keys(requested)[0] as 'skipExcluded' | 'scanners';
    const resolved = { skipExcluded: true, scanners: ['ScanCode'] };

    renderChanges(
      { analyzer: {}, scanner: requested },
      { analyzer: {}, scanner: { [key]: resolved[key] } },
      ['scanner']
    );

    expect(screen.getByText('Added by the server')).toBeInTheDocument();
    expect(allChanges()).toHaveLength(1);
    expect(screen.queryByText(/Requested/)).not.toBeInTheDocument();
  });

  it('shows the option a plugin template added to a plugin without options', () => {
    const requested = { DOS: { options: {}, secrets: {} } };

    renderChanges(
      { analyzer: {}, scanner: { config: requested } },
      {
        analyzer: {},
        scanner: {
          config: {
            DOS: {
              options: { timeout: '300' },
              secrets: { token: 'dosToken' },
            },
          },
        },
      },
      ['scanner']
    );

    expect(row('timeout').getByText('Added by the server')).toBeInTheDocument();
    expect(row('token').getByText('Added by the server')).toBeInTheDocument();
    expect(allChanges()).toHaveLength(2);
  });

  it('shows the entries the server added to and removed from a list', () => {
    renderChanges(
      { analyzer: {}, reporter: { formats: ['WebApp', 'PdfTemplate'] } },
      { analyzer: {}, reporter: { formats: ['WebApp', 'SpdxDocument'] } },
      ['reporter']
    );

    const formats = screen
      .getByText('Added by the server')
      .closest('dd') as HTMLElement;

    expect(formats).toHaveTextContent('Added by the serverSpdxDocument');
    expect(formats).toHaveTextContent('Removed by the serverPdfTemplate');
    expect(allChanges()).toHaveLength(2);
  });

  it('shows scanners by what the server did to each of them', () => {
    renderChanges(
      {
        analyzer: {},
        scanner: { scanners: ['ScanCode', 'Provenant'], projectScanners: [] },
      },
      {
        analyzer: {},
        scanner: {
          scanners: ['ScanCode', 'ScanOSS'],
          projectScanners: ['ScanCode'],
        },
      },
      ['scanner']
    );

    const scanners = row('Enabled scanners');

    expect(scanners.getByText(/^ScanOSS \(/)).toHaveTextContent(
      'ScanOSS (Packages only)'
    );
    expect(scanners.getByText(/^Provenant \(/)).toHaveTextContent(
      'Provenant (Packages only)'
    );
    expect(
      scanners.getByText('ScanCode: Packages only → Both')
    ).toBeInTheDocument();
    expect(allChanges()).toHaveLength(3);
  });

  it('shows the server turning off project scanning as a change of scope', () => {
    renderChanges(
      { analyzer: {}, scanner: { scanners: ['ScanCode'] } },
      {
        analyzer: {},
        scanner: { scanners: ['ScanCode'], projectScanners: [] },
      },
      ['scanner']
    );

    expect(
      row('Enabled scanners').getByText('ScanCode: Both → Packages only')
    ).toBeInTheDocument();
    expect(allChanges()).toHaveLength(1);
  });

  it('shows scanners the server chose as added, without repeating them', () => {
    renderChanges(
      { analyzer: {}, scanner: {} },
      { analyzer: {}, scanner: { scanners: ['ScanCode'] } },
      ['scanner']
    );

    expect(
      row('Enabled scanners').getByText('Added by the server')
    ).toBeInTheDocument();
    expect(allChanges()).toHaveLength(1);
    expect(screen.getAllByText(/ScanCode/)).toHaveLength(1);
  });

  it('shows a setting the server removed with its requested value', () => {
    renderChanges(
      { analyzer: {}, advisor: { skipExcluded: true, advisors: ['OSV'] } },
      { analyzer: {}, advisor: { advisors: ['OSV'] } },
      ['advisor']
    );

    const skipExcluded = row('Skip excluded');

    expect(skipExcluded.getByText('Removed by the server')).toBeInTheDocument();
    expect(skipExcluded.getByText('On').closest('del')).not.toBeNull();
    expect(allChanges()).toHaveLength(1);
  });

  it('shows an entry the server emptied once, as removed', () => {
    const option = (value: string) => ({
      options: { apiUrl: value },
      secrets: {},
    });

    renderChanges(
      {
        analyzer: {},
        reporter: {
          formats: ['WebApp'],
          config: { CycloneDX: option('url'), SpdxDocument: option('url') },
        },
      },
      {
        analyzer: {},
        reporter: {
          formats: [],
          config: { CycloneDX: {} as never, SpdxDocument: option('') },
        },
      },
      ['reporter']
    );

    expect(
      row('Report formats').getByText('Removed by the server')
    ).toBeInTheDocument();
    expect(
      row('apiUrl').getByText('Removed by the server')
    ).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { name: 'CycloneDX' })).toHaveLength(
      1
    );
    expect(allChanges()).toHaveLength(3);
  });

  it('marks a plugin the server added on its block only', () => {
    const spdx = { options: { outputFileFormats: 'YAML' }, secrets: {} };

    renderChanges(
      { analyzer: {}, reporter: { config: { SpdxDocument: spdx } } },
      {
        analyzer: {},
        reporter: {
          config: {
            SpdxDocument: spdx,
            CycloneDX: { options: { outputFileFormats: 'JSON' }, secrets: {} },
          },
        },
      },
      ['reporter']
    );

    const block = screen.getByRole('heading', { name: 'CycloneDX' })
      .parentElement?.parentElement as HTMLElement;

    expect(allChanges()).toHaveLength(1);
    expect(
      within(block.firstElementChild as HTMLElement).getByText(
        'Added by the server'
      )
    ).toBeInTheDocument();
  });

  it('shows a plugin the server removed in its place', () => {
    const spdx = { options: {}, secrets: {} };

    renderChanges(
      {
        analyzer: {},
        reporter: {
          config: {
            SpdxDocument: spdx,
            CycloneDX: { options: { outputFileFormats: 'JSON' }, secrets: {} },
          },
        },
      },
      { analyzer: {}, reporter: { config: { SpdxDocument: spdx } } },
      ['reporter']
    );

    expect(screen.getByRole('heading', { name: 'CycloneDX' })).toBeVisible();
    expect(screen.getByText('Removed by the server')).toBeInTheDocument();
    expect(allChanges()).toHaveLength(1);
  });

  it('matches list entries by identity when the server inserts one before them', () => {
    const provider = (id: string) => ({ type: id, id, enabled: true });

    renderChanges(
      { analyzer: { packageCurationProviders: [provider('ClearlyDefined')] } },
      {
        analyzer: {
          packageCurationProviders: [
            provider('RepositoryConfiguration'),
            provider('ClearlyDefined'),
          ],
        },
      },
      ['analyzer']
    );

    expect(
      within(
        screen.getByRole('heading', { name: 'RepositoryConfiguration' })
          .parentElement as HTMLElement
      ).getByText('Added by the server')
    ).toBeInTheDocument();
    expect(allChanges()).toHaveLength(1);
  });

  it('shows providers the server put in another order as a change of the list', () => {
    const provider = (id: string) => ({ type: id, id, enabled: true });

    renderChanges(
      {
        analyzer: {
          packageCurationProviders: [
            provider('ORTConfig'),
            provider('ClearlyDefined'),
          ],
        },
      },
      {
        analyzer: {
          packageCurationProviders: [
            provider('ClearlyDefined'),
            provider('ORTConfig'),
          ],
        },
      },
      ['analyzer']
    );

    const providers = row('Package curation providers');

    expect(providers.getByText('Changed by the server')).toBeInTheDocument();
    expect(
      providers.getByText('ORTConfig, ClearlyDefined').closest('del')
    ).not.toBeNull();
    expect(
      providers.getByText('ClearlyDefined, ORTConfig')
    ).toBeInTheDocument();
    expect(allChanges()).toHaveLength(1);
  });

  it('marks nothing if only the order of an unordered list differs or a value stays empty', () => {
    renderChanges(
      {
        analyzer: {},
        reporter: { formats: ['WebApp', 'SpdxDocument'] },
        scanner: { scanners: ['ScanCode'] },
      },
      {
        analyzer: { packageCurationProviders: [] },
        reporter: { formats: ['SpdxDocument', 'WebApp'] },
        scanner: { scanners: ['ScanCode'], config: {} },
      },
      ['analyzer', 'reporter', 'scanner']
    );

    expect(allChanges()).toHaveLength(0);
  });

  it('shows a job the server added and one it removed as such', () => {
    renderChanges(
      { analyzer: {}, advisor: { advisors: ['OSV'] } },
      { analyzer: {}, evaluator: { packageConfigurationProviders: [] } },
      []
    );

    expect(screen.getByRole('button', { name: /^Advisor/ })).toHaveTextContent(
      'Removed by the server'
    );
    expect(
      screen.getByRole('button', { name: /^Evaluator,/ })
    ).toHaveTextContent('Added by the server');
  });

  it('names plugins in what the server added to a list', () => {
    render(
      <JobConfigurations
        run={createOrtRun({
          status: 'FINISHED',
          jobs: {},
          jobConfigs: { analyzer: {}, scanner: { scanners: ['ScanOSS'] } },
          resolvedJobConfigs: {
            analyzer: {},
            scanner: { scanners: ['ScanOSS', 'DOS'] },
          },
        })}
        plugins={[
          createPluginDescriptor({
            id: 'DOS',
            type: 'SCANNER',
            displayName: 'Double Open Server',
            options: [],
          }),
        ]}
        openSections={['scanner']}
        onOpenSectionsChange={() => {}}
      />
    );

    expect(
      row('Enabled scanners').getByText('Double Open Server (Both)')
    ).toBeInTheDocument();
  });
});

describe('summary of the server changes', () => {
  const dos = { type: 'DOS', id: 'DOS', enabled: true };
  const onOpenSectionsChange = vi.fn();

  const renderSummary = (
    jobConfigs: OrtRun['jobConfigs'],
    resolvedJobConfigs: OrtRun['jobConfigs']
  ) => {
    onOpenSectionsChange.mockClear();

    render(
      <JobConfigurations
        run={createOrtRun({
          status: 'FINISHED',
          jobs: {},
          jobConfigs,
          resolvedJobConfigs,
        })}
        plugins={[]}
        openSections={['analyzer']}
        onOpenSectionsChange={onOpenSectionsChange}
      />
    );
  };

  it('counts the changes of each job while the jobs are collapsed', () => {
    renderSummary(
      { analyzer: {}, scanner: { skipExcluded: false } },
      {
        analyzer: { allowDynamicVersions: true },
        scanner: { skipExcluded: true, skipConcluded: true },
      }
    );

    expect(
      screen.getByText('The server changed 3 settings:')
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Analyzer (1)' })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Scanner (2)' })
    ).toBeInTheDocument();
  });

  it('opens a job from the summary and keeps the open jobs open', async () => {
    renderSummary(
      { analyzer: {}, scanner: { skipExcluded: false } },
      { analyzer: {}, scanner: { skipExcluded: true } }
    );

    await userEvent.click(screen.getByRole('button', { name: 'Scanner (1)' }));

    expect(onOpenSectionsChange).toHaveBeenCalledWith(['analyzer', 'scanner']);
  });

  it('shows nothing if the server changed nothing', () => {
    renderSummary(
      { analyzer: { allowDynamicVersions: true } },
      { analyzer: { allowDynamicVersions: true } }
    );

    expect(screen.queryByText(/^The server changed/)).not.toBeInTheDocument();
  });

  it('does not count a change to a setting the page does not show', () => {
    renderSummary(
      { analyzer: {}, evaluator: {}, reporter: {} },
      {
        analyzer: {},
        evaluator: {},
        reporter: { packageConfigurationProviders: [dos] },
      }
    );

    expect(screen.queryByText(/^The server changed/)).not.toBeInTheDocument();
  });

  it('does not link to a job the server removed', () => {
    renderSummary(
      { analyzer: {}, advisor: { advisors: ['OSV'] } },
      { analyzer: {} }
    );

    expect(screen.getByText('Advisor (1)')).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Advisor (1)' })
    ).not.toBeInTheDocument();
  });
});
