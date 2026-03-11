package org.red5.io.moq.cmaf.validate;

import org.red5.io.moq.cmaf.deserialize.CmafDeserializer;
import org.red5.io.moq.cmaf.model.CmafFragment;
import org.red5.io.moq.cmaf.model.MoofBox;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * CMSF validation helpers built on top of the existing CMAF model.
 */
public final class CmsfPayloadValidator {
    private CmsfPayloadValidator() {
    }

    public static String encodeInitData(byte[] initSegmentBytes) {
        if (initSegmentBytes == null || initSegmentBytes.length == 0) {
            throw new IllegalArgumentException("Initialization segment bytes are required");
        }
        return Base64.getEncoder().encodeToString(initSegmentBytes);
    }

    public static byte[] decodeInitData(String base64InitData) {
        if (base64InitData == null || base64InitData.isBlank()) {
            throw new IllegalArgumentException("initData is required");
        }
        try {
            return Base64.getDecoder().decode(base64InitData);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("initData must be valid base64", e);
        }
    }

    public static void validateInitData(String base64InitData) {
        validateInitializationHeader(decodeInitData(base64InitData));
    }

    public static void validateInitializationHeader(byte[] initSegmentBytes) {
        List<String> boxTypes = readTopLevelBoxTypes(initSegmentBytes);
        if (boxTypes.isEmpty()) {
            throw new IllegalArgumentException("Initialization header must contain ISO BMFF boxes");
        }
        if (!boxTypes.contains("ftyp")) {
            throw new IllegalArgumentException("Initialization header must contain an ftyp box");
        }
        if (!boxTypes.contains("moov")) {
            throw new IllegalArgumentException("Initialization header must contain a moov box");
        }
        if (boxTypes.contains("moof") || boxTypes.contains("mdat")) {
            throw new IllegalArgumentException("Initialization header must not contain media fragment boxes");
        }
    }

    public static CmafFragment validateObjectPayload(byte[] objectPayload) {
        if (objectPayload == null || objectPayload.length == 0) {
            throw new IllegalArgumentException("Object payload is required");
        }

        try {
            CmafFragment fragment = new CmafDeserializer().deserialize(objectPayload);
            if (fragment.getMoof() == null) {
                throw new IllegalArgumentException("CMSF object payload must contain a moof box");
            }
            if (fragment.getMdat() == null) {
                throw new IllegalArgumentException("CMSF object payload must contain an mdat box");
            }
            if (fragment.getMoof().getMfhd() == null) {
                throw new IllegalArgumentException("CMSF object payload must contain an mfhd box");
            }
            if (fragment.getMoof().getTrafs().size() != 1) {
                throw new IllegalArgumentException("CMSF object payload must contain exactly one track fragment");
            }

            MoofBox.TrafBox traf = fragment.getMoof().getTrafs().get(0);
            if (traf.getTfhd() == null) {
                throw new IllegalArgumentException("CMSF object payload must contain a tfhd box");
            }
            if (traf.getTfhd().getTrackId() <= 0) {
                throw new IllegalArgumentException("CMSF object payload trackId must be positive");
            }
            if (traf.getTruns().isEmpty()) {
                throw new IllegalArgumentException("CMSF object payload must contain at least one trun box");
            }

            return fragment;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid CMSF object payload", e);
        }
    }

    private static List<String> readTopLevelBoxTypes(byte[] bytes) {
        List<String> boxTypes = new ArrayList<>();
        int offset = 0;
        while (offset + 8 <= bytes.length) {
            int boxSize = ((bytes[offset] & 0xFF) << 24)
                | ((bytes[offset + 1] & 0xFF) << 16)
                | ((bytes[offset + 2] & 0xFF) << 8)
                | (bytes[offset + 3] & 0xFF);

            if (boxSize < 8 || offset + boxSize > bytes.length) {
                throw new IllegalArgumentException("Invalid ISO BMFF box size in payload");
            }

            String boxType = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII);
            boxTypes.add(boxType);
            offset += boxSize;
        }
        return boxTypes;
    }
}
