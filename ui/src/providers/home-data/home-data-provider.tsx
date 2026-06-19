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

import { ReactNode, useMemo } from 'react';
import { useAuth } from 'react-oidc-context';

import { HomeDataContext } from './home-data-context';
import { createLocalHomeDataProvider } from './local-home-data-provider';
import type { HomeDataProviderValue } from './types';

type HomeDataProviderProps = {
  value?: HomeDataProviderValue;
  children: ReactNode;
};

const getHomeDataUserId = (
  profile: Record<string, unknown> | undefined
): string | undefined => {
  const subject = profile?.sub;

  return typeof subject === 'string' && subject ? subject : undefined;
};

/** Provide home page data using the authenticated user's local storage scope. */
export const HomeDataProvider = ({
  value,
  children,
}: HomeDataProviderProps) => {
  const auth = useAuth();
  const userId = getHomeDataUserId(auth.user?.profile);
  const localProvider = useMemo(
    () => createLocalHomeDataProvider(userId),
    [userId]
  );

  return (
    <HomeDataContext.Provider value={value ?? localProvider}>
      {children}
    </HomeDataContext.Provider>
  );
};
