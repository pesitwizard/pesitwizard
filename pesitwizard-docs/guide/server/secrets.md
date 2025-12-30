# Gestion des secrets

Le module `pesitwizard-security` fournit une gestion sécurisée des secrets (mots de passe partenaires, clés API, etc.).

## Providers disponibles

| Provider | Description | Cas d'usage |
|----------|-------------|-------------|
| `aes` | Chiffrement AES local | Développement, petites installations |
| `vault` | HashiCorp Vault | Production, multi-environnements |

## Configuration AES (par défaut)

Le provider AES utilise une clé maître pour chiffrer/déchiffrer les secrets stockés en base de données.

```yaml
pesitwizard:
  secrets:
    provider: aes
    aes:
      key-file: /app/secrets/master.key
```

### Génération de la clé maître

```bash
# Générer une clé AES-256
openssl rand -base64 32 > master.key

# Sécuriser les permissions
chmod 600 master.key
```

### Kubernetes Secret

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: pesitwizard-secrets
type: Opaque
data:
  master.key: <base64-encoded-key>
```

```yaml
# Dans le Deployment
volumes:
  - name: secrets
    secret:
      secretName: pesitwizard-secrets
volumeMounts:
  - name: secrets
    mountPath: /app/secrets
    readOnly: true
```

## Configuration HashiCorp Vault

Pour les environnements de production, Vault offre une gestion centralisée des secrets.

```yaml
pesitwizard:
  secrets:
    provider: vault
    vault:
      address: https://vault.example.com
      token: ${VAULT_TOKEN}
      path: secret/data/pesitwizard
```

### Authentification Vault

**Token (développement)** :
```yaml
pesitwizard:
  secrets:
    vault:
      token: ${VAULT_TOKEN}
```

**Kubernetes Auth (production)** :
```yaml
pesitwizard:
  secrets:
    vault:
      auth-method: kubernetes
      kubernetes:
        role: pesitwizard
        jwt-path: /var/run/secrets/kubernetes.io/serviceaccount/token
```

### Structure des secrets dans Vault

```
secret/data/pesitwizard/
├── partners/
│   ├── PARTNER_ID_1/
│   │   └── password
│   └── PARTNER_ID_2/
│       └── password
└── global/
    └── master-key
```

### Configuration Vault (HCL)

```hcl
# Policy pour pesitwizard
path "secret/data/pesitwizard/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}

# Kubernetes auth role
vault write auth/kubernetes/role/pesitwizard \
    bound_service_account_names=pesitwizard \
    bound_service_account_namespaces=pesitwizard \
    policies=pesitwizard \
    ttl=1h
```

## API SecretsService

Le service `SecretsService` expose une API simple pour gérer les secrets :

```java
@Autowired
private SecretsService secretsService;

// Stocker un secret
secretsService.storeSecret("partners/BANK01/password", "s3cr3t");

// Récupérer un secret
String password = secretsService.getSecret("partners/BANK01/password");

// Supprimer un secret
secretsService.deleteSecret("partners/BANK01/password");
```

## Rotation des secrets

### Rotation de la clé AES

1. Générer une nouvelle clé
2. Déchiffrer tous les secrets avec l'ancienne clé
3. Rechiffrer avec la nouvelle clé
4. Remplacer le fichier de clé

```bash
# Script de rotation (à implémenter selon vos besoins)
./scripts/rotate-master-key.sh old.key new.key
```

### Rotation avec Vault

Vault gère automatiquement la rotation via les politiques de TTL.

## Sécurité

::: warning Bonnes pratiques
- Ne jamais commiter les clés dans Git
- Utiliser des permissions restrictives (600)
- Préférer Vault en production
- Activer l'audit logging
:::

## Variables d'environnement

| Variable | Description |
|----------|-------------|
| `PESITWIZARD_SECRETS_PROVIDER` | `aes` ou `vault` |
| `PESITWIZARD_SECRETS_AES_KEY_FILE` | Chemin vers la clé AES |
| `VAULT_ADDR` | URL de Vault |
| `VAULT_TOKEN` | Token d'authentification Vault |
