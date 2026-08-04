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

/*
 * Cross-tab synchronization for persisted stores.
 *
 * A persisted store reads its local storage entry once while the page loads and then
 * writes its complete state back on every change. Without synchronization, a tab keeps
 * serving whatever it read at load time, so the state a second tab wrote in the meantime
 * is overwritten as soon as the first tab changes anything.
 *
 * Browsers fire a storage event in all other tabs of the same origin whenever one of them
 * writes to local storage. Re-reading the persisted state when that happens keeps the tabs
 * close enough to each other that they stop overwriting one another. Two tabs writing
 * within the same tick can still lose one change, which is accepted for stores that a
 * backend-backed provider is meant to replace.
 */

/** The part of a storage event needed to decide whether a store has to be re-read. */
type PersistedStorageEvent = Pick<StorageEvent, 'key' | 'storageArea'>;

/**
 * Create a storage event handler that re-reads the persisted state of the store using the
 * given storage entry.
 */
export const createCrossTabSyncHandler =
  (storageName: string, rehydrate: () => void) =>
  (event: PersistedStorageEvent) => {
    // Events of other storage areas, for example the session storage, are none of this
    // store's business.
    if (
      event.storageArea != null &&
      event.storageArea !== window.localStorage
    ) {
      return;
    }

    // A missing key means that the whole storage was cleared, which affects every store.
    if (event.key !== null && event.key !== storageName) return;

    rehydrate();
  };

/**
 * Re-read the persisted state of a store whenever another tab writes to its storage entry.
 *
 * The listener is registered for the lifetime of the page because the stores are created
 * once per page and live as long as it does.
 */
export const syncPersistedStoreAcrossTabs = (
  storageName: string,
  rehydrate: () => void
) => {
  if (typeof window === 'undefined') return;

  window.addEventListener(
    'storage',
    createCrossTabSyncHandler(storageName, rehydrate)
  );
};
