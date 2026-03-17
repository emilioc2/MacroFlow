package com.macroflow.dto;

/**
 * Response body for DELETE /auth/account.
 * Confirms that deletion has been scheduled; actual removal happens within 30 days.
 */
public record AccountDeletionResponse(String message) {}
