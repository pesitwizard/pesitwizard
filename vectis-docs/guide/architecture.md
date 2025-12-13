# Architecture

## Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────┐
│                        Vectis Cloud                               │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │ Client UI    │     │ Admin UI     │     │ Server       │    │
│  │ (Vue.js)     │     │ (Vue.js)     │     │ (Spring Boot)│    │
│  └──────┬───────┘     └──────┬───────┘     └──────┬───────┘    │
│         │                    │                    │             │
│         ▼                    ▼                    ▼             │
│  ┌──────────────┐     ┌──────────────┐     ┌──────────────┐    │
│  │ Client API   │     │ Admin API    │     │ Vectis        │    │
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

### Client Vectis

Le **client** permet d'envoyer et recevoir des fichiers vers/depuis des serveurs Vectis externes (banques).

| Composant | Description | Port |
|-----------|-------------|------|
| vectis-client | Backend Spring Boot | 9081 |
| vectis-client-ui | Interface Vue.js | 3001 |

**Fonctionnalités** :
- Envoi de fichiers (virements, prélèvements)
- Réception de fichiers (relevés, avis)
- Historique des transferts
- Configuration multi-serveurs

### Serveur Vectis

Le **serveur** permet de recevoir des fichiers de partenaires externes.

| Composant | Description | Port |
|-----------|-------------|------|
| vectis-server | Serveur PeSIT + API | 5000 (PeSIT), 8080 (HTTP) |

**Fonctionnalités** :
- Réception de fichiers
- Envoi de fichiers (sur demande)
- Gestion des partenaires
- Fichiers virtuels
- Clustering haute disponibilité

### Console d'administration

La **console** permet de gérer les déploiements de serveurs Vectis sur Kubernetes.

| Composant | Description | Port |
|-----------|-------------|------|
| vectis-admin | Backend Spring Boot | 9080 |
| vectis-admin-ui | Interface Vue.js | 3000 |

**Fonctionnalités** :
- Création/suppression de clusters
- Déploiement sur Kubernetes
- Configuration des partenaires
- Monitoring des pods

## Déploiement Kubernetes

```
┌─────────────────────────────────────────────────────────────┐
│                     Kubernetes Cluster                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    Namespace: vectis                  │   │
│  │                                                      │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐            │   │
│  │  │ Pod 1   │  │ Pod 2   │  │ Pod 3   │            │   │
│  │  │ (Leader)│  │         │  │         │            │   │
│  │  └────┬────┘  └─────────┘  └─────────┘            │   │
│  │       │                                            │   │
│  │       │ vectis-leader=true                         │   │
│  │       ▼                                            │   │
│  │  ┌─────────────────────────────────────────┐      │   │
│  │  │         LoadBalancer Service            │      │   │
│  │  │         (selector: vectis-leader=true)   │      │   │
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

Chaque cluster Vectis dispose de son propre schéma PostgreSQL :

```
vectis (database)
├── admin (schema)
│   ├── vectis_clusters
│   ├── container_registries
│   └── container_orchestrators
│
└── cluster_<uuid> (schema)
    ├── vectis_server_configs
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
