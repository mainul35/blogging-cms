# Publishing via Cloudflare Tunnel

How the app becomes publicly reachable without opening any inbound port on
`proxy-vm` at all.

## proxy-vm already has a tunnel — you don't need to install anything

Checking `docker ps` on proxy-vm turned up a `cloudflared` container
already running with `network_mode: host`, launched with
`tunnel run --token <...>` — a **dashboard-managed** tunnel (its ingress
rules live in the Cloudflare Zero Trust dashboard, not a local
`config.yml`), separate from a couple of other per-service `cloudflared`
containers already in use for other things on this box. Since it runs with
host networking, `127.0.0.1` inside that container *is* proxy-vm's own
loopback — which is exactly why `docker-compose.prod.yml`'s `frontend`
service publishes as `127.0.0.1:2368:3000`: this tunnel can already reach
it directly, no new tunnel, install, or local config needed at all.

This also means TLS is already handled — Cloudflare terminates it at their
edge, and the tunnel carries plain HTTP from there to `127.0.0.1:2368`. No
certbot/Let's Encrypt on the VM.

## What you actually need to do

Everything below happens in the **Cloudflare Zero Trust dashboard**
(`one.dash.cloudflare.com` → Networks → Tunnels) — not on proxy-vm itself.
This needs your Cloudflare account access, which is why this is the one
piece of the whole deployment I can't do for you.

1. Find the tunnel that corresponds to the plain `cloudflared` container
   (host network mode) — cross-reference by tunnel name/ID if you run
   `docker inspect cloudflared --format='{{.Config.Cmd}}'` on proxy-vm and
   compare against the dashboard's tunnel list.
2. **Public Hostname → Add a public hostname**:
   - Subdomain/domain: whatever you want this blog to live at (e.g.
     `blog.yourdomain.com`)
   - Service: **HTTP**, URL: `127.0.0.1:2368`
3. Save. Cloudflare creates the DNS record for you automatically.
4. Set `PUBLIC_DOMAIN` in your `blogging-cms-prod-env` secret (see
   [DEPLOYMENT.md](DEPLOYMENT.md#2-blogging-cms-prod-env-secret-file)) to
   that exact hostname — it must match what you entered above.

## Verify

```bash
curl -I https://blog.yourdomain.com          # from anywhere, not just proxy-vm
```

A `200`/`30x` confirms the full path: Cloudflare → the existing tunnel →
`127.0.0.1:2368` → `frontend` (which proxies `/api/*` to `backend`
server-side itself — see [DEPLOYMENT.md](DEPLOYMENT.md)'s architecture
section). If you get a `502` from Cloudflare, the tunnel itself is fine but
the local origin isn't answering — check on proxy-vm directly:

```bash
curl -I http://127.0.0.1:2368                        # does the frontend container answer?
docker ps --filter name=blog_frontend
```

## Firewall note

Since this `cloudflared` container never accepts inbound connections (it
only dials out), nothing legitimate needs to reach proxy-vm's public
IP/router on any port for this app — only `127.0.0.1:2368` does, and only
from that tunnel container on the same host.

## If you ever want Harbor reachable remotely too

Not recommended for now (keep the registry on `localhost:5000`, reachable
only from proxy-vm's own Docker daemon, per
[harbor-registry-setup.md](harbor-registry-setup.md)) — but if you later
want it, add another Public Hostname rule in the same dashboard pointing a
separate hostname at `http://127.0.0.1:5000`.
