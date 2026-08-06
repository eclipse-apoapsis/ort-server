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

import { FormControl } from '@/components/ui/form';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { capitalize } from '@/helpers/capitalize';
import { useSecrets, UseSecretsParams } from '@/hooks/use-secrets';

type SecretSelectProps = {
  /** The name of the selected secret, empty when nothing is selected yet. */
  value: string | undefined;
  onChange: (value: string) => void;
  placeholder: string;
  orgId?: string;
  productId?: string;
  repositoryId?: string;
  permissions: UseSecretsParams['permissions'];
  className?: string;
};

/**
 * A field for picking one of the secrets of a hierarchy. Which levels it offers depends on the ids
 * that are given and on what the user is allowed to read.
 *
 * Render it inside a `FormField`, as it brings the `FormControl` of the field with it.
 */
export const SecretSelect = ({
  value,
  onChange,
  placeholder,
  orgId,
  productId,
  repositoryId,
  permissions,
  className,
}: SecretSelectProps) => {
  const secrets = useSecrets({ orgId, productId, repositoryId, permissions });

  return (
    <Select value={value || undefined} onValueChange={onChange}>
      <FormControl>
        <SelectTrigger className={className}>
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
      </FormControl>
      <SelectContent>
        {secrets.map((secret) => (
          <SelectItem
            key={`${secret.hierarchy}:${secret.name}`}
            value={secret.name}
          >
            {`${secret.name} (${capitalize(secret.hierarchy)})`}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
};
