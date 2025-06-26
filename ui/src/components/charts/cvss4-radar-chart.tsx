/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import {
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
} from 'recharts';

import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import {
  ChartConfig,
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
} from '@/components/ui/chart';
import { Cvss4Result } from '@/helpers/vulnerability-statistics';

type Cvss4RadarChartProps = {
  cvssScore: Cvss4Result;
};

export const Cvss4RadarChart = ({ cvssScore }: Cvss4RadarChartProps) => {
  const { base, overall, environmental, threat } = cvssScore.scores;
  const version = `4.0`;
  const nomenclature = cvssScore.nomenclature;
  const url = 'https://www.first.org/cvss/v4-0/specification-document';

  // In the absence of reported Temporal and Environmental metrics, default these unspecified metrics to values
  // that do not alter the Base Score. This ensures that the overall severity assessment reflects the inherent
  // qualities of the vulnerability without additional modifiers.
  //
  // The CVSS v3.1 specification indicates that when Temporal and Environmental metrics are not defined,
  // they are treated in a way that the Base Score remains unaffected. Therefore, for the purpose of rendering
  // the radar chart, use the Base Score for these metrics, and indicate true data points with a dot.
  //
  // See: https://www.first.org/cvss/v3-1/specification-document
  const chartData = [
    { component: 'Base', score: base, dot: true },
    {
      component: 'Modified Impact',
      score: overall,
      dot: false,
    },
    { component: 'Impact', score: overall, dot: false },
    { component: 'Temporal', score: threat || base, dot: !!threat },
    { component: 'Exploitability', score: overall, dot: false },
    {
      component: 'Environmental',
      score: environmental || overall,
      dot: !!environmental,
    },
  ];

  const chartConfig = {
    score: {
      label: 'Score',
      color: 'hsl(var(--chart-1))',
    },
  } satisfies ChartConfig;

  return (
    <Card>
      <CardHeader className='items-center'>
        <CardTitle>
          {nomenclature} {version} Severity Radar (
          <a
            className='font-normal break-all text-blue-400 hover:underline'
            href={url}
            target='_blank'
          >
            details
          </a>
          )
        </CardTitle>
        <CardDescription>
          Severity scores from CVSS assessing impact and exploitability of the
          vulnerability.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ChartContainer
          config={chartConfig}
          className='mx-auto aspect-square max-h-[250px] w-full'
        >
          <RadarChart
            data={chartData}
            margin={{
              top: 10,
              right: 10,
              bottom: 10,
              left: 10,
            }}
          >
            <ChartTooltip
              cursor={false}
              content={(props) => (
                <ChartTooltipContent {...props} indicator='line' />
              )}
            />
            <PolarAngleAxis
              dataKey='component'
              tickFormatter={(value) => {
                return value === 'Base'
                  ? 'Base'
                  : value === 'Temporal'
                    ? 'Temp'
                    : value.slice(0, 3);
              }}
            />
            <PolarRadiusAxis
              style={{ visibility: 'hidden' }}
              angle={60}
              tickCount={6}
              domain={[0, 10]}
            />
            <PolarGrid radialLines={false} />
            <Radar
              dataKey='score'
              fill='hsl(var(--chart-1))'
              fillOpacity={0.2}
              stroke='hsl(var(--chart-1))'
              strokeWidth={2}
              dot={(props) => {
                const { payload } = props;
                return payload.payload.dot ? (
                  <circle cx={payload.x} cy={payload.y} r='6' fill='orange' />
                ) : (
                  <circle
                    cx={payload.x}
                    cy={payload.y}
                    r='4'
                    fill='hsl(var(--chart-1))'
                  />
                );
              }}
            />
          </RadarChart>
        </ChartContainer>
      </CardContent>
    </Card>
  );
};
