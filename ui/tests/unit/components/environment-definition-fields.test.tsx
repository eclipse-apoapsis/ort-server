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

import { describe, expect, it, vi } from 'vitest';

import { ENVIRONMENT_DEFINITION_SCHEMAS } from '@/lib/environment-definition-fields';
import { defaultValues } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/_repo-layout/create-run/-components';
import { PackageManagerField } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components';
import { EnvironmentDefinitionsFields } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/-components/environment-definitions';
import { renderWithForm } from '../fixtures/form-harness';

vi.mock('@tanstack/react-router', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@tanstack/react-router')>();

  return {
    ...actual,
    useParams: () => ({}),
    useRouter: () => ({
      options: {
        context: {
          permissions: {
            organization: undefined,
            product: undefined,
            repository: undefined,
          },
        },
      },
    }),
  };
});

vi.mock('@/hooks/use-infrastructure-services', () => ({
  useInfrastructureServices: () => ({
    items: [],
    totalCount: 0,
    isPending: false,
    isError: false,
    error: null,
    hasNextPage: false,
    isFetchingNextPage: false,
    fetchNextPage: () => {},
  }),
}));

const baseDefaultValues = defaultValues(null, [], [], false, [], []);
const conanDefaultEntry = ENVIRONMENT_DEFINITION_SCHEMAS.find(
  (schema) => schema.key === 'conan'
)?.defaultEntries[0];

if (!conanDefaultEntry) {
  throw new Error('The Conan environment definition requires a default entry.');
}

const populatedDefaultValues = {
  ...baseDefaultValues,
  jobConfigs: {
    ...baseDefaultValues.jobConfigs,
    analyzer: {
      ...baseDefaultValues.jobConfigs.analyzer,
      environmentDefinitions: {
        conan: [conanDefaultEntry],
      },
    },
  },
};

const packageManagerDefaultValues = {
  ...baseDefaultValues,
  jobConfigs: {
    ...baseDefaultValues.jobConfigs,
    analyzer: {
      ...baseDefaultValues.jobConfigs.analyzer,
      packageManagers: {
        ...baseDefaultValues.jobConfigs.analyzer.packageManagers,
        Conan: {
          ...baseDefaultValues.jobConfigs.analyzer.packageManagers.Conan,
          options: [{ key: 'remote', value: 'central' }],
        },
      },
    },
  },
};

const renderEnvironmentDefinitions = (
  defaultValues: typeof baseDefaultValues
) =>
  renderWithForm((form) => <EnvironmentDefinitionsFields form={form} />, {
    defaultValues,
  });

describe('EnvironmentDefinitionsFields', () => {
  it('renders an empty environment definition list', () => {
    const markup = renderEnvironmentDefinitions(baseDefaultValues);

    expect(markup).toContain('Environment configuration');
    expect(markup).toContain('Add environment configuration');
    expect(markup).not.toContain('>Package manager<');
  });

  it('binds the package manager label to the selector', () => {
    const markup = renderEnvironmentDefinitions(populatedDefaultValues);
    const selectorId = 'environment-definition-conan-0';

    expect(markup).toContain('Conan #1');
    expect(markup).toMatch(
      new RegExp(`<label[^>]*for="${selectorId}"[^>]*>Package manager</label>`)
    );
    expect(markup).toMatch(new RegExp(`<button[^>]*id="${selectorId}"`));
    expect(markup).toContain('Remote name');
  });
});

describe('PackageManagerField', () => {
  it('renders controls inside an open package manager accordion', () => {
    const markup = renderWithForm(
      (form) => <PackageManagerField form={form} />,
      { defaultValues: packageManagerDefaultValues }
    );

    expect(markup).toContain('Enabled package managers');
    expect(markup).toContain('data-state="open"');
    expect(markup).toContain('Options:');
    expect(markup).toContain('>Key<');
    expect(markup).toContain('>Value<');
    expect(markup).toContain('Must run after');
  });
});
