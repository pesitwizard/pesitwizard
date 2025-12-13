# Qu'est-ce que PeSIT ?

**PeSIT** (Protocole d'Échange pour un Système Interbancaire de Télécompensation) est un protocole de transfert de fichiers développé par le secteur bancaire français dans les années 1980.

## Historique

Le protocole a été créé par le **GSIT** (Groupement pour un Système Interbancaire de Télécompensation) pour permettre les échanges de fichiers entre banques et avec leurs clients entreprises.

## Caractéristiques

- **Fiabilité** : Mécanismes de reprise sur erreur, points de synchronisation
- **Sécurité** : Authentification des partenaires, chiffrement (PeSIT-E sur TLS)
- **Traçabilité** : Historique complet des transferts
- **Interopérabilité** : Standard reconnu par toutes les banques françaises

## Versions

| Version | Transport | Sécurité |
|---------|-----------|----------|
| PeSIT D | TCP/IP | Authentification simple |
| PeSIT E | TCP/IP + TLS | Chiffrement, certificats |

## Qui utilise PeSIT ?

- **Banques** : BNP Paribas, Société Générale, BPCE, Crédit Agricole...
- **Entreprises** : Pour automatiser les échanges de fichiers bancaires
- **Éditeurs** : Intégration dans les ERP et logiciels comptables
- **Prestataires** : Centres de traitement, PSP

## Notre solution : Vectis

**Vectis** implémente le protocole PeSIT dans une architecture moderne :

- **Vectis Client** : Pour envoyer/recevoir des fichiers vers les banques
- **Vectis Server** : Pour recevoir des fichiers de partenaires
- **Console d'administration** : Pour gérer l'ensemble

[Démarrer rapidement →](/guide/quickstart)
