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

import { UseFormReturn } from 'react-hook-form';

import { FormControl, FormField } from '@/components/ui/form';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { CreateRunFormValues } from '../_repo-layout/create-run/-create-run-utils';

type EvaluatorFieldsProps = {
  form: UseFormReturn<CreateRunFormValues>;
};

export const EvaluatorFields = ({ form }: EvaluatorFieldsProps) => {
  return (
    <div className='flex flex-row align-middle'>
      <FormField
        control={form.control}
        name='jobConfigs.evaluator.enabled'
        render={({ field }) => (
          <FormControl>
            <div className='flex items-center space-x-2'>
              <Switch
                className='my-4 mr-4 data-[state=checked]:bg-green-500'
                checked={field.value}
                onCheckedChange={field.onChange}
                id='evaluator-enabled-switch'
              />
              <Label htmlFor='evaluator-enabled-switch'>Evaluator</Label>
            </div>
          </FormControl>
        )}
      />
    </div>
  );
};
