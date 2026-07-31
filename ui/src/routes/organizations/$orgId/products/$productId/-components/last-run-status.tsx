/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { QueryBoundary } from '@/components/query-boundary';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';
import { useLatestRepositoryRun } from '@/hooks/use-latest-repository-run';

export const LastRunStatus = ({ repoId }: { repoId: number }) => (
  <QueryBoundary
    fallback={<Skeleton className='h-5 w-16 rounded-md' />}
    resetKey={repoId}
  >
    <LastRunStatusInner repoId={repoId} />
  </QueryBoundary>
);

const LastRunStatusInner = ({ repoId }: { repoId: number }) => {
  const run = useLatestRepositoryRun(repoId);

  if (!run) return <div className='min-h-5' />;

  return (
    <Badge className={`border ${getStatusBackgroundColor(run.status)}`}>
      {run.status}
    </Badge>
  );
};
