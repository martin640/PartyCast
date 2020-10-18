# PartyCast

PartyCast is collaborative music player based on ExoPlayer2 and communication between devices is done through WebSockets. Music is queued in rounds where each lobby member can enqueue only single song.

App contains default ExoPlayer2/WS implementation enabling server to be run on Android device, however PartyCast core can be implemented on standalone embedded device with custom rules and behavior.

**If you are looking for standalone server for embedded device, see <a href="https://github.com/martin640/NodeCast">NodeCast</a>.**

## Concepts of PartyCast
- **Media is played only by host (not clients)**
- Clients can only control queue and view media status
- Information about media, queue, library and lobby is exchanged in real-time through websocket protocol
- PartyCast core can be implemented on any platform both as server and client

## Application releases
Latest app release is usually built by contributors under <a href="app/release">app/release</a> and version should be in format `[major version].[minor version].[patch]-git` (for example 1.0.2-git).
