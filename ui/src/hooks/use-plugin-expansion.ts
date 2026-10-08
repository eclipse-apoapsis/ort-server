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

import { useState } from 'react';

type UsePluginExpansionArgs = {
  /** The ids of the plugins which currently have settings to show and can be expanded. */
  expandableIds: readonly string[];
  /** The ids of the plugins which are expanded at first. Later changes are ignored. */
  initiallyExpandedIds: readonly string[];
  /** The number of times the form was submitted. Each new submit expands `idsWithErrors`. */
  submitCount: number;
  /** The ids of the plugins whose options have validation errors. */
  idsWithErrors: readonly string[];
};

/**
 * Manage which plugins of a plugin multi-select field show their options. After each submit,
 * plugins with validation errors are expanded, so that their error messages are visible.
 */
export function usePluginExpansion({
  expandableIds,
  initiallyExpandedIds,
  submitCount,
  idsWithErrors,
}: UsePluginExpansionArgs) {
  const [expandedIds, setExpandedIds] = useState<string[]>(() => [
    ...initiallyExpandedIds,
  ]);
  const [lastSubmitCount, setLastSubmitCount] = useState(submitCount);

  // Expand the plugins with errors after each new submit. The state is adjusted during
  // rendering instead of in an effect, so the expanded options show up in the same render.
  if (submitCount !== lastSubmitCount) {
    setLastSubmitCount(submitCount);

    const expandedIdSet = new Set(expandedIds);
    const newIds = idsWithErrors.filter(
      (id) => expandableIds.includes(id) && !expandedIdSet.has(id)
    );

    if (newIds.length > 0) {
      setExpandedIds([...expandedIds, ...newIds]);
    }
  }

  // Expand or collapse one plugin. The caller decides whether the plugin has anything to
  // expand, because this can change in the same event, for example when a plugin is enabled.
  const setExpanded = (pluginId: string, isExpanded: boolean) => {
    const otherIds = expandedIds.filter((id) => id !== pluginId);

    setExpandedIds(isExpanded ? [...otherIds, pluginId] : otherIds);
  };

  const setAllExpanded = (isExpanded: boolean) =>
    setExpandedIds(isExpanded ? [...expandableIds] : []);

  const allExpanded =
    expandableIds.length > 0 &&
    expandableIds.every((id) => expandedIds.includes(id));

  return {
    expandedIds,
    setExpandedIds,
    setExpanded,
    setAllExpanded,
    allExpanded,
  };
}
