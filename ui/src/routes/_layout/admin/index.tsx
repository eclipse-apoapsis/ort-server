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

import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { useEffect } from 'react';

import { useUser } from '@/hooks/useUser';

const AdminDashboard = () => {
  const user = useUser();
  const navigate = useNavigate();

  useEffect(() => {
    if (!user.hasRole('superuser')) {
      navigate({
        to: '/403',
      });
    }
  }, [user, navigate]);

  return (
    <>
      <div className='flex flex-col'>
        <div className='flex-1 space-y-4'>
          <div className='flex items-center justify-between space-y-2'>
            <h2 className='text-3xl font-bold tracking-tight'>Dashboard</h2>
          </div>
        </div>
      </div>
    </>
  );
};

export const Route = createFileRoute('/_layout/admin/')({
  component: AdminDashboard,
});
