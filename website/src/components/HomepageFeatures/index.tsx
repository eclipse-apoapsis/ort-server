import clsx from 'clsx';
import Heading from '@theme/Heading';
import styles from './styles.module.css';
import {Icon} from '@iconify/react';

type FeatureItem = {
  title: string;
  Svg?: React.ComponentType<React.ComponentProps<'svg'>>;
  icon?: string;
  description: JSX.Element;
};

const FeatureList: FeatureItem[] = [
  {
    title: 'Builds on the OSS Review Toolkit',
    Svg: require('@site/static/img/ort-logo.svg').default,
    description: (
      <>
          The server integrates is baed on the OSS Review Toolkit, leveraging its APIs for component analysis, license
          scanning, vulnerability databases, compliance rules, and report generation. This allows users to manage and
          analyze dependencies and licenses with ease, offering detailed insights and compliance reporting.
      </>
    ),
  },
  {
    title: 'Software Composition Analysis',
    icon: 'carbon:network-4',
    description: (
      <>
          The ORT Server provides a comprehensive solution for organizations to perform Software Composition Analysis
          (SCA) at scale. It supports a wide range of project setups, from mobile apps to cloud services, enabling
          automatic generation of Software Bill of Materials (SBOMs), dependency analysis, and identification of
          vulnerabilities, ensuring efficient management of software components across projects.
      </>
    ),
  },
  {
    title: 'Web UI',
    icon: 'devicon:react',
    description: (
      <>
          The ORT Server includes a react-based web UI designed to streamline access to critical functions and data.
          This interface allows users to manage the compliance and security of their projects with ease, providing
          detailed reports of software components, licenses, and vulnerabilities.
      </>
    ),
  },
  {
    title: 'REST API',
    icon: 'devicon:openapi',
    description: (
      <>
          The ORT Server provides a REST API that allows for seamless integration with other tools and automation
          workflows. This API offers endpoints for performing tasks like managing repositories, secrets, or users, and
          getting the results of ORT runs, making it easy to incorporate ORT functionality into existing DevOps
          pipelines or CI/CD environments.
      </>
    ),
  },
  {
    title: 'Scalable Architecture',
    icon: 'devicon:kubernetes',
    description: (
      <>
          The ORT Server is designed with scalability in mind, leveraging container orchestration platforms like
          Kubernetes for efficient resource management and deployment. It allows organizations to scale their software
          composition analysis workflows horizontally by running the actual ORT components in separate containers.
          Kubernetes integration also provides enhanced automation, fault tolerance, and dynamic load balancing,
          ensuring high availability and reliability in production environments.
      </>
    ),
  },
  {
    title: 'Access and User Management',
    icon: 'carbon:two-factor-authentication',
    description: (
      <>
          The ORT Server incorporates robust user access and role management capabilities based on Keycloak, an open
          source identity and access management solution. It supports user authentication, authorization, and
          multi-factor authentication, allowing organizations to define roles and permissions for different users. This
          ensures that different users can access only the relevant parts of the system.
      </>
    ),
  },
];

function Feature({title, Svg, icon, description}: FeatureItem) {
  return (
    <div className={clsx('col col--4')}>
      {icon && (
        <div className="text--center">
          <Icon className={styles.featureSvg} icon={icon} />
        </div>
      )}
      {Svg && (
        <div className="text--center">
          <Svg className={styles.featureSvg} role="img" />
        </div>
      )}
      <div className="text--center padding-horiz--md">
        <Heading as="h3">{title}</Heading>
        <p>{description}</p>
      </div>
    </div>
  );
}

export default function HomepageFeatures(): JSX.Element {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {FeatureList.map((props, idx) => (
            <Feature key={idx} {...props} />
          ))}
        </div>
      </div>
    </section>
  );
}
