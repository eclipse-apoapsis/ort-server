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

import { useParams, useRouter } from '@tanstack/react-router';

import { FormControl } from '@/components/ui/form';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { capitalize } from '@/helpers/capitalize';
import { useInfrastructureServices } from '@/hooks/use-infrastructure-services';

type InfrastructureServiceSelectProps = {
  /** The name of the selected service, empty when nothing is selected yet. */
  value: string | undefined;
  onChange: (value: string) => void;
  placeholder: string;
  className?: string;
};

/**
 * A field for picking one of the infrastructure services of a hierarchy.
 *
 * The only place this is used is a field of an environment definition, which sits three components
 * deep in the create-run form, so it reads the repository it belongs to and what the user is
 * allowed to see itself rather than having them handed down through all of them.
 *
 * Render it inside a `FormField`, as it brings the `FormControl` of the field with it.
 */
export const InfrastructureServiceSelect = ({
  value,
  onChange,
  placeholder,
  className,
}: InfrastructureServiceSelectProps) => {
  const { orgId, productId, repoId } = useParams({ strict: false });
  const permissions = useRouter().options.context.permissions;

  const services = useInfrastructureServices({
    orgId,
    productId,
    repoId,
    permissions,
  });

  return (
    <Select value={value || undefined} onValueChange={onChange}>
      <FormControl>
        <SelectTrigger className={className}>
          <SelectValue placeholder={placeholder} />
        </SelectTrigger>
      </FormControl>
      <SelectContent>
        {services.map((service) => (
          <SelectItem
            key={`${service.hierarchy}:${service.name}`}
            value={service.name}
          >
            {`${service.name} (${capitalize(service.hierarchy)})`}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
};
