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
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { ProvenanceFile, ProvenanceFileStatistics } from '@/api';
import { Route as FileArchivesRoute } from '@/routes/admin/file-archives';
import { Route as FileListsRoute } from '@/routes/admin/file-lists';
import { adminProvenanceFilesSearchParameterSchema } from '@/schemas';
import { renderInteractiveWithRouter } from '../fixtures/render-interactive';
import { pageOf } from '../fixtures/table';

const mocks = vi.hoisted(() => ({
  getFileArchives: vi.fn(),
  getFileLists: vi.fn(),
  downloadFileArchive: vi.fn(),
  downloadFileList: vi.fn(),
  deleteFileArchive: vi.fn(),
  deleteFileList: vi.fn(),
  getFileArchiveStatistics: vi.fn(),
  getFileListStatistics: vi.fn(),
  toastInfo: vi.fn(),
  toastError: vi.fn(),
}));

vi.mock('@/api/sdk.gen', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/api/sdk.gen')>()),
  getFileArchives: mocks.getFileArchives,
  getFileLists: mocks.getFileLists,
  downloadFileArchive: mocks.downloadFileArchive,
  downloadFileList: mocks.downloadFileList,
  deleteFileArchive: mocks.deleteFileArchive,
  deleteFileList: mocks.deleteFileList,
  getFileArchiveStatistics: mocks.getFileArchiveStatistics,
  getFileListStatistics: mocks.getFileListStatistics,
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
    downloadFile: mocks.downloadFileArchive,
    defaultFileName: 'file-archive.zip',
    deleteFile: mocks.deleteFileArchive,
    getStatistics: mocks.getFileArchiveStatistics,
  },
  {
    name: 'file lists',
    path: '/admin/file-lists/',
    route: FileListsRoute,
    getFiles: mocks.getFileLists,
    downloadFile: mocks.downloadFileList,
    defaultFileName: 'file-list.yml',
    deleteFile: mocks.deleteFileList,
    getStatistics: mocks.getFileListStatistics,
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

/** Create statistics about the given number of VCS files. */
const createStatistics = (count: number): ProvenanceFileStatistics => ({
  snapshotTime: '2026-01-01T12:00:00Z',
  totalCount: count,
  totalSizeInBytes: 1024 * count,
  averageSizeInBytes: 1024,
  byKind: [{ kind: 'VCS', count, sizeInBytes: 1024 * count }],
  topUrls: [],
});

/** The calls of the given statistics mock that listed the storage again. */
const refreshCalls = (getStatistics: typeof mocks.getFileArchiveStatistics) =>
  getStatistics.mock.calls.filter(([options]) => options?.query?.refresh);

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

/** Click the download button of the file with the given URL. */
const downloadFile = async (user: User, url: string) => {
  const row = (await screen.findByText(url)).closest('tr')!;
  await user.click(within(row).getByRole('button', { name: 'Download' }));
};

describe.each(pages)('the $name page', (page) => {
  /** The names of the files that were saved, in the order of the downloads. */
  let savedFileNames: string[];

  beforeEach(() => {
    savedFileNames = [];
    vi.spyOn(window.URL, 'createObjectURL').mockReturnValue('blob:file');
    vi.spyOn(window.URL, 'revokeObjectURL').mockReturnValue(undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (
      this: HTMLAnchorElement
    ) {
      savedFileNames.push(this.download);
    });

    vi.clearAllMocks();
    page.getFiles.mockResolvedValue(pageOf(files, { limit: 20 }));
    page.deleteFile.mockResolvedValue({ data: undefined });
    page.getStatistics.mockResolvedValue({ data: createStatistics(2) });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('saves the downloaded file with the name suggested by the server', async () => {
    const data = new Blob(['content']);
    page.downloadFile.mockResolvedValue({
      data,
      headers: { 'content-disposition': 'attachment; filename=repo-2-abc.zip' },
    });
    const { user } = renderPage(page);

    await downloadFile(user, 'https://example.org/repo-2.git');

    expect(page.downloadFile).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ path: { id: 'file-2' }, responseType: 'blob' })
    );
    await waitFor(() => expect(savedFileNames).toEqual(['repo-2-abc.zip']));
    expect(window.URL.createObjectURL).toHaveBeenCalledExactlyOnceWith(data);
    expect(mocks.toastError).not.toHaveBeenCalled();
  });

  it('saves the downloaded file with a default name if the server suggests none', async () => {
    page.downloadFile.mockResolvedValue({ data: new Blob([]), headers: {} });
    const { user } = renderPage(page);

    await downloadFile(user, 'https://example.org/repo-1.git');

    await waitFor(() => expect(savedFileNames).toEqual([page.defaultFileName]));
  });

  it('shows an error if the download fails', async () => {
    page.downloadFile.mockRejectedValue(new Error('Not found.'));
    const { user } = renderPage(page);

    await downloadFile(user, 'https://example.org/repo-1.git');

    await waitFor(() => expect(mocks.toastError).toHaveBeenCalledOnce());
    expect(savedFileNames).toEqual([]);
  });

  it('deletes the selected file after confirmation and fetches the files again', async () => {
    const { user } = renderPage(page);

    await deleteFile(user, 'https://example.org/repo-2.git');

    expect(page.deleteFile).toHaveBeenCalledExactlyOnceWith(
      expect.objectContaining({ path: { id: 'file-2' } })
    );
    await waitFor(() => expect(page.getFiles).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(page.getStatistics).toHaveBeenCalledTimes(2));
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

  it('shows the size per kind of provenance', async () => {
    renderPage(page);

    expect(await screen.findByText(/VCS: 2 \(.+\)/)).toBeInTheDocument();
  });

  it('lists the storage again on refresh and uses the returned statistics', async () => {
    const { user } = renderPage(page);
    await screen.findByText(/VCS: 2 /);
    page.getStatistics.mockResolvedValue({ data: createStatistics(7) });

    await user.click(screen.getByRole('button', { name: 'Refresh' }));

    expect(await screen.findByText(/VCS: 7 /)).toBeInTheDocument();
    await waitFor(() => expect(page.getFiles).toHaveBeenCalledTimes(2));
    expect(refreshCalls(page.getStatistics)).toHaveLength(1);
    // The statistics are only requested on loading the page and on the refresh.
    expect(page.getStatistics).toHaveBeenCalledTimes(2);
  });

  it('fetches the files again only after the storage has been listed again', async () => {
    const { user } = renderPage(page);
    await screen.findByText(/VCS: 2 /);
    let finishRefresh: () => void = () => {};
    page.getStatistics.mockReturnValue(
      new Promise((resolve) => {
        finishRefresh = () => resolve({ data: createStatistics(7) });
      })
    );

    await user.click(screen.getByRole('button', { name: 'Refresh' }));

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Refresh' })).toBeDisabled()
    );
    expect(page.getFiles).toHaveBeenCalledOnce();

    finishRefresh();

    await waitFor(() => expect(page.getFiles).toHaveBeenCalledTimes(2));
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Refresh' })).toBeEnabled()
    );
  });

  it('shows an error and enables the button again if the refresh fails', async () => {
    const { user } = renderPage(page);
    await screen.findByText(/VCS: 2 /);
    page.getStatistics.mockRejectedValue(new Error('Access denied.'));

    await user.click(screen.getByRole('button', { name: 'Refresh' }));

    await waitFor(() =>
      expect(mocks.toastError).toHaveBeenCalledWith(
        `Refreshing the ${page.name} failed.`,
        expect.any(Error)
      )
    );
    expect(screen.getByRole('button', { name: 'Refresh' })).toBeEnabled();
    expect(screen.getByText(/VCS: 2 /)).toBeInTheDocument();
    expect(page.getFiles).toHaveBeenCalledOnce();
  });
});
