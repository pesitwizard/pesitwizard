# Sécurité

## Vue d'ensemble

La sécurité du serveur Vectis repose sur plusieurs niveaux :

1. **Authentification PeSIT** : Identifiants partenaire/mot de passe
2. **Chiffrement TLS** : PeSIT-E sur TLS 1.2/1.3
3. **Authentification API** : Basic Auth ou JWT
4. **Réseau** : Firewall, VPN, IP whitelisting

## Authentification PeSIT

### Configuration des partenaires

Chaque partenaire doit être enregistré avec un identifiant et mot de passe :

```bash
curl -X POST http://localhost:8080/api/partners \
  -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{
    "partnerId": "BANQUE_XYZ",
    "password": "MotDePasseComplexe123!",
    "enabled": true
  }'
```

### Politique de mots de passe

Recommandations :
- Minimum 12 caractères
- Mélange majuscules/minuscules/chiffres/symboles
- Rotation tous les 90 jours
- Pas de réutilisation des 5 derniers mots de passe

## Chiffrement TLS (PeSIT-E)

### Générer les certificats serveur

```bash
# Générer la clé privée et le certificat
openssl req -x509 -newkey rsa:4096 \
  -keyout server-key.pem \
  -out server-cert.pem \
  -days 365 \
  -nodes \
  -subj "/CN=vectis.monentreprise.com/O=Mon Entreprise/C=FR"

# Convertir en PKCS12
openssl pkcs12 -export \
  -in server-cert.pem \
  -inkey server-key.pem \
  -out server-keystore.p12 \
  -name server \
  -password pass:changeit
```

### Configuration TLS

```yaml
vectis:
  server:
    tls:
      enabled: true
      keystore-path: /config/server-keystore.p12
      keystore-password: changeit
      # Optionnel : authentification client par certificat
      client-auth: NEED  # NONE, WANT, NEED
      truststore-path: /config/truststore.p12
      truststore-password: changeit
```

### Importer les certificats clients

```bash
# Importer le certificat d'un client
keytool -importcert \
  -alias client-banque-xyz \
  -file banque-xyz.crt \
  -keystore truststore.p12 \
  -storetype PKCS12 \
  -storepass changeit
```

## Sécurité de l'API REST

### Basic Authentication

```yaml
vectis:
  admin:
    username: admin
    password: ${ADMIN_PASSWORD}  # Via variable d'environnement
```

### Changer le mot de passe admin

```bash
# Via variable d'environnement
docker run -e VECTIS_ADMIN_PASSWORD=NouveauMotDePasse ...
```

### HTTPS pour l'API

En production, placez un reverse proxy (nginx, traefik) devant l'API :

```nginx
server {
    listen 443 ssl;
    server_name vectis-admin.monentreprise.com;
    
    ssl_certificate /etc/ssl/certs/server.crt;
    ssl_certificate_key /etc/ssl/private/server.key;
    
    location / {
        proxy_pass http://vectis-server:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## Sécurité réseau

### Firewall

Ports à ouvrir :

| Port | Service | Accès |
|------|---------|-------|
| 5000 | PeSIT | Partenaires uniquement |
| 8080 | API REST | Interne uniquement |

### IP Whitelisting

Restreindre l'accès PeSIT aux IPs connues :

```yaml
# NetworkPolicy Kubernetes
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: vectis-server-policy
spec:
  podSelector:
    matchLabels:
      app: vectis-server
  policyTypes:
  - Ingress
  ingress:
  - from:
    - ipBlock:
        cidr: 10.0.0.0/8  # Réseau interne
    - ipBlock:
        cidr: 203.0.113.0/24  # IPs de la banque
    ports:
    - port: 5000
```

### VPN

Pour les échanges sensibles, utilisez un VPN site-à-site :

```
[Votre réseau] ──VPN IPSec── [Réseau banque]
      │                            │
      ▼                            ▼
 Vectis Client              Vectis Server
```

## Audit et traçabilité

### Logs d'accès

Tous les accès sont journalisés :

```
2025-01-10 10:30:00 INFO  [CONNECT] Partner=BANQUE_XYZ IP=203.0.113.50 Status=SUCCESS
2025-01-10 10:30:01 INFO  [CREATE] Partner=BANQUE_XYZ File=VIREMENT.XML Status=SUCCESS
2025-01-10 10:30:05 INFO  [RELEASE] Partner=BANQUE_XYZ Duration=5s Bytes=15234
```

### Rétention des logs

```yaml
logging:
  file:
    name: /var/log/vectis/vectis-server.log
    max-size: 100MB
    max-history: 90  # 90 jours
```

### Export vers SIEM

Configurez Filebeat ou Fluentd pour envoyer les logs vers votre SIEM :

```yaml
# filebeat.yml
filebeat.inputs:
- type: log
  paths:
    - /var/log/vectis/*.log
  
output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "vectis-logs-%{+yyyy.MM.dd}"
```

## Checklist sécurité

- [ ] Mots de passe partenaires complexes
- [ ] TLS activé (PeSIT-E)
- [ ] Certificats valides et non expirés
- [ ] API protégée par HTTPS
- [ ] Mot de passe admin changé
- [ ] Firewall configuré
- [ ] IP whitelisting activé
- [ ] Logs centralisés
- [ ] Alertes configurées
- [ ] Sauvegardes testées
