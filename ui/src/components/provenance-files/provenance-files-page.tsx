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

import {
  QueryKey,
  useMutation,
  useQueryClient,
  useSuspenseQuery,
} from '@tanstack/react-query';
import { useNavigate } from '@tanstack/react-router';
import { useCallback, useState } from 'react';

import { ProvenanceFileStatistics } from '@/api';
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from '@/components/ui/card';
import { updateColumnSorting } from '@/helpers/handle-multisort';
import { routePrefetchStaleTime } from '@/lib/query-client';
import { toast, toastError } from '@/lib/toast';
import { AdminProvenanceFilesSearchParameters } from '@/schemas';
import { ProvenanceFileStatisticsView } from './provenance-file-statistics';
import {
  ProvenanceFilesConfig,
  provenanceFilesDefaultPageSize,
  toProvenanceFilesQuery,
} from './provenance-files-route';
import { ProvenanceFilesTable } from './provenance-files-table';

type ProvenanceFilesPageProps<
  TFilesQueryKey extends QueryKey,
  TStatisticsQueryKey extends QueryKey,
> = {
  /** The configuration for the type of files shown on the page. */
  config: ProvenanceFilesConfig<TFilesQueryKey, TStatisticsQueryKey>;
  /** The current search parameters of the page. */
  search: AdminProvenanceFilesSearchParameters;
};

/**
 * A page to manage stored provenance files, such as file archives or file lists.
 */
export const ProvenanceFilesPage = <
  TFilesQueryKey extends QueryKey,
  TStatisticsQueryKey extends QueryKey,
>({
  config,
  search,
}: ProvenanceFilesPageProps<TFilesQueryKey, TStatisticsQueryKey>) => {
  const { title, description, thingName, to } = config;
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data: files } = useSuspenseQuery({
    ...config.filesQueryOptions(toProvenanceFilesQuery(search)),
    staleTime: routePrefetchStaleTime,
  });

  const { data: statistics } = useSuspenseQuery({
    ...config.statisticsQueryOptions(),
    staleTime: routePrefetchStaleTime,
  });

  const { mutateAsync: deleteFile } = useMutation({
    mutationFn: config.deleteFile,
    onSuccess() {
      toast.info(`Delete ${thingName}`, {
        description: `The ${thingName} was deleted successfully.`,
      });
      queryClient.invalidateQueries({ queryKey: config.filesQueryKey });
      queryClient.invalidateQueries({
        queryKey: config.statisticsQueryOptions().queryKey,
      });
    },
    onError(error) {
      toastError(error.message, error);
    },
  });

  // A failed deletion is reported by onError, so the error is not passed on to the delete dialog, where it would be
  // unhandled. The callback has to be stable, as the columns of the table are recreated when it changes.
  const onDelete = useCallback(
    (id: string) => deleteFile(id).catch(() => undefined),
    [deleteFile]
  );

  const [isRefreshing, setIsRefreshing] = useState(false);

  // The refresh returns the new statistics, so they are stored directly instead of fetching them again. Only the files
  // have to be fetched again, after the storage has been listed again.
  const refresh = async () => {
    setIsRefreshing(true);

    try {
      const refreshedStatistics = await config.refreshStatistics();
      queryClient.setQueryData<ProvenanceFileStatistics>(
        config.statisticsQueryOptions().queryKey,
        refreshedStatistics
      );
      await queryClient.invalidateQueries({ queryKey: config.filesQueryKey });
    } catch (error) {
      toastError(`Refreshing the ${thingName}s failed.`, error);
    } finally {
      setIsRefreshing(false);
    }
  };

  const filterBy = (filter: string | undefined) =>
    navigate({ to, search: { ...search, page: 1, filter } });

  return (
    <Card className='h-fit'>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className='flex flex-col gap-6'>
        <ProvenanceFileStatisticsView
          statistics={statistics}
          onRefresh={refresh}
          isRefreshing={isRefreshing}
          onFilter={filterBy}
        />
        <ProvenanceFilesTable
          files={files.data}
          totalCount={files.pagination.totalCount}
          search={search}
          defaultPageSize={provenanceFilesDefaultPageSize}
          thingName={thingName}
          onDelete={onDelete}
          onFilterChange={filterBy}
          setCurrentPageOptions={(currentPage) => ({
            to,
            search: { ...search, page: currentPage },
          })}
          setPageSizeOptions={(size) => ({
            to,
            search: { ...search, page: 1, pageSize: size },
          })}
          setSortingOptions={(sortBy) => ({
            to,
            search: {
              ...search,
              page: 1,
              sortBy: updateColumnSorting(search.sortBy, sortBy),
            },
          })}
        />
      </CardContent>
    </Card>
  );
};
