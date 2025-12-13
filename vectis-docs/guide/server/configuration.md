# Configuration du Serveur

## Variables d'environnement

| Variable | Description | Défaut |
|----------|-------------|--------|
| `SPRING_DATASOURCE_URL` | URL JDBC PostgreSQL | - |
| `SPRING_DATASOURCE_USERNAME` | Utilisateur DB | vectis |
| `SPRING_DATASOURCE_PASSWORD` | Mot de passe DB | vectis |
| `VECTIS_CLUSTER_ENABLED` | Activer le clustering | false |
| `POD_NAME` | Nom du pod (K8s) | - |
| `POD_NAMESPACE` | Namespace (K8s) | default |

## Fichier application.yml

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/vectis
    username: vectis
    password: vectis

vectis:
  # Configuration du clustering
  cluster:
    enabled: true
    name: vectis-cluster
  
  # Sécurité API
  admin:
    username: admin
    password: admin
```

## Configuration des serveurs PeSIT

Un serveur Vectis peut héberger plusieurs "serveurs PeSIT logiques" sur différents ports.

### Via API

```bash
curl -X POST http://localhost:8080/api/servers \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "serverId": "PESIT_SERVER",
    "port": 5000,
    "autoStart": true,
    "maxConnections": 100,
    "readTimeout": 60000
  }'
```

### Paramètres

| Paramètre | Description | Défaut |
|-----------|-------------|--------|
| `serverId` | Identifiant du serveur (PI_04) | - |
| `port` | Port TCP d'écoute | 5000 |
| `autoStart` | Démarrer automatiquement | true |
| `maxConnections` | Connexions simultanées max | 100 |
| `readTimeout` | Timeout lecture (ms) | 60000 |

## Configuration des partenaires

Les partenaires sont les clients autorisés à se connecter.

### Via API

```bash
curl -X POST http://localhost:8080/api/partners \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "partnerId": "CLIENT_ENTREPRISE",
    "name": "Mon Client",
    "password": "secret123",
    "enabled": true,
    "allowedOperations": ["READ", "WRITE"]
  }'
```

### Paramètres

| Paramètre | Description |
|-----------|-------------|
| `partnerId` | Identifiant du partenaire (PI_03) |
| `name` | Nom affiché |
| `password` | Mot de passe (PI_05) |
| `enabled` | Partenaire actif |
| `allowedOperations` | Opérations autorisées (READ, WRITE) |

## Configuration des fichiers virtuels

Les fichiers virtuels définissent les chemins de stockage.

### Via API

```bash
curl -X POST http://localhost:8080/api/virtual-files \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "fileId": "VIREMENTS",
    "name": "Fichiers de virements",
    "sendDirectory": "/data/send/virements",
    "receiveDirectory": "/data/received/virements",
    "filenamePattern": "*.xml"
  }'
```

### Paramètres

| Paramètre | Description |
|-----------|-------------|
| `fileId` | Identifiant du fichier virtuel (PI_12) |
| `name` | Nom affiché |
| `sendDirectory` | Répertoire des fichiers à envoyer |
| `receiveDirectory` | Répertoire des fichiers reçus |
| `filenamePattern` | Pattern de noms de fichiers |

## Répertoires de stockage

```
/data
├── send/           # Fichiers à envoyer
│   ├── virements/
│   └── releves/
├── received/       # Fichiers reçus
│   ├── virements/
│   └── releves/
└── temp/           # Fichiers temporaires
```

### Configuration du volume (Kubernetes)

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: vectis-data
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 50Gi
```

## Logs et monitoring

### Niveaux de log

```yaml
logging:
  level:
    com.vectis: INFO
    com.vectis.server.handler: DEBUG  # Détails des sessions
    com.vectis.protocol: DEBUG        # Messages PeSIT
```

### Métriques Prometheus

Le serveur expose des métriques sur `/actuator/prometheus` :

- `vectis_connections_active` : Connexions actives
- `vectis_transfers_total` : Nombre total de transferts
- `vectis_transfers_bytes_total` : Volume transféré
- `vectis_errors_total` : Nombre d'erreurs

### Health checks

```bash
# Readiness (prêt à recevoir du trafic)
curl http://localhost:8080/actuator/health/readiness

# Liveness (application en vie)
curl http://localhost:8080/actuator/health/liveness

# Health complet
curl http://localhost:8080/actuator/health
```
