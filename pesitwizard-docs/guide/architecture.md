# Architecture

## Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────┐
│                        PeSIT Wizard Cloud                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │ Client UI    │     │ Admin UI     │     │ Server       │    │
│  │ (Vue.js)     │     │ (Vue.js)     │     │ (Spring Boot)│    │
│  └──────┬───────┘     └──────┬───────┘     └──────┬───────┘    │
│         │                    │                    │             │
│         ▼                    ▼                    ▼             │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │ Client API   │     │ Admin API    │     │ PeSIT Wizard        │    │
│  │ (Spring Boot)│     │ (Spring Boot)│     │ Protocol     │    │
│  │ Port 9081    │     │ Port 9080    │     │ Port 5000    │    │
│  └──────┬───────┘     └──────┬───────┘     └──────┬───────┘    │
│         │                    │                    │             │
│         └────────────────────┼────────────────────┘             │
│                              │                                  │
│                              ▼                                  │
│                       ┌──────────────┐                         │
│                       │ PostgreSQL   │                         │
│                       │ Database     │                         │
│                       └──────────────┘                         │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Composants

### Client PeSIT Wizard

Le **client** permet d'envoyer et recevoir des fichiers vers/depuis des serveurs PeSIT Wizard externes (banques).

| Composant | Description | Port |
|-----------|-------------|------|
| pesitwizard-client | Backend Spring Boot | 9081 |
| pesitwizard-client-ui | Interface Vue.js | 3001 |

**Fonctionnalités** :
- Envoi de fichiers (virements, prélèvements)
- Réception de fichiers (relevés, avis)
- Historique des transferts
- Configuration multi-serveurs

### Serveur PeSIT Wizard

Le **serveur** permet de recevoir des fichiers de partenaires externes.

| Composant | Description | Port |
|-----------|-------------|------|
| pesitwizard-server | Serveur PeSIT + API | 5000 (PeSIT), 8080 (HTTP) |

**Fonctionnalités** :
- Réception de fichiers
- Envoi de fichiers (sur demande)
- Gestion des partenaires
- Fichiers virtuels
- Clustering haute disponibilité

## Déploiement Kubernetes

```
┌─────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    Namespace: pesitwizard                  │   │
│  │                                                      │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐            │   │
│  │  │ Pod 1   │  │ Pod 2   │  │ Pod 3   │            │   │
│  │  │ (Leader)│  │         │  │         │            │   │
│  │  └────┬────┘  └─────────┘  └─────────┘            │   │
│  │       │                                            │   │
│  │       │ pesitwizard-leader=true                         │   │
│  │       ▼                                            │   │
│  │  ┌─────────────────────────────────────────┐      │   │
│  │  │         LoadBalancer Service            │      │   │
│  │  │         (selector: pesitwizard-leader=true)   │      │   │
│  │  └─────────────────────────────────────────┘      │   │
│  │                                                      │   │
│  └─────────────────────────────────────────────────────┘   │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Haute disponibilité

- **3 replicas** par défaut
- **Élection de leader** via JGroups
- **Labeling automatique** du pod leader
- **LoadBalancer** route vers le leader uniquement

## Base de données

Chaque cluster PeSIT Wizard dispose de son propre schéma PostgreSQL :

```
pesitwizard (database)
├── admin (schema)
│   ├── pesitwizard_clusters
│   ├── container_registries
│   └── container_orchestrators
│
└── cluster_<uuid> (schema)
    ├── pesitwizard_server_configs
    ├── partners
    ├── virtual_files
    └── transfer_history
```

## Flux de données

### Envoi de fichier (Client → Banque)

```
1. Utilisateur upload fichier via UI
2. Client API stocke le fichier temporairement
3. Client API ouvre connexion PeSIT vers la banque
4. Échange CONNECT/ACONNECT
5. Échange CREATE/ACK
6. Envoi des données (DTF)
7. Fermeture (DESELECT, RELEASE)
8. Historique mis à jour en base
```

### Réception de fichier (Banque → Client)

```
1. Utilisateur demande fichier via UI
2. Client API ouvre connexion PeSIT vers la banque
3. Échange CONNECT/ACONNECT (mode lecture)
4. Échange SELECT/ACK
5. Réception des données (DTF)
6. Fichier stocké localement
7. Historique mis à jour en base
```
