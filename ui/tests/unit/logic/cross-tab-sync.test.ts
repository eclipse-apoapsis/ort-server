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

import { beforeEach, expect, it, vi } from 'vitest';

import { createCrossTabSyncHandler } from '@/store/cross-tab-sync';

const localStorage = {} as Storage;
const sessionStorage = {} as Storage;

vi.stubGlobal('window', { localStorage });

const STORAGE_NAME = 'home-recent-runs-storage';

const rehydrate = vi.fn();
const handleStorageEvent = createCrossTabSyncHandler(STORAGE_NAME, rehydrate);

beforeEach(() => {
  rehydrate.mockClear();
});

it('re-reads the state when another tab writes the storage entry', () => {
  handleStorageEvent({ key: STORAGE_NAME, storageArea: localStorage });

  expect(rehydrate).toHaveBeenCalledOnce();
});

it('re-reads the state when the whole storage was cleared', () => {
  handleStorageEvent({ key: null, storageArea: localStorage });

  expect(rehydrate).toHaveBeenCalledOnce();
});

it('ignores writes to the storage entry of another store', () => {
  handleStorageEvent({
    key: 'home-favorites-storage',
    storageArea: localStorage,
  });

  expect(rehydrate).not.toHaveBeenCalled();
});

it('ignores writes to another storage area', () => {
  handleStorageEvent({ key: STORAGE_NAME, storageArea: sessionStorage });

  expect(rehydrate).not.toHaveBeenCalled();
});

it('re-reads the state when the storage area is unknown', () => {
  handleStorageEvent({ key: STORAGE_NAME, storageArea: null });

  expect(rehydrate).toHaveBeenCalledOnce();
});
