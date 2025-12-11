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

import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';
import z from 'zod';

import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { regexSchema } from '@/schemas';

// Make the schema required for form usage (remove .optional())
const formSchema = regexSchema.unwrap();

type FormValues = z.infer<typeof formSchema>;

interface RegexInputProps {
  label?: string;
  description?: string;
  placeholder?: string;
  initialValue?: string;
  onRegexChange: (value: string) => void;
  className?: string;
}

export function RegexForm({
  label,
  description,
  placeholder = '(regular expression)',
  initialValue = '',
  className,
  onRegexChange,
}: RegexInputProps) {
  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      value: initialValue,
    },
    mode: 'onChange',
  });

  const onSubmit = (data: FormValues) => {
    if (data.value.trim() === '') return;
    onRegexChange(data.value);
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className={className}>
        <FormField
          control={form.control}
          name='value'
          render={({ field }) => (
            <FormItem>
              {label && <FormLabel>{label}</FormLabel>}
              <FormControl>
                <Input
                  placeholder={placeholder}
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={() => {
                    field.onBlur();
                    form.handleSubmit(onSubmit)();
                  }}
                />
              </FormControl>
              {description && <FormDescription>{description}</FormDescription>}
              <FormMessage />
            </FormItem>
          )}
        />
        <input type='submit' hidden />
      </form>
    </Form>
  );
}
