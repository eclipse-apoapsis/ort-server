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

import { Eye, Pen, Shield } from 'lucide-react';
import { createElement, type JSX } from 'react';
import { z } from 'zod';

import {
  OrganizationRole,
  ProductRole,
  RepositoryRole,
  UserGroup,
} from '@/api';
import { roleSchema } from '@/schemas';

// These helpers bridge the inconsistent generated list response and role
// mutation contracts until the API uses role terminology consistently.

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

export const getRoleIcon = (role: z.infer<typeof roleSchema>): JSX.Element => {
  switch (role) {
    case 'ADMIN':
      return createElement(Shield, { size: 16 });
    case 'WRITER':
      return createElement(Pen, { size: 16 });
    case 'READER':
      return createElement(Eye, { size: 16 });
  }
};
