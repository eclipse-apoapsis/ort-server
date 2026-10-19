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

import { RotateCcwIcon } from 'lucide-react';

import { Button } from '@/components/ui/button';

type ResetToDefaultsButtonProps = {
  onClick: () => void;
};

export const ResetToDefaultsButton = ({
  onClick,
}: ResetToDefaultsButtonProps) => (
  <Button type='button' variant='outline' size='xs' onClick={onClick}>
    <RotateCcwIcon />
    Reset to defaults
  </Button>
);
