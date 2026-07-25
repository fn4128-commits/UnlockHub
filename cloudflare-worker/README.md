# SafePing Cloudflare Worker

Cloudflare Workers + D1 backend for the no-cost MVP path.

## What it provides

- `GET /` temporary receiver web inbox
- `GET /health`
- `POST /api/unlock-events`
- `POST /api/inactivity-alerts`
- `GET /api/messages?guardianHandle=...`
- `POST /api/messages/{id}/read`

## Local test

```powershell
npm.cmd test
```

## Cloudflare setup

1. Create or sign in to a Cloudflare account.
2. Install dependencies:

```powershell
npm.cmd install
```

3. Log in to Cloudflare:

```powershell
npx.cmd wrangler login
```

4. Create the D1 database:

```powershell
npx.cmd wrangler d1 create safeping
```

5. Copy the returned `database_id` into `wrangler.toml`.

6. Apply the database schema:

```powershell
npx.cmd wrangler d1 execute safeping --remote --file=./schema.sql
```

7. Deploy:

```powershell
npx.cmd wrangler deploy
```

The deploy command prints a `workers.dev` URL. Use that URL as the backend URL in the Android app.

## MVP identity

The receiver is still a plain `guardianHandle`, such as `mom`. This is intentionally simple for the free MVP. Before real users, replace this with invite acceptance and verified accounts.

## Google login setup

Create a Google OAuth Web application in Google Cloud Console.

Add this authorized redirect URI:

```text
https://safeping.unlockhub.workers.dev/auth/google/callback
```

Then store credentials as Cloudflare Worker secrets:

```powershell
npx.cmd wrangler secret put GOOGLE_CLIENT_ID
npx.cmd wrangler secret put GOOGLE_CLIENT_SECRET
```

After both secrets are set, redeploy:

```powershell
npx.cmd wrangler deploy
```

The account page is:

```text
https://safeping.unlockhub.workers.dev/app
```
