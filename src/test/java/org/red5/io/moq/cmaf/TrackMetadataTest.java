package org.red5.io.moq.cmaf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.red5.io.moq.cmaf.model.MoofBox;
import org.red5.io.moq.cmaf.model.SampleFlags;
import org.red5.io.moq.cmaf.model.TrackMetadata;

/**
 * Unit tests for track metadata boxes (tkhd, stsd, sample entries).
 * These boxes contain video/audio parameters like dimensions, codec info, sample rate, etc.
 */
class TrackMetadataTest {

    @Test
    @DisplayName("Test TkhdBox (Track Header) with video dimensions")
    void testTkhdBoxVideoDimensions() throws IOException {
        TrackMetadata.TkhdBox tkhd = new TrackMetadata.TkhdBox();
        tkhd.setVersion(0);
        tkhd.setFlags(0x000007); // track_enabled | track_in_movie | track_in_preview
        tkhd.setTrackId(1);
        tkhd.setDuration(60000); // 60 seconds at timescale 1000
        tkhd.setWidthPixels(1920);
        tkhd.setHeightPixels(1080);

        // Serialize
        byte[] data = tkhd.serialize();
        assertNotNull(data);
        assertEquals(92, data.length); // Version 0 tkhd size

        // Deserialize
        TrackMetadata.TkhdBox deserialized = new TrackMetadata.TkhdBox();
        deserialized.deserialize(ByteBuffer.wrap(data));

        // Verify
        assertEquals(0, deserialized.getVersion());
        assertEquals(0x000007, deserialized.getFlags());
        assertEquals(1, deserialized.getTrackId());
        assertEquals(60000, deserialized.getDuration());
        assertEquals(1920, deserialized.getWidthPixels());
        assertEquals(1080, deserialized.getHeightPixels());
    }

    @Test
    @DisplayName("Test TkhdBox version 1 with 64-bit times")
    void testTkhdBoxVersion1() throws IOException {
        TrackMetadata.TkhdBox tkhd = new TrackMetadata.TkhdBox();
        tkhd.setVersion(1);
        tkhd.setFlags(0x000001);
        tkhd.setTrackId(2);
        tkhd.setDuration(0x100000000L); // > 32-bit duration
        tkhd.setWidthPixels(3840);
        tkhd.setHeightPixels(2160);

        // Serialize
        byte[] data = tkhd.serialize();
        assertNotNull(data);
        assertEquals(104, data.length); // Version 1 tkhd size

        // Deserialize
        TrackMetadata.TkhdBox deserialized = new TrackMetadata.TkhdBox();
        deserialized.deserialize(ByteBuffer.wrap(data));

        // Verify
        assertEquals(1, deserialized.getVersion());
        assertEquals(2, deserialized.getTrackId());
        assertEquals(0x100000000L, deserialized.getDuration());
        assertEquals(3840, deserialized.getWidthPixels());
        assertEquals(2160, deserialized.getHeightPixels());
    }

    @Test
    @DisplayName("Test TkhdBox with audio (no dimensions)")
    void testTkhdBoxAudio() throws IOException {
        TrackMetadata.TkhdBox tkhd = new TrackMetadata.TkhdBox();
        tkhd.setVersion(0);
        tkhd.setFlags(0x000003);
        tkhd.setTrackId(2);
        tkhd.setDuration(120000);
        tkhd.setVolume(0x0100); // Volume 1.0 in 8.8 fixed point
        tkhd.setWidthPixels(0); // No dimensions for audio
        tkhd.setHeightPixels(0);

        // Serialize
        byte[] data = tkhd.serialize();
        assertNotNull(data);

        // Deserialize
        TrackMetadata.TkhdBox deserialized = new TrackMetadata.TkhdBox();
        deserialized.deserialize(ByteBuffer.wrap(data));

        // Verify
        assertEquals(2, deserialized.getTrackId());
        assertEquals(120000, deserialized.getDuration());
        assertEquals(0x0100, deserialized.getVolume());
        assertEquals(0, deserialized.getWidthPixels());
        assertEquals(0, deserialized.getHeightPixels());
    }

