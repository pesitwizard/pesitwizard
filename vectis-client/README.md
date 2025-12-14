# Vectis Client

Client Java pour effectuer des transferts de fichiers via le protocole PeSIT. Expose une API REST utilisée par l'interface web `vectis-client-ui`.

## Fonctionnalités

- **Envoi de fichiers** vers des serveurs PeSIT
- **Réception de fichiers** depuis des serveurs PeSIT
- **Gestion des serveurs** : Configuration de plusieurs serveurs PeSIT cibles
- **Historique des transferts** : Stockage en base de données

## Prérequis

- Java 21+
- Maven 3.9+

## Build

```bash
# Installer d'abord la bibliothèque PeSIT
cd ../vectis-pesit
mvn install -DskipTests

# Builder le client
cd ../vectis-client
mvn package -DskipTests
```

## Exécution

```bash
java -jar target/vectis-client-1.0.0-SNAPSHOT.jar
```

Le serveur démarre sur le port **9081**.

## Configuration

Fichier `application.yml` :

```yaml
server:
  port: 9081

spring:
  datasource:
    url: jdbc:h2:file:./data/vectis-client-db
```

## API REST

### Serveurs

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| GET | `/api/servers` | Liste des serveurs configurés |
| POST | `/api/servers` | Ajouter un serveur |
| DELETE | `/api/servers/{id}` | Supprimer un serveur |

### Transferts

| Méthode | Endpoint | Description |
|---------|----------|-------------|
| POST | `/api/transfers/send` | Envoyer un fichier |
| POST | `/api/transfers/receive` | Recevoir un fichier |
| GET | `/api/transfers` | Historique des transferts |

### Exemple d'envoi

```bash
curl -X POST http://localhost:9081/api/transfers/send \
  -H "Content-Type: multipart/form-data" \
  -F "file=@document.pdf" \
  -F "serverId=1" \
  -F "remoteFilename=DOCUMENT.PDF"
```

## Docker

```bash
docker build -t vectis-client .
docker run -p 9081:9081 vectis-client
```

## Stack technique

- Spring Boot 3.x
- Java 21
- H2 / PostgreSQL
- vectis-pesit
