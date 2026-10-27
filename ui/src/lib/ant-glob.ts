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

import { escapeRegex } from '@/lib/regex';

const segmentToRegex = (segment: string) =>
  Array.from(segment, (char) =>
    char === '*' ? '[^/]*' : char === '?' ? '[^/]' : escapeRegex(char)
  ).join('');

/**
 * Convert an Ant-style glob pattern, as ORT uses for path includes and excludes, to a regular
 * expression that matches whole paths, the same way as Spring's `AntPathMatcher` in ORT: `**` as
 * a whole path segment matches any number of directories, `*` any characters except `/`, and `?`
 * one character except `/`. Within a segment, as in `**.json`, `**` matches like `*`. A leading
 * `./` is ignored, as in ORT.
 */
export function antGlobToRegex(pattern: string): RegExp {
  const segments = pattern.replace(/^\.\//, '').split('/');
  let regex = '';
  // Whether the next segment must be separated from the previous one by a `/`, which a `**`
  // segment takes care of itself.
  let needsSeparator = false;

  segments.forEach((segment, index) => {
    if (segment !== '**') {
      regex += (needsSeparator ? '/' : '') + segmentToRegex(segment);
      needsSeparator = true;
    } else if (index === segments.length - 1) {
      regex += needsSeparator ? '(?:/.*)?' : '.*';
    } else {
      regex += needsSeparator ? '(?:/.*)?/' : '(?:.*/)?';
      needsSeparator = false;
    }
  });

  return new RegExp(`^${regex}$`);
}
