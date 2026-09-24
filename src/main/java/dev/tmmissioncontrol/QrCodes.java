package dev.tmmissioncontrol;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.common.BitMatrix;

import java.util.Map;

public final class QrCodes {
    private QrCodes() {
    }

    public static String svg(String text) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1,
                    EncodeHintType.CHARACTER_SET, "UTF-8"
            ));
            int w = matrix.getWidth();
            int h = matrix.getHeight();
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (matrix.get(x, y)) {
                        path.append("M").append(x).append(" ").append(y).append("h1v1h-1z");
                    }
                }
            }
            return """
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 %d %d" shape-rendering="crispEdges">
                      <rect width="100%%" height="100%%" fill="#fff"/>
                      <path fill="#111" d="%s"/>
                    </svg>
                    """.formatted(w, h, path);
        } catch (Exception e) {
            throw new IllegalStateException("Could not build QR code", e);
        }
    }
}
