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

import { describe, expect, it } from 'vitest';
import { z } from 'zod';

import { zJobConfigurations } from '@/api/zod.gen';
import { jobConfigFields } from '@/lib/job-config-fields';

/**
 * Follow a dotted path through a schema. A record accepts any key, a list only `*`. Return
 * whether the path leads to a field.
 */
const pathExists = (schema: z.ZodType, segments: string[]): boolean => {
  if (schema instanceof z.ZodOptional || schema instanceof z.ZodNullable) {
    return pathExists(schema.unwrap() as z.ZodType, segments);
  }

  const [segment, ...rest] = segments;

  if (segment === undefined) return true;

  if (schema instanceof z.ZodObject) {
    const field = (schema.shape as Record<string, z.ZodType>)[segment];
    return field !== undefined && pathExists(field, rest);
  }

  if (schema instanceof z.ZodRecord) {
    return pathExists(schema.valueType as z.ZodType, rest);
  }

  if (schema instanceof z.ZodArray) {
    return segment === '*' && pathExists(schema.element as z.ZodType, rest);
  }

  return false;
};

describe('jobConfigFields', () => {
  it.each(Object.keys(jobConfigFields))(
    'has the path %s in the job configurations',
    (path) => {
      expect(pathExists(zJobConfigurations, path.split('.'))).toBe(true);
    }
  );

  it('rejects a path that is not in the job configurations', () => {
    expect(pathExists(zJobConfigurations, ['analyzer', 'noSuchField'])).toBe(
      false
    );
  });
});
