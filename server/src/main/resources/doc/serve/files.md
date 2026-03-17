## Files
The runtime environment and any runtime config files are located in the working
directory of the MOSA server. It is recommended to create a dedicated directory
for each server instance.

### IP blocklist
It is possible to block specific clients using a file-based IP address block list.

**File name:** `mosa_ip_blocklist.yaml`  
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
- CIDR prefix is optional; if omitted, full length is assumed (IPv4 `/32`, IPv6 `/128`).

### Identity blocklist
It is possible to block specific identities using a file-based gid block list. Blocked gids are rejected on `/v1/approvegid` and all authenticated requests.

**File name:** `mosa_identity_blocklist.yaml`  
**Reload:** Automatically reloaded when changed, checked every 5 seconds.

#### Format (YAML)
```yaml
blockedGids:
  - 9yCzAH4qTNXzm19b8rGRXmtluzmB3DOYCtSrmN4DqVc
  - TUAZ14ejUY04XseWKMuxTK_mAC72Psyn1t6AdM8UDpo
```

#### Rules
- Entries accept base64url-encoded gid strings.

### Vector store index
The Lucene vector store persists its index under the server working directory.

**Directory name:** `mosa_lucene_index`  
**Reset:** Stop the server and delete the directory to delete the index.

If the server reports that an older local Lucene index is incompatible after an upgrade, stop the server, delete `mosa_lucene_index` manually, and start again. A new empty Lucene index will be created automatically.
