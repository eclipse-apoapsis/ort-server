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

import diff from 'microdiff';
import { createContext, useContext } from 'react';

import { JobConfigurations, OrtRun } from '@/api';
import { pathStartsWith } from '@/helpers/config-diff/path';
import { unorderedStringListPaths } from '@/helpers/config-diff/unordered-string-lists';
import {
  ConfigPath,
  hiddenJobKeys,
  isEmpty,
  isLeafValue,
  isRecord,
  jobSections,
  lastKey,
} from './config-utils';

/**
 * What the server did to one element of the page while resolving the configuration: it set
 * something that was not requested, replaced a requested value, or dropped one. Unset, null and
 * empty values all count as not requested, as the server treats them alike.
 */
export type ServerChange =
  | { kind: 'added'; path: ConfigPath; requested: unknown }
  | { kind: 'changed'; path: ConfigPath; requested: unknown; resolved: unknown }
  | { kind: 'removed'; path: ConfigPath; requested: unknown };

export type ServerChanges = ServerChange[];

const isSamePath = (first: ConfigPath, second: ConfigPath) =>
  first.length === second.length && pathStartsWith(first, second);

const isUnset = (value: unknown) => value === undefined || value === null;

const isSameValue = (
  requested: unknown,
  resolved: unknown,
  path: ConfigPath
) => {
  // Unset project scanners fall back to the package scanners, while an empty list scans no
  // projects, so here the two differ.
  if (
    isSamePath(path, ['scanner', 'projectScanners']) &&
    isUnset(requested) !== isUnset(resolved)
  ) {
    return false;
  }

  if (isEmpty(requested) && isEmpty(resolved)) return true;

  const unordered = unorderedStringListPaths.some((list) =>
    isSamePath(list, path)
  );
  const comparable = (value: unknown) =>
    unordered && Array.isArray(value) ? [...value].sort() : value;

  return (
    diff({ value: comparable(requested) }, { value: comparable(resolved) })
      .length === 0
  );
};

/**
 * Whether the path is the options or the secrets of a plugin. The page shows each entry of these
 * in a row of its own, even though they are plain string maps.
 */
const isPluginSettingsMap = (path: ConfigPath) =>
  ['options', 'secrets'].includes(lastKey(path));

const compareRecords = (
  requested: Record<string, unknown>,
  resolved: Record<string, unknown>,
  path: ConfigPath
) =>
  [...new Set([...Object.keys(requested), ...Object.keys(resolved)])].flatMap(
    (key) => compare(requested[key], resolved[key], [...path, key])
  );

/** An entry keeps its identity in a list even if the server moves it to another position. */
const entryKey = (entry: unknown, index: number) =>
  isRecord(entry) ? (entry.id ?? entry.name ?? index) : index;

/**
 * Compare a list of settings objects entry by entry, under the positions in the resolved list.
 * Entries that the server removed have no such position, so they follow after the last one. The
 * order of a list can matter, as with providers that take precedence over later ones, so a list
 * whose remaining entries the server put in another order is a change of the list itself.
 */
const compareLists = (
  requested: unknown[],
  resolved: unknown[],
  path: ConfigPath
): ServerChanges => {
  const requestedByKey = new Map(
    requested.map((entry, index) => [entryKey(entry, index), entry])
  );
  const resolvedKeys = new Set(resolved.map(entryKey));
  const requestedKeys = new Set(requested.map(entryKey));
  const keptInRequest = requested
    .map(entryKey)
    .filter((key) => resolvedKeys.has(key));
  const keptInResolved = resolved
    .map(entryKey)
    .filter((key) => requestedKeys.has(key));
  const reordered = keptInRequest.some(
    (key, index) => key !== keptInResolved[index]
  );

  return [
    ...(reordered
      ? [{ kind: 'changed', path, requested, resolved } satisfies ServerChange]
      : []),
    ...resolved.flatMap((entry, index) =>
      compare(requestedByKey.get(entryKey(entry, index)), entry, [
        ...path,
        index,
      ])
    ),
    ...requested
      .filter((entry, index) => !resolvedKeys.has(entryKey(entry, index)))
      .map((entry, index): ServerChange => ({
        kind: 'removed',
        path: [...path, resolved.length + index],
        requested: entry,
      })),
  ];
};

