# serve command

## Overview
Starts the MOSA server and allows varous configurations via command line flags.

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
