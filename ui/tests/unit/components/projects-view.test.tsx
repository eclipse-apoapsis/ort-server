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

import { act, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { GetRunProjectsData, Project } from '@/api';
import { TooltipProvider } from '@/components/ui/tooltip';
import { Route } from '@/routes/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/projects/index';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';

type ProjectsQuery = NonNullable<GetRunProjectsData['query']>;

const mocks = vi.hoisted(() => ({
  run: vi.fn(),
  licenses: vi.fn(),
  projects: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getRepositoryRun: mocks.run,
  getRunProjectLicenses: mocks.licenses,
  getRunProjects: mocks.projects,
}));

vi.mock('@/components/licenses', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/components/licenses')>()),
  LicensesAccordion: () => null,
}));

const ProjectsComponent = Route.options.component!;

const vcs = { type: 'GIT', url: '', revision: '', path: '' };

const createProject = (name: string): Project => ({
  authors: [],
  declaredLicenses: [],
  definitionFilePath: `${name}/pom.xml`,
  description: `Description of ${name}`,
  homepageUrl: '',
  identifier: { type: 'Maven', namespace: 'com.example', name, version: '1.0' },
  processedDeclaredLicense: { mappedLicenses: {}, unmappedLicenses: [] },
  scopeNames: [],
  vcs,
  vcsProcessed: vcs,
});

// Serve two projects, named after the query they come from.
const getProjectsPage = (query: ProjectsQuery = {}) => {
  const tag = query.identifier ? 'filtered' : 'project';

  return {
    data: [createProject(`${tag}-1`), createProject(`${tag}-2`)],
    pagination: {
      limit: query.limit ?? 10,
      offset: query.offset ?? 0,
      totalCount: query.identifier ? 2 : 7,
    },
  };
};

const renderProjects = () =>
  renderInteractiveWithRouter(
    <TooltipProvider>
      <ProjectsComponent />
    </TooltipProvider>,
    {
      path: '/organizations/1/products/2/repositories/3/runs/4/projects',
      routes: [
        {
          path: '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/projects/',
        },
      ],
      withQueryClient: true,
    }
  );

const findDefinitionFile = (name: string) =>
  screen.findByText(`${name}/pom.xml`);

describe('projects table', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.run.mockResolvedValue({ data: { id: 42, jobs: {} } });
    mocks.licenses.mockResolvedValue({
      data: { processedDeclaredLicenses: [], unmappedDeclaredLicenses: [] },
    });
    mocks.projects.mockImplementation(
      async ({ query }: { query?: ProjectsQuery }) => ({
        data: getProjectsPage(query),
      })
    );
  });

  it('is compiled by React Compiler', () => {
    expect(ProjectsComponent.toString()).toContain('react.memo_cache_sentinel');
  });

  it('applies filters from the URL', async () => {
    const { container, router } = renderProjects();

    expect(await findDefinitionFile('project-1')).toBeVisible();

    await act(() =>
      router.navigate({
        to: '.',
        search: { projectId: 'core', declaredLicense: ['MIT', 'Apache-2.0'] },
      })
    );

    await waitFor(() =>
      expect(screen.getByText('filtered-1/pom.xml')).toBeVisible()
    );
    expect(screen.queryByText('project-1/pom.xml')).not.toBeInTheDocument();
    expect(mocks.projects.mock.calls.at(-1)?.[0].query).toMatchObject({
      identifier: 'core',
      declaredLicense: 'MIT,Apache-2.0',
    });
    expect(container).toHaveTextContent('7 in total, 2 matching filters');
  });

  it('expands a row', async () => {
    const { user } = renderProjects();

    const definitionFile = await findDefinitionFile('project-1');
    expect(
      screen.queryByText('Description of project-1')
    ).not.toBeInTheDocument();

    await user.click(definitionFile.closest('tr')!.querySelector('button')!);

    expect(await screen.findByText('Description of project-1')).toBeVisible();
    expect(
      screen.queryByText('Description of project-2')
    ).not.toBeInTheDocument();
  });
});
