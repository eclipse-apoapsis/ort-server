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

import { z } from 'zod';

import {
  OrganizationRole,
  ProductRole,
  RepositoryRole,
  UserGroup,
} from '@/api';
import { groupsSchema } from '@/schemas';

// Temporary helper functions to help with migration from groups to roles.

export const mapUserGroupToOrganizationRole = (
  group: UserGroup
): OrganizationRole => {
  switch (group) {
    case 'READERS':
      return 'READER';
    case 'WRITERS':
      return 'WRITER';
    case 'ADMINS':
      return 'ADMIN';
  }
};

export const mapUserGroupToProductRole = (group: UserGroup): ProductRole => {
  switch (group) {
    case 'READERS':
      return 'READER';
    case 'WRITERS':
      return 'WRITER';
    case 'ADMINS':
      return 'ADMIN';
  }
};

export const mapUserGroupToRepositoryRole = (
  group: UserGroup
): RepositoryRole => {
  switch (group) {
    case 'READERS':
      return 'READER';
    case 'WRITERS':
      return 'WRITER';
    case 'ADMINS':
      return 'ADMIN';
  }
};

export const mapGroupSchemaToOrganizationRole = (
  group: z.infer<typeof groupsSchema>
): OrganizationRole => {
  switch (group) {
    case 'admins':
      return 'ADMIN';
    case 'writers':
      return 'WRITER';
    case 'readers':
      return 'READER';
  }
};

export const mapGroupSchemaToProductRole = (
  group: z.infer<typeof groupsSchema>
): ProductRole => {
  switch (group) {
    case 'admins':
      return 'ADMIN';
    case 'writers':
      return 'WRITER';
    case 'readers':
      return 'READER';
  }
};

export const mapGroupSchemaToRepositoryRole = (
  group: z.infer<typeof groupsSchema>
): RepositoryRole => {
  switch (group) {
    case 'admins':
      return 'ADMIN';
    case 'writers':
      return 'WRITER';
    case 'readers':
      return 'READER';
  }
};
