# package-lock.json — known gap and how to close it

`backend/package-lock.json` is intentionally not committed yet.

A real lockfile requires resolving the actual dependency graph against
the npm registry and recording real integrity (sha512) hashes. This
repository was authored in a sandbox with no registry access
(`npm install` returns `403 host_not_allowed` here) — hand-writing a
lockfile with fabricated integrity hashes would produce a file that
*looks* legitimate but would either fail `npm ci`'s integrity check or,
worse, silently pass while not actually pinning what it claims to. That's
a worse outcome than shipping without one.

## This is already handled in CI

`.github/workflows/backend-ci.yml` runs `npm install` (which doesn't
require a pre-existing lockfile) and uploads the resulting
`package-lock.json` as a build artifact on every run.

## To generate the real lockfile once and commit it

Anywhere with real npm registry access (your Termux environment has
this):

```bash
cd backend
npm install
git add package-lock.json
git commit -m "Add resolved package-lock.json"
```

After that, flip the CI step back to `npm ci` for faster, fully
reproducible installs (it's currently `npm install` specifically so CI
doesn't fail on a missing lockfile before you've generated one).
