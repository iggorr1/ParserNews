package com.parsernews.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a small price chart as a PNG for Telegram: the target's recent price line, a vertical
 * marker at the moment the news broke (with the price then), a horizontal line at the offer price,
 * and the current price. Pure Java2D — no external chart service (the price data stays on our host)
 * and no new dependency. Best-effort: returns null on any failure so dispatch falls back to text.
 */
@Service
public class PriceChartImageService {
    private static final Logger log = LoggerFactory.getLogger(PriceChartImageService.class);

    private static final int W = 900, H = 440;
    private static final int ML = 66, MR = 74, MT = 44, MB = 40;

    private static final Color BG = new Color(0x0d, 0x13, 0x1b);
    private static final Color GRID = new Color(0x24, 0x32, 0x44);
    private static final Color TEXT = new Color(0xed, 0xf4, 0xf8);
    private static final Color MUTED = new Color(0x91, 0xa0, 0xb4);
    private static final Color ACCENT = new Color(0x38, 0xd6, 0xc9);
    private static final Color WARN = new Color(0xd8, 0xa7, 0x3e);
    private static final Color NEWS = new Color(0xf0, 0x72, 0x72);

    // Fixed locale so labels look the same regardless of the host's default (dot decimals,
    // English month names) instead of the JVM locale's comma decimals / localized months.
    private static final java.util.Locale LOCALE = java.util.Locale.US;
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd MMM", LOCALE).withZone(ZoneOffset.UTC);

    public byte[] render(
            String ticker,
            String company,
            StockPriceService.PriceHistory history,
            Instant newsInstant,
            java.math.BigDecimal offerPrice,
            String currency
    ) {
        try {
            List<StockPriceService.PricePoint> pts = history == null ? List.of() : history.points();
            if (pts.size() < 2) {
                return null;
            }
            BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(BG);
            g.fillRect(0, 0, W, H);

            long tMin = pts.get(0).ts();
            long tMax = pts.get(pts.size() - 1).ts();
            if (tMax <= tMin) {
                g.dispose();
                return null;
            }
            double pMin = Double.MAX_VALUE, pMax = -Double.MAX_VALUE;
            for (var p : pts) {
                pMin = Math.min(pMin, p.close());
                pMax = Math.max(pMax, p.close());
            }
            double offer = offerPrice == null ? Double.NaN : offerPrice.doubleValue();
            if (!Double.isNaN(offer)) {
                pMin = Math.min(pMin, offer);
                pMax = Math.max(pMax, offer);
            }
            double pad = (pMax - pMin) * 0.08;
            if (pad <= 0) {
                pad = Math.max(0.5, pMax * 0.02);
            }
            pMin -= pad;
            pMax += pad;

            int plotL = ML, plotR = W - MR, plotT = MT, plotB = H - MB;

            // Title
            g.setColor(TEXT);
            g.setFont(new Font("SansSerif", Font.BOLD, 18));
            String title = "$" + ticker + (company == null || company.isBlank() ? "" : "  ·  " + company);
            g.drawString(clip(g, title, plotR - plotL), plotL, 26);

            // Y gridlines + labels
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            for (int i = 0; i <= 4; i++) {
                double val = pMax - (pMax - pMin) * i / 4.0;
                int y = plotT + (plotB - plotT) * i / 4;
                g.setColor(GRID);
                g.drawLine(plotL, y, plotR, y);
                g.setColor(MUTED);
                g.drawString(fmt(val), 6, y + 4);
            }
            // X date labels
            for (int i = 0; i <= 4; i++) {
                long t = tMin + (tMax - tMin) * i / 4;
                int x = plotL + (plotR - plotL) * i / 4;
                g.setColor(MUTED);
                String lbl = DATE.format(Instant.ofEpochSecond(t));
                g.drawString(lbl, x - g.getFontMetrics().stringWidth(lbl) / 2, plotB + 18);
            }

            // Price line
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int[] xs = new int[pts.size()];
            int[] ys = new int[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                xs[i] = xFor(pts.get(i).ts(), tMin, tMax, plotL, plotR);
                ys[i] = yFor(pts.get(i).close(), pMin, pMax, plotT, plotB);
            }
            for (int i = 1; i < pts.size(); i++) {
                g.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
            }

            // Offer price line
            if (!Double.isNaN(offer)) {
                int yo = yFor(offer, pMin, pMax, plotT, plotB);
                g.setColor(WARN);
                g.setStroke(dashed());
                g.drawLine(plotL, yo, plotR, yo);
                g.drawString("offer " + fmt(offer), plotR - 84, yo - 5);
            }

            // News marker (vertical line + price then). Clamp the moment into the visible range:
            // news very often breaks after the last price bar (after the close, or because intraday
            // data lags), which would otherwise drop the marker off the right edge entirely.
            if (newsInstant != null) {
                long tn = Math.max(tMin, Math.min(newsInstant.getEpochSecond(), tMax));
                int xn = xFor(tn, tMin, tMax, plotL, plotR);
                double closeAtNews = closestClose(pts, tn);
                int yn = yFor(closeAtNews, pMin, pMax, plotT, plotB);
                g.setColor(NEWS);
                g.setStroke(dashed());
                g.drawLine(xn, plotT, xn, plotB);
                g.setStroke(new BasicStroke(2f));
                g.fillOval(xn - 4, yn - 4, 8, 8);
                String lbl = "news " + fmt(closeAtNews);
                int w = g.getFontMetrics().stringWidth(lbl);
                // Put the label on whichever side of the line has room (left when near the right edge).
                int lx = (xn + 6 + w <= plotR) ? xn + 6 : xn - 6 - w;
                g.drawString(lbl, Math.max(plotL, lx), plotT + 12);
            }

            // Current price dot + label
            g.setColor(ACCENT);
            g.setStroke(new BasicStroke(2f));
            int lastX = xs[xs.length - 1], lastY = ys[ys.length - 1];
            g.fillOval(lastX - 4, lastY - 4, 8, 8);
            String cur = fmt(pts.get(pts.size() - 1).close()) + (currency != null && !"USD".equals(currency) ? " " + currency : "");
            g.drawString(cur, Math.min(lastX + 6, plotR - g.getFontMetrics().stringWidth(cur)), lastY - 6);

            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (Exception exception) {
            log.warn("Price chart render failed for {}: {}", ticker, exception.getMessage());
            return null;
        }
    }

    private static int xFor(long t, long tMin, long tMax, int l, int r) {
        return (int) Math.round(l + (double) (t - tMin) / (tMax - tMin) * (r - l));
    }

    private static int yFor(double v, double pMin, double pMax, int t, int b) {
        return (int) Math.round(b - (v - pMin) / (pMax - pMin) * (b - t));
    }

    private static double closestClose(List<StockPriceService.PricePoint> pts, long t) {
        StockPriceService.PricePoint best = pts.get(0);
        for (var p : pts) {
            if (Math.abs(p.ts() - t) < Math.abs(best.ts() - t)) {
                best = p;
            }
        }
        return best.close();
    }

    private static BasicStroke dashed() {
        return new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 4f, new float[]{5f, 4f}, 0f);
    }

    private static String fmt(double v) {
        return v >= 100 ? String.format(LOCALE, "%.1f", v) : String.format(LOCALE, "%.2f", v);
    }

    private static String clip(Graphics2D g, String s, int maxWidth) {
        if (g.getFontMetrics().stringWidth(s) <= maxWidth) {
            return s;
        }
        while (s.length() > 4 && g.getFontMetrics().stringWidth(s + "…") > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "…";
    }
}