/**
 * Find what the server changed at or below the path, at the same depth as the page shows it: an
 * element added or removed as a whole is one change, and so is a value shown as a whole in one
 * row. Only elements that both sides have and that show further elements are compared further.
 */
const compare = (
  requested: unknown,
  resolved: unknown,
  path: ConfigPath
): ServerChanges => {
  if (isPluginSettingsMap(path)) {
    return compareRecords(
      isRecord(requested) ? requested : {},
      isRecord(resolved) ? resolved : {},
      path
    );
  }

  if (isSameValue(requested, resolved, path)) return [];

  // An element that both sides have, even if the request leaves it empty, keeps its place on the
  // page, so only what is inside it can have changed.
  if (!isLeafValue(resolved) && isRecord(requested) && isRecord(resolved)) {
    return compareRecords(requested, resolved, path);
  }

  if (isEmpty(requested)) return [{ kind: 'added', path, requested }];

  if (isEmpty(resolved)) return [{ kind: 'removed', path, requested }];

  if (
    !isLeafValue(resolved) &&
    Array.isArray(requested) &&
    Array.isArray(resolved)
  ) {
    return compareLists(requested, resolved, path);
  }

  return [{ kind: 'changed', path, requested, resolved }];
};

/**
 * Compare the requested and the resolved configuration of a run. A run whose configuration is
 * not resolved has no changes to show.
 */
export const getServerChanges = (run: OrtRun): ServerChanges | undefined =>
  run.resolvedJobConfigs
    ? compare(run.jobConfigs, run.resolvedJobConfigs, [])
    : undefined;

/** The number of changes the page shows in each job, for the jobs that have any. */
export const countChangesByJob = (
  changes: ServerChanges | undefined,
  jobConfigs: JobConfigurations
) =>
  jobSections.flatMap(({ value, title }) => {
    const hidden = hiddenJobKeys(jobConfigs, value);
    const count = (changes ?? []).filter(
      ({ path }) => path[0] === value && !hidden.includes(String(path[1]))
    ).length;

    return count > 0 ? [{ job: value, title, count }] : [];
  });

export const ServerChangesContext = createContext<ServerChanges | undefined>(
  undefined
);

export const useServerChanges = () => useContext(ServerChangesContext);

export const findChange = (
  changes: ServerChanges | undefined,
  path: ConfigPath
) => changes?.find((change) => isSamePath(change.path, path));

/** The change of the element at the path, if the element is one the server can change. */
export const useServerChange = (path: ConfigPath | undefined) => {
  const changes = useServerChanges();

  return path && findChange(changes, path);
};

/**
 * The entries directly below the path that the server removed. They are not part of the
 * resolved configuration, so the page adds them to the elements it shows.
 */
export const removedEntries = (
  changes: ServerChanges | undefined,
  path: ConfigPath
) =>
  (changes ?? []).filter(
    (change): change is Extract<ServerChange, { kind: 'removed' }> =>
      change.kind === 'removed' &&
      change.path.length === path.length + 1 &&
      pathStartsWith(change.path, path)
  );

/**
 * The keys of the entries directly below the path that the server removed and that the resolved
 * object lacks. An entry the server emptied is still part of the object, so it is shown from there.
 */
export const removedKeys = (
  changes: ServerChanges | undefined,
  path: ConfigPath,
  resolved: Record<string, unknown>
) =>
  removedEntries(changes, path)
    .map((entry) => lastKey(entry.path))
    .filter((key) => resolved[key] === undefined);
