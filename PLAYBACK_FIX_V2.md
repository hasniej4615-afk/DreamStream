# Video Playback Fix V2 - Resolution of 400 Bad Request

## ✅ STATUS: FIXED

The video extraction engine has been further refined to resolve "400 Bad Request" errors during playback.

### Issues Resolved

#### 1. ❌ URL Truncation in JS Blobs → ✅ FIXED
- **Problem**: The regex was excluding backslashes (`\`), causing URLs to be truncated when they contained escaped characters like `\&` or `\/` in JavaScript blocks.
- **Impact**: Truncated URLs missing critical signature parameters.
- **Solution**: Updated `videoPattern` to allow backslashes and implemented proper unescaping in `sanitizeUrl`.

#### 2. ❌ Signature Corruption via Over-Sanitization → ✅ FIXED
- **Problem**: `sanitizeUrl` was manually decoding percent-encoded characters like `%26` (&), `%2F` (/), etc.
- **Impact**: Cloudflare R2/S3 signed URLs require exact matching. Decoding these characters invalidates the AWS4 signature, leading to `400 Bad Request`.
- **Solution**: Removed aggressive percent-decoding while keeping necessary JS/HTML unescaping.

#### 3. ❌ Aggressive Filter Discarding Valid URLs → ✅ FIXED
- **Problem**: The filter was discarding any URL containing `.svg`.
- **Impact**: Some signed URLs include `filename="playback.svg"` in their query parameters as a decoy or metadata. These valid video URLs were being discarded, leaving only truncated versions (which didn't have the `.svg` part yet) as candidates.
- **Solution**: Refined the filter to only check the URL path for `.svg` extensions, allowing it in query parameters.

#### 4. ❌ Heuristic Improvement: Length-Based Sorting → ✅ FIXED
- **Problem**: If multiple versions of a URL (truncated vs. full) were found, the extractor might pick the wrong one.
- **Solution**: Candidates are now sorted by length descending. This ensures that the most complete URL (with all signatures and parameters) is always tried first.

### Files Modified
- `app/src/main/java/com/ikeike/javvy/util/VideoExtractor.kt`

### Testing Verified
- ✅ URLs with AWS4 signatures now extract in full.
- ✅ No more `400 Bad Request` errors in ExoPlayer for signed R2 streams.
- ✅ Correctly handles escaped ampersands in JavaScript.
- ✅ Filter no longer trips on signature metadata.
