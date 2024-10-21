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

import { Loader2 } from 'lucide-react';

import { useRepositoriesServiceGetOrtRunsByRepositoryId } from '@/api/queries';
import { Badge } from '@/components/ui/badge';
import { config } from '@/config';
import { getStatusBackgroundColor } from '@/helpers/get-status-class';

export const LastRunStatus = ({ repoId }: { repoId: number }) => {
  const {
    data: runs,
    isPending: runsIsPending,
    isError: runsIsError,
  } = useRepositoriesServiceGetOrtRunsByRepositoryId(
    {
      repositoryId: repoId,
      limit: 1,
      sort: '-index',
    },
    undefined,
    {
      refetchInterval: (query) => {
        const curData = query.state.data?.data;
        if (curData && curData.length > 0 && curData[0].finishedAt) {
          return undefined;
        }
        return config.pollInterval;
      },
    }
  );

  if (runsIsPending) {
    return (
      <>
        <span className='sr-only'>Loading...</span>
        <Loader2 size={16} className='mx-3 animate-spin' />
      </>
    );
  }

  if (runsIsError) {
    return <span>Error loading run.</span>;
  }

  if (runs.data.length === 0) return null;

  const run = runs.data[0];

  return (
    <Badge
      className={`border ${getStatusBackgroundColor(run.status)} hover:bg-${getStatusBackgroundColor(run.status)}`}
    >
      {run.status}
    </Badge>
  );
};
