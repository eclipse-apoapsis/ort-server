/*
 * Copyright (C) 2025 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

import { PackageIdType } from '@/schemas';

type State = {
  packageIdType: PackageIdType;
};

type Actions = {
  setPackageIdType: (type: PackageIdType) => void;
};

export const useUserSettingsStore = create<State & Actions>()(
  persist(
    (set) => ({
      packageIdType: 'ORT_ID',
      setPackageIdType: (type) => set({ packageIdType: type }),
    }),
    {
      name: 'user-settings-storage',
      version: 1,
    }
  )
);
