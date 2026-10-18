import Link from '@docusaurus/Link';
import useBaseUrl from '@docusaurus/useBaseUrl';
import Heading from '@theme/Heading';
import clsx from 'clsx';

import Icon from './Icon';
import styles from './OpenSource.module.css';
import SectionHeader from './SectionHeader';
import shared from './shared.module.css';

type Project = {
  title: string;
  logo: string;
  logoAlt: string;
  /** The logo file has large empty margins that are cropped by the tile. */
  croppedLogo?: boolean;
  description: string;
  links: { label: string; href: string }[];
};

const projects: Project[] = [
  {
    title: 'OSS Review Toolkit',
    logo: '/img/ort-logo.svg',
    logoAlt: 'OSS Review Toolkit logo',
    description:
      'ORT Server builds on ORT, the open-source toolkit for license compliance and vulnerability analysis. Both projects share contributors and evolve together.',
    links: [
      {
        label: 'Visit oss-review-toolkit.org',
        href: 'https://oss-review-toolkit.org',
      },
      {
        label: 'View on GitHub',
        href: 'https://github.com/oss-review-toolkit/ort',
      },
    ],
  },
  {
    title: 'Eclipse Apoapsis',
    logo: '/img/eclipse-foundation.svg',
    logoAlt: 'Eclipse Foundation logo',
    croppedLogo: true,
    description:
      'ORT Server is the reference implementation of the Eclipse Apoapsis project, developed openly under the Eclipse Foundation.',
    links: [
      {
        label: 'Visit the project site',
        href: 'https://projects.eclipse.org/projects/technology.apoapsis',
      },
    ],
  },
];

function ProjectCard({
  title,
  logo,
  logoAlt,
  croppedLogo,
  description,
  links,
}: Project) {
  return (
    <li className={clsx(styles.card, shared.liftCard)}>
      {/* The logos contain white parts, so they always sit on a dark tile. */}
      <div className={styles.logoTile}>
        <img
          src={useBaseUrl(logo)}
          alt={logoAlt}
          className={clsx(styles.logo, croppedLogo && styles.logoCropped)}
        />
      </div>
      <div className={styles.body}>
        <Heading as='h3' className={styles.title}>
          {title}
        </Heading>
        <p className={styles.description}>{description}</p>
        <div className={styles.links}>
          {links.map(({ label, href }) => (
            <Link key={href} className={styles.link} href={href}>
              {label}
              <Icon name='arrow-right' />
            </Link>
          ))}
        </div>
      </div>
    </li>
  );
}

export default function OpenSource(): JSX.Element {
  return (
    <section className={shared.section} aria-labelledby='open-source'>
      <div className='container'>
        <SectionHeader
          id='open-source'
          eyebrow='Community'
          title='Built on open source'
        />
        <ul className={styles.grid}>
          {projects.map((project) => (
            <ProjectCard key={project.title} {...project} />
          ))}
        </ul>
      </div>
    </section>
  );
}
