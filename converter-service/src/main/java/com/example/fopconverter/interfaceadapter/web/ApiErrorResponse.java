package com.example.fopconverter.interfaceadapter.web;

import java.time.Instant;

public record ApiErrorResponse(String errorCode, String message, Instant timestamp) {}