# PeSIT Wizard - Open Source PeSIT File Transfer

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21+-orange.svg)](https://openjdk.org/)

Solution open source de transfert de fichiers basée sur le protocole **PeSIT** (Protocole d'Échange pour un Système Interbancaire de Télécompensation).

## Qu'est-ce que PeSIT ?

PeSIT est le protocole standard utilisé par les banques françaises pour les échanges de fichiers sécurisés :
- Virements SEPA
- Relevés de compte
- Prélèvements
- Échanges interbancaires

## Composants

| Module | Description |
|--------|-------------|
| `pesitwizard-server` | Serveur PeSIT complet avec API REST |
| `pesitwizard-client` | Client Java pour envoyer/recevoir des fichiers |
| `pesitwizard-client-ui` | Interface graphique pour le client |
| `pesitwizard-pesit` | Bibliothèque d'implémentation du protocole PeSIT |
| `pesitwizard-docs` | Documentation |

## Démarrage rapide

### Prérequis

- Java 21+
- Maven 3.9+

### Installation

```bash
# Cloner le repo
git clone https://github.com/cpoder/pesitwizard.git
cd pesitwizard

# Builder le serveur
cd pesitwizard-server
mvn package -DskipTests

# Lancer le serveur
java -jar target/pesitwizard-server-1.0.0-SNAPSHOT.jar
```

Le serveur démarre sur :
- **Port 5000** : Protocole PeSIT
- **Port 8080** : API REST

### Envoyer un fichier

```bash
# Avec le client Java
cd pesitwizard-client
mvn exec:java -Dexec.args="send -h localhost -p 5000 -f /path/to/file.txt"
```

### API REST

```bash
# Statut du serveur
curl http://localhost:8080/actuator/health

# Liste des transferts
curl http://localhost:8080/api/v1/transfers
```

## Configuration

### Fichier `application.yml`

```yaml
pesitwizard:
  server:
    port: 5000
    ssl:
      enabled: false
  
spring:
  datasource:
    url: jdbc:h2:file:./data/pesitwizard-db
```

### Variables d'environnement

| Variable | Description | Défaut |
|----------|-------------|--------|
| `PESITWIZARD_SERVER_PORT` | Port PeSIT | `5000` |
| `SERVER_PORT` | Port API REST | `8080` |

## Docker

```bash
# Builder l'image
docker build -t pesitwizard-server ./pesitwizard-server

# Lancer
docker run -p 5000:5000 -p 8080:8080 pesitwizard-server
```

## Kubernetes

### Installation rapide

**Client PeSIT Wizard** (transfert de fichiers avec UI) :
```bash
curl -fsSL https://raw.githubusercontent.com/cpoder/pesitwizard/main/scripts/install-client.sh | bash
```

**Serveur PeSIT Wizard** (standalone) :
```bash
curl -fsSL https://raw.githubusercontent.com/cpoder/pesitwizard/main/scripts/install-server.sh | bash
```

**Désinstallation** :
```bash
curl -fsSL https://raw.githubusercontent.com/cpoder/pesitwizard/main/scripts/uninstall.sh | bash
```

### Helm Charts

Les charts Helm sont disponibles dans `pesitwizard-helm-charts/` :
- `pesitwizard-client` : Client avec API et UI
- `pesitwizard-server` : Serveur standalone

```bash
# Installation manuelle avec Helm
helm install pesitwizard-client ./pesitwizard-helm-charts/pesitwizard-client -n pesitwizard --create-namespace
helm install pesitwizard-server ./pesitwizard-helm-charts/pesitwizard-server -n pesitwizard --create-namespace
```

## Documentation

- [Guide de démarrage](https://docs.pesitwizard.com/guide/quickstart)
- [Configuration serveur](https://docs.pesitwizard.com/guide/server/configuration)
- [API REST](https://docs.pesitwizard.com/api/)

## Structure du projet

```
pesitwizard/
├── pesitwizard-server/       # Serveur PeSIT
├── pesitwizard-client/       # Client Java
├── pesitwizard-client-ui/    # Interface client (Vue.js)
├── pesitwizard-pesit/        # Bibliothèque protocole
├── pesitwizard-docs/         # Documentation (VitePress)
└── scripts/             # Scripts utilitaires
```

## Contribuer

Les contributions sont les bienvenues ! Voir [CONTRIBUTING.md](CONTRIBUTING.md).

## Enterprise

Pour les fonctionnalités entreprise (clustering HA, console d'administration, support), voir [PeSIT Wizard Enterprise](https://pesitwizard.com).

## Licence

[Apache License 2.0](LICENSE)

---

**PeSIT Wizard** - Solution PeSIT moderne et open source.
