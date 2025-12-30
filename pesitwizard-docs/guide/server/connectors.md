# Connecteurs de stockage

Les connecteurs permettent de stocker les fichiers transférés sur différents backends de stockage.

## Connecteurs disponibles

| Connecteur | Module | Description |
|------------|--------|-------------|
| `local` | `pesitwizard-connector-local` | Système de fichiers local |
| `sftp` | `pesitwizard-connector-sftp` | Serveur SFTP distant |
| `s3` | `pesitwizard-connector-s3` | AWS S3 ou compatible (MinIO) |

## Connecteur Local

Stockage sur le système de fichiers local du serveur.

```yaml
pesitwizard:
  connector:
    type: local
    local:
      base-path: /data/pesitwizard/files
      create-directories: true
```

### Structure des répertoires

```
/data/pesitwizard/files/
├── received/           # Fichiers reçus
│   └── 2024/01/15/
│       └── file.dat
└── send/               # Fichiers à envoyer
    └── 2024/01/15/
        └── report.csv
```

## Connecteur SFTP

Stockage sur un serveur SFTP distant.

```yaml
pesitwizard:
  connector:
    type: sftp
    sftp:
      host: sftp.example.com
      port: 22
      username: pesitwizard
      # Authentification par mot de passe
      password: ${SFTP_PASSWORD}
      # OU authentification par clé
      private-key-file: /app/secrets/id_rsa
      private-key-passphrase: ${KEY_PASSPHRASE}
      # Options
      base-path: /uploads/pesitwizard
      known-hosts-file: /app/config/known_hosts
      strict-host-key-checking: true
```

### Génération de clé SSH

```bash
# Générer une paire de clés ED25519
ssh-keygen -t ed25519 -f id_rsa -N "" -C "pesitwizard@server"

# Copier la clé publique sur le serveur SFTP
ssh-copy-id -i id_rsa.pub user@sftp.example.com
```

### Kubernetes Secret pour SFTP

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: pesitwizard-sftp
type: Opaque
stringData:
  id_rsa: |
    -----BEGIN OPENSSH PRIVATE KEY-----
    ...
    -----END OPENSSH PRIVATE KEY-----
  known_hosts: |
    sftp.example.com ssh-ed25519 AAAA...
```

## Connecteur S3

Stockage sur AWS S3 ou un stockage compatible (MinIO, Ceph, etc.).

```yaml
pesitwizard:
  connector:
    type: s3
    s3:
      endpoint: https://s3.eu-west-1.amazonaws.com
      region: eu-west-1
      bucket: pesitwizard-files
      access-key: ${AWS_ACCESS_KEY_ID}
      secret-key: ${AWS_SECRET_ACCESS_KEY}
      # Options
      prefix: transfers/
      path-style-access: false  # true pour MinIO
```

### Configuration MinIO

```yaml
pesitwizard:
  connector:
    type: s3
    s3:
      endpoint: http://minio.minio-system:9000
      region: us-east-1
      bucket: pesitwizard
      access-key: ${MINIO_ACCESS_KEY}
      secret-key: ${MINIO_SECRET_KEY}
      path-style-access: true
```

### IAM Policy AWS

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:ListBucket"
      ],
      "Resource": [
        "arn:aws:s3:::pesitwizard-files",
        "arn:aws:s3:::pesitwizard-files/*"
      ]
    }
  ]
}
```

### Authentification IRSA (EKS)

Pour EKS, utilisez IRSA au lieu des clés d'accès :

```yaml
# ServiceAccount avec annotation IRSA
apiVersion: v1
kind: ServiceAccount
metadata:
  name: pesitwizard
  annotations:
    eks.amazonaws.com/role-arn: arn:aws:iam::123456789:role/pesitwizard-s3
```

```yaml
# Configuration sans clés
pesitwizard:
  connector:
    type: s3
    s3:
      region: eu-west-1
      bucket: pesitwizard-files
      # Pas de access-key/secret-key = utilise les credentials du pod
```

## API Connector

L'interface `StorageConnector` expose les opérations de base :

```java
public interface StorageConnector {
    // Écrire un fichier
    void write(String path, InputStream content) throws IOException;
    
    // Lire un fichier
    InputStream read(String path) throws IOException;
    
    // Supprimer un fichier
    void delete(String path) throws IOException;
    
    // Vérifier l'existence
    boolean exists(String path) throws IOException;
    
    // Lister les fichiers
    List<String> list(String prefix) throws IOException;
}
```

## Connecteur personnalisé

Vous pouvez créer votre propre connecteur en implémentant l'interface :

```java
@Component
@ConditionalOnProperty(name = "pesitwizard.connector.type", havingValue = "custom")
public class CustomConnector implements StorageConnector {
    
    @Override
    public void write(String path, InputStream content) throws IOException {
        // Votre implémentation
    }
    
    // ... autres méthodes
}
```

## Variables d'environnement

| Variable | Description |
|----------|-------------|
| `PESITWIZARD_CONNECTOR_TYPE` | Type de connecteur (`local`, `sftp`, `s3`) |
| `SFTP_PASSWORD` | Mot de passe SFTP |
| `AWS_ACCESS_KEY_ID` | Clé d'accès AWS |
| `AWS_SECRET_ACCESS_KEY` | Clé secrète AWS |

## Bonnes pratiques

::: tip Recommandations
- Utilisez S3 ou équivalent pour la haute disponibilité
- Activez le chiffrement côté serveur (SSE-S3 ou SSE-KMS)
- Configurez des politiques de rétention/lifecycle
- Utilisez IRSA sur EKS plutôt que des clés statiques
:::
