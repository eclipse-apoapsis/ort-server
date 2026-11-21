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

import { create } from 'zustand';

type State = {
  isOpen: boolean;
  // The topic shown in the help panel. It is kept when the panel closes, so that the panel keeps
  // its content while it slides out.
  topic: string | null;
  // The button that opened the panel, which gets the focus back when the panel closes.
  trigger: HTMLElement | null;
};

type Actions = {
  open: (topic: string, trigger: HTMLElement | null) => void;
  close: () => void;
};

export const useHelpPanelStore = create<State & Actions>()((set) => ({
  isOpen: false,
  topic: null,
  trigger: null,
  open: (topic, trigger) => set({ isOpen: true, topic, trigger }),
  close: () => set({ isOpen: false }),
}));
