# Deploying Harbor on proxy-vm

A standalone walkthrough for setting up [Harbor](https://goharbor.io/) as
your private Docker image registry on `proxy-vm` — kept separate from
[DEPLOYMENT.md](DEPLOYMENT.md) since this is infrastructure you'll reuse for
other projects too, not something specific to blogging-cms-app.

## What Harbor is, and why not just a plain registry

Docker's own `registry:2` image gives you push/pull and nothing else.
Harbor adds on top of that: a web UI, project-based access control, **robot
accounts** (scoped, revocable credentials for CI instead of a real user's
password — this is what Jenkins will use), vulnerability scanning, and
image replication. For a homelab/self-hosted setup it's the difference
between "a registry" and "a registry you can actually operate."

## Prerequisites

- Docker + Compose plugin on proxy-vm (already installed)
- `openssl` (for the self-signed cert — already on any Linux box)
- Resources: Harbor's own documented minimum is 2 vCPU / 4GB RAM / 40GB
  disk **for Harbor alone**. Since proxy-vm already runs Jenkins and will
  also run the blogging-cms-app stack (postgres, redis, backend, frontend,
  edge), check actual headroom first:
  ```bash
  ssh proxy-vm
  nproc; free -h; df -h /
  ```
  If it's tight, Harbor's `install.sh` supports a smaller footprint by
  skipping optional components (Trivy scanner, Notary, ChartMuseum) —
  covered below.

## Port planning (important — avoids clashing with the app)

Harbor ships its own internal nginx proxy that wants ports 80/443 by
default. The blogging-cms-app stack's `edge` reverse proxy
([docker-compose.prod.yml](../../docker-compose.prod.yml)) already claims
80/443 for the public site. Give Harbor different ports up front —
`8080`/`8443` are used throughout this guide.

## Install

```bash
ssh proxy-vm
mkdir -p ~/harbor-install && cd ~/harbor-install

# Grab the latest offline installer version number from
# https://github.com/goharbor/harbor/releases and substitute it below.
curl -LO https://github.com/goharbor/harbor/releases/download/v2.11.0/harbor-offline-installer-v2.11.0.tgz
tar xzf harbor-offline-installer-v2.11.0.tgz
cd harbor
cp harbor.yml.tmpl harbor.yml
```

### Self-signed cert (fine for an internal-only registry)

```bash
mkdir -p certs
openssl req -x509 -newkey rsa:4096 -nodes -days 3650 \
  -keyout certs/harbor.key -out certs/harbor.crt \
  -subj "/CN=harbor.proxy-vm.local" \
  -addext "subjectAltName=DNS:harbor.proxy-vm.local"
```

If you don't have a real DNS entry for `harbor.proxy-vm.local` yet, add one
on any machine that needs to reach it (including proxy-vm itself, if
Jenkins resolves the hostname rather than using `localhost`):

```bash
echo "127.0.0.1 harbor.proxy-vm.local" | sudo tee -a /etc/hosts   # on proxy-vm
# and on your local PC, pointed at proxy-vm's actual LAN/Tailscale IP instead of 127.0.0.1
```

### Edit `harbor.yml`

```yaml
hostname: harbor.proxy-vm.local

http:
  port: 8080

https:
  port: 8443
  certificate: /home/<you>/harbor-install/harbor/certs/harbor.crt
  private_key: /home/<you>/harbor-install/harbor/certs/harbor.key

harbor_admin_password: <set-a-real-one-here>

data_volume: /data/harbor   # make sure this path has real disk behind it
```

### Run the installer

```bash
sudo ./install.sh
# Add --with-trivy if you want vulnerability scanning and have the RAM for it.
# Skip it for a lean first install; you can re-run install.sh with it later.
```

This generates its own `docker-compose.yml` under `~/harbor-install/harbor`
and starts everything. Give it a minute, then check:

```bash
docker ps --filter name=harbor   # should show ~10 containers, all healthy
```

## First login & project setup

1. Browse to `https://harbor.proxy-vm.local:8443` (accept the self-signed
   cert warning) and log in as `admin` / the password you set.
2. **Projects → New Project** → name it `blogging-cms`, visibility
   **Private**.
3. Inside that project: **Robot Accounts → New Robot Account** → name it
   e.g. `jenkins-blogging-cms`, scope it to **push + pull** on this project
   only. Harbor shows the generated secret **once** — copy it immediately.
   This account name + secret is exactly what goes into the
   `harbor-robot-blogging-cms` Jenkins credential in
   [DEPLOYMENT.md](DEPLOYMENT.md#1-harbor-robot-cms-blogging-cms-username-with-password).

## Trusting the self-signed cert for `docker login`/`push`/`pull`

Every Docker daemon that needs to talk to this registry — including
proxy-vm's own daemon, since Jenkins runs as a container using proxy-vm's
docker socket directly (no separate daemon to configure) — needs to trust
the cert:

```bash
ssh proxy-vm
sudo mkdir -p /etc/docker/certs.d/harbor.proxy-vm.local:8443
sudo cp ~/harbor-install/harbor/certs/harbor.crt \
  /etc/docker/certs.d/harbor.proxy-vm.local:8443/ca.crt
sudo systemctl restart docker
```

Because Jenkins's pipeline runs `docker` commands against this same host
daemon (via the mounted socket), this one host-level trust setup is all
that's needed — nothing extra to configure inside the Jenkins container
itself.

### Verify

```bash
docker login harbor.proxy-vm.local:8443 -u jenkins-blogging-cms
docker pull hello-world
docker tag hello-world harbor.proxy-vm.local:8443/blogging-cms/hello-world:test
docker push harbor.proxy-vm.local:8443/blogging-cms/hello-world:test
```

If that push succeeds, Jenkins will be able to do exactly the same thing.

## Maintenance

- **Data lives at** `data_volume` in `harbor.yml` (`/data/harbor` above) —
  back this up like any other stateful service; it holds every pushed image
  layer plus Harbor's own Postgres database (project/user/robot metadata).
- **Upgrading**: download the new offline installer, stop the stack
  (`docker compose down` inside `~/harbor-install/harbor`), follow Harbor's
  official [upgrade guide](https://goharbor.io/docs/latest/administration/upgrade/) —
  it's a real migration step, not just swapping images.
- **Cleaning old images**: Harbor's UI has a **Tag Retention** policy per
  project (Project → Policies) — worth setting up once you have a build
  history, so old `IMAGE_TAG` SHAs from the Jenkins pipeline don't
  accumulate forever.
