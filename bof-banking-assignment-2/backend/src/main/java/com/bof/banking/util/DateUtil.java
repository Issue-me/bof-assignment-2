package com.bof.banking.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for date operations.
 */
public final class DateUtil {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DISPLAY_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    private DateUtil() {
        // Utility class - no instantiation
    }

    /**
     * Handles format date.
     * @param date the date or time value used by this operation.
     * @return the resulting text value.
     */
    public static String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FORMATTER) : null;
    }

    /**
     * Handles format date time.
     * @param dateTime the date or time value used by this operation.
     * @return the resulting text value.
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DATETIME_FORMATTER) : null;
    }

    /**
     * Handles format display date.
     * @param date the date or time value used by this operation.
     * @return the resulting text value.
     */
    public static String formatDisplayDate(LocalDate date) {
        return date != null ? date.format(DISPLAY_DATE_FORMATTER) : null;
    }

    /**
     * Handles format display date time.
     * @param dateTime the date or time value used by this operation.
     * @return the resulting text value.
     */
    public static String formatDisplayDateTime(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.format(DISPLAY_DATETIME_FORMATTER) : null;
    }

    /**
     * Handles parse date.
     * @param dateString the date or time value used by this operation.
     * @return the result of the operation.
     */
    public static LocalDate parseDate(String dateString) {
        return dateString != null ? LocalDate.parse(dateString, DATE_FORMATTER) : null;
    }

    /**
     * Handles parse date time.
     * @param dateTimeString the date or time value used by this operation.
     * @return the result of the operation.
     */
    public static LocalDateTime parseDateTime(String dateTimeString) {
        return dateTimeString != null ? LocalDateTime.parse(dateTimeString, DATETIME_FORMATTER) : null;
    }

    /**
     * Returns start of month data.
     * @return the result of the operation.
     */
    public static LocalDate getStartOfMonth() {
        return LocalDate.now().withDayOfMonth(1);
    }

    /**
     * Returns end of month data.
     * @return the result of the operation.
     */
    public static LocalDate getEndOfMonth() {
        return LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
    }

    /**
     * Returns start of day data.
     * @param date the date or time value used by this operation.
     * @return the result of the operation.
     */
    public static LocalDateTime getStartOfDay(LocalDate date) {
        return date.atStartOfDay();
    }

    /**
     * Returns end of day data.
     * @param date the date or time value used by this operation.
     * @return the result of the operation.
     */
    public static LocalDateTime getEndOfDay(LocalDate date) {
        return date.atTime(23, 59, 59);
    }
}
