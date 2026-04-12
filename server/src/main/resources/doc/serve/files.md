## Files
The MOSA server reads and writes its top-level runtime files in the current
working directory unless a command line option explicitly overrides that
location. It is recommended to create a dedicated directory for each server
instance.

### IP blocklist
It is possible to block specific clients using a file-based IP address block
list.

**File name:** `mosa_ip_blocklist.yaml`  
**Managed by:** Operator-maintained configuration  
**Reload:** Automatically reloaded when changed, checked every 5 seconds.

#### Format (YAML)
```yaml
blockedClients:
  - 203.0.113.10
  - 10.0.0.0/8
  - 2001:db8::1
  - 2001:db8::/32
```

#### Rules
- Entries accept IPv4 or IPv6 addresses.
- CIDR prefix is optional; if omitted, full length is assumed (IPv4 `/32`,
  IPv6 `/128`).

### Admin gid allowlist
Admin-only endpoints use a dedicated gid allowlist.

**File name:** `mosa_admin_gids.yaml`  
**Managed by:** Operator-maintained configuration  
**Reload:** Automatically reloaded when changed, checked every 5 seconds.

#### Format (YAML)
```yaml
adminGids:
  - 9yCzAH4qTNXzm19b8rGRXmtluzmB3DOYCtSrmN4DqVc
  - TUAZ14ejUY04XseWKMuxTK_mAC72Psyn1t6AdM8UDpo
```

#### Rules
- Entries accept base64url-encoded gid strings.
- Changes take effect without a restart.

### Structured state database
Structured pod-local state is persisted in a SQLite database outside the vector store.

**File name:** `mosa_db.sqlite`  
**Managed by:** Automatically created and updated by the server at runtime  
**Reload:** Loaded during startup. Restart after manual changes.

The database file is created on startup if it does not exist yet. The server
stores pending admin messages there, removes expired entries during cleanup,
persists blocked identities for moderation and authentication checks, stores
complaints, and may add further structured runtime state in future tables.

### Privacy profile
The privacy endpoint is generated from an operator-maintained ClearTerms
profile.

**File name:** `mosa_privacy.yaml`  
**Managed by:** Operator-maintained configuration  
**Reload:** Loaded during startup. Restart after changes.

Language fragments may be added as sibling files such as
`mosa_privacy.de.yaml`.

### Terms profile
The terms endpoint is generated from an operator-maintained ClearTerms profile.

**File name:** `mosa_terms.yaml`  
**Managed by:** Operator-maintained configuration  
**Reload:** Loaded during startup. Restart after changes.

Language fragments may be added as sibling files such as `mosa_terms.de.yaml`.

### Imprint profile
The imprint endpoint is loaded from a dedicated YAML document.

**File name:** `mosa_imprint.yaml`  
**Managed by:** Operator-maintained configuration  
**Reload:** Loaded during startup. Restart after changes.

This file contains legally relevant publisher information and is required at
startup.

### Pod signing key pair
The server uses an Ed25519 key pair to sign pod certificates and related
artifacts.

**File name:** `mosa_pod_keypair.pem`  
**Managed by:** Automatically created on first startup unless already present  
**Reload:** Loaded during startup. Restart after replacement.

This file contains private key material and must be protected accordingly. If
the file is deleted, the server creates a new identity on the next startup.

### Snapshot archive keys
Freeze-snapshot archives use persisted symmetric archive keys.

**File name:** `mosa_snapshot_archive_keys.json`  
**Managed by:** Automatically created on first startup unless already present  
**Reload:** Loaded during startup. Restart after replacement.

This file contains secret key material and must be protected accordingly. It
stores the active archive key separately from historical keys so older
snapshots remain restorable after future key rotation. Freeze snapshots cover
both content records from the Lucene vector store and pod-local moderation data
from SQLite.

### Vector store index
The Lucene vector store persists its local index in a directory under the
server working directory by default.

**Directory name:** `mosa_lucene_index`  
**Managed by:** Automatically created and updated by the server at runtime  
**Reload:** Live runtime data. Stop the server before manual maintenance.

If the server reports that an older local Lucene index is incompatible after an
upgrade, stop the server, delete `mosa_lucene_index` manually, and start again.
A new empty Lucene index will be created automatically.