    @Test
    @DisplayName("Test VisualSampleEntry for H.264/AVC")
    void testVisualSampleEntryAVC() throws IOException {
        TrackMetadata.VisualSampleEntry entry = new TrackMetadata.VisualSampleEntry("avc1");
        entry.setDataReferenceIndex(1);
        entry.setWidth(1920);
        entry.setHeight(1080);
        entry.setCompressorName("H.264");

        // Simulate avcC configuration
        byte[] avcConfig = new byte[]{0x01, 0x64, 0x00, 0x1F}; // Simplified avcC
        entry.setCodecConfig(avcConfig);

        // Serialize
        byte[] data = entry.serialize();
        assertNotNull(data);
        assertTrue(data.length > 86); // Base visual entry + config

        // Deserialize
        TrackMetadata.VisualSampleEntry deserialized = new TrackMetadata.VisualSampleEntry();
        deserialized.deserialize(ByteBuffer.wrap(data));

        // Verify
        assertEquals("avc1", deserialized.getType());
        assertEquals(1, deserialized.getDataReferenceIndex());
        assertEquals(1920, deserialized.getWidth());
        assertEquals(1080, deserialized.getHeight());
        assertEquals("H.264", deserialized.getCompressorName());
        assertArrayEquals(avcConfig, deserialized.getCodecConfig());
    }

    @Test
    @DisplayName("Test VisualSampleEntry for different resolutions")
    void testVisualSampleEntryResolutions() throws IOException {
        // Test common video resolutions
        int[][] resolutions = {
            {640, 480},    // VGA
            {1280, 720},   // 720p
            {1920, 1080},  // 1080p
            {3840, 2160},  // 4K
            {7680, 4320}   // 8K
        };

        for (int[] res : resolutions) {
            TrackMetadata.VisualSampleEntry entry = new TrackMetadata.VisualSampleEntry("hev1");
            entry.setWidth(res[0]);
            entry.setHeight(res[1]);

            byte[] data = entry.serialize();
            TrackMetadata.VisualSampleEntry deserialized = new TrackMetadata.VisualSampleEntry();
            deserialized.deserialize(ByteBuffer.wrap(data));

            assertEquals(res[0], deserialized.getWidth(), "Width mismatch for " + res[0] + "x" + res[1]);
            assertEquals(res[1], deserialized.getHeight(), "Height mismatch for " + res[0] + "x" + res[1]);
        }
    }

    @Test
    @DisplayName("Test VisualSampleEntry compressor name encoding")
    void testVisualSampleEntryCompressorName() throws IOException {
        TrackMetadata.VisualSampleEntry entry = new TrackMetadata.VisualSampleEntry("vp09");
        entry.setWidth(1920);
        entry.setHeight(1080);
        entry.setCompressorName("VP9 Video Codec"); // 15 chars

        byte[] data = entry.serialize();
        TrackMetadata.VisualSampleEntry deserialized = new TrackMetadata.VisualSampleEntry();
        deserialized.deserialize(ByteBuffer.wrap(data));

        assertEquals("VP9 Video Codec", deserialized.getCompressorName());
    }

    @Test
    @DisplayName("Test AudioSampleEntry for AAC")
    void testAudioSampleEntryAAC() throws IOException {
        TrackMetadata.AudioSampleEntry entry = new TrackMetadata.AudioSampleEntry("mp4a");
        entry.setDataReferenceIndex(1);
        entry.setChannelCount(2); // Stereo
        entry.setSampleSize(16); // 16-bit
        entry.setSampleRateHz(48000); // 48 kHz

        // Simulate esds (Elementary Stream Descriptor)
        byte[] esds = new byte[]{0x03, 0x19, 0x00, 0x00}; // Simplified esds
        entry.setCodecConfig(esds);

        // Serialize
        byte[] data = entry.serialize();
        assertNotNull(data);
        assertTrue(data.length > 36); // Base audio entry + config

        // Deserialize
        TrackMetadata.AudioSampleEntry deserialized = new TrackMetadata.AudioSampleEntry();
        deserialized.deserialize(ByteBuffer.wrap(data));

        // Verify
        assertEquals("mp4a", deserialized.getType());
        assertEquals(1, deserialized.getDataReferenceIndex());
        assertEquals(2, deserialized.getChannelCount());
        assertEquals(16, deserialized.getSampleSize());
        assertEquals(48000, deserialized.getSampleRateHz());
        assertArrayEquals(esds, deserialized.getCodecConfig());
    }

