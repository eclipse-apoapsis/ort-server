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

import { QueryErrorResetBoundary } from '@tanstack/react-query';
import {
  CatchBoundary,
  type ErrorComponentProps,
} from '@tanstack/react-router';
import { TriangleAlert } from 'lucide-react';
import { Suspense, type ReactNode } from 'react';

import { Button } from '@/components/ui/button';
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from '@/components/ui/tooltip';

type QueryBoundaryProps = {
  children: ReactNode;
  fallback: ReactNode;
  errorFallback?: (props: ErrorComponentProps) => ReactNode;
  resetKey?: string | number;
  loadingLabel?: string;
};

export const QueryErrorFallback = ({ error, reset }: ErrorComponentProps) => {
  const retryLabel = `Could not load data: ${error.message}. Click to retry.`;

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <Button
          type='button'
          variant='ghost'
          size='xs'
          aria-label={retryLabel}
          onClick={reset}
        >
          <TriangleAlert className='text-traffic-light-red size-4' />
          Retry
        </Button>
      </TooltipTrigger>
      <TooltipContent>{retryLabel}</TooltipContent>
    </Tooltip>
  );
};

export const QueryBoundary = ({
  children,
  fallback,
  errorFallback,
  resetKey = 'reset',
  loadingLabel = 'Loading data...',
}: QueryBoundaryProps) => (
  <QueryErrorResetBoundary>
    {({ reset: resetQueries }) => (
      <CatchBoundary
        getResetKey={() => resetKey}
        errorComponent={({ error, reset }) => {
          const fallbackProps = {
            error,
            reset: () => {
              resetQueries();
              reset();
            },
          };

          return errorFallback ? (
            errorFallback(fallbackProps)
          ) : (
            <QueryErrorFallback {...fallbackProps} />
          );
        }}
      >
        <Suspense
          fallback={
            <>
              <span className='sr-only' role='status'>
                {loadingLabel}
              </span>
              {fallback}
            </>
          }
        >
          {children}
        </Suspense>
      </CatchBoundary>
    )}
  </QueryErrorResetBoundary>
);
