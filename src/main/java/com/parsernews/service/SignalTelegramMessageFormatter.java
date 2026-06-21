package com.parsernews.service;

import com.parsernews.persistence.SecFilingEntity;
import com.parsernews.web.ArticleController;
import org.springframework.stereotype.Service;

@Service
public class SignalTelegramMessageFormatter {
    public String formatRss(ArticleController.ArticleListResponse article) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "RSS M&A Signal");
        appendLine(builder, article.title());
        appendLine(builder, "");
        appendLine(builder, "Source: " + value(article.source()) + hostSuffix(article.host()));
        appendLine(builder, "Strength: " + article.candidateStrength() + " / score " + article.candidateScore());
        appendLine(builder, "Verdict: " + article.reviewVerdict());
        appendLine(builder, "Relevance: " + article.dealRelevance());
        appendLine(builder, "Tradability: " + article.tradability());
        appendLine(builder, "Stage: " + article.dealStage() + " / " + article.dealTiming());
        appendLine(builder, "Buyer: " + value(article.buyerCompany()));
        appendLine(builder, "Target: " + value(article.targetCompany()));
        appendLine(builder, "Offer: " + offerText(article));
        appendLine(builder, "Alert eligible: " + (article.alertEligible() ? "yes" : "no"));
        if (!article.alertEligible()) {
            appendLine(builder, "Alert reason: " + value(article.alertReason()));
        }
        appendLine(builder, "Manual review: " + article.manualReviewStatus() + reasonSuffix(article.manualReviewReason()));
        if (article.reviewSummary() != null && !article.reviewSummary().isBlank()) {
            appendLine(builder, "");
            appendLine(builder, article.reviewSummary());
        }
        appendLine(builder, "");
        appendLine(builder, article.url());
        return builder.toString().trim();
    }

    public String formatSec(SecFilingEntity filing) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "SEC M&A Signal");
        appendLine(builder, value(filing.getCompanyName()) + " " + value(filing.getForm()).trim());
        appendLine(builder, "");
        appendLine(builder, "Company: " + value(filing.getCompanyName()));
        appendLine(builder, "CIK: " + value(filing.getCik()));
        appendLine(builder, "Form: " + value(filing.getForm()));
        appendLine(builder, "Filing date: " + value(filing.getFilingDate()));
        appendLine(builder, "Signal: " + filing.getSecSignalType());
        appendLine(builder, "Priority: " + filing.getSecSignalPriority());
        appendLine(builder, "Summary: " + firstNonBlank(filing.getSecSignalSummary(), filing.getSignalReason()));
        if (filing.getSecSignalWarnings() != null && !filing.getSecSignalWarnings().isBlank()) {
            appendLine(builder, "Warnings: " + filing.getSecSignalWarnings());
        }
        appendLine(builder, "Manual review: " + filing.getManualReviewStatus() + reasonSuffix(filing.getManualReviewReason()));
        appendLine(builder, "");
        appendLine(builder, firstNonBlank(filing.getDocumentUrl(), filing.getFilingUrl()));
        return builder.toString().trim();
    }

    private String offerText(ArticleController.ArticleListResponse article) {
        if (article.offerPrice() == null) {
            return "Unknown";
        }
        return value(article.offerCurrency()) + article.offerPrice() + " / " + article.paymentType();
    }

    private String hostSuffix(String host) {
        return host == null || host.isBlank() ? "" : " / " + host;
    }

    private String reasonSuffix(Object reason) {
        return reason == null ? "" : " / " + reason;
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? value(fallback) : first;
    }

    private String value(Object value) {
        return value == null || value.toString().isBlank() ? "Unknown" : value.toString();
    }

    private void appendLine(StringBuilder builder, String text) {
        builder.append(text == null ? "" : text).append('\n');
    }
}