    @Test
    @DisplayName("Test AudioSampleEntry for Opus")
    void testAudioSampleEntryOpus() throws IOException {
        TrackMetadata.AudioSampleEntry entry = new TrackMetadata.AudioSampleEntry("Opus");
        entry.setDataReferenceIndex(1);
        entry.setChannelCount(2);
        entry.setSampleSize(16);
        entry.setSampleRateHz(48000);

        // Opus config
        byte[] opusConfig = new byte[]{
            0x4F, 0x70, 0x75, 0x73, 0x48, 0x65, 0x61, 0x64 // "OpusHead"
        };
        entry.setCodecConfig(opusConfig);

        byte[] data = entry.serialize();
        TrackMetadata.AudioSampleEntry deserialized = new TrackMetadata.AudioSampleEntry();
        deserialized.deserialize(ByteBuffer.wrap(data));

        assertEquals("Opus", deserialized.getType());
        assertEquals(2, deserialized.getChannelCount());
        assertEquals(48000, deserialized.getSampleRateHz());
        assertArrayEquals(opusConfig, deserialized.getCodecConfig());
    }

    @Test
    @DisplayName("Test AudioSampleEntry for different sample rates")
    void testAudioSampleEntryDifferentRates() throws IOException {
        // Note: ISO BMFF uses 16.16 fixed point for sample rate, so max is 65535 Hz
        int[] sampleRates = {8000, 16000, 22050, 44100, 48000};

        for (int rate : sampleRates) {
            TrackMetadata.AudioSampleEntry entry = new TrackMetadata.AudioSampleEntry("mp4a");
            entry.setChannelCount(2);
            entry.setSampleSize(16);
            entry.setSampleRateHz(rate);

            byte[] data = entry.serialize();
            TrackMetadata.AudioSampleEntry deserialized = new TrackMetadata.AudioSampleEntry();
            deserialized.deserialize(ByteBuffer.wrap(data));

            assertEquals(rate, deserialized.getSampleRateHz(), "Sample rate mismatch for " + rate);
        }
    }

    @Test
    @DisplayName("Test AudioSampleEntry for different channel configurations")
    void testAudioSampleEntryChannelConfigs() throws IOException {
        int[] channelCounts = {1, 2, 6, 8}; // Mono, stereo, 5.1, 7.1

        for (int channels : channelCounts) {
            TrackMetadata.AudioSampleEntry entry = new TrackMetadata.AudioSampleEntry("mp4a");
            entry.setChannelCount(channels);
            entry.setSampleSize(16);
            entry.setSampleRateHz(48000);

            byte[] data = entry.serialize();
            TrackMetadata.AudioSampleEntry deserialized = new TrackMetadata.AudioSampleEntry();
            deserialized.deserialize(ByteBuffer.wrap(data));

            assertEquals(channels, deserialized.getChannelCount(), "Channel count mismatch for " + channels);
        }
    }

    @Test
    @DisplayName("Test StsdBox with single video entry")
    void testStsdBoxVideoEntry() throws IOException {
        TrackMetadata.StsdBox stsd = new TrackMetadata.StsdBox();

        TrackMetadata.VisualSampleEntry videoEntry = new TrackMetadata.VisualSampleEntry("avc1");
        videoEntry.setWidth(1920);
        videoEntry.setHeight(1080);
        videoEntry.setCompressorName("H.264");

        stsd.setEntries(new TrackMetadata.SampleEntry[]{videoEntry});

        // Serialize
        byte[] data = stsd.serialize();
        assertNotNull(data);

        // Deserialize
        TrackMetadata.StsdBox deserialized = new TrackMetadata.StsdBox();
        deserialized.deserialize(ByteBuffer.wrap(data));

        // Verify
        assertEquals(1, deserialized.getEntries().length);
        assertTrue(deserialized.getEntries()[0] instanceof TrackMetadata.VisualSampleEntry);

        TrackMetadata.VisualSampleEntry deserializedEntry =
                (TrackMetadata.VisualSampleEntry) deserialized.getEntries()[0];
        assertEquals("avc1", deserializedEntry.getType());
        assertEquals(1920, deserializedEntry.getWidth());
        assertEquals(1080, deserializedEntry.getHeight());
    }

