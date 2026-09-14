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

import { z } from 'zod';

import { FormControl } from '@/components/ui/form';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { getRoleIcon } from '@/helpers/role-helpers.ts';
import { roleSchema } from '@/schemas';

type Role = z.infer<typeof roleSchema>;

type RoleLevel = 'organization' | 'product' | 'repository';

const roleDescriptions: Record<RoleLevel, Record<Role, string>> = {
  organization: {
    READER: 'Can view the organization and its products and repositories',
    WRITER:
      'Can view and edit the organization and its products and repositories',
    ADMIN:
      'Can view, edit, and delete the organization and its products and repositories',
  },
  product: {
    READER: 'Can view the product and its repositories',
    WRITER: 'Can view and edit the product and its repositories',
    ADMIN: 'Can view, edit, and delete the product and its repositories',
  },
  repository: {
    READER: 'Can view the repository',
    WRITER: 'Can view and edit the repository',
    ADMIN: 'Can view, edit, and delete the repository',
  },
};

type RoleSelectProps = {
  value: Role;
  onChange: (role: Role) => void;
  /** The hierarchy level the role is assigned for, which selects the role descriptions. */
  level: RoleLevel;
};

/**
 * A field for picking the role to assign to a user in a hierarchy.
 *
 * Each option describes what the role allows on the given hierarchy level. The trigger shows only
 * the name of the selected role, so the description does not take up space in the form.
 *
 * Render it inside a `FormField`, as it brings the `FormControl` of the field with it.
 */
export const RoleSelect = ({ value, onChange, level }: RoleSelectProps) => {
  return (
    <Select value={value} onValueChange={onChange}>
      <FormControl>
        <SelectTrigger>
          <SelectValue placeholder='Select a role'>
            <div className='flex items-center gap-2'>
              {getRoleIcon(value)}
              {value}
            </div>
          </SelectValue>
        </SelectTrigger>
      </FormControl>
      <SelectContent align='end' className='w-60'>
        {roleSchema.options.map((role) => (
          <SelectItem key={role} value={role} textValue={role}>
            <div className='flex flex-col items-start gap-0.5'>
              <div className='flex items-center gap-2'>
                {getRoleIcon(role)}
                {role}
              </div>
              <span className='text-muted-foreground text-xs'>
                {roleDescriptions[level][role]}
              </span>
            </div>
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
};
