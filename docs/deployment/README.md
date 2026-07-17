# Deployment Documentation

blogging-cms-app ships to `proxy-vm` as Docker containers, built and
deployed by a Jenkins pipeline. The guide is split into independent pieces
so you can jump straight to whichever one you actually need, rather than
re-reading the whole thing every time:

| Doc | What it's for |
|---|---|
| [DEPLOYMENT.md](DEPLOYMENT.md) | The main guide — architecture, prerequisites, required secrets, Jenkins job setup, rollback, day-2 ops. Start here for the full picture or a first deploy. |
| [harbor-registry-setup.md](harbor-registry-setup.md) | Standalone: deploying Harbor (private Docker image registry) on proxy-vm. General-purpose infra, reusable beyond this project. |
| [cloudflare-tunnel-setup.md](cloudflare-tunnel-setup.md) | Standalone: publishing the site through Cloudflare Tunnel — no inbound ports opened on proxy-vm at all. |

## First-time setup order

Each piece above is self-contained, but a first deploy from nothing goes in
this order:

1. **Fix Jenkins** — [DEPLOYMENT.md § Jenkins container troubleshooting](DEPLOYMENT.md#jenkins-container-troubleshooting) (it was reported unhealthy; nothing else here can run until it's up).
2. **Deploy Harbor** — [harbor-registry-setup.md](harbor-registry-setup.md). Needed before the pipeline can push images anywhere.
3. **Set up Cloudflare Tunnel** — [cloudflare-tunnel-setup.md](cloudflare-tunnel-setup.md). Needed to know your real `PUBLIC_DOMAIN` before building the frontend image (it's baked in at build time).
4. **Configure Jenkins credentials + job** — [DEPLOYMENT.md § Required secrets](DEPLOYMENT.md#required-secrets--environment-variables) and [§ Jenkins job setup](DEPLOYMENT.md#jenkins-job-setup-one-time).
5. **Run the pipeline** — first `Build Now` does checkout, dependency health-check, image build, Harbor push, deploy, and smoke test in one go.

After that, redeploying (a new feature, a fix, a config change) is just
re-running the same Jenkins job — there's no separate "release" step.
