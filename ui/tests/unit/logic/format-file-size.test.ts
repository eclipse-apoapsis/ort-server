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

import { expect, it } from 'vitest';

import { formatFileSize } from '@/helpers/format-file-size';

it.each([
  { name: 'zero bytes', bytes: 0, expected: '0 B' },
  { name: 'bytes below 1 kB', bytes: 512, expected: '512 B' },
  { name: 'exactly 1 kB', bytes: 1024, expected: '1.0 kB' },
  { name: 'kB range', bytes: 1536, expected: '1.5 kB' },
  { name: 'MB range', bytes: 2.3 * 1024 * 1024, expected: '2.3 MB' },
  { name: 'GB range', bytes: 1.1 * 1024 * 1024 * 1024, expected: '1.1 GB' },
  {
    name: 'large GB value',
    bytes: 500 * 1024 * 1024 * 1024,
    expected: '500.0 GB',
  },
])('formatFileSize - $name', ({ bytes, expected }) => {
  expect(formatFileSize(bytes)).toBe(expected);
});
