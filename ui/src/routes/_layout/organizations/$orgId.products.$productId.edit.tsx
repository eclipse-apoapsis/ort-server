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

import { useProductsServiceGetProductByIdKey, useProductsServicePatchProductById } from '@/api/queries';
import { createFileRoute, useNavigate } from '@tanstack/react-router';
import { ApiError, ProductsService } from '@/api/requests';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
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
import { Button } from '@/components/ui/button';
import {
  Card,
  CardContent,
  CardFooter,
  CardHeader,
} from '@/components/ui/card';
import { useSuspenseQuery } from '@tanstack/react-query';
import { useToast } from "@/components/ui/use-toast";
import { ToastError } from "@/components/toast-error";

const formSchema = z.object({
  name: z.string(),
  description: z.string().optional(),
});

const EditProductPage = () => {
  const params = Route.useParams();
  const navigate = useNavigate();
  const { toast } = useToast();

  const { data: product } = useSuspenseQuery({
    queryKey: [useProductsServiceGetProductByIdKey, params.orgId, params.productId],
    queryFn: async () =>
      await ProductsService.getProductById(
        Number.parseInt(params.productId)
      ),
    },  
  );

  const { mutateAsync } = useProductsServicePatchProductById({
    onSuccess() {
      toast({
        title: 'Edit Product',
        description: 'Product updated successfully.',
      });
      navigate({
        to: '/organizations/$orgId/products/$productId',
        params: { orgId: params.orgId, productId: params.productId },
      });
    },
    onError(error: ApiError) {
      toast({
        title: 'Edit Product - FAILURE',
        description: <ToastError message={`${error.message}: ${error.body.message}`} cause={error.body.cause} />,
        variant: 'destructive',
      });
    }
  });

  const form = useForm<z.infer<typeof formSchema>>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      name: product.name,
      description: product.description as unknown as string,
    },
  });

  async function onSubmit(values: z.infer<typeof formSchema>) {
    await mutateAsync({
      productId: product.id,
      requestBody: {
        name: values.name,
        // There's a bug somewhere in the OpenAPI generation, similar to organizations.
        description: values.description as Record<string, unknown> | undefined,
      },
    });
  }

  return (
    <Card className="w-full max-w-4xl mx-auto">
      <CardHeader>Edit Product</CardHeader>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8">
          <CardContent>
            <FormField
              control={form.control}
              name="name"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Name</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    Enter the name of your product
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Input {...field} />
                  </FormControl>
                  <FormDescription>
                    Optional description of the product
                  </FormDescription>
                  <FormMessage />
                </FormItem>
              )}
            />
          </CardContent>
          <CardFooter>
            <Button className="m-1" variant="outline" onClick={() => navigate({ to: '/organizations/' + params.orgId + '/products/' + params.productId })}>
              Cancel
            </Button>
            <Button type="submit">Submit</Button>
          </CardFooter>
        </form>
      </Form>
    </Card>
  );
}

export const Route = createFileRoute('/_layout/organizations/$orgId/products/$productId/edit')({
  loader: async ({ context, params }) => {
    await context.queryClient.ensureQueryData({
        queryKey: [useProductsServiceGetProductByIdKey, params.productId],
        queryFn: () =>
          ProductsService.getProductById(Number.parseInt(params.productId)),
    });
  },
  component: EditProductPage,
})
