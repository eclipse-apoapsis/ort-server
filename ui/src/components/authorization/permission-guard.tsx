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

import { useNavigate } from '@tanstack/react-router';
import { useEffect, type ReactNode } from 'react';

import { LoadingIndicator } from '@/components/loading-indicator';
import { ToastError } from '@/components/toast-error';
import { toast } from '@/lib/toast';

export interface PermissionGuardProps {
  children: ReactNode;
  isAllowed: boolean | undefined;
  isLoading: boolean;
  error: unknown;
}

export const PermissionGuard = ({
  children,
  isAllowed,
  isLoading,
  error,
}: PermissionGuardProps) => {
  const navigate = useNavigate();
  const isForbidden = !isLoading && !error && isAllowed === false;

  useEffect(() => {
    if (isForbidden) {
      void navigate({
        replace: true,
        to: '/403',
      });
    }
  }, [isForbidden, navigate]);

  useEffect(() => {
    if (error) {
      toast.error('Unable to load permissions', {
        description: <ToastError error={error} />,
        duration: Infinity,
        cancel: {
          label: 'Dismiss',
          onClick: () => {},
        },
      });
    }
  }, [error]);

  if (isLoading) {
    return <LoadingIndicator />;
  }

  if (isForbidden || isAllowed === undefined) {
    return null;
  }

  return <>{children}</>;
};
