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

import { createFileRoute } from '@tanstack/react-router';

import {
  getFileListsOptions,
  getFileListsQueryKey,
  getFileListStatisticsOptions,
  getFileListStatisticsQueryKey,
} from '@/api/@tanstack/react-query.gen';
import { deleteFileList, getFileListStatistics } from '@/api/sdk.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  loadProvenanceFiles,
  provenanceFilesLoaderDeps,
  ProvenanceFilesPage,
  type ProvenanceFilesConfig,
} from '@/components/provenance-files';
import { adminProvenanceFilesSearchParameterSchema } from '@/schemas';

const fileListsConfig: ProvenanceFilesConfig<
  ReturnType<typeof getFileListsQueryKey>,
  ReturnType<typeof getFileListStatisticsQueryKey>
> = {
  title: 'File Lists',
  description:
    'File lists contain the paths and checksums of all files of the provenances scanned by ORT Server. They are created by the scanner and used by other components of ORT Server when processing scan results. Deleted file lists are recreated when the provenance is scanned again.',
  thingName: 'file list',
  to: '/admin/file-lists',
  filesQueryOptions: (query) => getFileListsOptions({ query }),
  filesQueryKey: getFileListsQueryKey(),
  deleteFile: (id) => deleteFileList({ path: { id }, throwOnError: true }),
  statisticsQueryOptions: () => getFileListStatisticsOptions(),
  refreshStatistics: () =>
    getFileListStatistics({
      query: { refresh: true },
      throwOnError: true,
    }).then(({ data }) => data),
};

const FileListsComponent = () => (
  <ProvenanceFilesPage config={fileListsConfig} search={Route.useSearch()} />
);

export const Route = createFileRoute('/admin/file-lists/')({
  validateSearch: adminProvenanceFilesSearchParameterSchema,
  loaderDeps: provenanceFilesLoaderDeps,
  loader: ({ context: { queryClient }, deps }) =>
    loadProvenanceFiles(queryClient, fileListsConfig, deps),
  component: FileListsComponent,
  pendingComponent: LoadingIndicator,
});
