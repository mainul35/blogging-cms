# Deploying Harbor on proxy-vm

A standalone walkthrough for setting up [Harbor](https://goharbor.io/) as
your private Docker image registry on `proxy-vm` — kept separate from
[DEPLOYMENT.md](DEPLOYMENT.md) since this is infrastructure you'll reuse for
other projects too, not something specific to blogging-cms-app. This
replaces the plain `registry:2` container that used to run on port 5000.

## What Harbor is, and why not just a plain registry

Docker's own `registry:2` image gives you push/pull and nothing else.
Harbor adds on top of that: a web UI, project-based access control, **robot
accounts** (scoped, revocable credentials for CI instead of a real user's
password — this is what Jenkins uses), vulnerability scanning, and image
replication. For a homelab/self-hosted setup it's the difference between "a
registry" and "a registry you can actually operate."

## Prerequisites

- Docker + Compose plugin on proxy-vm (already installed)
- Resources: Harbor's own documented minimum is 2 vCPU / 4GB RAM / 40GB
  disk **for Harbor alone**. Check actual headroom first:
  ```bash
  ssh proxy-vm
  nproc; free -h; df -h /
  ```
- **Root/sudo, if you have it** — Harbor's `install.sh` is designed to run
  as root, because its `prepare` step generates config files pre-owned for
  each internal container's specific user. If you don't have sudo on the
  box (this deployment didn't), see [Installing without root](#installing-without-root)
  below — it's still doable, just with two extra manual steps.

## Port planning

Harbor ships its own internal nginx proxy that serves both the web UI and
the registry API on one port. Since this is an internal-only registry
(Jenkins pushes to it via the same host's Docker daemon, never over the
public internet), there's no need for a real hostname or TLS cert at all —
`hostname: localhost` plus a plain HTTP port lets Docker's own built-in
"insecure registry" exception for `localhost`/`127.0.0.1` handle
authentication-free access automatically. Port **5000** replaces the old
plain registry.

## Install

```bash
ssh proxy-vm
mkdir -p ~/harbor-install && cd ~/harbor-install

# Grab the latest offline installer version from
# https://github.com/goharbor/harbor/releases and substitute it below.
curl -LO https://github.com/goharbor/harbor/releases/download/v2.15.2/harbor-offline-installer-v2.15.2.tgz
tar xzf harbor-offline-installer-v2.15.2.tgz
cd harbor
cp harbor.yml.tmpl harbor.yml
```

### Edit `harbor.yml`

```yaml
# DO NOT use localhost/127.0.0.1 if you want this reachable from other
# machines -- we deliberately do here, since only this host's own Docker
# daemon (Jenkins, via the mounted socket) ever needs to reach it.
hostname: localhost

http:
  port: 5000

# No https: block at all -- delete it entirely (don't just comment it out;
# Harbor's prepare step treats a present-but-disabled https key differently
# than an absent one).

harbor_admin_password: <set a real one -- see the seeding gotcha below>

data_volume: /data   # default; fine as-is if / has room (see Prerequisites)
```

### Run the installer

```bash
sudo ./install.sh
# Add --with-trivy for vulnerability scanning if you have the RAM for it.
# Skip it for a lean first install; you can re-run install.sh with it later.
```

This generates its own `docker-compose.yml` under `~/harbor-install/harbor`
and starts everything. Give it a minute, then check:

```bash
docker ps --filter name=harbor   # should show 8 containers, all healthy
curl -I http://localhost:5000/   # 200 OK once nginx is up
```

## Installing without root

If you don't have sudo, `install.sh`'s Step 4 (`prepare`) still runs fine —
it's containerized — but leaves the generated files under
`common/config/` owned by whatever UID that container used internally
(often `10000` or similar), which your own user can't read, and Step 5
(`docker compose up`) then fails with something like
`open .../common/config/jobservice/env: permission denied`.

Since Docker group membership is already root-equivalent for anything you
can reach via a bind mount, fix ownership with a throwaway container
instead of `sudo chown`:

```bash
docker run --rm -v ~/harbor-install/harbor:/fix alpine chown -R $(id -u):$(id -g) /fix
```

That alone isn't quite enough, though — it fixes **ownership** (so *your*
user can read/write), but the files end up at mode `640` (owner+group
only). Each Harbor container reads its config as a *different* internal
user (`harbor`, `10000`, etc.) that isn't your user, so several containers
(`registry`, `core`, ...) will still crash-loop on the same permission
error. The real fix is making the configs world-readable, not chowning them
to match every internal UID individually:

```bash
chmod -R o+rX ~/harbor-install/harbor/common
docker compose up -d   # from ~/harbor-install/harbor
```

## A harbor-log / rsyslog compatibility bug (as of Harbor v2.15.2)

Worth knowing about even if you do have root: the `harbor-log` container
may crash-loop with:

```
rsyslogd: there are no active actions configured. Inputs would run, but no output whatsoever were created.
rsyslogd: run failed with error -2103
```

This reproduces with Harbor's own **unmodified** `rsyslog_docker.conf`,
purely because the bundled image ships a newer `rsyslogd` than the
entrypoint's privilege model expects: the entrypoint drops from root to an
unprivileged `syslog` user (`sudo -u \#10000 rsyslogd -n`) before starting
rsyslog, and this specific rsyslog version silently refuses to register the
log-file output action when it can't guarantee file-ownership semantics
under a dropped-privilege process — reproducible even with the stock
config, running as that unprivileged user, regardless of any customization.
It works fine as root.

Fix: override the container's command to skip the privilege drop (this
container's only job is aggregating internal Harbor logs, not accepting
untrusted input, so running its rsyslogd as root is a reasonable trade):

