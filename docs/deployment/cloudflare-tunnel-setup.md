# Publishing via Cloudflare Tunnel

How the app becomes publicly reachable without opening any inbound port on
`proxy-vm` at all. `cloudflared` (Cloudflare's connector) runs on proxy-vm
and makes an **outbound-only** connection to Cloudflare's network; traffic
for your domain arrives at Cloudflare, rides that tunnel back to
`cloudflared`, and only then reaches the app over `127.0.0.1` — nothing
ever needs to be forwarded through your router, and Cloudflare terminates
TLS for you (no certbot/Let's Encrypt needed on the VM at all, which
supersedes the "add Caddy for TLS later" note that used to be in
[DEPLOYMENT.md](DEPLOYMENT.md)).

This is why `docker-compose.prod.yml`'s `edge` service publishes as
`127.0.0.1:80:80` rather than `80:80` — `cloudflared` is meant to be the
*only* thing that can reach it; binding to every interface would let anyone
who finds proxy-vm's real IP bypass the tunnel and hit the app directly,
unencrypted.

## Prerequisites

- The domain you want to use (e.g. `yourdomain.com`) added to a Cloudflare
  account — the free plan is enough for this.
- `PUBLIC_DOMAIN` in your `blogging-cms-prod-env` secret (see
  [DEPLOYMENT.md](DEPLOYMENT.md#2-blogging-cms-prod-env-secret-file)) set to
  the actual subdomain you'll route through the tunnel, e.g.
  `blog.yourdomain.com` — it must match the tunnel's ingress hostname
  exactly (below).

## Install & authenticate

```bash
ssh proxy-vm

# Debian/Ubuntu — add Cloudflare's apt repo and install
curl -fsSL https://pkg.cloudflare.com/cloudflare-main.gpg | sudo gpg --dearmor -o /usr/share/keyrings/cloudflare-main.gpg
echo "deb [signed-by=/usr/share/keyrings/cloudflare-main.gpg] https://pkg.cloudflare.com/cloudflared $(lsb_release -cs) main" | \
  sudo tee /etc/apt/sources.list.d/cloudflared.list
sudo apt update && sudo apt install cloudflared

cloudflared tunnel login
# Opens a browser link -- log into Cloudflare and pick the zone (yourdomain.com).
# Drops a cert.pem under ~/.cloudflared/ used for the next commands.
```

## Create the tunnel

```bash
cloudflared tunnel create blogging-cms
# Prints a Tunnel ID (UUID) and writes ~/.cloudflared/<tunnel-id>.json
# (the tunnel's credentials -- treat it like any other secret).
```

## Configure ingress

```bash
sudo mkdir -p /etc/cloudflared
sudo tee /etc/cloudflared/config.yml <<'EOF'
tunnel: <tunnel-id>
credentials-file: /root/.cloudflared/<tunnel-id>.json

ingress:
  - hostname: blog.yourdomain.com
    service: http://127.0.0.1:80
  # Required catch-all -- cloudflared refuses to start without one.
  - service: http_status:404
EOF
```

Replace `<tunnel-id>` in both places, and `blog.yourdomain.com` with your
actual `PUBLIC_DOMAIN`.

## Route DNS and run as a service

```bash
cloudflared tunnel route dns blogging-cms blog.yourdomain.com
# Creates a CNAME in Cloudflare DNS pointing blog.yourdomain.com at the tunnel.

sudo cloudflared service install
sudo systemctl enable --now cloudflared
systemctl status cloudflared   # should be active (running)
```

## Verify

```bash
curl -I https://blog.yourdomain.com          # from anywhere, not just proxy-vm
```

A `200`/`30x` here confirms the full path: Cloudflare → tunnel →
`cloudflared` → `127.0.0.1:80` → `edge` → `frontend`/`backend`. If you get a
`502` from Cloudflare, the tunnel itself is fine but the local origin isn't
answering — check on proxy-vm directly:

```bash
curl -I http://127.0.0.1:80    # does the edge container answer at all?
docker ps --filter name=blog_edge
```

## Firewall note

Since `cloudflared` never accepts inbound connections (it only dials out),
you can — and should — block inbound 80/443 on proxy-vm's firewall/router
entirely once this is working. Nothing legitimate needs to reach those
ports from the public internet directly anymore; only `127.0.0.1` does.

## If you ever want Harbor reachable remotely too

Not recommended for now (keep the registry LAN/SSH-only, per
[harbor-registry-setup.md](harbor-registry-setup.md)) — but if you later
want it, add a second `ingress` rule above the catch-all pointing a
separate hostname at `https://127.0.0.1:8443`, and route DNS for that
hostname the same way.
