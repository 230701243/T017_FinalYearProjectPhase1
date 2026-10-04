package com.financeapp.service;

import com.financeapp.dto.MultiScenarioWhatIfResponse;
import com.financeapp.dto.ThreeMonthCashFlowResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates standards-compliant, high-DPI vector SVG charts for monthly financial reports.
 * Designed for offline embedding directly into downloadable HTML reports.
 * Fully localized with Noto font support for all scripts (Indian scripts, Arabic, CJK).
 */
@Service
public class ReportChartService {

    private static final Logger log = LoggerFactory.getLogger(ReportChartService.class);

    public String generateCashFlowProjectionChartSvg(
            ThreeMonthCashFlowResponse cashFlow,
            BigDecimal reserveThresholdUsd,
            String username,
            CurrencyConversionService currencyConversionService
    ) {
        return generateCashFlowProjectionChartSvg(cashFlow, reserveThresholdUsd, username, currencyConversionService, "en", null);
    }

    public String generateCashFlowProjectionChartSvg(
            ThreeMonthCashFlowResponse cashFlow,
            BigDecimal reserveThresholdUsd,
            String username,
            CurrencyConversionService currencyConversionService,
            String lang,
            ReportTranslationService translationService
    ) {
        log.info("[CHART-SERVICE] generateCashFlowProjectionChartSvg invoked for user '{}', lang='{}'", username, lang);
        String noDataText = translationService != null
                ? translationService.getMessage(lang, "chart.noData", "No cash flow projection data available.")
                : "No cash flow projection data available.";

        if (cashFlow == null || cashFlow.projections() == null || cashFlow.projections().isEmpty()) {
            log.warn("[CHART-SERVICE] Cash-Flow data or projections empty for user '{}' (cashFlow={}, projections={})",
                    username, cashFlow, cashFlow != null ? cashFlow.projections() : null);
            return "<div style=\"padding: 24px; text-align: center; color: #94a3b8;\">" + noDataText + "</div>";
        }

        var scriptInfo = translationService != null ? translationService.getScriptInfo(lang) : null;
        String fontCss = scriptInfo != null ? scriptInfo.fontCss() : "'Segoe UI', -apple-system, sans-serif";
        String fontName = scriptInfo != null ? scriptInfo.fontName() : "Segoe UI";

        var currInfo = currencyConversionService.convertUsdForUser(BigDecimal.ONE, username);
        String symbol = currInfo.currencySymbol();

        record Point(String label, double val, String formatted) {}
        List<Point> points = new ArrayList<>();

        // Month 0: Starting Balance
        String currentLabel = translationService != null
                ? translationService.getMessage(lang, "chart.current", "Current")
                : "Current";
        var startConverted = currencyConversionService.convertUsdForUser(cashFlow.startingBalance(), username);
        points.add(new Point(currentLabel, startConverted.convertedAmount().doubleValue(), startConverted.formatted()));

        // Projections Month 1..3
        for (ThreeMonthCashFlowResponse.MonthlyCashFlowProjection p : cashFlow.projections()) {
            var conv = currencyConversionService.convertUsdForUser(p.endBalance(), username);
            points.add(new Point(p.monthName(), conv.convertedAmount().doubleValue(), conv.formatted()));
        }

        var reserveConv = currencyConversionService.convertUsdForUser(reserveThresholdUsd, username);
        double reserveVal = reserveConv.convertedAmount().doubleValue();

        // Find min and max for scaling
        double minVal = reserveVal;
        double maxVal = reserveVal;
        for (Point pt : points) {
            if (pt.val < minVal) minVal = pt.val;
            if (pt.val > maxVal) maxVal = pt.val;
        }

        double yMin = minVal < 0 ? minVal * 1.15 : 0.0;
        double yMax = maxVal > 0 ? maxVal * 1.18 : 1000.0;
        double range = yMax - yMin;
        if (range <= 0) range = 100.0;

        int svgWidth = 780;
        int svgHeight = 340;
        int plotLeft = 95;
        int plotRight = 730;
        int plotTop = 60;
        int plotBottom = 275;
        int plotWidth = plotRight - plotLeft;
        int plotHeight = plotBottom - plotTop;

        String chartTitle = translationService != null
                ? translationService.getMessage(lang, "chart.cashFlowProjection", "Cash-Flow Projection")
                : "Cash-Flow Projection";
        String projectedBalLabel = translationService != null
                ? translationService.getMessage(lang, "chart.projectedBalance", "Projected Balance")
                : "Projected Balance";
        String minReserveLabel = translationService != null
                ? translationService.getMessage(lang, "chart.minReserve", "Min Reserve")
                : "Min Reserve";
        String yAxisLabel = translationService != null
                ? translationService.getMessage(lang, "chart.balance", "Balance ({{symbol}})").replace("{{symbol}}", symbol)
                : "Balance (" + symbol + ")";

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
           .append(svgWidth).append(" ").append(svgHeight).append("\" ")
           .append("style=\"width: 100%; height: auto; max-width: ").append(svgWidth).append("px; display: block; margin: 0 auto; background: #0f172a; border: 1px solid rgba(255,255,255,0.1); border-radius: 10px; font-family: ").append(fontCss).append(";\">\n");

        svg.append("  <defs>\n")
           .append("    <style>text { font-family: ").append(fontCss).append("; }</style>\n")
           .append("    <linearGradient id=\"cfAreaGrad\" x1=\"0%\" y1=\"0%\" x2=\"0%\" y2=\"100%\">\n")
           .append("      <stop offset=\"0%\" stop-color=\"#6366f1\" stop-opacity=\"0.35\"/>\n")
           .append("      <stop offset=\"100%\" stop-color=\"#6366f1\" stop-opacity=\"0.02\"/>\n")
           .append("    </linearGradient>\n")
           .append("  </defs>\n");

        // Title
        svg.append("  <text x=\"").append(plotLeft).append("\" y=\"32\" fill=\"#f1f5f9\" font-size=\"16\" font-weight=\"800\">").append(chartTitle).append("</text>\n");

        // Legend (Top Right)
        int legRight = plotRight;
        svg.append("  <line x1=\"").append(legRight - 280).append("\" y1=\"28\" x2=\"").append(legRight - 260).append("\" y2=\"28\" stroke=\"#6366f1\" stroke-width=\"3\"/>\n")
           .append("  <circle cx=\"").append(legRight - 270).append("\" cy=\"28\" r=\"3.5\" fill=\"#818cf8\" stroke=\"#ffffff\" stroke-width=\"1.5\"/>\n")
           .append("  <text x=\"").append(legRight - 252).append("\" y=\"32\" fill=\"#cbd5e1\" font-size=\"11\" font-weight=\"600\">").append(projectedBalLabel).append("</text>\n");

        svg.append("  <line x1=\"").append(legRight - 135).append("\" y1=\"28\" x2=\"").append(legRight - 110).append("\" y2=\"28\" stroke=\"#ef4444\" stroke-width=\"2\" stroke-dasharray=\"5,3\"/>\n")
           .append("  <text x=\"").append(legRight - 102).append("\" y=\"32\" fill=\"#ef4444\" font-size=\"11\" font-weight=\"600\">").append(minReserveLabel).append("</text>\n");

        // Y-axis Title (Rotated)
        svg.append("  <text x=\"24\" y=\"").append(plotTop + plotHeight / 2).append("\" fill=\"#94a3b8\" font-size=\"12\" font-weight=\"600\" text-anchor=\"middle\" transform=\"rotate(-90 24 ").append(plotTop + plotHeight / 2).append(")\">").append(yAxisLabel).append("</text>\n");

        // Gridlines (5 horizontal ticks)
        for (int i = 0; i <= 4; i++) {
            double tickVal = yMin + (range * i / 4.0);
            double tickY = plotBottom - ((tickVal - yMin) / range * plotHeight);
            svg.append("  <line x1=\"").append(plotLeft).append("\" y1=\"").append(String.format("%.1f", tickY)).append("\" x2=\"").append(plotRight).append("\" y2=\"").append(String.format("%.1f", tickY)).append("\" stroke=\"rgba(255,255,255,0.08)\" stroke-width=\"1\"/>\n");
            svg.append("  <text x=\"").append(plotLeft - 10).append("\" y=\"").append(String.format("%.1f", tickY + 4)).append("\" fill=\"#94a3b8\" font-size=\"11\" text-anchor=\"end\">")
               .append(symbol).append(String.format("%,.0f", tickVal)).append("</text>\n");
        }

        // Horizontal Red Dashed Reference Line for Minimum Reserve Threshold
        double reserveY = plotBottom - ((reserveVal - yMin) / range * plotHeight);
        if (reserveY >= plotTop - 10 && reserveY <= plotBottom + 10) {
            svg.append("  <line x1=\"").append(plotLeft).append("\" y1=\"").append(String.format("%.1f", reserveY))
               .append("\" x2=\"").append(plotRight).append("\" y2=\"").append(String.format("%.1f", reserveY))
               .append("\" stroke=\"#ef4444\" stroke-width=\"2\" stroke-dasharray=\"6,4\"/>\n");
            svg.append("  <text x=\"").append(plotRight).append("\" y=\"").append(String.format("%.1f", reserveY - 6))
               .append("\" fill=\"#ef4444\" font-size=\"11\" font-weight=\"700\" text-anchor=\"end\">")
               .append(minReserveLabel).append(": ").append(reserveConv.formatted()).append("</text>\n");
        }

        // Compute coordinate arrays for points
        int n = points.size();
        double[] xCoords = new double[n];
        double[] yCoords = new double[n];
        for (int i = 0; i < n; i++) {
            xCoords[i] = plotLeft + (i * ((double) plotWidth / (n - 1)));
            yCoords[i] = plotBottom - ((points.get(i).val - yMin) / range * plotHeight);
        }

        // Polygon area under line
        svg.append("  <polygon points=\"");
        svg.append(String.format("%.1f,%.1f ", xCoords[0], (double) plotBottom));
        for (int i = 0; i < n; i++) {
            svg.append(String.format("%.1f,%.1f ", xCoords[i], yCoords[i]));
        }
        svg.append(String.format("%.1f,%.1f", xCoords[n - 1], (double) plotBottom));
        svg.append("\" fill=\"url(#cfAreaGrad)\"/>\n");

        // Solid projected balance line
        svg.append("  <polyline points=\"");
        for (int i = 0; i < n; i++) {
            svg.append(String.format("%.1f,%.1f ", xCoords[i], yCoords[i]));
        }
        svg.append("\" fill=\"none\" stroke=\"#6366f1\" stroke-width=\"3.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>\n");

        // Markers, exact balance value labels, and X-axis labels
        for (int i = 0; i < n; i++) {
            Point pt = points.get(i);
            double cx = xCoords[i];
            double cy = yCoords[i];

            // Point marker
            svg.append("  <circle cx=\"").append(String.format("%.1f", cx)).append("\" cy=\"").append(String.format("%.1f", cy)).append("\" r=\"5.5\" fill=\"#818cf8\" stroke=\"#ffffff\" stroke-width=\"2\"/>\n");

            // Exact value above marker
            svg.append("  <text x=\"").append(String.format("%.1f", cx)).append("\" y=\"").append(String.format("%.1f", cy - 12))
               .append("\" fill=\"#f8fafc\" font-size=\"12\" font-weight=\"700\" text-anchor=\"middle\">")
               .append(pt.formatted).append("</text>\n");

            // X-axis month label
            svg.append("  <text x=\"").append(String.format("%.1f", cx)).append("\" y=\"").append(plotBottom + 24)
               .append("\" fill=\"#cbd5e1\" font-size=\"12\" font-weight=\"600\" text-anchor=\"middle\">")
               .append(pt.label).append("</text>\n");
        }

        svg.append("</svg>\n");
        return svg.toString();
    }

