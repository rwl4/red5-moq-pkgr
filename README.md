# MoQ Packaging Library

A pure Java 21 library for serialization and deserialization of media content for MoQ (Media over QUIC) Transport, supporting multiple formats:
- **MSF** (MOQT Streaming Format) catalog and timeline support according to [draft-ietf-moq-msf](https://datatracker.ietf.org/doc/draft-ietf-moq-msf/)
- **CMAF** (Common Media Application Format) packaging according to the [MoQ CMAF Packaging specification](https://github.com/wilaw/moq-cmaf-packaging/blob/main/draft-wilaw-moq-cmafpackaging.md)
- **LOC** (Low Overhead Media Container) format according to [draft-ietf-moq-loc](https://datatracker.ietf.org/doc/html/draft-mzanaty-moq-loc-05)
- **MoqMI** (MoQ Media Interop) format according to [draft-cenzano-moq-media-interop](https://datatracker.ietf.org/doc/html/draft-cenzano-moq-media-interop-03)

## Features

### CMAF Support

- **Full ISO BMFF Support**: Parse and generate ISO Base Media File Format boxes including initialization segments
- **CMAF Fragment Handling**: Complete support for styp, moof, and mdat boxes
- **High Performance**: Pure Java implementation capable of handling 4K/8K video content
- **Serialization/Deserialization**: Convert between CMAF fragments and byte arrays
- **Media File I/O**: Read and write CMAF files for debugging
- **Multiple Media Types**: Support for audio, video, metadata, and other content types
- **Codec Support**: H.264/AVC, H.265/HEVC, VP9, AV1, AAC, Opus, Dolby Digital/Plus

### LOC Support

- **Low Overhead Format**: Minimal encapsulation overhead optimized for WebCodecs
- **Header Extensions**: Support for capture timestamp, video frame marking, audio level, and video config
- **Temporal/Spatial Layers**: Full support for SVC and simulcast video encoding
- **End-to-End Encryption Ready**: Metadata designed for relay operation with encrypted payloads
- **WebCodecs Compatible**: Direct mapping to EncodedAudioChunk and EncodedVideoChunk

### MoqMI Support

- **Media Interoperability**: Standard header extensions for cross-implementation compatibility
- **Multiple Codecs**: H.264/AVC, Opus, AAC-LC, UTF-8 text
- **Dynamic Updates**: Support for changing encoding parameters mid-stream
- **Timestamp Management**: Numerator/timebase pairs for precise timing
- **Track Naming**: Standard video0, audio0 naming convention
- **Group Mapping**: IDR-based grouping for video, per-object for audio

### MSF Support

- **Catalog Builder**: Fluent builder API for creating MSF catalogs and tracks
- **Live/VOD Content**: Support for both live streaming (targetLatency) and VOD (trackDuration)
- **Media Timeline**: JSON array format with GZIP compression for seeking in live streams
- **Event Timeline**: Application metadata with wallclock, location, or media PTS indexing
- **Track Types**: Video, audio, caption, subtitle, sign language, media/event timeline
- **Simulcast**: Alt group support for adaptive bitrate switching
- **SVC Layers**: Temporal and spatial layer dependencies
- **Delta Updates**: Add, remove, or clone tracks without full catalog resend
- **Validation**: MSF-specific rules for latency consistency and track dependencies

### Common

- **MoQ Transport Ready**: All formats designed for use with MoQ Transport protocol
- **Comprehensive Testing**: Full test coverage for CMAF, LOC, MoqMI, and MSF implementations

## Requirements

- Java 21 or later
- Maven 3.8+

## Building

### Compile and Package

Tests are included in the build process, to skip tests use `-DskipTests` flag.

```bash
mvn clean package
```

All 336 tests should pass with 100% success rate.

## Quick Start

### Creating a CMAF Fragment

```java
import org.red5.io.moq.cmaf.model.CmafFragment;
import org.red5.io.moq.cmaf.serialize.CmafSerializer;

// Create media data
byte[] mediaData = new byte[1024];
// ... fill with actual media data

// Create a CMAF fragment
CmafFragment fragment = CmafSerializer.createMinimalFragment(1, mediaData);
fragment.setGroupId(1);
fragment.setObjectId(1);
fragment.setMediaType(CmafFragment.MediaType.VIDEO);

// Serialize to bytes (ready for MoQ Transport)
CmafSerializer serializer = new CmafSerializer();
byte[] serialized = serializer.serialize(fragment);
```

### Deserializing a CMAF Fragment

```java
import org.red5.io.moq.cmaf.deserialize.CmafDeserializer;

// Receive bytes from MoQ Transport
byte[] receivedData = ...;

// Deserialize
CmafDeserializer deserializer = new CmafDeserializer();
CmafFragment fragment = deserializer.deserialize(receivedData);

// Access fragment data
long sequenceNumber = fragment.getSequenceNumber();
byte[] mediaData = fragment.getMdat().getData();
```

### File Operations (for Debugging)

```java
import org.red5.io.moq.cmaf.util.MediaFileWriter;
import org.red5.io.moq.cmaf.util.MediaFileReader;
import java.nio.file.Paths;

// Write fragment to file
MediaFileWriter writer = new MediaFileWriter();
writer.writeFragment(fragment, Paths.get("output.cmaf"));

// Read fragment from file
MediaFileReader reader = new MediaFileReader();
CmafFragment readFragment = reader.readFragment(Paths.get("output.cmaf"));

// Analyze file
reader.analyzeFile(Paths.get("output.cmaf"));
```

### Creating a LOC Object (Audio)

```java
import org.red5.io.moq.loc.model.LocObject;
import org.red5.io.moq.loc.serialize.LocSerializer;

// Create audio data (e.g., 10ms Opus frame at 48kHz)
byte[] audioData = new byte[480];
long timestamp = System.currentTimeMillis() * 1000; // microseconds

// Create LOC object with metadata
LocObject obj = LocSerializer.createMinimalAudioObject(audioData, timestamp);
obj.setGroupId(100);
obj.setObjectId(1);
obj.setAudioLevel(true, 45); // voice activity, level 45

// Serialize for MoQ Transport
LocSerializer serializer = new LocSerializer();
byte[] headerExtensions = serializer.serializeHeaderExtensions(obj);
byte[] payload = serializer.getPayload(obj);
```

### Creating a LOC Object (Video)

```java
import org.red5.io.moq.loc.model.LocObject;
import org.red5.io.moq.loc.serialize.LocSerializer;

// Create video data (key frame)
byte[] videoData = new byte[8192];
long timestamp = System.currentTimeMillis() * 1000;

// Create LOC object for independent frame
LocObject obj = LocSerializer.createMinimalVideoObject(videoData, timestamp, true);
obj.setGroupId(50);
obj.setObjectId(0); // First object in group (key frame)

// Add video config (codec extradata)
byte[] configData = new byte[]{0x01, 0x42, 0xC0, 0x1E}; // H.264 avcC
obj.setVideoConfig(configData);

// Serialize
LocSerializer serializer = new LocSerializer();
byte[] serialized = serializer.serialize(obj);
```

## fMP4 (Init Segment + Fragments) for HTTP Chunked Streaming

This library includes lightweight builders for CMAF-style fMP4 output. The init segment is sent once, then each
fragment is appended. This is suitable for browser streaming using Mediabunny's `ReadableStreamSource`.

### Build an init segment

```java
import org.red5.io.moq.cmaf.util.Fmp4InitSegmentBuilder;

byte[] avcC = /* full avcC box bytes (size + type + payload) */;
byte[] esds = /* full esds box bytes (size + type + payload) */;

byte[] initSegment = new Fmp4InitSegmentBuilder()
    .addVideoTrack(new Fmp4InitSegmentBuilder.VideoTrackConfig(
        1, 90000, "avc1", avcC, 1920, 1080))
    .addAudioTrack(new Fmp4InitSegmentBuilder.AudioTrackConfig(
        2, 48000, "mp4a", esds, 2, 48000, 16))
    .build();
```

### Build a fragment

```java
import org.red5.io.moq.cmaf.model.SampleFlags;
import org.red5.io.moq.cmaf.util.Fmp4FragmentBuilder;

Fmp4FragmentBuilder builder = new Fmp4FragmentBuilder();

Fmp4FragmentBuilder.FragmentConfig config = new Fmp4FragmentBuilder.FragmentConfig()
    .setSequenceNumber(1)
    .setTrackId(1)
    .setBaseDecodeTime(0)
    .setMediaData(mediaBytes);

config.addSample(new Fmp4FragmentBuilder.SampleData(
    sampleDuration, sampleSize, SampleFlags.createSyncSampleFlags()));

byte[] fragmentBytes = builder.buildFragment(config).serialize();
```

### Browser playback with Mediabunny

```ts
import { Input, ReadableStreamSource, ALL_FORMATS } from 'mediabunny';

const { readable, writable } = new TransformStream<Uint8Array, Uint8Array>();
const source = new ReadableStreamSource(readable);
const input = new Input({ source, formats: ALL_FORMATS });

const writer = writable.getWriter();
writer.write(initSegmentBytes);
writer.write(fragmentBytes);
// Continue writing fragments...
```

Note: `codecConfig` in the init segment must include the full codec box (`avcC`, `hvcC`, `av1C`, `esds`, `Opus`)
with size and type bytes, not just the raw config payload.

### Local HTTP chunked demo endpoint

```bash
java -cp target/red5-moq-pkgr-*.jar org.red5.io.moq.cmaf.util.HttpChunkedFmp4Server
```

This demo server streams placeholder data at `http://localhost:8080/stream`. Replace the codec configs and
media payloads in `HttpChunkedFmp4Server` with real stream data for actual playback.

### Deserializing a LOC Object

```java
import org.red5.io.moq.loc.deserialize.LocDeserializer;
import org.red5.io.moq.loc.model.LocObject;

// Receive from MoQ Transport (header extensions and payload separated)
byte[] headerExtensions = ...;
byte[] payload = ...;

// Deserialize
LocDeserializer deserializer = new LocDeserializer();
LocObject obj = deserializer.deserialize(
    headerExtensions,
    payload,
    LocObject.MediaType.VIDEO
);

// Access metadata
if (obj.isIndependentFrame()) {
    System.out.println("Key frame received");
}
long captureTime = obj.getCaptureTimestamp().getCaptureTimestampMicros();
```

### Creating a MoqMI Object (H.264 Video)

```java
import org.red5.io.moq.moqmi.model.MoqMIObject;
import org.red5.io.moq.moqmi.serialize.MoqMISerializer;

// Create H.264 video data (AVCC format)
byte[] h264Data = new byte[8192];
long seqId = 0;
long pts = 0;
long dts = 0;
long timebase = 30; // 30 fps

// Create MoqMI object with H.264 metadata
MoqMIObject obj = MoqMISerializer.createH264Object(h264Data, seqId, pts, dts, timebase);
obj.setGroupId(0);
obj.setObjectId(0);

// For IDR frames, add extradata (AVCDecoderConfigurationRecord)
byte[] extradata = new byte[]{0x01, 0x42, (byte) 0xC0, 0x1E};
MoqMIObject idrObj = MoqMISerializer.createH264ObjectWithExtradata(
    h264Data, seqId, pts, dts, timebase, extradata);

// Serialize for MoQ Transport
MoqMISerializer serializer = new MoqMISerializer();
byte[] headerExtensions = serializer.serializeHeaderExtensions(obj);
byte[] payload = serializer.getPayload(obj);
```

### Creating a MoqMI Object (Opus Audio)

```java
import org.red5.io.moq.moqmi.serialize.MoqMISerializer;

// Create Opus audio data
byte[] opusData = new byte[480];
long seqId = 5;
long pts = 150;
long timebase = 48000; // 48 kHz
long sampleFreq = 48000;
long numChannels = 2; // Stereo

// Create MoqMI object with Opus metadata
MoqMIObject obj = MoqMISerializer.createOpusObject(
    opusData, seqId, pts, timebase, sampleFreq, numChannels);
obj.setGroupId(5);
obj.setObjectId(0);

// Serialize
MoqMISerializer serializer = new MoqMISerializer();
byte[] serialized = serializer.serialize(obj);
```

### Deserializing a MoqMI Object

```java
import org.red5.io.moq.moqmi.deserialize.MoqMIDeserializer;
import org.red5.io.moq.moqmi.model.*;

// Receive from MoQ Transport (header extensions and payload separated)
byte[] headerExtensions = ...;
byte[] payload = ...;

// Deserialize
MoqMIDeserializer deserializer = new MoqMIDeserializer();
MoqMIObject obj = deserializer.deserialize(headerExtensions, payload);

// Access metadata
MoqMIObject.MediaType mediaType = obj.getMediaType();
if (mediaType == MoqMIObject.MediaType.VIDEO_H264_AVCC) {
    H264MetadataExtension metadata = obj.getHeaderExtension(H264MetadataExtension.class);
    long pts = metadata.getPtsTimestamp();
    long timebase = metadata.getTimebase();

    // Check for extradata (present on IDR frames)
    H264ExtradataExtension extradata = obj.getHeaderExtension(H264ExtradataExtension.class);
    if (extradata != null) {
        byte[] decoderConfig = extradata.getExtradata();
    }
}
```

## MSF (MOQT Streaming Format) Catalogs and Timelines

This library provides comprehensive support for MSF (MOQT Streaming Format) as defined in draft-ietf-moq-msf-00, which supersedes the WARP format. MSF catalogs are JSON payloads carried on the `catalog` track.

### MSF Live Video/Audio Catalog

```java
import org.red5.io.moq.msf.catalog.MsfCatalog;
import org.red5.io.moq.msf.catalog.MsfCatalogSerializer;
import org.red5.io.moq.msf.catalog.MsfTrack;

// Build a live streaming catalog with video and audio
MsfCatalog catalog = MsfCatalog.builder()
    .addTrack(MsfTrack.video("1080p-video")
        .namespace("conference.example.com/stream")
        .live()
        .targetLatency(2000)    // 2 second latency target
        .renderGroup(1)
        .altGroup(1)
        .codec("av01.0.08M.10.0.110.09")
        .resolution(1920, 1080)
        .framerate(30)
        .bitrate(4000000)
        .label("HD Video"))
    .addTrack(MsfTrack.audio("stereo-audio")
        .namespace("conference.example.com/stream")
        .live()
        .targetLatency(2000)
        .renderGroup(1)
        .codec("opus")
        .sampleRate(48000)
        .channelConfig("2")
        .bitrate(128000)
        .language("en")
        .label("English Audio"))
    .build();

// Serialize to JSON
MsfCatalogSerializer serializer = new MsfCatalogSerializer();
String json = serializer.toJson(catalog);
```

**Output JSON:**
```json
{
  "version": 1,
  "generatedAt": 1746104606044,
  "tracks": [
    {
      "name": "1080p-video",
      "namespace": "conference.example.com/stream",
      "packaging": "loc",
      "role": "video",
      "isLive": true,
      "targetLatency": 2000,
      "renderGroup": 1,
      "altGroup": 1,
      "codec": "av01.0.08M.10.0.110.09",
      "framerate": 30,
      "bitrate": 4000000,
      "width": 1920,
      "height": 1080,
      "label": "HD Video"
    },
    {
      "name": "stereo-audio",
      "namespace": "conference.example.com/stream",
      "packaging": "loc",
      "role": "audio",
      "isLive": true,
      "targetLatency": 2000,
      "renderGroup": 1,
      "codec": "opus",
      "samplerate": 48000,
      "channelConfig": "2",
      "bitrate": 128000,
      "lang": "en",
      "label": "English Audio"
    }
  ]
}
```

### MSF VOD (Video on Demand) Catalog

```java
// VOD catalogs omit targetLatency and include trackDuration
MsfCatalog vodCatalog = MsfCatalog.builder()
    .addTrack(MsfTrack.video("movie")
        .vod()
        .trackDuration(7200000L)  // 2 hours in milliseconds
        .codec("avc1.64001f")
        .resolution(1920, 1080)
        .framerate(24)
        .bitrate(8000000))
    .addTrack(MsfTrack.audio("movie-audio")
        .vod()
        .trackDuration(7200000L)
        .codec("mp4a.40.2")
        .sampleRate(48000)
        .channelConfig("5.1")
        .bitrate(384000)
        .language("en"))
    .build();
```

**Output JSON:**
```json
{
  "version": 1,
  "generatedAt": 1746104606044,
  "tracks": [
    {
      "name": "movie",
      "packaging": "loc",
      "role": "video",
      "isLive": false,
      "trackDuration": 7200000,
      "codec": "avc1.64001f",
      "framerate": 24,
      "bitrate": 8000000,
      "width": 1920,
      "height": 1080
    },
    {
      "name": "movie-audio",
      "packaging": "loc",
      "role": "audio",
      "isLive": false,
      "trackDuration": 7200000,
      "codec": "mp4a.40.2",
      "samplerate": 48000,
      "channelConfig": "5.1",
      "bitrate": 384000,
      "lang": "en"
    }
  ]
}
```

### MSF Media Timeline Track

Media timeline tracks allow clients to seek within live streams by mapping media PTS to MoQ group/object locations and wallclock times.

```java
import org.red5.io.moq.msf.timeline.MsfMediaTimeline;
import org.red5.io.moq.msf.timeline.MsfMediaTimelineRecord;

// Create a media timeline track in the catalog
MsfCatalog catalogWithTimeline = MsfCatalog.builder()
    .addTrack(MsfTrack.video("video").live().targetLatency(2000))
    .addTrack(MsfTrack.audio("audio").live().targetLatency(2000))
    .addTrack(MsfTrack.mediaTimeline("history")
        .live()
        .dependsOn("video")
        .dependsOn("audio"))
    .build();

// Create timeline records (JSON array format)
MsfMediaTimeline timeline = new MsfMediaTimeline();
List<MsfMediaTimelineRecord> records = List.of(
    new MsfMediaTimelineRecord(0, 0, 0, 1746104606044L),      // PTS 0ms, group 0, obj 0
    new MsfMediaTimelineRecord(1000, 1, 0, 1746104607044L),   // PTS 1000ms, group 1, obj 0
    new MsfMediaTimelineRecord(2000, 2, 0, 1746104608044L),   // PTS 2000ms, group 2, obj 0
    new MsfMediaTimelineRecord(3000, 3, 0, 1746104609044L)    // PTS 3000ms, group 3, obj 0
);

// Serialize to JSON
String timelineJson = timeline.toJson(records);

// Serialize with GZIP compression (recommended for large timelines)
byte[] compressed = timeline.toGzipJson(records);
```

**Timeline JSON format (array of arrays):**
```json
[[0,[0,0],1746104606044],[1000,[1,0],1746104607044],[2000,[2,0],1746104608044],[3000,[3,0],1746104609044]]
```

Each record: `[mediaPtsMillis, [groupId, objectId], wallclockMillis]`

### MSF Event Timeline Track

Event timeline tracks carry application-specific metadata events synchronized to media playback.

```java
import com.google.gson.JsonObject;
import org.red5.io.moq.msf.timeline.MsfEventTimeline;
import org.red5.io.moq.msf.timeline.MsfEventTimelineEntry;

// Create an event timeline track
MsfCatalog catalogWithEvents = MsfCatalog.builder()
    .addTrack(MsfTrack.video("video").live())
    .addTrack(MsfTrack.eventTimeline("scores", "com.sports/live-scores/v1")
        .live()
        .dependsOn("video"))
    .build();

// Create event entries indexed by wallclock time
JsonObject scoreUpdate = new JsonObject();
scoreUpdate.addProperty("home", 2);
scoreUpdate.addProperty("away", 1);
scoreUpdate.addProperty("period", "2nd");

MsfEventTimeline eventTimeline = new MsfEventTimeline();
List<MsfEventTimelineEntry> events = List.of(
    MsfEventTimelineEntry.withWallclock(1746104606044L, scoreUpdate),
    MsfEventTimelineEntry.withMediaPts(45000L, createGoalEvent()),  // Event at 45s media time
    MsfEventTimelineEntry.withLocation(10, 5, createReplayMarker()) // Event at group 10, object 5
);

String eventJson = eventTimeline.toJson(events);
```

**Event Timeline JSON format:**
```json
[
  {"t":1746104606044,"data":{"home":2,"away":1,"period":"2nd"}},
  {"m":45000,"data":{"event":"goal","player":"Smith","minute":45}},
  {"l":[10,5],"data":{"type":"replay","duration":15000}}
]
```

Index types:
- `t` - Wallclock timestamp (milliseconds since epoch)
- `m` - Media PTS (milliseconds)
- `l` - MoQ location as `[groupId, objectId]`

### MSF Simulcast (Alt Groups)

Simulcast tracks share the same `altGroup` and `targetLatency` for adaptive bitrate switching.

```java
MsfCatalog simulcast = MsfCatalog.builder()
    .addTrack(MsfTrack.video("hd")
        .live()
        .targetLatency(1500)
        .renderGroup(1)
        .altGroup(1)           // Same alt group
        .codec("av01")
        .resolution(1920, 1080)
        .bitrate(5000000))
    .addTrack(MsfTrack.video("sd")
        .live()
        .targetLatency(1500)   // Must match alt group members
        .renderGroup(1)
        .altGroup(1)           // Same alt group
        .codec("av01")
        .resolution(640, 480)
        .bitrate(500000))
    .addTrack(MsfTrack.audio("audio")
        .live()
        .targetLatency(1500)
        .renderGroup(1)
        .codec("opus"))
    .build();
```

### MSF SVC Layered Tracks

SVC (Scalable Video Coding) tracks use `temporalId`, `spatialId`, and `depends` to describe layer relationships.

```java
MsfCatalog svcCatalog = MsfCatalog.builder()
    // Base layer: 480p @ 15fps
    .addTrack(MsfTrack.video("480p15")
        .live()
        .renderGroup(1)
        .temporalId(0)
        .spatialId(0)
        .codec("av01.0.01M.10.0.110.09")
        .resolution(640, 480)
        .framerate(15))
    // Temporal enhancement: 480p @ 30fps (depends on base)
    .addTrack(MsfTrack.video("480p30")
        .live()
        .renderGroup(1)
        .temporalId(1)
        .spatialId(0)
        .codec("av01.0.04M.10.0.110.09")
        .resolution(640, 480)
        .framerate(30)
        .dependsOn("480p15"))
    // Spatial enhancement: 1080p @ 15fps (depends on base)
    .addTrack(MsfTrack.video("1080p15")
        .live()
        .renderGroup(1)
        .temporalId(0)
        .spatialId(1)
        .codec("av01.0.05M.10.0.110.09")
        .resolution(1920, 1080)
        .framerate(15)
        .dependsOn("480p15"))
    .addTrack(MsfTrack.audio("audio")
        .live()
        .renderGroup(1)
        .codec("opus"))
    .build();
```

### MSF Broadcast Termination

Signal that a live broadcast has ended with `isComplete=true` and empty tracks.

```java
// Create termination catalog
MsfCatalog termination = MsfCatalog.termination();

// Produces:
// {
//   "version": 1,
//   "generatedAt": 1746104606044,
//   "isComplete": true,
//   "tracks": []
// }
```

### MSF Delta Updates

Delta updates add, remove, or clone tracks without resending the full catalog.

```java
// Create a delta update to add a new track
MsfCatalog delta = new MsfCatalog();
delta.setDeltaUpdate(true);
delta.setGeneratedAt(System.currentTimeMillis());

WarpTrack newAudio = new WarpTrack();
newAudio.setName("commentary");
newAudio.setPackaging("loc");
newAudio.setRole("audio");
newAudio.setIsLive(true);
newAudio.setTargetLatency(2000L);
newAudio.setLang("es");
newAudio.setLabel("Spanish Commentary");

delta.setAddTracks(List.of(newAudio));

String deltaJson = serializer.toJson(delta);
```

**Delta Update JSON:**
```json
{
  "version": 1,
  "deltaUpdate": true,
  "generatedAt": 1746104606044,
  "addTracks": [
    {
      "name": "commentary",
      "packaging": "loc",
      "role": "audio",
      "isLive": true,
      "targetLatency": 2000,
      "lang": "es",
      "label": "Spanish Commentary"
    }
  ]
}
```

### MSF Caption and Subtitle Tracks

```java
MsfCatalog accessibleCatalog = MsfCatalog.builder()
    .addTrack(MsfTrack.video("video").live().targetLatency(2000))
    .addTrack(MsfTrack.audio("audio").live().targetLatency(2000))
    .addTrack(MsfTrack.caption("cc-en")
        .live()
        .language("en")
        .label("English Closed Captions"))
    .addTrack(MsfTrack.subtitle("sub-es")
        .live()
        .language("es")
        .label("Spanish Subtitles"))
    .build();
```

### MSF Constants and Latency Thresholds

```java
import org.red5.io.moq.msf.catalog.MsfConstants;

// MSF version
int version = MsfConstants.VERSION;  // 1

// Standard track name for catalog
String catalogTrack = MsfConstants.CATALOG_TRACK_NAME;  // "catalog"

// Latency thresholds (milliseconds)
long realtimeMax = MsfConstants.Latency.REALTIME_MAX_MS;       // 500ms
long interactiveMin = MsfConstants.Latency.INTERACTIVE_MIN_MS; // 500ms
long interactiveMax = MsfConstants.Latency.INTERACTIVE_MAX_MS; // 2500ms
long standardMin = MsfConstants.Latency.STANDARD_MIN_MS;       // 2500ms

// Generate initial group ID (current timestamp)
long groupId = MsfConstants.generateInitialGroupId();
```

## WARP & CARP Catalogs and Timelines (Legacy)

This library also includes support for the legacy WARP and CARP formats. WARP/CARP catalogs are JSON payloads carried on the `catalog` track, and WARP timeline tracks use CSV format.
When guidance differs, IETF drafts in `docs/` are authoritative over non-IETF drafts.

### WARP Catalog (JSON)

```java
import org.red5.io.moq.warp.catalog.WarpCatalog;
import org.red5.io.moq.warp.catalog.WarpCatalogSerializer;
import org.red5.io.moq.warp.catalog.WarpCatalogValidator;
import org.red5.io.moq.warp.catalog.WarpTrack;

WarpTrack video = new WarpTrack();
video.setName("video0");
video.setPackaging("loc");
video.setIsLive(true);
video.setMimeType("video/h264");

WarpCatalog catalog = new WarpCatalog();
catalog.setVersion(1);
catalog.setTracks(List.of(video));

WarpCatalogValidator.validateCatalog(catalog);
String json = new WarpCatalogSerializer().toJson(catalog);
```

### WARP Timeline (CSV)

```java
import org.red5.io.moq.warp.timeline.WarpTimeline;
import org.red5.io.moq.warp.timeline.WarpTimelineRecord;

WarpTimeline timeline = new WarpTimeline();
String csv = timeline.toCsv(List.of(
    new WarpTimelineRecord(1000, 1L, 0L, 1700000000000L, "start")
));
```

### CARP SAP Event Timeline (JSON)

```java
import org.red5.io.moq.carp.CarpCatalogValidator;
import org.red5.io.moq.carp.timeline.CarpSapTimeline;
import org.red5.io.moq.carp.timeline.CarpSapTimelineEntry;

WarpTrack sapTimeline = new WarpTrack();
sapTimeline.setName("sap-timeline");
sapTimeline.setPackaging("eventtimeline");
sapTimeline.setEventType(CarpCatalogValidator.SAP_EVENT_TYPE);
sapTimeline.setIsLive(true);

String json = new CarpSapTimeline().toJson(List.of(
    new CarpSapTimelineEntry(0, 0, 2, 0),
    new CarpSapTimelineEntry(0, 60, 3, 2100)
));
```

## Architecture

### Package Structure

```
org.red5.io.moq
├── cmaf/               # CMAF format support
│   ├── model/              # Data structures for ISO BMFF boxes
│   │   ├── Box.java
│   │   ├── StypBox.java
│   │   ├── MoofBox.java
│   │   ├── MdatBox.java
│   │   ├── MoovBox.java            # Initialization segment support
│   │   ├── InitializationSegment.java
│   │   ├── TrackMetadata.java      # Video/audio track metadata
│   │   ├── SampleFlags.java        # ISO BMFF sample flags
│   │   └── CmafFragment.java
│   ├── serialize/          # Serialization to bytes
│   │   └── CmafSerializer.java
│   ├── deserialize/        # Deserialization from bytes
│   │   └── CmafDeserializer.java
│   └── util/              # File I/O utilities
│       ├── MediaFileReader.java
│       └── MediaFileWriter.java
├── loc/                # LOC format support
│   ├── model/              # LOC data structures
│   │   ├── LocObject.java
│   │   ├── LocHeaderExtension.java
│   │   ├── CaptureTimestampExtension.java
│   │   ├── VideoFrameMarkingExtension.java
│   │   ├── AudioLevelExtension.java
│   │   └── VideoConfigExtension.java
│   ├── serialize/          # LOC serialization
│   │   └── LocSerializer.java
│   └── deserialize/        # LOC deserialization
│       └── LocDeserializer.java
├── moqmi/              # MoqMI format support
│   ├── model/              # MoqMI data structures
│   │   ├── MoqMIObject.java
│   │   ├── MoqMIHeaderExtension.java
│   │   ├── MediaTypeExtension.java
│   │   ├── H264MetadataExtension.java
│   │   ├── H264ExtradataExtension.java
│   │   ├── OpusDataExtension.java
│   │   ├── AacLcDataExtension.java
│   │   └── Utf8TextExtension.java
│   ├── serialize/          # MoqMI serialization
│   │   └── MoqMISerializer.java
│   └── deserialize/        # MoqMI deserialization
│       └── MoqMIDeserializer.java
└── msf/                # MSF (MOQT Streaming Format) support
    ├── catalog/            # Catalog classes
    │   ├── MsfCatalog.java         # Catalog with builder pattern
    │   ├── MsfTrack.java           # Track with builder pattern
    │   ├── MsfCatalogSerializer.java
    │   ├── MsfCatalogValidator.java
    │   ├── MsfConstants.java
    │   ├── TrackRole.java          # Enum: video, audio, caption, etc.
    │   └── PackagingType.java      # Enum: loc, mediatimeline, eventtimeline
    └── timeline/           # Timeline classes
        ├── MsfMediaTimeline.java       # JSON array format
        ├── MsfMediaTimelineRecord.java
        ├── MsfEventTimeline.java       # JSON object format
        └── MsfEventTimelineEntry.java
```

## Specification Compliance

### MoQ CMAF Packaging

This library implements [draft-wilaw-moq-cmafpackaging](https://github.com/wilaw/moq-cmaf-packaging/blob/main/draft-wilaw-moq-cmafpackaging.md):

- **Fragment-to-Group Mapping**: Complete CMAF fragments map to single MoQ objects
- **ISO BMFF Structure**: Each object contains styp + moof + mdat boxes
- **Single Track**: One ISO BMFF track per object
- **Decode Order**: Content in decode order with increasing timestamps
- **Time Alignment**: Support for media time-aligned group numbers across tracks

### LOC (Low Overhead Media Container)

This library implements [draft-ietf-moq-loc](https://datatracker.ietf.org/doc/html/draft-mzanaty-moq-loc-05):

- **LOC Payload**: Direct mapping to WebCodecs EncodedAudioChunk/EncodedVideoChunk internal data
- **LOC Header Extensions**: Support for registered extensions (capture timestamp, video frame marking, audio level, video config)
- **Varint Encoding**: Efficient encoding for metadata values
- **Extension Types**: Support for both varint values (even IDs) and byte array values (odd IDs)
- **Relay-Friendly Metadata**: Metadata accessible without decrypting payloads

### MSF (MOQT Streaming Format)

This library implements [draft-ietf-moq-msf-00](https://datatracker.ietf.org/doc/draft-ietf-moq-msf/):

- **Catalog Format**: JSON-based catalog with version, tracks, and metadata
- **Track Types**: Video, audio, caption, subtitle, sign language, media timeline, event timeline
- **Packaging Types**: LOC, mediatimeline, eventtimeline
- **Live Streaming**: `isLive=true` with `targetLatency` for real-time playback control
- **VOD Content**: `isLive=false` with `trackDuration` for on-demand content
- **Media Timeline**: JSON array format `[[pts, [groupId, objectId], wallclock], ...]` with GZIP compression
- **Event Timeline**: JSON object format with wallclock (`t`), location (`l`), or media PTS (`m`) indexing
- **Broadcast Termination**: `isComplete=true` with empty tracks signals stream end
- **Delta Updates**: Add, remove, or clone tracks without full catalog resend
- **Simulcast**: Alt groups with matching `targetLatency` for ABR switching
- **SVC Layers**: `temporalId`, `spatialId`, and `depends` for scalable video
- **Validation**: MSF-specific rules (latency consistency, timeline dependencies, etc.)

## Testing

The library includes comprehensive unit tests:

### Test Suites

**CMAF Tests:**

- `CmafFragmentTest`: Fragment serialization/deserialization, all box types
- `InitializationSegmentTest`: Initialization segment (ftyp + moov) support
- `MediaFileOperationsTest`: File I/O operations
- `PerformanceTest`: 4K/8K performance benchmarks
- `CodecSupportTest`: Codec validation (H.264, HEVC, VP9, AV1, AAC, Opus, etc.)

**LOC Tests:**

- `LocObjectTest`: LOC object serialization/deserialization, header extensions, and all metadata types

**MoqMI Tests:**

- `MoqMIObjectTest`: MoqMI object serialization/deserialization, all media types (H.264, Opus, AAC-LC, UTF-8), and header extensions

**MSF Tests:**

- `MsfBuilderTest`: Builder patterns for MsfCatalog and MsfTrack, serialization round-trips
- `MsfSerializerTest`: JSON serialization/deserialization, delta updates, validation integration
- `MsfTrackBuilderTest`: All track builder setters and factory methods (video, audio, timeline, caption)
- `MsfTimelineTest`: Media and event timeline JSON serialization with GZIP compression
- `MsfTimelineEdgeCasesTest`: Edge cases for empty lists, null data, large values, invalid JSON
- `MsfValidationEdgeCasesTest`: Validation rules for render groups, alt groups, latency consistency
- `MsfCatalogFieldsTest`: isComplete and targetLatency field handling
- `MsfCatalogValidatorTest`: MSF-specific validation rules and error handling

Run all tests:

```bash
mvn test
```

Run specific test suite:

```bash
mvn test -Dtest=LocObjectTest
mvn test -Dtest=CmafFragmentTest
mvn test -Dtest=MoqMIObjectTest
mvn test -Dtest=MsfBuilderTest
mvn test -Dtest=MsfSerializerTest
mvn test -Dtest=PerformanceTest
```

## Performance

The pure Java implementation has been thoroughly tested and validated for high-performance media processing:

**Performance Benchmarks:**

- **4K Video @ 30fps @ 25 Mbps**: 2.94ms per fragment, 2.0 GB/s throughput
- **4K Video @ 60fps @ 50 Mbps**: 6.42ms per fragment, 1.9 GB/s throughput
- **8K Video @ 30fps @ 100 Mbps**: 13.7ms per fragment, 1.7 GB/s throughput
- **Multi-track**: 563 fragments/sec for simultaneous video+audio processing
- **Memory Efficient**: Minimal garbage collection overhead

**Codec Support:**

- Video: H.264/AVC, H.265/HEVC, VP9, AV1
- Audio: AAC, Opus, Dolby Digital/Plus

See `PerformanceTest.java` and `CodecSupportTest.java` for detailed benchmarks.

## License

Apache License 2.0 - See LICENSE file for details

## Contributing

Based on the MOQtail project. Contributions welcome!

## Diamond Sponsors

- Red5 - [red5.net](https://www.red5.net/media-over-quic-moq/)

## References

### Specifications

- [MSF - MOQT Streaming Format (draft-ietf-moq-msf)](https://datatracker.ietf.org/doc/draft-ietf-moq-msf/)
- [MoQ CMAF Packaging Draft](https://github.com/wilaw/moq-cmaf-packaging/blob/main/draft-wilaw-moq-cmafpackaging.md)
- [LOC - Low Overhead Media Container (draft-ietf-moq-loc)](https://datatracker.ietf.org/doc/html/draft-mzanaty-moq-loc-05)
- [MoQ Media Interop (draft-cenzano-moq-media-interop)](https://datatracker.ietf.org/doc/html/draft-cenzano-moq-media-interop-03)
- [ISO Base Media File Format (ISO/IEC 14496-12)](https://www.iso.org/standard/74428.html)
- [CMAF (ISO/IEC 23000-19)](https://www.iso.org/standard/79106.html)
- [MoQ Transport](https://datatracker.ietf.org/wg/moq/about/)
- [WebCodecs](https://www.w3.org/TR/webcodecs/)
- [RFC9626 - Video Frame Marking](https://datatracker.ietf.org/doc/html/rfc9626)
- [RFC6464 - Audio Level Extension](https://datatracker.ietf.org/doc/html/rfc6464)

## Related Projects
  
- [MOQPUB](https://github.com/Red5/red5-moqpub)

## Support

For issues, questions, or contributions, please open an issue on GitHub.
