import Link from '@docusaurus/Link';
import Heading from '@theme/Heading';
import clsx from 'clsx';

import styles from './Ecosystem.module.css';
import Icon, { type IconName } from './Icon';
import shared from './shared.module.css';

type Item = {
  label: string;
  /** Internal path or external URL, items without a link are plain labels. */
  link?: string;
};

type Group = {
  title: string;
  icon: IconName;
  tone: 'toneCrimson' | 'toneTeal' | 'tonePurple';
  description: string;
  items: Item[];
};

const groups: Group[] = [
  {
    title: 'SBOM',
    icon: 'document-export',
    tone: 'toneCrimson',
    description: 'Generate software bills of materials in standard formats.',
    items: [{ label: 'SPDX' }, { label: 'CycloneDX' }],
  },
  {
    title: 'Integration',
    icon: 'api',
    tone: 'tonePurple',
    description:
      'Trigger runs and fetch results from your scripts and CI/CD pipelines.',
    items: [
      { label: 'CLI', link: '/docs/user-guide/cli' },
      { label: 'REST API', link: '/api/ort-server-api' },
      {
        label: 'GitHub Action',
        link: 'https://github.com/eclipse-apoapsis/actions',
      },
    ],
  },
  {
    title: 'Deployment',
    icon: 'deploy',
    tone: 'toneTeal',
    description:
      'Run it on Kubernetes, installed with Helm and secured with Keycloak.',
    items: [
      { label: 'Kubernetes' },
      {
        label: 'Helm chart',
        link: 'https://github.com/eclipse-apoapsis/helm-charts',
      },
      { label: 'Keycloak', link: '/docs/admin-guide/guides/keycloak-config' },
    ],
  },
];

function Chip({ label, link }: Item) {
  if (!link) {
    return <li className={styles.chip}>{label}</li>;
  }
  return (
    <li>
      <Link className={clsx(styles.chip, styles.chipLink)} to={link}>
        {label}
        <Icon name='arrow-right' className={styles.chipIcon} />
      </Link>
    </li>
  );
}

export default function Ecosystem(): JSX.Element {
  return (
    <section className={styles.ecosystem} aria-labelledby='ecosystem'>
      <div className='container'>
        <h2 id='ecosystem' className={styles.heading}>
          Fits into your existing toolchain
        </h2>
        <ul className={styles.grid}>
          {groups.map(({ title, icon, tone, description, items }) => (
            <li key={title} className={clsx(styles.card, shared.liftCard)}>
              <div className={styles.cardHeader}>
                <span className={clsx(shared.iconTile, shared[tone])}>
                  <Icon name={icon} />
                </span>
                <Heading as='h3' className={styles.title}>
                  {title}
                </Heading>
              </div>
              <p className={styles.description}>{description}</p>
              <ul className={styles.chips}>
                {items.map((item) => (
                  <Chip key={item.label} {...item} />
                ))}
              </ul>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