    @Test
    @DisplayName("Test StsdBox with single audio entry")
    void testStsdBoxAudioEntry() throws IOException {
        TrackMetadata.StsdBox stsd = new TrackMetadata.StsdBox();

        TrackMetadata.AudioSampleEntry audioEntry = new TrackMetadata.AudioSampleEntry("mp4a");
        audioEntry.setChannelCount(2);
        audioEntry.setSampleSize(16);
        audioEntry.setSampleRateHz(48000);

        stsd.setEntries(new TrackMetadata.SampleEntry[]{audioEntry});

        // Serialize
        byte[] data = stsd.serialize();
        assertNotNull(data);

        // Deserialize
        TrackMetadata.StsdBox deserialized = new TrackMetadata.StsdBox();
        deserialized.deserialize(ByteBuffer.wrap(data));

        // Verify
        assertEquals(1, deserialized.getEntries().length);
        assertTrue(deserialized.getEntries()[0] instanceof TrackMetadata.AudioSampleEntry);

        TrackMetadata.AudioSampleEntry deserializedEntry =
                (TrackMetadata.AudioSampleEntry) deserialized.getEntries()[0];
        assertEquals("mp4a", deserializedEntry.getType());
        assertEquals(2, deserializedEntry.getChannelCount());
        assertEquals(48000, deserializedEntry.getSampleRateHz());
    }

    @Test
    @DisplayName("Test TfhdBox with default sample fields")
    void testTfhdBoxWithDefaults() throws IOException {
        MoofBox.TfhdBox tfhd = new MoofBox.TfhdBox();
        tfhd.setTrackId(1);
        tfhd.setDefaultSampleDuration(3000);
        tfhd.setDefaultSampleSize(50000);
        tfhd.setDefaultSampleFlags(SampleFlags.createSyncSampleFlags());
        tfhd.setSampleDescriptionIndex(1);

        // Serialize
        byte[] data = tfhd.serialize();
        assertNotNull(data);

        // Deserialize
        MoofBox.TfhdBox deserialized = new MoofBox.TfhdBox();
        deserialized.deserialize(ByteBuffer.wrap(data));

        // Verify
        assertEquals(1, deserialized.getTrackId());
        assertEquals(3000, deserialized.getDefaultSampleDuration());
        assertEquals(50000, deserialized.getDefaultSampleSize());
        assertNotNull(deserialized.getDefaultSampleFlags());
        assertTrue(deserialized.getDefaultSampleFlags().isSyncSample());
        assertEquals(1, deserialized.getSampleDescriptionIndex());
    }

    @Test
    @DisplayName("Test TfhdBox with base data offset")
    void testTfhdBoxWithBaseDataOffset() throws IOException {
        MoofBox.TfhdBox tfhd = new MoofBox.TfhdBox();
        tfhd.setTrackId(1);
        tfhd.setBaseDataOffset(1024);

        // Serialize
        byte[] data = tfhd.serialize();
        assertNotNull(data);

        // Deserialize
        MoofBox.TfhdBox deserialized = new MoofBox.TfhdBox();
        deserialized.deserialize(ByteBuffer.wrap(data));

        // Verify
        assertEquals(1, deserialized.getTrackId());
        assertEquals(1024, deserialized.getBaseDataOffset());
    }