    public String generateScenarioComparisonChartSvg(
            MultiScenarioWhatIfResponse activeSimulation,
            String username,
            CurrencyConversionService currencyConversionService
    ) {
        return generateScenarioComparisonChartSvg(activeSimulation, username, currencyConversionService, "en", null);
    }

    public String generateScenarioComparisonChartSvg(
            MultiScenarioWhatIfResponse activeSimulation,
            String username,
            CurrencyConversionService currencyConversionService,
            String lang,
            ReportTranslationService translationService
    ) {
        log.info("[CHART-SERVICE] generateScenarioComparisonChartSvg invoked for user '{}', lang='{}'", username, lang);
        if (activeSimulation == null) {
            log.warn("[CHART-SERVICE] activeSimulation is null for user '{}', returning empty string", username);
            return "";
        }

        var scriptInfo = translationService != null ? translationService.getScriptInfo(lang) : null;
        String fontCss = scriptInfo != null ? scriptInfo.fontCss() : "'Segoe UI', -apple-system, sans-serif";

        var currInfo = currencyConversionService.convertUsdForUser(BigDecimal.ONE, username);
        String symbol = currInfo.currencySymbol();

        record BarItem(String label, String subLabel, double val, String formatted, String color) {}
        List<BarItem> bars = new ArrayList<>();

        String buyNowTitle = translationService != null
                ? translationService.getMessage(lang, "chart.buyNow", "Buy Now") : "Buy Now";
        String waitTitle = translationService != null
                ? translationService.getMessage(lang, "chart.wait", "Wait") : "Wait";
        String emiTitle = translationService != null
                ? translationService.getMessage(lang, "chart.emi", "EMI") : "EMI";

        String immSub = translationService != null
                ? translationService.getMessage(lang, "chart.immediatePurchase", "Immediate Purchase") : "Immediate Purchase";
        String defSub = translationService != null
                ? translationService.getMessage(lang, "chart.deferredPurchase", "Deferred Purchase") : "Deferred Purchase";
        String instSub = translationService != null
                ? translationService.getMessage(lang, "chart.installmentPlan", "Installment Plan") : "Installment Plan";

        // 1. Buy Now
        var buyNowBal = activeSimulation.buyNow().projectionState().month3EndingBalance();
        var buyNowConv = currencyConversionService.convertUsdForUser(buyNowBal, username);
        bars.add(new BarItem("⚡ " + buyNowTitle, immSub, buyNowConv.convertedAmount().doubleValue(), buyNowConv.formatted(), "#f87171"));

        // 2. Wait
        var waitBal = activeSimulation.buyInNMonths().projectionState().month3EndingBalance();
        var waitConv = currencyConversionService.convertUsdForUser(waitBal, username);
        bars.add(new BarItem("⏳ " + waitTitle + " (" + activeSimulation.delayMonths() + "m)", defSub, waitConv.convertedAmount().doubleValue(), waitConv.formatted(), "#38bdf8"));

        // 3. EMI
        var emiBal = activeSimulation.buyViaEMI().projectionState().month3EndingBalance();
        var emiConv = currencyConversionService.convertUsdForUser(emiBal, username);
        bars.add(new BarItem("💳 " + emiTitle + " (" + activeSimulation.emiTenureMonths() + "m)", instSub, emiConv.convertedAmount().doubleValue(), emiConv.formatted(), "#a855f7"));

        double minVal = 0.0;
        double maxVal = 0.0;
        for (BarItem b : bars) {
            if (b.val < minVal) minVal = b.val;
            if (b.val > maxVal) maxVal = b.val;
        }

        double yMin = minVal < 0 ? minVal * 1.15 : 0.0;
        double yMax = maxVal > 0 ? maxVal * 1.25 : 1000.0;
        double range = yMax - yMin;
        if (range <= 0) range = 100.0;

        int svgWidth = 780;
        int svgHeight = 340;
        int plotLeft = 95;
        int plotRight = 730;
        int plotTop = 65;
        int plotBottom = 265;
        int plotWidth = plotRight - plotLeft;
        int plotHeight = plotBottom - plotTop;

        String chartTitle = translationService != null
                ? translationService.getMessage(lang, "chart.scenarioComparison", "Scenario Comparison")
                : "Scenario Comparison";
        String yAxisLabel = translationService != null
                ? translationService.getMessage(lang, "chart.month3EndingBalance", "Projected Balance ({{symbol}})").replace("{{symbol}}", symbol)
                : "Projected Balance (" + symbol + ")";

        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ")
           .append(svgWidth).append(" ").append(svgHeight).append("\" ")
           .append("style=\"width: 100%; height: auto; max-width: ").append(svgWidth).append("px; display: block; margin: 0 auto; background: #0f172a; border: 1px solid rgba(255,255,255,0.1); border-radius: 10px; font-family: ").append(fontCss).append(";\">\n");

        svg.append("  <defs>\n")
           .append("    <style>text { font-family: ").append(fontCss).append("; }</style>\n")
           .append("  </defs>\n");

        // Title & Description
        var itemCostConv = currencyConversionService.convertUsdForUser(activeSimulation.totalAmount(), username);
        svg.append("  <text x=\"").append(plotLeft).append("\" y=\"30\" fill=\"#f1f5f9\" font-size=\"16\" font-weight=\"800\">").append(chartTitle).append("</text>\n");
        svg.append("  <text x=\"").append(plotLeft).append("\" y=\"48\" fill=\"#94a3b8\" font-size=\"11.5\">Simulated: \"")
           .append(activeSimulation.scenarioDescription()).append("\" (")
           .append(itemCostConv.formatted()).append(")</text>\n");

        // Y-axis Title (Rotated)
        svg.append("  <text x=\"24\" y=\"").append(plotTop + plotHeight / 2).append("\" fill=\"#94a3b8\" font-size=\"12\" font-weight=\"600\" text-anchor=\"middle\" transform=\"rotate(-90 24 ").append(plotTop + plotHeight / 2).append(")\">").append(yAxisLabel).append("</text>\n");

        // Gridlines (5 horizontal ticks)
        for (int i = 0; i <= 4; i++) {
            double tickVal = yMin + (range * i / 4.0);
            double tickY = plotBottom - ((tickVal - yMin) / range * plotHeight);
            svg.append("  <line x1=\"").append(plotLeft).append("\" y1=\"").append(String.format("%.1f", tickY)).append("\" x2=\"").append(plotRight).append("\" y2=\"").append(String.format("%.1f", tickY)).append("\" stroke=\"rgba(255,255,255,0.08)\" stroke-width=\"1\"/>\n");
            svg.append("  <text x=\"").append(plotLeft - 10).append("\" y=\"").append(String.format("%.1f", tickY + 4)).append("\" fill=\"#94a3b8\" font-size=\"11\" text-anchor=\"end\">")
               .append(symbol).append(String.format("%,.0f", tickVal)).append("</text>\n");
        }

        // Render 3 Bars
        int barWidth = 110;
        int[] barCenters = new int[]{
            plotLeft + plotWidth / 6,
            plotLeft + plotWidth / 2,
            plotLeft + (5 * plotWidth) / 6
        };

        for (int i = 0; i < bars.size(); i++) {
            BarItem bar = bars.get(i);
            int cx = barCenters[i];
            double barH = ((bar.val - yMin) / range) * plotHeight;
            if (barH < 4) barH = 4;
            double barY = plotBottom - barH;
            double barX = cx - (barWidth / 2.0);

            // Bar rectangle
            svg.append("  <rect x=\"").append(String.format("%.1f", barX)).append("\" y=\"").append(String.format("%.1f", barY))
               .append("\" width=\"").append(barWidth).append("\" height=\"").append(String.format("%.1f", barH))
               .append("\" rx=\"6\" fill=\"").append(bar.color).append("\" fill-opacity=\"0.9\" stroke=\"").append(bar.color).append("\" stroke-width=\"1\"/>\n");

            // Exact value labeled on top of each bar
            svg.append("  <text x=\"").append(cx).append("\" y=\"").append(String.format("%.1f", barY - 10))
               .append("\" fill=\"#ffffff\" font-size=\"13\" font-weight=\"800\" text-anchor=\"middle\">")
               .append(bar.formatted).append("</text>\n");

            // Scenario title below bar
            svg.append("  <text x=\"").append(cx).append("\" y=\"").append(plotBottom + 24)
               .append("\" fill=\"#f1f5f9\" font-size=\"13\" font-weight=\"700\" text-anchor=\"middle\">")
               .append(bar.label).append("</text>\n");

            // Subtitle below scenario title
            svg.append("  <text x=\"").append(cx).append("\" y=\"").append(plotBottom + 40)
               .append("\" fill=\"#94a3b8\" font-size=\"11\" text-anchor=\"middle\">")
               .append(bar.subLabel).append("</text>\n");
        }

        svg.append("</svg>\n");
        return svg.toString();
    }
}
