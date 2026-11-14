# Chromecast test build — 0.18.11-community

Adds a standard Google Cast device picker below the video player, Play on TV, and Play here. Choosing a receiver sends the currently playing free video from its current position; Play on TV sends another selected video to an already-connected receiver. Use the Cast icon for receiver controls and Stop Casting. Play here pauses the remote video and resumes the same local video at the TV position while this video screen remains open.

Only HTTPS public player.odycdn.com streams are sent to the receiver. No local files, loopback endpoints, wallet data, or account credentials are sent. Purchased/local-only media is unsupported. Local playback pauses only after the receiver accepts a load. Receiver/load errors display a message. Devices without Google Play services retain ordinary playback.

Phone and Chromecast must share Wi-Fi. TV playback depends on receiver codec support and the public media host allowing receiver access. The existing automatic-next-video feature remains phone-only; this change does not implement a TV autoplay queue. Navigating away does not stop the receiver. Reopen the Cast picker to stop it. Play here position handoff is scoped to the current video screen.

Validation: ARM64 debug build and all 24 JVM tests passed, including two Cast URL/type policy tests; Android lint passed with the existing translation baseline and legacy warnings. Physical Chromecast playback still needs user confirmation. This is a debug-signed owner test build, not a published release.

Manual acceptance: discover receiver; cast a free video; confirm picture and sound on TV and phone paused; pause/seek/stop with Cast controls; Play here returns to TV position; cast a second video; disconnect/reconnect; rotate/navigate; verify ordinary phone playback and wallet remain usable.

Implementation follows https://developers.google.com/cast/docs/android_sender/integrate using the existing Cast dependency and default receiver configuration.
