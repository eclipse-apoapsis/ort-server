import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import CallToAction from '@site/src/components/Homepage/CallToAction';
import Ecosystem from '@site/src/components/Homepage/Ecosystem';
import Hero from '@site/src/components/Homepage/Hero';
import OpenSource from '@site/src/components/Homepage/OpenSource';
import Pipeline from '@site/src/components/Homepage/Pipeline';
import Platform from '@site/src/components/Homepage/Platform';
import ProductTour from '@site/src/components/Homepage/ProductTour';
import Layout from '@theme/Layout';

export default function Home(): JSX.Element {
  const { siteConfig } = useDocusaurusContext();
  return (
    <Layout title={`${siteConfig.title}`} description={`${siteConfig.tagline}`}>
      <Hero />
      <main>
        <Ecosystem />
        <Pipeline />
        <ProductTour />
        <Platform />
        <OpenSource />
        <CallToAction />
      </main>
    </Layout>
  );
}
