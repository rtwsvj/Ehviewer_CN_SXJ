/*
 * Copyright 2026 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.hippo.ehviewer.download;

import androidx.annotation.NonNull;
import com.hippo.ehviewer.client.data.GalleryInfo;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

/** Streaming, fail-closed parser for versioned JSON Lines and legacy download-list CSV. */
public final class DownloadCsvParser {

    public static final String EXPORT_HEADER = "ehviewer-download-jsonl-v1";
    public static final long MAX_TOTAL_BYTES = 16L * 1024L * 1024L;
    public static final int MAX_LINE_CHARS = 64 * 1024;
    public static final int MAX_RECORDS = 10_000;

    private static final int BUFFER_CHARS = 4096;

    private DownloadCsvParser() {}

    @NonNull
    public static String toExportLine(@NonNull GalleryInfo info) {
        return info.toJson().toString();
    }

    @NonNull
    public static Result parse(@NonNull InputStream input) throws IOException {
        return parse(input, MAX_TOTAL_BYTES, MAX_LINE_CHARS, MAX_RECORDS);
    }

    @NonNull
    static Result parse(@NonNull InputStream input, long maxTotalBytes, int maxLineChars,
            int maxRecords) throws IOException {
        if (maxTotalBytes <= 0 || maxLineChars <= 0 || maxRecords <= 0) {
            throw new IllegalArgumentException("CSV limits must be positive");
        }

        LimitedInputStream limited = new LimitedInputStream(input, maxTotalBytes);
        InputStreamReader reader = new InputStreamReader(limited,
                StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT));
        ParserState state = new ParserState(maxLineChars, maxRecords);
        char[] buffer = new char[BUFFER_CHARS];
        try {
            int count;
            while ((count = reader.read(buffer)) != -1) {
                for (int i = 0; i < count; i++) {
                    state.accept(buffer[i]);
                }
            }
            state.finish();
            return new Result(state.records);
        } catch (LimitExceededException e) {
            throw new ParseException(Reason.TOTAL_BYTES, 0,
                    "File exceeds the " + maxTotalBytes + " byte limit", e);
        } catch (CharacterCodingException e) {
            throw new ParseException(Reason.INVALID_UTF8, state.lineNumber + 1,
                    "Invalid UTF-8 near line " + (state.lineNumber + 1), e);
        }
    }

    public enum Reason {
        TOTAL_BYTES,
        LINE_LENGTH,
        RECORD_COUNT,
        MALFORMED_ROW,
        INVALID_UTF8
    }

    public static final class ParseException extends IOException {
        @NonNull
        public final Reason reason;
        public final int lineNumber;

        private ParseException(@NonNull Reason reason, int lineNumber, @NonNull String message) {
            super(message);
            this.reason = reason;
            this.lineNumber = lineNumber;
        }

        private ParseException(@NonNull Reason reason, int lineNumber, @NonNull String message,
                @NonNull Throwable cause) {
            super(message, cause);
            this.reason = reason;
            this.lineNumber = lineNumber;
        }
    }

    public static final class Result {
        @NonNull
        public final List<GalleryInfo> records;

        private Result(@NonNull List<GalleryInfo> records) {
            this.records = Collections.unmodifiableList(new ArrayList<>(records));
        }
    }

    private static final class ParserState {
        private final int maxLineChars;
        private final int maxRecords;
        private final StringBuilder line = new StringBuilder();
        private final List<GalleryInfo> records = new ArrayList<>();
        private int lineNumber;
        private boolean firstContent = true;
        private Format format = Format.UNKNOWN;

        private ParserState(int maxLineChars, int maxRecords) {
            this.maxLineChars = maxLineChars;
            this.maxRecords = maxRecords;
        }

        private void accept(char value) throws ParseException {
            if (value == '\n') {
                consumeLine();
                return;
            }
            if (line.length() >= maxLineChars) {
                throw new ParseException(Reason.LINE_LENGTH, lineNumber + 1,
                        "Line " + (lineNumber + 1) + " exceeds the " + maxLineChars
                                + " character limit");
            }
            line.append(value);
        }

        private void finish() throws ParseException {
            if (line.length() > 0) {
                consumeLine();
            }
        }

        private void consumeLine() throws ParseException {
            lineNumber++;
            int length = line.length();
            if (length > 0 && line.charAt(length - 1) == '\r') {
                line.setLength(length - 1);
            }
            String value = line.toString();
            line.setLength(0);
            if (value.isEmpty()) {
                return;
            }
            if (firstContent && value.charAt(0) == '\ufeff') {
                value = value.substring(1);
            }
            if (value.isEmpty()) {
                return;
            }

            if (firstContent) {
                firstContent = false;
                if (EXPORT_HEADER.equals(value)) {
                    format = Format.JSONL;
                    return;
                }
                if (value.startsWith(DownloadManager.DOWNLOAD_INFO_HEADER)) {
                    format = Format.LEGACY_CSV;
                    value = value.substring(DownloadManager.DOWNLOAD_INFO_HEADER.length());
                    if (value.isEmpty()) {
                        return;
                    }
                } else {
                    if (value.charAt(0) == '{') {
                        // JSON Lines is intentionally versioned. Treating arbitrary JSON as an
                        // export would let a sparse object silently create a corrupt download.
                        throw malformedRow(null);
                    }
                    format = Format.LEGACY_CSV;
                }
            }

            if (records.size() >= maxRecords) {
                throw new ParseException(Reason.RECORD_COUNT, lineNumber,
                        "File exceeds the " + maxRecords + " record limit");
            }

            GalleryInfo info;
            try {
                if (format == Format.JSONL) {
                    info = parseJsonRecord(value);
                } else {
                    info = GalleryInfo.fromCSV(value);
                }
            } catch (JSONException | RuntimeException malformed) {
                throw malformedRow(malformed);
            }
            if (info == null || info.gid <= 0 || info.token == null || info.token.isEmpty()
                    || info.pages < 0) {
                throw malformedRow(null);
            }
            records.add(info);
        }

        @NonNull
        private GalleryInfo parseJsonRecord(@NonNull String value) throws JSONException {
            JSONObject object = new JSONObject(value);
            Object gid = object.opt("gid");
            Object pages = object.opt("pages");
            Object token = object.opt("token");
            Object title = object.opt("title");
            if (!isPositiveIntegral(gid, Long.MAX_VALUE)
                    || !isNonNegativeIntegral(pages, Integer.MAX_VALUE)
                    || !isNonEmptyString(token)
                    || !isNonEmptyString(title)
                    || (object.has("thumb") && !object.isNull("thumb")
                    && !(object.opt("thumb") instanceof String))) {
                throw new JSONException("Missing or invalid required download fields");
            }
            return GalleryInfo.galleryInfoFromJson(object);
        }

        private static boolean isPositiveIntegral(Object value, long maximum) {
            if (!(value instanceof Number)) {
                return false;
            }
            Number number = (Number) value;
            double asDouble = number.doubleValue();
            long asLong = number.longValue();
            return !Double.isNaN(asDouble) && !Double.isInfinite(asDouble) && asDouble == asLong
                    && asLong > 0L && asLong <= maximum;
        }

        private static boolean isNonNegativeIntegral(Object value, long maximum) {
            if (!(value instanceof Number)) {
                return false;
            }
            Number number = (Number) value;
            double asDouble = number.doubleValue();
            long asLong = number.longValue();
            return !Double.isNaN(asDouble) && !Double.isInfinite(asDouble) && asDouble == asLong
                    && asLong >= 0L && asLong <= maximum;
        }

        private static boolean isNonEmptyString(Object value) {
            return value instanceof String && !((String) value).trim().isEmpty();
        }

        private ParseException malformedRow(Throwable cause) {
            String message = "Malformed CSV record at line " + lineNumber;
            return cause == null
                    ? new ParseException(Reason.MALFORMED_ROW, lineNumber, message)
                    : new ParseException(Reason.MALFORMED_ROW, lineNumber, message, cause);
        }
    }

    private enum Format {
        UNKNOWN,
        JSONL,
        LEGACY_CSV
    }

    private static final class LimitedInputStream extends InputStream {
        private final InputStream delegate;
        private final long limit;
        private long consumed;

        private LimitedInputStream(@NonNull InputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            if (consumed >= limit) {
                int extra = delegate.read();
                if (extra == -1) {
                    return -1;
                }
                throw new LimitExceededException();
            }
            int value = delegate.read();
            if (value != -1) {
                consumed++;
            }
            return value;
        }

        @Override
        public int read(@NonNull byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            if (consumed >= limit) {
                int extra = delegate.read();
                if (extra == -1) {
                    return -1;
                }
                throw new LimitExceededException();
            }
            int allowed = (int) Math.min((long) length, limit - consumed);
            int count = delegate.read(buffer, offset, allowed);
            if (count > 0) {
                consumed += count;
            }
            return count;
        }
    }

    private static final class LimitExceededException extends IOException {}
}
