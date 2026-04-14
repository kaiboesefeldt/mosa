Update the client-side usage of the moved contracts and services.

This includes:

- adapting client services, pod access, UI controllers, and tests to the new namespaces
- removing imports that still pull identity or snapshot concerns through `straightway.mosa.content`
- checking whether any helper types or naming in the client still preserve the old false ownership and cleaning that up if necessary

The outcome of this subtask is that the client package graph no longer reinforces the old `content` catch-all.
