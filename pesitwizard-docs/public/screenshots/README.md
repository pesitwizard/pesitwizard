# Screenshots pour la documentation

Ce dossier contient les captures d'écran utilisées dans la documentation.

## Structure

```
screenshots/
├── client/           # Screenshots du client PeSIT Wizard
│   ├── dashboard.png
│   ├── transfer-send.png
│   ├── transfer-receive.png
│   ├── path-placeholders.png
│   ├── favorites.png
│   ├── favorite-edit.png
│   ├── schedules.png
│   ├── schedule-create.png
│   └── calendars.png
│
└── admin/            # Screenshots de l'admin PeSIT Wizard
    ├── dashboard.png
    ├── clusters.png
    ├── virtual-files.png
    ├── virtual-file-placeholders.png
    ├── partners.png
    └── transfers.png
```

## Comment générer les screenshots

### Prérequis

1. Démarrer les services :
   ```bash
   cd /home/cpo/pesitwizard/scripts
   ./start-all.sh
   ```

2. Accéder aux interfaces :
   - Client UI : http://localhost:5173
   - Admin UI : http://localhost:3000

### Screenshots à capturer

#### Client UI (http://localhost:5173)

| Fichier | Page | Description |
|---------|------|-------------|
| `dashboard.png` | Dashboard | Vue d'ensemble avec statistiques |
| `transfer-send.png` | Transfer | Formulaire SEND avec champs remplis |
| `transfer-receive.png` | Transfer | Formulaire RECEIVE avec placeholders |
| `path-placeholders.png` | Transfer | Composant placeholders avec tags visibles |
| `favorites.png` | Favorites | Liste des favoris avec cartes |
| `favorite-edit.png` | Favorites | Modal d'édition d'un favori |
| `schedules.png` | Schedules | Liste des planifications |
| `schedule-create.png` | Favorites | Modal de création de planification |
| `calendars.png` | Calendars | Liste des calendriers métier |
| `tls-config-nav.png` | Navigation | Menu latéral avec TLS Config surligné |
| `tls-import-truststore.png` | TLS Config | Modal import truststore (CA) |
| `tls-import-keystore.png` | TLS Config | Modal import keystore (client cert) |
| `tls-enabled.png` | TLS Config | Vue avec TLS activé et certificats configurés |
| `tls-status.png` | TLS Config | Statut TLS avec informations certificats |

#### Admin UI (http://localhost:3000)

| Fichier | Page | Description |
|---------|------|-------------|
| `dashboard.png` | Dashboard | Vue d'ensemble des clusters |
| `clusters.png` | Clusters | Liste des clusters |
| `virtual-files.png` | Virtual Files | Liste des fichiers virtuels |
| `virtual-file-placeholders.png` | Virtual Files | Formulaire avec placeholders |
| `partners.png` | Partners | Liste des partenaires |
| `transfers.png` | Transfers | Historique des transferts |

### Conseils pour les captures

1. **Résolution** : 1280x800 minimum
2. **Format** : PNG
3. **Contenu** : Utiliser des données réalistes (ex: "BNP Paribas", "VIREMENTS_SEPA")
4. **Thème** : Mode clair
5. **Recadrage** : Éviter les barres de navigateur

### Outils recommandés

- **macOS** : Cmd+Shift+4 puis Espace pour capturer une fenêtre
- **Linux** : Flameshot, GNOME Screenshot
- **Windows** : Snipping Tool, ShareX
- **Navigateur** : Extension "Full Page Screen Capture"
