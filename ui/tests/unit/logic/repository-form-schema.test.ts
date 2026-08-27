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

import { createRepositoryFormSchema } from '@/schemas';

const formValues = {
  description: '',
  name: '',
  type: 'GIT' as const,
  url: 'https://example.org/repository.git',
  username: '',
  password: '',
};

describe('createRepositoryFormSchema', () => {
  it('accepts a repository without credentials', () => {
    expect(createRepositoryFormSchema.safeParse(formValues).success).toBe(true);
  });

  it('accepts and retains complete credentials', () => {
    const values = {
      ...formValues,
      username: 'jdoe',
      password: 'token',
    };

    const result = createRepositoryFormSchema.safeParse(values);

    expect(result.success).toBe(true);
    expect(result.data).toEqual(values);
  });

  it('rejects credentials containing only whitespace', () => {
    const result = createRepositoryFormSchema.safeParse({
      ...formValues,
      username: '   ',
      password: '   ',
    });

    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path)).toContainEqual([
      'username',
    ]);
    expect(result.error?.issues.map((issue) => issue.path)).toContainEqual([
      'password',
    ]);
  });

  it('requires a password or token when a username is set', () => {
    const result = createRepositoryFormSchema.safeParse({
      ...formValues,
      username: 'jdoe',
    });

    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path)).toContainEqual([
      'password',
    ]);
  });

  it('requires a username when a password or token is set', () => {
    const result = createRepositoryFormSchema.safeParse({
      ...formValues,
      password: 'token',
    });

    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path)).toContainEqual([
      'username',
    ]);
  });

  it('retains the repository URL validation', () => {
    const result = createRepositoryFormSchema.safeParse({
      ...formValues,
      url: 'not a URL',
    });

    expect(result.success).toBe(false);
    expect(result.error?.issues.map((issue) => issue.path)).toContainEqual([
      'url',
    ]);
  });
});
