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
  getFileArchivesOptions,
  getFileArchivesQueryKey,
  getFileArchiveStatisticsOptions,
  getFileArchiveStatisticsQueryKey,
} from '@/api/@tanstack/react-query.gen';
import { deleteFileArchive, getFileArchiveStatistics } from '@/api/sdk.gen';
import { LoadingIndicator } from '@/components/loading-indicator';
import {
  loadProvenanceFiles,
  provenanceFilesLoaderDeps,
  ProvenanceFilesPage,
  type ProvenanceFilesConfig,
} from '@/components/provenance-files';
import { adminProvenanceFilesSearchParameterSchema } from '@/schemas';

const fileArchivesConfig: ProvenanceFilesConfig<
  ReturnType<typeof getFileArchivesQueryKey>,
  ReturnType<typeof getFileArchiveStatisticsQueryKey>
> = {
  title: 'File Archives',
  description:
    'File archives contain the license files and other relevant files of the provenances scanned by ORT Server. They are created by the scanner and used by the reporter, for instance to include license texts in reports. Deleted file archives are recreated when the provenance is scanned again.',
  thingName: 'file archive',
  to: '/admin/file-archives',
  filesQueryOptions: (query) => getFileArchivesOptions({ query }),
  filesQueryKey: getFileArchivesQueryKey(),
  deleteFile: (id) => deleteFileArchive({ path: { id }, throwOnError: true }),
  statisticsQueryOptions: () => getFileArchiveStatisticsOptions(),
  refreshStatistics: () =>
    getFileArchiveStatistics({
      query: { refresh: true },
      throwOnError: true,
    }).then(({ data }) => data),
};

const FileArchivesComponent = () => (
  <ProvenanceFilesPage config={fileArchivesConfig} search={Route.useSearch()} />
);

export const Route = createFileRoute('/admin/file-archives/')({
  validateSearch: adminProvenanceFilesSearchParameterSchema,
  loaderDeps: provenanceFilesLoaderDeps,
  loader: ({ context: { queryClient }, deps }) =>
    loadProvenanceFiles(queryClient, fileArchivesConfig, deps),
  component: FileArchivesComponent,
  pendingComponent: LoadingIndicator,
});
