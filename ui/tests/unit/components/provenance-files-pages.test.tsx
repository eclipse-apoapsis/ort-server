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

import { screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { ProvenanceFile } from '@/api';
import { Route as FileArchivesRoute } from '@/routes/admin/file-archives';
import { Route as FileListsRoute } from '@/routes/admin/file-lists';
import { adminProvenanceFilesSearchParameterSchema } from '@/schemas';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getFileArchives: vi.fn(),
  getFileLists: vi.fn(),
  deleteFileArchive: vi.fn(),
  deleteFileList: vi.fn(),
  toastInfo: vi.fn(),
  toastError: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getFileArchives: mocks.getFileArchives,
  getFileLists: mocks.getFileLists,
  deleteFileArchive: mocks.deleteFileArchive,
  deleteFileList: mocks.deleteFileList,
}));

vi.mock('@/lib/toast', () => ({
  toast: { info: mocks.toastInfo },
  toastError: mocks.toastError,
}));

const pages = [
  {
    name: 'file archives',
    path: '/admin/file-archives/',
    route: FileArchivesRoute,
    getFiles: mocks.getFileArchives,
    deleteFile: mocks.deleteFileArchive,
  },
  {
    name: 'file lists',
    path: '/admin/file-lists/',
    route: FileListsRoute,
    getFiles: mocks.getFileLists,
    deleteFile: mocks.deleteFileList,
  },
];

const createFile = (index: number): ProvenanceFile => ({
  id: `file-${index}`,
  key: `vcs|Git|https://example.org/repo-${index}.git|abc`,
  kind: 'VCS',
  url: `https://example.org/repo-${index}.git`,
  vcsType: 'Git',
  revision: 'abc',
  sizeInBytes: 1024 * index,
  lastModified: '2026-01-01T00:00:00Z',
});

const files = [createFile(1), createFile(2)];

const renderPage = (page: (typeof pages)[number]) => {
  const Component = page.route.options.component!;

  return renderInteractiveWithRouter(<Component />, {
    path: page.path,
    routes: [
      {
        path: page.path,
        validateSearch: adminProvenanceFilesSearchParameterSchema.parse,
      },
    ],
    withQueryClient: true,
  });
};

type User = ReturnType<typeof renderPage>['user'];

/** Click the delete button of the file with the given URL and confirm the deletion. */
const deleteFile = async (user: User, url: string) => {
  const row = (await screen.findByText(url)).closest('tr')!;
  await user.click(within(row).getByRole('button', { name: 'Delete' }));

  const dialog = await screen.findByRole('alertdialog');
  expect(dialog).toHaveTextContent('currently active runs that depend on this');
  await user.click(within(dialog).getByRole('button', { name: 'Delete' }));
};

describe.each(pages)('the $name page', (page) => {
  beforeEach(() => {
    vi.clearAllMocks();
    page.getFiles.mockResolvedValue(pageOf(files, { limit: 20 }));
    page.deleteFile.mockResolvedValue({ data: undefined });
  });

  it('deletes the selected file after confirmation and fetches the files again', async () => {
    const { user } = renderPage(page);

    await deleteFile(user, 'https://example.org/repo-2.git');

    expect(page.deleteFile).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ path: { id: 'file-2' } })
    );
    await waitFor(() => expect(page.getFiles).toHaveBeenCalledTimes(2));
    expect(mocks.toastInfo).toHaveBeenCalledOnce();
    expect(mocks.toastError).not.toHaveBeenCalled();
  });

  it('does not delete the file if the deletion is cancelled', async () => {
    const { user } = renderPage(page);
    const row = (
      await screen.findByText('https://example.org/repo-1.git')
    ).closest('tr')!;

    await user.click(within(row).getByRole('button', { name: 'Delete' }));
    await user.click(
      within(await screen.findByRole('alertdialog')).getByRole('button', {
        name: 'Cancel',
      })
    );

    expect(page.deleteFile).not.toHaveBeenCalled();
    expect(page.getFiles).toHaveBeenCalledOnce();
  });

  it('shows an error and keeps the files if the deletion fails', async () => {
    page.deleteFile.mockRejectedValue(new Error('Access denied.'));
    const { user } = renderPage(page);

    await deleteFile(user, 'https://example.org/repo-1.git');

    await waitFor(() =>
      expect(mocks.toastError).toHaveBeenCalledWith(
        'Access denied.',
        expect.any(Error)
      )
    );
    expect(mocks.toastInfo).not.toHaveBeenCalled();
    expect(page.getFiles).toHaveBeenCalledOnce();
  });
});
