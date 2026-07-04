# Phase 03 User Setup: Google OAuth 2 Login

## Required Environment

Set accepted Google OAuth client IDs before using `POST /auth/google` outside tests:

```properties
GOOGLE_OAUTH_CLIENT_IDS=web-client-id.apps.googleusercontent.com,mobile-client-id.apps.googleusercontent.com
```

The application maps this to:

```properties
google.oauth.client-ids=${GOOGLE_OAUTH_CLIENT_IDS:}
```

## Google Console Setup

- Create OAuth 2.0 client IDs in Google Cloud Console for the frontend/mobile clients that will obtain ID tokens.
- Add the correct local/staging/production origins in Google Console for web clients.
- Do not put client secrets in this backend for the ID-token exchange flow.

## Smoke Test

- Obtain a Google ID token from the client app.
- Call `POST /auth/google` with `{ "idToken": "..." }`.
- Expect the backend token response: `accessToken`, `refreshToken`, `tokenType`, `accessExpiresIn`, `refreshExpiresIn`.
