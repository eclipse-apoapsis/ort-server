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

import type { ReactNode } from 'react';
import type { UseFormReturn } from 'react-hook-form';
import { describe, expect, it } from 'vitest';

import {
  defaultValues,
  type CreateRunFormValues,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';
import {
  AdvisorFields,
  EvaluatorFields,
  NotifierFields,
  ReporterFields,
  ScannerFields,
} from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components';
import {
  createPluginDescriptor,
  createPluginSecrets,
} from '../fixtures/create-run';
import { renderWithForm } from '../fixtures/form-harness';

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
const secrets = createPluginSecrets();
const formDefaultValues = defaultValues(
  null,
  advisorPlugins,
  scannerPlugins,
  true,
  [],
  packageConfigurationProviderPlugins
);
const onToggle = () => {};

type JobFieldsTestCase = {
  name: string;
  sectionValue: string;
  trigger: string;
  contentLabel: string;
  render: (form: UseFormReturn<CreateRunFormValues>) => ReactNode;
};

const testCases: JobFieldsTestCase[] = [
  {
    name: 'advisor',
    sectionValue: 'advisor',
    trigger: 'Advisor',
    contentLabel: 'Skip excluded',
    render: (form) => (
      <AdvisorFields
        form={form}
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
    trigger: 'Evaluator',
    contentLabel: 'Package configuration providers',
    render: (form) => (
      <EvaluatorFields
        form={form}
        value='evaluator'
        onToggle={onToggle}
        isSuperuser
        packageConfigurationProviderPlugins={
          packageConfigurationProviderPlugins
        }
        secrets={secrets}
        isRerun={false}
      />
    ),
  },
  {
    name: 'notifier',
    sectionValue: 'notifier',
    trigger: 'Notifier',
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
    trigger: 'Reporter',
    contentLabel: 'Report formats',
    render: (form) => (
      <ReporterFields
        form={form}
        value='reporter'
        onToggle={onToggle}
        reporterPlugins={reporterPlugins}
        isSuperuser
        packageConfigurationProviderPlugins={
          packageConfigurationProviderPlugins
        }
        secrets={secrets}
        isRerun={false}
      />
    ),
  },
  {
    name: 'scanner',
    sectionValue: 'scanner',
    trigger: 'Scanner',
    contentLabel: 'Skip concluded',
    render: (form) => (
      <ScannerFields
        form={form}
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
    ({ sectionValue, trigger, contentLabel, render }) => {
      const markup = renderWithForm(render, {
        defaultValues: formDefaultValues,
        openAccordion: sectionValue,
      });

      const triggerIndex = markup.indexOf(`>${trigger}<`);
      const switchIndexes = [...markup.matchAll(/role="switch"/g)].map(
        (match) => match.index
      );

      expect(triggerIndex).toBeGreaterThan(-1);
      expect(markup).toContain('data-state="open"');
      expect(markup).toContain(contentLabel);
      expect(switchIndexes.some((index) => index < triggerIndex)).toBe(true);
      expect(switchIndexes.some((index) => index > triggerIndex)).toBe(true);
    }
  );
});
