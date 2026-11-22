# DraftMind

Floating hero-draft helper for Mobile Legends (Android).

- Floating **DM** bubble over the game; tap it during hero pick.
- Add enemy picks, your team's picks and bans; it scores every free hero on
  counters, synergy, missing lanes, frontline/CC and damage balance.
- Hero/counter data lives in `app/src/main/assets/heroes.json` (starter set; edit freely).

Every push to `main` builds the APK on GitHub Actions and publishes it under
**Releases → latest** as `DraftMind.apk`.

Roadmap: stage 2 auto-detects picks from the draft screen (screen capture + portrait matching).