```bash
# In ~/harbor-install/harbor/docker-compose.yml, under services.log, replace
# the image's own entrypoint with:
    command: ["sh", "-c", "chown -R 10000:10000 /var/log/docker && crond && rm -f /var/run/rsyslogd.pid && rsyslogd -n"]
```

Then `docker compose up -d --force-recreate` (recreate everything, not just
`log`, since the other containers' syslog logging driver needs `harbor-log`
reachable *before* they can even start).

## First login & project setup

1. Browse to `http://<proxy-vm-ip>:5000` (or `http://localhost:5000` from
   the VM itself) and log in as `admin` / the password you set.
   - **If login fails with 401** even though the password matches
     `harbor_admin_password` in `harbor.yml`: `harbor_admin_password` only
     seeds the database on its *very first* successful initialization. If
     `harbor-core`/`harbor-db` crash-looped a few times before you got
     things working (e.g. while fixing the issues above), the DB may have
     been partially seeded with a mismatched value. Since there's no real
     data yet at that point, the clean fix is wiping the DB and reseeding:
     stop `db`, clear its data directory (bind-mounted at `/data/database`
     per `data_volume` above), and restart it — same throwaway-container
     approach as the ownership fix, since that directory is root-owned too.
2. **Projects → New Project** → name it `blogging-cms`, visibility
   **Private**. (Or via API: `curl -u admin:<password> -X POST http://localhost:5000/api/v2.0/projects -H 'Content-Type: application/json' -d '{"project_name":"blogging-cms","public":false}'`)
3. Inside that project: **Robot Accounts → New Robot Account** → name it
   e.g. `jenkins-blogging-cms`, scope it to **push + pull** on this project
   only. Harbor shows the generated secret **once** — copy it immediately.
   The full robot name Harbor generates (`robot$blogging-cms+jenkins-blogging-cms`)
   plus that secret is exactly what goes into the `harbor-robot-blogging-cms`
   Jenkins credential in
   [DEPLOYMENT.md](DEPLOYMENT.md#1-harbor-robot-cms-blogging-cms-username-with-password).

## Verify

```bash
docker login localhost:5000 -u 'robot$blogging-cms+jenkins-blogging-cms'
docker pull hello-world
docker tag hello-world localhost:5000/blogging-cms/hello-world:test
docker push localhost:5000/blogging-cms/hello-world:test
```

If that push succeeds, Jenkins will be able to do exactly the same thing —
it runs `docker` commands against this same host daemon via its mounted
socket, so there's nothing extra to configure inside the Jenkins container.

## Maintenance

- **Data lives at** `data_volume` in `harbor.yml` (`/data` by default) —
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
