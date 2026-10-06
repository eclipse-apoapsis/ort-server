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

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { OrtRunSummary, Vulnerability } from '@/api';
import { getRepositoryRuns } from '@/api/sdk.gen';
import { Cvss4RadarChart } from '@/components/charts/cvss4-radar-chart';
import { Cvss4VectorCard } from '@/components/charts/cvss4-vector-card';
import { Cvss23RadarChart } from '@/components/charts/cvss23-radar-chart';
import { EpssChart } from '@/components/charts/epss-chart';
import { JobDurations } from '@/components/charts/job-durations';
import { VulnerabilityMetrics } from '@/components/charts/vulnerability-metrics';
import {
  ChartContainer,
  ChartLegendContent,
  ChartStyle,
  ChartTooltipContent,
} from '@/components/ui/chart';
import { TooltipProvider } from '@/components/ui/tooltip';
import { isCompiledByReactCompiler } from '../fixtures/react-compiler';

const navigate = vi.hoisted(() => vi.fn());

vi.mock('@tanstack/react-router', () => ({ useNavigate: () => navigate }));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getRepositoryRuns: vi.fn(),
}));

// Recharts draws bars only after measuring the page and running its
// animations, neither of which jsdom does. Show the data a bar chart receives
// instead, with a button per bar that clicks it.
vi.mock('recharts', async (importOriginal) => ({
  ...(await importOriginal<typeof import('recharts')>()),
  BarChart: ({
    data,
    onClick,
  }: {
    data?: Record<string, unknown>[];
    onClick?: (state: { activeLabel: string }) => void;
  }) => (
    <ul>
      {data?.map((entry) => (
        <li key={JSON.stringify(entry)}>
          <button
            onClick={() => onClick?.({ activeLabel: String(entry.runId) })}
          >
            {JSON.stringify(entry)}
          </button>
        </li>
      ))}
    </ul>
  ),
}));

const vulnerability = (vector: string, epss?: number): Vulnerability => ({
  externalId: 'CVE-2026-0001',
  references: [
    { url: 'https://example.com/cvss', score: 9.0, vector },
    ...(epss === undefined
      ? []
      : [
          {
            url: 'https://example.com/epss',
            scoringSystem: 'EPSS',
            score: epss,
            vector: String(epss),
          },
        ]),
  ],
});

const CVSS_31 = 'CVSS:3.1/AV:N/AC:L/PR:N/UI:N/S:U/C:H/I:H/A:H';
const CVSS_40 =
  'CVSS:4.0/AV:N/AC:L/AT:N/PR:N/UI:N/VC:H/VI:H/VA:H/SC:N/SI:N/SA:N';

// `ResponsiveContainer` draws the chart only once it has measured a size.
beforeEach(() => {
  vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue({
    width: 400,
    height: 200,
  } as DOMRect);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('charts', () => {
  it.each([
    ['Cvss23RadarChart', Cvss23RadarChart],
    ['Cvss4RadarChart', Cvss4RadarChart],
    ['Cvss4VectorCard', Cvss4VectorCard],
    ['EpssChart', EpssChart],
    ['VulnerabilityMetrics', VulnerabilityMetrics],
    ['JobDurations', JobDurations],
    ['ChartContainer', ChartContainer],
    ['ChartStyle', ChartStyle],
    ['ChartTooltipContent', ChartTooltipContent],
    ['ChartLegendContent', ChartLegendContent],
  ])('compiles %s with React Compiler', (_, component) => {
    expect(isCompiledByReactCompiler(component)).toBe(true);
  });
});

describe('VulnerabilityMetrics', () => {
  it('shows the metrics of a new vulnerability', async () => {
    const user = userEvent.setup();
    const { rerender } = render(
      <TooltipProvider>
        <VulnerabilityMetrics vulnerability={vulnerability(CVSS_31, 0.2)} />
      </TooltipProvider>
    );

    expect(screen.getByText(/CVSS 3\.1 Severity Radar/)).toBeInTheDocument();
    expect(screen.getByText(/"score":20/)).toBeInTheDocument();

    rerender(
      <TooltipProvider>
        <VulnerabilityMetrics vulnerability={vulnerability(CVSS_40, 0.7)} />
      </TooltipProvider>
    );

    expect(screen.queryByText(/CVSS 3\.1 Severity Radar/)).toBeNull();
    expect(screen.getByText(/4\.0 Severity Radar/)).toBeInTheDocument();
    expect(screen.getByText(/"score":70/)).toBeInTheDocument();
    expect(screen.getByText(/"percentile":70/)).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'Macrovector' }));

    expect(screen.getByText('000200')).toBeInTheDocument();
  });
});

