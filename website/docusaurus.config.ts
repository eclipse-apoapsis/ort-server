import type * as Preset from '@docusaurus/preset-classic';
import type { Config } from '@docusaurus/types';
import type * as OpenApiPlugin from 'docusaurus-plugin-openapi-docs';
import { themes as prismThemes } from 'prism-react-renderer';

const config: Config = {
  title: 'ORT Server',
  tagline:
    'A scalable application to automate software compliance checks, based on the OSS Review Toolkit.',
  favicon: 'img/favicon.ico',

  // Set the production url of your site here
  url: 'https://eclipse-apoapsis.github.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/ort-server',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'eclipse-apoapsis', // Usually your GitHub org/user name.
  projectName: 'ort-server', // Usually your repo name.

  onBrokenAnchors: 'throw',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'throw',

  // Even if you don't use internationalization, you can use this field to set
  // useful metadata like html lang. For example, if your site is Chinese, you
  // may want to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  markdown: {
    mermaid: true,
  },

  presets: [
    [
      'classic',
      {
        docs: {
          sidebarPath: './sidebars.ts',
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          editUrl:
            'https://github.com/eclipse-apoapsis/ort-server/tree/main/website/',
        },
        theme: {
          customCss: './src/css/custom.css',
        },
      } satisfies Preset.Options,
    ],
  ],

  plugins: [
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'api',
        path: 'api',
        routeBasePath: 'api',
        sidebarPath: './sidebarsApi.ts',
        docItemComponent: '@theme/ApiItem',
      },
    ],
    [
      'docusaurus-plugin-openapi-docs',
      {
        id: 'api', // plugin id
        docsPluginId: 'api', // configured for preset-classic
        config: {
          api: {
            specPath: '../ui/build/openapi.json',
            outputDir: 'api',
            sidebarOptions: {
              groupPathsBy: 'tag',
              categoryLinkSource: 'tag',
            },
          } satisfies OpenApiPlugin.Options,
        },
      },
    ],
  ],

  themes: [
    '@docusaurus/theme-mermaid',
    'docusaurus-theme-openapi-docs',
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        indexBlog: false,
        docsDir: ['api', 'docs'],
        docsRouteBasePath: ['/api', '/docs'],
        searchResultLimits: 15,
        searchResultContextMaxLength: 200,
        explicitSearchResultPath: true,
      },
    ],
  ],

  themeConfig: {
    image: 'img/social-card.png',
    navbar: {
      title: 'ORT Server',
      logo: {
        alt: 'ORT Server Logo',
        src: 'img/logo.svg',
      },
      items: [
        {
          type: 'docSidebar',
          sidebarId: 'tutorialSidebar',
          position: 'left',
          label: 'Docs',
        },
        {
          to: '/api/ort-server-api',
          label: 'API',
          position: 'left',
          activeBaseRegex: `/api/`,
        },
        {
          href: 'https://github.com/eclipse-apoapsis/ort-server',
          label: 'GitHub',
          position: 'right',
        },
      ],
    },
    footer: {
      style: 'dark',
      links: [
        {
          title: 'Docs',
          items: [
            {
              label: 'Introduction',
              to: '/docs/intro',
            },
          ],
        },
        {
          title: 'Community',
          items: [
            {
              label: 'GitHub',
              href: 'https://github.com/eclipse-apoapsis/ort-server',
            },
            {
              label: 'Matrix Chat',
              href: 'https://matrix.to/#/#apoapsis:matrix.eclipse.org',
            },
            {
              label: 'OSS Review Toolkit',
              href: 'https://oss-review-toolkit.org',
            },
          ],
        },
        {
          title: 'Eclipse Foundation',
          items: [
            {
              label: 'Eclipse Apoapsis Project Site',
              href: 'https://projects.eclipse.org/projects/technology.apoapsis',
            },
            {
              label: 'Eclipse Foundation Website',
              href: 'http://www.eclipse.org/',
            },
            {
              label: 'Privacy policy',
              href: 'http://www.eclipse.org/legal/privacy.php',
            },
            {
              label: 'Terms of use',
              href: 'http://www.eclipse.org/legal/termsofuse.php',
            },
            {
              label: 'Copyright agent',
              href: 'http://www.eclipse.org/legal/copyright.php',
            },
            {
              label: 'Legal',
              href: 'http://www.eclipse.org/legal',
            },
          ],
        },
      ],
      logo: {
        alt: 'Eclipse Foundation',
        src: 'img/eclipse-foundation.svg',
        href: 'https://www.eclipse.org',
        height: 150,
      },
      copyright: `Copyright © ${new Date().getFullYear()} The ORT Server Authors. Built with Docusaurus.`,
    },
    prism: {
      theme: prismThemes.github,
      darkTheme: prismThemes.dracula,
    },
  } satisfies Preset.ThemeConfig,
};

export default config;
