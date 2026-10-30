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

import { ReactNode } from 'react';

import { CopyToClipboard } from '@/components/copy-to-clipboard';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';

type PayloadViewProps = {
  id: string;
  title: string;
  description: ReactNode;
  payload: unknown;
};

/**
 * A payload shown as read-only JSON, with a button to copy it.
 */
export const PayloadView = ({
  id,
  title,
  description,
  payload,
}: PayloadViewProps) => {
  const json = JSON.stringify(payload, null, 2);

  return (
    <>
      <h3 className='mt-4'>{title}</h3>
      <Label htmlFor={id} className='text-muted-foreground'>
        {description}
      </Label>
      <div className='relative mt-2 w-full'>
        <Textarea
          id={id}
          className='h-96 pr-12 font-mono'
          readOnly
          value={json}
        />
        <div className='absolute top-2 right-2 z-10'>
          <CopyToClipboard copyText={json} />
        </div>
      </div>
    </>
  );
};
