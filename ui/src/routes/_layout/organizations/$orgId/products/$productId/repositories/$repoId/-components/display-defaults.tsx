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

import { Secret } from '@/api/requests';
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from '@/components/ui/accordion';

type DisplayDefaultsProps = {
  orgDefaults: Secret[];
  prodDefaults: Secret[];
  repoDefaults: Secret[];
};

export const DisplayDefaults = ({
  orgDefaults,
  prodDefaults,
  repoDefaults,
}: DisplayDefaultsProps) => {
  return (
    <Accordion type='multiple'>
      {orgDefaults.length > 0 && (
        <AccordionItem value='org-secrets'>
          <AccordionTrigger className='font-semibold text-blue-400'>
            From Organization
          </AccordionTrigger>
          <AccordionContent>
            <div className='flex justify-between gap-2 pb-2'>
              <div className='w-1/2 font-semibold'>Property</div>
              <div className='flex-1 font-semibold'>Value</div>
            </div>
            {orgDefaults.map((def) => (
              <div key={def.name} className='flex justify-between gap-2 pb-2'>
                <div className='w-1/2 break-all'>{def.name}</div>
                <div className='flex-1'>{def.description}</div>
              </div>
            ))}
          </AccordionContent>
        </AccordionItem>
      )}
      {prodDefaults.length > 0 && (
        <AccordionItem value='prod-secrets'>
          <AccordionTrigger className='font-semibold text-blue-400'>
            From Product
          </AccordionTrigger>
          <AccordionContent>
            <div className='flex justify-between gap-2 pb-2'>
              <div className='w-1/2 font-semibold'>Property</div>
              <div className='flex-1 font-semibold'>Value</div>
            </div>
            {prodDefaults.map((def) => (
              <div key={def.name} className='flex justify-between gap-2 pb-2'>
                <div className='w-1/2 break-all'>{def.name}</div>
                <div className='flex-1'>{def.description}</div>
              </div>
            ))}
          </AccordionContent>
        </AccordionItem>
      )}
      {repoDefaults.length > 0 && (
        <AccordionItem value='repo-secrets'>
          <AccordionTrigger className='font-semibold text-blue-400'>
            From Repository
          </AccordionTrigger>
          <AccordionContent>
            <div className='flex justify-between gap-2 pb-2'>
              <div className='w-1/2 font-semibold'>Property</div>
              <div className='flex-1 font-semibold'>Value</div>
            </div>
            {repoDefaults.map((def) => (
              <div key={def.name} className='flex justify-between gap-2 pb-2'>
                <div className='w-1/2 break-all'>{def.name}</div>
                <div className='flex-1'>{def.description}</div>
              </div>
            ))}
          </AccordionContent>
        </AccordionItem>
      )}
    </Accordion>
  );
};
