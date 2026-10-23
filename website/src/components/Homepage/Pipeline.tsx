import Heading from '@theme/Heading';
import clsx from 'clsx';
import { Fragment, type JSX } from 'react';

import Icon, { type IconName } from './Icon';
import styles from './Pipeline.module.css';
import SectionHeader from './SectionHeader';
import shared from './shared.module.css';

type Stage = {
  name: string;
  icon: IconName;
  tone: 'toneCrimson' | 'toneTeal' | 'tonePurple';
  description: string;
  /** All stages except the analyzer can be disabled for a run. */
  optional?: boolean;
};

// Each step is a list of stages that run in parallel.
const steps: Stage[][] = [
  [
    {
      name: 'Analyzer',
      icon: 'tree-view-alt',
      tone: 'toneTeal',
      description:
        'Resolves the full dependency tree for more than 20 package managers.',
    },
  ],
  [
    {
      name: 'Advisor',
      optional: true,
      icon: 'security',
      tone: 'toneCrimson',
      description: 'Checks every package against vulnerability databases.',
    },
    {
      name: 'Scanner',
      optional: true,
      icon: 'scan-alt',
      tone: 'tonePurple',
      description:
        'Detects licenses, copyrights and code snippets in the source code.',
    },
  ],
  [
    {
      name: 'Evaluator',
      optional: true,
      icon: 'rule',
      tone: 'toneTeal',
      description: 'Applies your compliance rules and policies to the results.',
    },
  ],
  [
    {
      name: 'Reporter',
      optional: true,
      icon: 'report',
      tone: 'tonePurple',
      description:
        'Produces reports and SBOMs in SPDX, CycloneDX and many other formats.',
    },
  ],
  [
    {
      name: 'Notifier',
      optional: true,
      icon: 'notification',
      tone: 'toneCrimson',
      description: 'Informs your team when a run finishes.',
    },
  ],
];

function StageCard({ name, icon, tone, description, optional }: Stage) {
  return (
    <li className={clsx(styles.stage, shared.liftCard)}>
      {optional && <span className={styles.optional}>Optional</span>}
      <span className={clsx(shared.iconTile, shared[tone])}>
        <Icon name={icon} />
      </span>
      <div>
        <Heading as='h3' className={styles.stageName}>
          {name}
        </Heading>
        <p className={styles.stageDescription}>{description}</p>
      </div>
    </li>
  );
}

export default function Pipeline(): JSX.Element {
  return (
    <section
      className={clsx(shared.section, shared.sectionAlt)}
      aria-labelledby='how-it-works'
    >
      <div className='container'>
        <SectionHeader
          id='how-it-works'
          eyebrow='How it works'
          title='From source code to compliance report'
        >
          Every run follows the proven ORT pipeline. Each stage runs as its own
          worker, and all stages except the Analyzer are optional, so you can
          adapt the pipeline to your needs.
        </SectionHeader>
        <ol className={styles.pipeline}>
          {steps.map((stages, index) => (
            <Fragment key={stages[0].name}>
              {index > 0 && (
                <li className={styles.connector} aria-hidden='true'>
                  <Icon name='arrow-right' />
                </li>
              )}
              {stages.length === 1 ? (
                <StageCard {...stages[0]} />
              ) : (
                <li className={styles.parallel}>
                  <span className={styles.parallelLabel}>In parallel</span>
                  <ul className={styles.parallelStages}>
                    {stages.map((stage) => (
                      <StageCard key={stage.name} {...stage} />
                    ))}
                  </ul>
                </li>
              )}
            </Fragment>
          ))}
        </ol>
      </div>
    </section>
  );
}