describe('Cvss4VectorCard', () => {
  it('shows a new macro vector', () => {
    const macroVector = {
      name: '000000',
      exploitability: 'High',
      complexity: 'High',
      vulnerableSystem: 'High',
      subsequentSystem: 'High',
      exploitation: 'High',
      sequrityRequirements: 'High',
    };
    const { rerender } = render(
      <TooltipProvider>
        <Cvss4VectorCard macroVector={macroVector} />
      </TooltipProvider>
    );

    expect(screen.getAllByText('High')).toHaveLength(6);

    rerender(
      <TooltipProvider>
        <Cvss4VectorCard
          macroVector={{ ...macroVector, name: '111111', complexity: 'Low' }}
        />
      </TooltipProvider>
    );

    expect(screen.getByText('111111')).toBeInTheDocument();
    expect(screen.getAllByText('High')).toHaveLength(5);
    expect(screen.getByText('Low')).toBeInTheDocument();
  });
});

describe('EpssChart', () => {
  it('shows new EPSS data', () => {
    const { rerender } = render(
      <EpssChart epssData={{ score: 0.1, percentile: 0.4 }} />
    );

    expect(screen.getByText(/"percentile":40/)).toBeInTheDocument();

    rerender(<EpssChart epssData={{ score: 0.1, percentile: 0.9 }} />);

    expect(screen.getByText(/"percentile":90/)).toBeInTheDocument();
    expect(screen.queryByText(/"percentile":40/)).toBeNull();
  });
});

describe('ChartContainer', () => {
  it('passes the colors of a new configuration to the chart style', () => {
    const chart = (color: string) => (
      <ChartContainer id='test' config={{ score: { color } }}>
        <div />
      </ChartContainer>
    );
    const { container, rerender } = render(chart('red'));
    const style = () => container.querySelector('style')?.textContent;

    expect(style()).toContain('--color-score: red;');

    rerender(chart('blue'));

    expect(style()).toContain('--color-score: blue;');
    expect(style()).not.toContain('red');
  });
});

describe('JobDurations', () => {
  const run = (index: number): OrtRunSummary =>
    ({
      id: index,
      index,
      organizationId: 1,
      productId: 2,
      repositoryId: 3,
      createdAt: '2026-01-01T00:00:00Z',
      finishedAt: '2026-01-01T00:10:00Z',
      revision: 'main',
      status: 'FINISHED',
      labels: {},
      jobs: {
        analyzer: {
          id: index,
          createdAt: '2026-01-01T00:00:00Z',
          startedAt: '2026-01-01T00:01:00Z',
          finishedAt: '2026-01-01T00:05:00Z',
          status: 'FINISHED',
        },
      },
    }) as OrtRunSummary;

  const respondWithRuns = (...runs: OrtRunSummary[]) =>
    vi.mocked(getRepositoryRuns).mockResolvedValue({
      data: {
        data: runs,
        pagination: { limit: 10, offset: 0, totalCount: runs.length },
      },
    } as Awaited<ReturnType<typeof getRepositoryRuns>>);

  const renderJobDurations = () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const wrapper = ({ children }: { children: ReactNode }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
    render(<JobDurations repoId='3' pageIndex={0} pageSize={10} />, {
      wrapper,
    });

    return queryClient;
  };

  const bar = (runIndex: number) =>
    screen.findByRole('button', { name: new RegExp(`"runId":${runIndex},`) });

  it('shows the durations of newly loaded runs', async () => {
    respondWithRuns(run(1));
    const queryClient = renderJobDurations();

    expect(await bar(1)).toBeInTheDocument();

    respondWithRuns(run(1), run(2));
    await queryClient.refetchQueries();

    expect(await bar(2)).toBeInTheDocument();
  });

  it('navigates to the run of a clicked bar', async () => {
    const user = userEvent.setup();
    respondWithRuns(run(1), run(2));
    renderJobDurations();

    await user.click(await bar(2));

    expect(navigate).toHaveBeenCalledWith(
      expect.objectContaining({
        params: { orgId: '1', productId: '2', repoId: '3', runIndex: '2' },
      })
    );
  });
});
