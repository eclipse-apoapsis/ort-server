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

import { useQuery } from '@tanstack/react-query';
import { getRouteApi, Link } from '@tanstack/react-router';

import { Identifier } from '@/api';
import { getRunDetectedLicensesForIdentifierOptions } from '@/api/@tanstack/react-query.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';
import { identifierToString } from '@/helpers/identifier-conversion';
import { SpdxExpressionBadgeGroup } from './spdx-expression-badge-group';

const routeApi = getRouteApi(
  '/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex'
);

export type LicensesAccordionProps = {
  runId: number;
  identifier: Identifier;
  declaredLicenses: string[];
};

export const LicensesAccordion = ({
  runId,
  identifier,
  declaredLicenses,
}: LicensesAccordionProps) => {
  const params = routeApi.useParams();
  const identifierString = identifierToString(identifier);
  const { data: licenses, isPending } = useQuery({
    ...getRunDetectedLicensesForIdentifierOptions({
      path: { runId, identifier: identifierString },
    }),
  });

  return (
    <Accordion type='multiple' className='w-full'>
      <AccordionItem value='licenses'>
        <AccordionTrigger className='py-0 font-bold'>Licenses</AccordionTrigger>
        <AccordionContent>
          <div className='grid grid-cols-[auto_minmax(0,1fr)] items-baseline gap-x-2 gap-y-2 pt-2 pl-2 text-sm'>
            <div className='font-semibold'>Declared:</div>
            {declaredLicenses.length ? (
              <div className='flex flex-wrap gap-1'>
                {declaredLicenses.map((license, index) => (
                  <SpdxExpressionBadgeGroup
                    key={`${license}-${index}`}
                    expression={license}
                    suffix={
                      index < declaredLicenses.length - 1 ? ',' : undefined
                    }
                  />
                ))}
              </div>
            ) : (
              <div className='text-muted-foreground italic'>
                No declared licenses.
              </div>
            )}

            <div className='font-semibold'>Detected:</div>
            {isPending ? (
              <LoadingIndicator />
            ) : licenses?.length ? (
              <div className='flex flex-wrap gap-1'>
                {licenses.map((license, index) => (
                  <Link
                    key={`${license}-${index}`}
                    className='hover:opacity-80'
                    to='/organizations/$orgId/products/$productId/repositories/$repoId/runs/$runIndex/license-findings'
                    params={params}
                    search={{
                      detectedLicense: [license],
                      marked: license,
                      packageMarked: identifierString,
                      page: 1,
                      packagePage: 1,
                      findingsPage: 1,
                    }}
                  >
                    <SpdxExpressionBadgeGroup
                      expression={license}
                      suffix={index < licenses.length - 1 ? ',' : undefined}
                    />
                  </Link>
                ))}
              </div>
            ) : (
              <div className='text-muted-foreground italic'>
                No licenses detected.
              </div>
            )}
          </div>
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
};
