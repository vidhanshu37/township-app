package com.township.backend.complaints;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ImageSnifferTest {

    @Test
    void recognisesJpeg() {
        assertEquals("image/jpeg", ImageSniffer.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0}));
    }

    @Test
    void recognisesPng() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};
        assertEquals("image/png", ImageSniffer.detect(png));
    }

    @Test
    void recognisesWebp() {
        byte[] webp = "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        assertEquals("image/webp", ImageSniffer.detect(webp));
    }

    @Test
    void refusesSvgGifHtmlAndEmpty() {
        assertNull(ImageSniffer.detect("<svg xmlns='http://www.w3.org/2000/svg'></svg>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageSniffer.detect("GIF89a....".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageSniffer.detect("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)));
        assertNull(ImageSniffer.detect(new byte[0]));
        assertNull(ImageSniffer.detect(null));
    }

    @Test
    void aRenamedExecutableIsNotAnImage() {
        assertNull(ImageSniffer.detect(new byte[]{'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0}));
    }

    @Test
    void truncatedHeadersAreRefused() {
        assertNull(ImageSniffer.detect(new byte[]{(byte) 0xFF, (byte) 0xD8}));
        assertNull(ImageSniffer.detect("RIFF".getBytes(StandardCharsets.ISO_8859_1)));
    }
}