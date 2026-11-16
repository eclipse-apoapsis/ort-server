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

import { useQuery } from '@tanstack/react-query';

import { defaultPluginsCount, formatCount } from './overview-format';
import { defaultPluginsOptions } from './overview-queries';

/**
 * How many of the default plugins are the built-in defaults, and how many an administrator has set
 * on this server, once the defaults have loaded.
 */
export const DefaultPluginsNote = () => {
  const { data } = useQuery(defaultPluginsOptions);

  if (!data) return null;

  const set = defaultPluginsCount(
    data.filter(({ source }) => source === 'GLOBAL')
  );
  const builtIn = defaultPluginsCount(data) - set;

  if (set === 0) return 'All built-in';
  if (builtIn === 0) return 'All set on this server';

  return `${formatCount(builtIn)} built-in, ${formatCount(set)} set on this server`;
};
