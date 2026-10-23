import useBaseUrl from '@docusaurus/useBaseUrl';
import Heading from '@theme/Heading';
import clsx from 'clsx';
import type { JSX } from 'react';

import Icon, { type IconName } from './Icon';
import styles from './Platform.module.css';
import SectionHeader from './SectionHeader';
import shared from './shared.module.css';

type Feature = {
  title: string;
  /** A Carbon icon, or 'ort' for the ORT logo. */
  icon: IconName | 'ort';
  tone: 'toneCrimson' | 'toneTeal' | 'tonePurple';
  description: string;
};

const features: Feature[] = [
  {
    title: 'Powered by ORT',
    icon: 'ort',
    tone: 'toneCrimson',
    description:
      "Builds on the OSS Review Toolkit's analysis, scanning and reporting capabilities, developed in close collaboration with the ORT project.",
  },
  {
    title: 'Analysis at scale',
    icon: 'data-base',
    tone: 'toneTeal',
    description:
      'Manage organizations, products and repositories in one central database, and analyze trends across all projects.',
  },
  {
    title: 'REST API',
    icon: 'api',
    tone: 'tonePurple',
    description:
      'Trigger runs, manage configuration and fetch results from your CI/CD pipelines through a documented OpenAPI interface.',
  },
  {
    title: 'Kubernetes-native',
    icon: 'kubernetes',
    tone: 'toneTeal',
    description:
      'Each pipeline stage runs in its own container and scales horizontally with your workload.',
  },
  {
    title: 'Access control',
    icon: 'user-admin',
    tone: 'toneCrimson',
    description:
      'Authentication, roles and permissions through Keycloak, scoped to organizations, products and repositories.',
  },
];

function FeatureCard({ title, icon, tone, description }: Feature) {
  const ortLogo = useBaseUrl('/img/ort-logo.svg');
  return (
    <li className={clsx(styles.card, shared.liftCard)}>
      <span className={clsx(shared.iconTile, shared[tone])}>
        {icon === 'ort' ? (
          <img src={ortLogo} alt='' className={styles.logo} />
        ) : (
          <Icon name={icon} />
        )}
      </span>
      <Heading as='h3' className={styles.title}>
        {title}
      </Heading>
      <p className={styles.description}>{description}</p>
    </li>
  );
}

export default function Platform(): JSX.Element {
  return (
    <section
      className={clsx(shared.section, shared.sectionAlt)}
      aria-labelledby='platform'
    >
      <div className='container'>
        <SectionHeader
          id='platform'
          eyebrow='Platform'
          title='Built for organizations'
        />
        <ul className={styles.grid}>
          {features.map((feature) => (
            <FeatureCard key={feature.title} {...feature} />
          ))}
        </ul>
      </div>
    </section>
  );
}
