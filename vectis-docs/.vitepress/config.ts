import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Vectis',
  description: 'Solution moderne de transfert de fichiers bancaires',
  lang: 'fr-FR',
  
  head: [
    ['link', { rel: 'icon', href: '/favicon.ico' }]
  ],

  themeConfig: {
    logo: '/logo.svg',
    
    nav: [
      { text: 'Accueil', link: '/' },
      { text: 'Guide', link: '/guide/' },
      { text: 'API', link: '/api/' },
      { text: 'Tarifs', link: '/pricing' }
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Introduction',
          items: [
            { text: 'Qu\'est-ce que PeSIT ?', link: '/guide/' },  // PeSIT est le protocole
            { text: 'Démarrage rapide', link: '/guide/quickstart' },
            { text: 'Architecture', link: '/guide/architecture' }
          ]
        },
        {
          text: 'Vectis Client',
          items: [
            { text: 'Installation', link: '/guide/client/installation' },
            { text: 'Configuration', link: '/guide/client/configuration' },
            { text: 'Utilisation', link: '/guide/client/usage' },
            { text: 'Intégration ERP', link: '/guide/client/erp-integration' }
          ]
        },
        {
          text: 'Vectis Server',
          items: [
            { text: 'Installation', link: '/guide/server/installation' },
            { text: 'Configuration', link: '/guide/server/configuration' },
            { text: 'Clustering', link: '/guide/server/clustering' },
            { text: 'Sécurité', link: '/guide/server/security' }
          ]
        },
        {
          text: 'Administration',
          items: [
            { text: 'Console d\'administration', link: '/guide/admin/console' },
            { text: 'Gestion des partenaires', link: '/guide/admin/partners' },
            { text: 'Fichiers virtuels', link: '/guide/admin/virtual-files' },
            { text: 'Orchestrateurs', link: '/guide/admin/orchestrators' },
            { text: 'Registres Docker', link: '/guide/admin/registries' },
            { text: 'Monitoring', link: '/guide/admin/monitoring' }
          ]
        }
      ],
      '/api/': [
        {
          text: 'API Reference',
          items: [
            { text: 'Vue d\'ensemble', link: '/api/' },
            { text: 'Authentification', link: '/api/authentication' },
            { text: 'Client API', link: '/api/client' },
            { text: 'Admin API', link: '/api/admin' },
            { text: 'Server API', link: '/api/server' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/cpoder/vectis' }
    ],

    footer: {
      message: 'Vectis - Solution PeSIT moderne pour les entreprises',
      copyright: 'Copyright © 2025'
    },

    search: {
      provider: 'local'
    }
  }
})
