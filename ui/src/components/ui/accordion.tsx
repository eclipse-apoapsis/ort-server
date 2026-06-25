/*
 * The component is originally a part of the shadcn/ui library at https://github.com/shadcn-ui/ui.
 * The library is licensed as set out below:
 *
 * Copyright (c) 2023 shadcn
 *
 * SPDX-License-Identifier: MIT
 */

import * as AccordionPrimitive from '@radix-ui/react-accordion';
import { ChevronDownIcon } from 'lucide-react';
import * as React from 'react';

import { cn } from '@/lib/utils';

function Accordion({
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Root>) {
  return <AccordionPrimitive.Root data-slot='accordion' {...props} />;
}

function AccordionItem({
  className,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Item>) {
  return (
    <AccordionPrimitive.Item
      data-slot='accordion-item'
      className={cn('border-b last:border-b-0', className)}
      {...props}
    />
  );
}

type AccordionTriggerProps = React.ComponentProps<
  typeof AccordionPrimitive.Trigger
> & {
  /**
   * Content rendered in the header before the trigger button. If the content is an actionable React element
   * (eg. a button), it will be rendered as a sibling of the trigger button, and will not toggle the accordion
   * when clicked.
   */
  beforeTrigger?: React.ReactNode;
  /** Class name applied to the header wrapping the trigger. */
  headerClassName?: string;
};

/** Render the Radix header separately so siblings can sit next to the trigger. */
function AccordionHeader({
  className,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Header>) {
  return (
    <AccordionPrimitive.Header className={cn('flex', className)} {...props} />
  );
}

/* Render only the clickable trigger, without creating a nested header. */
function AccordionTriggerButton({
  className,
  children,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Trigger>) {
  return (
    <AccordionPrimitive.Trigger
      data-slot='accordion-trigger'
      className={cn(
        'focus-visible:border-ring focus-visible:ring-ring/50 flex flex-1 items-start justify-between gap-4 rounded-md py-4 text-left text-sm font-medium transition-all outline-none hover:underline focus-visible:ring-[3px] disabled:pointer-events-none disabled:opacity-50 [&[data-state=open]>svg]:rotate-180',
        className
      )}
      {...props}
    >
      {children}
      <ChevronDownIcon className='text-muted-foreground pointer-events-none size-4 shrink-0 translate-y-0.5 transition-transform duration-200' />
    </AccordionPrimitive.Trigger>
  );
}

function AccordionTrigger({
  className,
  children,
  beforeTrigger,
  headerClassName,
  ...props
}: AccordionTriggerProps) {
  return (
    <AccordionHeader className={headerClassName}>
      {beforeTrigger}
      <AccordionTriggerButton className={className} {...props}>
        {children}
      </AccordionTriggerButton>
    </AccordionHeader>
  );
}

function AccordionContent({
  className,
  children,
  ...props
}: React.ComponentProps<typeof AccordionPrimitive.Content>) {
  return (
    <AccordionPrimitive.Content
      data-slot='accordion-content'
      className='data-[state=closed]:animate-accordion-up data-[state=open]:animate-accordion-down overflow-hidden text-sm'
      {...props}
    >
      <div className={cn('pt-0 pb-4', className)}>{children}</div>
    </AccordionPrimitive.Content>
  );
}

export { Accordion, AccordionItem, AccordionTrigger, AccordionContent };
