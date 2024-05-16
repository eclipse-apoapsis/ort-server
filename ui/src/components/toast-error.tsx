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

import { ApiError } from '@/api/requests';

type ToastErrorProps = {
  error: ApiError;
};

type ErrorBody = {
  message?: string;
  cause?: string;
};

export const ToastError = ({ error }: ToastErrorProps) => {
  // Casting is not generally recommended, but as both message and cause can be undefined, casting
  // and accessing them is safe.
  const body = error.body as ErrorBody;
  const message = body.message;
  const cause = body.cause;
  return (
    <div className="grid gap-2">
      <div>{message}</div>
      <div className="break-all">{cause}</div>
    </div>
  );
};
