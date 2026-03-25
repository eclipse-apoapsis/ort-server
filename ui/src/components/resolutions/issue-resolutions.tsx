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

import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';

import {
  deleteIssueResolutionForRepositoryMutation,
  getRunIssuesQueryKey,
} from '@/api/@tanstack/react-query.gen';
import {
  hasIssueResolutionActivity,
  ItemWithResolutions,
} from '@/helpers/resolutions';
import { ApiError } from '@/lib/api-error';
import { toast, toastError } from '@/lib/toast';
import { ResolutionCard, ResolutionForm } from './shared';
import {
  getDisplayItems,
  getManagedResolutionContext,
  ManagedResolutionContext,
} from './utils';

type IssueResolutionsProps = {
  item: Extract<ItemWithResolutions, { source: unknown }>;
  repositoryId: string;
  runId: number;
};

export function IssueResolutions({
  item,
  repositoryId,
  runId,
}: IssueResolutionsProps) {
  const [editingKey, setEditingKey] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const createContext: ManagedResolutionContext = {
    itemType: 'issue',
    identifier: item.message,
    repositoryId,
    runId,
  };
  const displayItems = getDisplayItems(item, true);
  const hasAnyResolution = hasIssueResolutionActivity(item);

  const { mutateAsync: deleteResolution } = useMutation({
    ...deleteIssueResolutionForRepositoryMutation(),
    onSuccess() {
      queryClient.invalidateQueries({
        queryKey: getRunIssuesQueryKey({ path: { runId } }),
      });
      toast.info('Resolution deleted', {
        description: 'Issue resolution deleted successfully.',
      });
    },
    onError(error: ApiError) {
      toastError(error.message, error);
    },
  });

  return (
    <div className='flex flex-col gap-2'>
      {displayItems.map((displayItem) => {
        const context = getManagedResolutionContext(createContext, displayItem);

        return (
          <ResolutionCard
            key={displayItem.key}
            displayItem={displayItem}
            editingKey={editingKey}
            setEditingKey={setEditingKey}
            context={context}
            onDelete={
              context
                ? () =>
                    deleteResolution({
                      path: {
                        repositoryId: context.repositoryId,
                        messageHash: context.identifier,
                      },
                    })
                : undefined
            }
          />
        );
      })}
      {!hasAnyResolution && (
        <ResolutionForm
          context={createContext}
          mode='create'
          defaultValues={{
            comment: '',
            reason: 'CANT_FIX_ISSUE',
          }}
        />
      )}
    </div>
  );
}
