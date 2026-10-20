# Optional cloud intent parsing

Local Session Director is the default and needs no account, key or network. Cloud mode converts only the text entered in its dialog into a bounded listening intent. It receives no library, audio, lyrics, history, private-session state or Taste Passport. It has no tools. The listener reviews the resulting Compass settings and explicitly chooses whether to build or play a route.

## Reviewed models

- Anthropic `claude-haiku-4-5-20251001`, shown as a legacy model. Standard global input/output: USD 1 / 5 per million tokens; 200,000-token context. [Model and pricing](https://platform.claude.com/docs/en/models/haiku-4-5/overview), [Messages API](https://platform.claude.com/docs/en/api/messages/create).
- OpenAI `gpt-5.4-mini-2026-03-17`. Standard global input/output: USD 0.75 / 4.50 per million tokens; 400,000-token context. Responses API, `store:false`, default service tier, reasoning effort `none`, JSON-object output and 256-token output limit. [Model and pricing](https://developers.openai.com/api/docs/models/gpt-5.4-mini), [text generation](https://developers.openai.com/api/docs/guides/text), [Responses reference](https://developers.openai.com/api/reference/resources/responses/methods/create).

No arbitrary model names, automatic substitutions or provider failover. A new model needs its own capability, price and behavior review. Models may be unavailable to a particular account or retired. The existing Anthropic model is retained with a legacy label. A provider key grants no music catalog/playback rights.

Rates were reviewed on 20 October 2026 and expire for dispatch after 19 December 2026. An app update must review and renew them. A date before the review also blocks dispatch. URLs, model IDs, prices, context bounds and expiry are centralized in `CloudPolicy.kt`. Mismatched models or anomalous usage pause requests. Fixtures prove local handling; no paid live request was made during implementation.

## Keys, consent and data

Settings → Optional music agent → Manage cloud options manages provider keys separately. Saving checks printable format and encrypts locally without sending a validation request. The first consented parse verifies live access. Password input is masked, excluded from activity saved state and cleared after saving. Keys use Android Keystore AES-GCM, with provider identity as authenticated data. Existing v0.2 Anthropic ciphertext migrates on use. Relabeling ciphertext for another provider fails authentication.

Only the selected key is sent, in its provider's authentication header, to an enum-owned HTTPS endpoint. Redirects are disabled. No shared keys, custom URLs, prompt logging, error-body forwarding, automatic retries or tools are supported. Successful responses are limited to 64 KiB, prompts to 2,000 characters, output to 256 tokens. Users choose a 15, 30 or 60 second timeout. Cancel disconnects transport. Key changes and data deletion cancel and join the active parse first.

Cloud mode previews the host, model, chosen prompt, reservation and usage before consent. Editing prompt/model resets consent. Canceling prevents a late result changing Compass. Incomplete, refused, malformed, out-of-range, extra-field and trailing-text intents are rejected; successfully reported tokens still count. Provider output is data and cannot execute anything.

Deleting locally does not revoke a key; the UI links to provider key management. `store:false` requests no retrievable OpenAI response storage; it does not promise zero provider retention. Provider policies still apply.

## Spending policy

The SQLite ledger is shared across providers. Defaults: USD 0.50 per UTC day, USD 5 per UTC calendar month and 10 requests per UTC day. Setting either monetary ceiling to zero stops dispatch. Key/provider/model/ceiling changes preserve usage. Lowering a ceiling below counted usage blocks further requests.

Before dispatch, Songlane reserves the **whole documented input-window cost plus 256 output tokens**, using integer nano-USD. This avoids assuming an exact tokenizer or guessed chars-per-token ratio. Reservations: USD 0.201280 for Haiku, USD 0.301152 for GPT-5.4 mini. A short prompt normally uses far less, but both remaining ceilings must cover that reservation.

Admission, reservation and call count use one write transaction; concurrent parser instances cannot overbook headroom. Reservations survive process death. A validated, bounded usage receipt settles once to standard-rate token cost. Cache reads conservatively count as normal input. Missing/malformed receipts, failures, cancellation, timeouts and crashes retain the reservation. There is no automatic refund/retry. Unknown models, cache writes, nonstandard service tiers and impossible token counts pause cloud access. Date rollback cannot reset an allowance.

This is a verified **local admission ceiling under the reviewed rate card**, not a provider-invoice guarantee. Taxes, changed prices, other apps/devices, alternative billing arrangements, cleared/reinstalled app data and deliberate device tampering are outside it. Use provider billing controls too. v0.2 had no token receipts: its current-day call count migrates conservatively as unresolved Haiku reservations; earlier charges cannot be reconstructed.

Only request IDs, model/provider IDs, UTC windows, token counts, estimated amounts and settlement state are stored. Prompts, responses and keys never enter the ledger. Keys, limits and usage are excluded from Passport and Android backups. Corrupted accounting disables cloud access without silently resetting usage or breaking local listening. Explicit **Delete all Songlane data** cancels parsing, deletes keys and clears accounting, including a damaged database. There is no separate budget-reset button.

## Verification limits

Unit tests cover exact money arithmetic, date/model allowlists and ceiling/timeout inputs. Emulator tests cover concurrency, restart persistence, UTC rollover/rollback, daily limits, idempotent settlement, keys/AAD/migration, payload minimization, consent, cancellation, timeout, sanitization, invalid receipts/intents and corrupted accounting. Fake HTTPS connections exercise headers, redirects, response bounds and cancellation. A Compose test operates key management/zero ceilings/full deletion.

Fixture tests do not prove paid account access, language-model quality, invoice reconciliation or current provider retention. Real consenting prompt evaluation and periodic pricing/model-lifecycle review remain release work. Deterministic route validation stays independent of model output.
