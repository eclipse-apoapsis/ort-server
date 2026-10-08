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

import { useQuery } from '@tanstack/react-query';
import { createFileRoute } from '@tanstack/react-router';

import { getServerSettingByKeyOptions } from '@/api/@tanstack/react-query.gen';
import { Card, CardContent } from '@/components/ui/card';
import { useUser } from '@/hooks/use-user';
import { useHomeFavorites, useHomeRecentRuns } from '@/providers/home-data';
import { HomeFavoritesSection } from './-components/home-favorites-section';
import { HomeOrganizationsSection } from './-components/home-organizations-section';
import { HomeRecentRunsSection } from './-components/home-recent-runs-section';

const PRODUCT_NAME = 'ORT Server';

const HomePage = () => {
  const favorites = useHomeFavorites();
  const recentRuns = useHomeRecentRuns();
  const user = useUser();
  const userDisplayName =
    user.fullName || user.username || user.user?.profile.email;
  const { data: dbProductName } = useQuery({
    ...getServerSettingByKeyOptions({ path: { key: 'MAIN_PRODUCT_NAME' } }),
  });
  const productName =
    dbProductName?.value && dbProductName.isEnabled
      ? dbProductName.value
      : PRODUCT_NAME;

  return (
    <div className='mx-auto flex w-full max-w-7xl flex-col gap-4'>
      <div>
        <h1 className='text-3xl font-bold tracking-tight'>
          Welcome to {productName}
          {userDisplayName && `, ${userDisplayName}`}!
        </h1>
      </div>
      <HomeOrganizationsSection />
      <Card>
        <CardContent className='text-muted-foreground text-sm'>
          Please note that the information displayed below is only persisted in
          your current browser.
        </CardContent>
      </Card>
      <div className='grid gap-4 xl:grid-cols-2'>
        <HomeFavoritesSection favorites={favorites} />
        <HomeRecentRunsSection recentRuns={recentRuns} />
      </div>
    </div>
  );
};

export const Route = createFileRoute('/')({
  component: HomePage,
});
