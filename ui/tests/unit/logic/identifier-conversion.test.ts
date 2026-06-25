/*
 * Copyright (C) 2024 The ORT Server Authors (See <https://github.com/eclipse-apoapsis/ort-server/blob/main/NOTICE>)
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

import {
  identifierToPurl,
  identifierToString,
} from '@/helpers/identifier-conversion';

const id = {
  type: 'Maven',
  namespace: 'com.google.guava',
  name: 'listenablefuture',
  version: '9999.0-empty-to-avoid-conflict-with-guava',
};

it('identifierToPurl', () => {
  expect(identifierToPurl(id)).toBe(
    'pkg:maven/com.google.guava/listenablefuture@9999.0-empty-to-avoid-conflict-with-guava'
  );
});

it('identifierToString', () => {
  expect(identifierToString(id)).toBe(
    'Maven:com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava'
  );
});
