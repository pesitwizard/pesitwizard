# Vectis Server

Serveur PeSIT (Protocole d'Échange pour un Système Interbancaire de Télécompensation) implémenté en Spring Boot.

## Fonctionnalités

- **Protocole PeSIT Hors-SIT** : Envoi et réception de fichiers
- **API REST** : Configuration et monitoring via HTTP
- **Gestion des partenaires** : Configuration des connexions entrantes/sortantes
- **Fichiers virtuels** : Mapping des fichiers logiques
- **TLS** : Support du chiffrement SSL/TLS

## Prérequis

- Java 21+
- Maven 3.9+

## Build

```bash
# Installer d'abord la bibliothèque protocole
cd ../vectis-pesit
mvn install -DskipTests

# Builder le serveur
cd ../vectis-server
mvn package -DskipTests
```

## Exécution

```bash
java -jar target/vectis-server-1.0.0-SNAPSHOT.jar
```

- **Port PeSIT** : 5000
- **Port API REST** : 8080

## Configuration

Fichier `application.yml` :

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/vectis-db  # H2 par défaut, PostgreSQL recommandé
    
vectis:
  server:
    port: 5000
    ssl:
      enabled: false
```

### Variables d'environnement

| Variable | Description | Défaut |
|----------|-------------|--------|
| `VECTIS_SERVER_PORT` | Port PeSIT | `5000` |
| `SERVER_PORT` | Port API REST | `8080` |
| `SPRING_DATASOURCE_URL` | URL JDBC | H2 file |

## API REST

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/v1/servers` | Liste des serveurs configurés |
| POST | `/api/v1/servers/{id}/start` | Démarrer un serveur |
| POST | `/api/v1/servers/{id}/stop` | Arrêter un serveur |
| GET | `/api/v1/partners` | Liste des partenaires |
| GET | `/api/v1/virtual-files` | Liste des fichiers virtuels |
| GET | `/api/v1/transfers` | Historique des transferts |

## Monitoring

Endpoints Actuator :

- Health : `GET /actuator/health`
- Metrics : `GET /actuator/metrics`

## Docker

```bash
docker build -t vectis-server .
docker run -p 5000:5000 -p 8080:8080 vectis-server
```

## Stack technique

- Spring Boot 3.x
- Java 21
- H2 / PostgreSQL

## Enterprise

Pour le clustering haute disponibilité et la console d'administration, voir [Vectis Enterprise](https://vectis.cloud).
