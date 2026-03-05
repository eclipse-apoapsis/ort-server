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

import { AxiosError } from 'axios';
import { toast } from 'sonner';

import { ToastCancelButtons, ToastError } from '@/components/toast-error';

export { toast };

function errorToText(error: unknown): string {
  if (error instanceof AxiosError) {
    const message = error.response?.data.message || error.message;
    const cause = error.response?.data.cause;
    return [message, cause].filter(Boolean).join('\n');
  }
  return 'An unknown error occurred.\nPlease try again.';
}

export function toastError(title: string, error: unknown): void {
  const id = Math.random().toString(36).slice(2);
  toast.error(title, {
    id,
    description: <ToastError error={error} />,
    duration: Infinity,
    cancel: <ToastCancelButtons toastId={id} copyText={errorToText(error)} />,
  });
}
