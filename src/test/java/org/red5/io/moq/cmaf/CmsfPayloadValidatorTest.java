package org.red5.io.moq.cmaf;

import org.junit.jupiter.api.Test;
import org.red5.io.moq.cmaf.model.CmafFragment;
import org.red5.io.moq.cmaf.model.MdatBox;
import org.red5.io.moq.cmaf.model.MoofBox;
import org.red5.io.moq.cmaf.model.SampleFlags;
import org.red5.io.moq.cmaf.util.Fmp4InitSegmentBuilder;
import org.red5.io.moq.cmaf.validate.CmsfPayloadValidator;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class CmsfPayloadValidatorTest {

    @Test
    void testEncodeAndValidateInitData() throws Exception {
        byte[] initSegment = new Fmp4InitSegmentBuilder()
            .addVideoTrack(new Fmp4InitSegmentBuilder.VideoTrackConfig(1, 90000, "avc1", new byte[] {1, 2, 3}, 1920, 1080))
            .build();

        String initData = CmsfPayloadValidator.encodeInitData(initSegment);

        assertDoesNotThrow(() -> CmsfPayloadValidator.validateInitData(initData));
        assertArrayEquals(initSegment, CmsfPayloadValidator.decodeInitData(initData));
    }

    @Test
    void testRejectsFragmentAsInitData() throws Exception {
        byte[] fragment = createValidCmsfObjectPayload();
        String initData = CmsfPayloadValidator.encodeInitData(fragment);

        assertThrows(IllegalArgumentException.class, () -> CmsfPayloadValidator.validateInitData(initData));
    }

    @Test
    void testValidateObjectPayload() throws Exception {
        byte[] fragment = createValidCmsfObjectPayload();

        CmafFragment parsed = CmsfPayloadValidator.validateObjectPayload(fragment);

        assertNotNull(parsed.getMoof());
        assertNotNull(parsed.getMdat());
        assertEquals(1, parsed.getMoof().getTrafs().size());
    }

    @Test
    void testRejectsMissingTrun() throws Exception {
        CmafFragment fragment = new CmafFragment();
        MoofBox moof = new MoofBox();
        moof.setMfhd(new MoofBox.MfhdBox(1));
        MoofBox.TrafBox traf = new MoofBox.TrafBox();
        MoofBox.TfhdBox tfhd = new MoofBox.TfhdBox();
        tfhd.setTrackId(1);
        traf.setTfhd(tfhd);
        moof.addTraf(traf);
        fragment.setMoof(moof);
        fragment.setMdat(new MdatBox(new byte[] {1, 2, 3}));

        assertThrows(IllegalArgumentException.class,
            () -> CmsfPayloadValidator.validateObjectPayload(fragment.serialize()));
    }

    private byte[] createValidCmsfObjectPayload() throws IOException {
        CmafFragment fragment = new CmafFragment();
        MoofBox moof = new MoofBox();
        moof.setMfhd(new MoofBox.MfhdBox(1));

        MoofBox.TrafBox traf = new MoofBox.TrafBox();
        MoofBox.TfhdBox tfhd = new MoofBox.TfhdBox();
        tfhd.setTrackId(1);
        traf.setTfhd(tfhd);
        traf.setTfdt(new MoofBox.TfdtBox(0));

        MoofBox.TrunBox trun = new MoofBox.TrunBox();
        trun.setVersion(0);
        trun.setTrunFlags(0x000100 | 0x000200 | 0x000400);
        trun.setSampleCount(1);
        MoofBox.TrunBox.Sample sample = new MoofBox.TrunBox.Sample();
        sample.setDuration(3000);
        sample.setSize(4);
        sample.setSampleFlags(new SampleFlags(0));
        trun.addSample(sample);
        traf.addTrun(trun);

        moof.addTraf(traf);
        fragment.setMoof(moof);
        fragment.setMdat(new MdatBox(new byte[] {0, 0, 0, 1}));
        return fragment.serialize();
    }
}