    @Test
    @DisplayName("Test complete track metadata for video")
    void testCompleteVideoTrackMetadata() throws IOException {
        // Create track header
        TrackMetadata.TkhdBox tkhd = new TrackMetadata.TkhdBox();
        tkhd.setVersion(0);
        tkhd.setFlags(0x000003);
        tkhd.setTrackId(1);
        tkhd.setDuration(60000);
        tkhd.setWidthPixels(1920);
        tkhd.setHeightPixels(1080);

        // Create sample description
        TrackMetadata.VisualSampleEntry videoEntry = new TrackMetadata.VisualSampleEntry("avc1");
        videoEntry.setWidth(1920);
        videoEntry.setHeight(1080);
        videoEntry.setCompressorName("H.264");

        TrackMetadata.StsdBox stsd = new TrackMetadata.StsdBox();
        stsd.setEntries(new TrackMetadata.SampleEntry[]{videoEntry});

        // Serialize and deserialize tkhd
        byte[] tkhdData = tkhd.serialize();
        TrackMetadata.TkhdBox tkhdDeserialized = new TrackMetadata.TkhdBox();
        tkhdDeserialized.deserialize(ByteBuffer.wrap(tkhdData));

        // Serialize and deserialize stsd
        byte[] stsdData = stsd.serialize();
        TrackMetadata.StsdBox stsdDeserialized = new TrackMetadata.StsdBox();
        stsdDeserialized.deserialize(ByteBuffer.wrap(stsdData));

        // Verify consistency
        assertEquals(tkhdDeserialized.getWidthPixels(),
                ((TrackMetadata.VisualSampleEntry) stsdDeserialized.getEntries()[0]).getWidth());
        assertEquals(tkhdDeserialized.getHeightPixels(),
                ((TrackMetadata.VisualSampleEntry) stsdDeserialized.getEntries()[0]).getHeight());
    }

    @Test
    @DisplayName("Test complete track metadata for audio")
    void testCompleteAudioTrackMetadata() throws IOException {
        // Create track header (no dimensions for audio)
        TrackMetadata.TkhdBox tkhd = new TrackMetadata.TkhdBox();
        tkhd.setVersion(0);
        tkhd.setFlags(0x000003);
        tkhd.setTrackId(2);
        tkhd.setDuration(120000);
        tkhd.setVolume(0x0100); // Full volume

        // Create sample description
        TrackMetadata.AudioSampleEntry audioEntry = new TrackMetadata.AudioSampleEntry("mp4a");
        audioEntry.setChannelCount(2);
        audioEntry.setSampleSize(16);
        audioEntry.setSampleRateHz(48000);

        TrackMetadata.StsdBox stsd = new TrackMetadata.StsdBox();
        stsd.setEntries(new TrackMetadata.SampleEntry[]{audioEntry});

        // Serialize and deserialize
        byte[] tkhdData = tkhd.serialize();
        TrackMetadata.TkhdBox tkhdDeserialized = new TrackMetadata.TkhdBox();
        tkhdDeserialized.deserialize(ByteBuffer.wrap(tkhdData));

        byte[] stsdData = stsd.serialize();
        TrackMetadata.StsdBox stsdDeserialized = new TrackMetadata.StsdBox();
        stsdDeserialized.deserialize(ByteBuffer.wrap(stsdData));

        // Verify
        assertEquals(2, tkhdDeserialized.getTrackId());
        assertEquals(0x0100, tkhdDeserialized.getVolume());
        assertEquals(2, ((TrackMetadata.AudioSampleEntry) stsdDeserialized.getEntries()[0]).getChannelCount());
        assertEquals(48000, ((TrackMetadata.AudioSampleEntry) stsdDeserialized.getEntries()[0]).getSampleRateHz());
    }

    @Test
    @DisplayName("Test toString methods for debugging")
    void testToStringMethods() {
        TrackMetadata.TkhdBox tkhd = new TrackMetadata.TkhdBox();
        tkhd.setTrackId(1);
        tkhd.setWidthPixels(1920);
        tkhd.setHeightPixels(1080);
        String tkhdStr = tkhd.toString();
        assertNotNull(tkhdStr);
        assertTrue(tkhdStr.contains("1920"));
        assertTrue(tkhdStr.contains("1080"));

        TrackMetadata.VisualSampleEntry video = new TrackMetadata.VisualSampleEntry("avc1");
        video.setWidth(1920);
        video.setHeight(1080);
        String videoStr = video.toString();
        assertNotNull(videoStr);
        assertTrue(videoStr.contains("avc1"));
        assertTrue(videoStr.contains("1920"));

        TrackMetadata.AudioSampleEntry audio = new TrackMetadata.AudioSampleEntry("mp4a");
        audio.setChannelCount(2);
        audio.setSampleRateHz(48000);
        String audioStr = audio.toString();
        assertNotNull(audioStr);
        assertTrue(audioStr.contains("mp4a"));
        assertTrue(audioStr.contains("48000"));
        assertTrue(audioStr.contains("channels=2") || audioStr.contains("channel"));
    }
}
