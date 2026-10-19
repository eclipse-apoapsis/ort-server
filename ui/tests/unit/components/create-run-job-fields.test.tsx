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

import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { FormProvider, useForm, type UseFormReturn } from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import { Accordion } from '@/components/ui/accordion';
import {
  getDefaultPluginSelections,
  type CreateRunFormValues,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';
import {
  AdvisorFields,
  AnalyzerFields,
  EvaluatorFields,
  NotifierFields,
  ReporterFields,
  ScannerFields,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components';
import {
  createFormDefaultValues,
  createPackageCurationProviderPlugins,
  createPermissions,
  createPluginDescriptor,
  createPluginSecrets,
} from '../fixtures/create-run';
import {
  createStableFormHarness,
  renderWithForm,
  renderWithStableForm,
} from '../fixtures/form-harness';
import {
  createTestRouter,
  renderStaticWithRouter,
  RouterTestProvider,
} from '../fixtures/router-harness';

const advisorPlugins = [
  createPluginDescriptor({
    id: 'OSV',
    type: 'ADVISOR',
    displayName: 'OSV',
  }),
];
const scannerPlugins = [
  createPluginDescriptor({
    id: 'ScanCode',
    type: 'SCANNER',
    displayName: 'ScanCode',
  }),
];
const reporterPlugins = ['CycloneDX', 'SpdxDocument', 'WebApp'].map((id) =>
  createPluginDescriptor({ id, type: 'REPORTER', displayName: id })
);
const packageConfigurationProviderPlugins = [
  createPluginDescriptor({
    id: 'OrtConfig',
    type: 'PACKAGE_CONFIGURATION_PROVIDER',
    displayName: 'ORT configuration',
  }),
];
const packageCurationProviderPlugins = createPackageCurationProviderPlugins();
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
];
const permissions = createPermissions();
const secrets = createPluginSecrets();
const formDefaultValues = createFormDefaultValues({
  plugins: {
    advisorPlugins,
    scannerPlugins,
    reporterPlugins,
    packageConfigurationProviderPlugins,
    packageManagerPlugins,
  },
  isSuperuser: true,
});
const onToggle = () => {};
const defaultPluginSelections = getDefaultPluginSelections([], {
  advisorPlugins,
  scannerPlugins,
  reporterPlugins,
  packageCurationProviderPlugins: [],
  packageConfigurationProviderPlugins,
  packageManagerPlugins,
});

type JobFieldsTestCase = {
  name: string;
  sectionValue: string;
  contentLabel: string;
  render: (form: UseFormReturn<CreateRunFormValues>) => ReactNode;
};

const AnalyzerFieldsHarness = () => {
  const form = useForm<CreateRunFormValues>({
    defaultValues: formDefaultValues,
  });

  return (
    <FormProvider {...form}>
      <Accordion type='multiple' defaultValue={['analyzer']}>
        <AnalyzerFields
          form={form}
          defaultPluginSelections={defaultPluginSelections}
          value='analyzer'
          onToggle={onToggle}
          isSuperuser
          packageCurationProviderPlugins={packageCurationProviderPlugins}
          packageManagerPlugins={packageManagerPlugins}
          pluginSecrets={secrets}
          permissions={permissions}
        />
      </Accordion>
    </FormProvider>
  );
};

const testCases: JobFieldsTestCase[] = [
  {
    name: 'advisor',
    sectionValue: 'advisor',
    contentLabel: 'Skip excluded',
    render: (form) => (
      <AdvisorFields
        form={form}
        defaultPluginSelections={defaultPluginSelections}
        value='advisor'
        onToggle={onToggle}
        advisorPlugins={advisorPlugins}
        secrets={secrets}
        isSuperuser
      />
    ),
  },
  {
    name: 'evaluator',
    sectionValue: 'evaluator',
    contentLabel: 'Package configuration providers',
    render: (form) => (
      <EvaluatorFields
        form={form}
        defaultPluginSelections={defaultPluginSelections}
        value='evaluator'
        onToggle={onToggle}
        isSuperuser
        packageConfigurationProviderPlugins={
          packageConfigurationProviderPlugins
        }
        secrets={secrets}
      />
    ),
  },
  {
    name: 'notifier',
    sectionValue: 'notifier',
    contentLabel: 'Recipient addresses',
    render: (form) => (
      <NotifierFields
        form={form}
        value='notifier'
        onToggle={onToggle}
        isSuperuser
      />
    ),
  },
  {
    name: 'reporter',
    sectionValue: 'reporter',
    contentLabel: 'Report formats',
    render: (form) => (
      <ReporterFields
        form={form}
        defaultPluginSelections={defaultPluginSelections}
        value='reporter'
        onToggle={onToggle}
        reporterPlugins={reporterPlugins}
        isSuperuser
        packageConfigurationProviderPlugins={
          packageConfigurationProviderPlugins
        }
        secrets={secrets}
      />
    ),
  },
  {
    name: 'scanner',
    sectionValue: 'scanner',
    contentLabel: 'Skip concluded',
    render: (form) => (
      <ScannerFields
        form={form}
        defaultPluginSelections={defaultPluginSelections}
        value='scanner'
        onToggle={onToggle}
        scannerPlugins={scannerPlugins}
        secrets={secrets}
        isSuperuser
      />
    ),
  },
];

describe('create run job fields', () => {
  it.each(testCases)(
    'renders the $name controls inside an open accordion',
    ({ sectionValue, contentLabel, render }) => {
      const markup = renderWithForm(render, {
        defaultValues: formDefaultValues,
        openAccordion: sectionValue,
      });

      expect(markup).toContain(contentLabel);
    }
  );

  it('renders the analyzer controls inside an open accordion', async () => {
    const markup = await renderStaticWithRouter(<AnalyzerFieldsHarness />, {
      path: '/organizations/1/products/2/repositories/3',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId',
        },
      ],
    });

    expect(markup).toContain('Repository configuration path');
  });

  it('renders the generated package manager plugin fields', async () => {
    const markup = await renderStaticWithRouter(<AnalyzerFieldsHarness />, {
      path: '/organizations/1/products/2/repositories/3',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId',
        },
      ],
    });

    expect(markup).toContain('Enabled package managers');
    expect(markup).toContain('Maven');
    expect(markup).toContain('NPM');
    // The package managers start collapsed, so their settings are not rendered.
    expect(markup).toContain('Expand all');
    expect(markup).not.toContain('Must run after');
  });

  it('shows the default package configuration providers first in their default order', () => {
    const providers = ['Alpha', 'Beta', 'Gamma'].map((id) =>
      createPluginDescriptor({
        id,
        type: 'PACKAGE_CONFIGURATION_PROVIDER',
        displayName: `Provider ${id}`,
      })
    );
    const values = createFormDefaultValues({
      plugins: {
        advisorPlugins,
        scannerPlugins,
        reporterPlugins,
        packageConfigurationProviderPlugins: providers,
        packageManagerPlugins,
      },
      isSuperuser: true,
      defaultPlugins: [
        {
          category: 'PACKAGE_CONFIGURATION_PROVIDER',
          pluginIds: ['Gamma', 'Alpha'],
          source: 'GLOBAL',
        },
      ],
    });

    const markup = renderWithForm(
      (form) => (
        <EvaluatorFields
          form={form}
          defaultPluginSelections={defaultPluginSelections}
          value='evaluator'
          onToggle={onToggle}
          isSuperuser
          packageConfigurationProviderPlugins={providers}
          secrets={secrets}
        />
      ),
      { defaultValues: values, openAccordion: 'evaluator' }
    );

    const positions = ['Gamma', 'Alpha', 'Beta'].map((id) =>
      markup.indexOf(`Provider ${id}`)
    );
    expect(positions.every((position) => position >= 0)).toBe(true);
    expect([...positions].sort((a, b) => a - b)).toEqual(positions);
  });

  it('hides the reporter package configuration providers once the evaluator is enabled', () => {
    const { form } = renderWithStableForm(
      (form) => (
        <Accordion type='multiple' defaultValue={['reporter']}>
          <ReporterFields
            form={form}
            defaultPluginSelections={defaultPluginSelections}
            value='reporter'
            onToggle={onToggle}
            reporterPlugins={reporterPlugins}
            isSuperuser
            packageConfigurationProviderPlugins={
              packageConfigurationProviderPlugins
            }
            secrets={secrets}
          />
        </Accordion>
      ),
      {
        defaultValues: {
          ...formDefaultValues,
          jobConfigs: {
            ...formDefaultValues.jobConfigs,
            evaluator: {
              ...formDefaultValues.jobConfigs.evaluator,
              enabled: false,
            },
          },
        },
      }
    );

    expect(
      screen.getByText('Package configuration providers')
    ).toBeInTheDocument();

    act(() => {
      form.setValue('jobConfigs.evaluator.enabled', true);
    });

    expect(
      screen.queryByText('Package configuration providers')
    ).not.toBeInTheDocument();
  });

  it('shows the keep-alive phases once keeping the worker alive is enabled', async () => {
    const user = userEvent.setup();
    const { StableFormHarness } = createStableFormHarness(
      (form) => (
        <Accordion type='multiple' defaultValue={['analyzer']}>
          <AnalyzerFields
            form={form}
            defaultPluginSelections={defaultPluginSelections}
            value='analyzer'
            onToggle={onToggle}
            isSuperuser
            packageCurationProviderPlugins={packageCurationProviderPlugins}
            packageManagerPlugins={packageManagerPlugins}
            pluginSecrets={secrets}
            permissions={permissions}
          />
        </Accordion>
      ),
      {
        defaultValues: {
          ...formDefaultValues,
          jobConfigs: {
            ...formDefaultValues.jobConfigs,
            analyzer: {
              ...formDefaultValues.jobConfigs.analyzer,
              keepAliveWorker: false,
            },
          },
        },
      }
    );
    const router = createTestRouter({
      path: '/organizations/1/products/2/repositories/3',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId',
          component: StableFormHarness,
        },
      ],
    });
    await router.load();
    render(<RouterTestProvider router={router} />);

    const phases = await screen.findByText('Phases');
    expect(phases.closest('.grid')).toHaveClass('invisible');

    await user.click(screen.getByRole('switch', { name: 'Keep worker alive' }));

    expect(phases.closest('.grid')).not.toHaveClass('invisible');
  });
});
