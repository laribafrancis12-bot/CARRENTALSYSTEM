package com.carrentalsystem.carsystem;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Metro Manila number coding (MMDA's Unified Vehicular Volume Reduction Program).
 *
 * The last digit of the plate decides the day the car is banned from the major roads:
 * 1-2 Monday, 3-4 Tuesday, 5-6 Wednesday, 7-8 Thursday, 9-0 Friday.
 * The ban applies from 7:00 to 10:00 AM and from 5:00 to 8:00 PM, Monday to Friday.
 * It is suspended on weekends and on holidays.
 *
 * Holidays that fall on the same date every year are included below. Holidays whose date
 * changes every year (Holy Week, Eid and others) are not, and MMDA can also suspend coding
 * on other days, so staff should still check MMDA announcements.
 */
public final class NumberCoding {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH);

    private NumberCoding() {}

    /** The last digit of a plate such as "ABC 1234" (4), or -1 if the plate does not end in a number. */
    public static int lastDigit(String plate) {
        if (plate == null) return -1;
        String p = plate.trim();
        if (p.isEmpty()) return -1;
        char last = p.charAt(p.length() - 1);
        return last >= '0' && last <= '9' ? last - '0' : -1;
    }

    /** The weekday a plate ending in this digit is banned on. */
    public static DayOfWeek codingDay(int digit) {
        if (digit < 0 || digit > 9) throw new IllegalArgumentException("Plate digit must be between 0 and 9");
        return switch (digit) {
            case 1, 2 -> DayOfWeek.MONDAY;
            case 3, 4 -> DayOfWeek.TUESDAY;
            case 5, 6 -> DayOfWeek.WEDNESDAY;
            case 7, 8 -> DayOfWeek.THURSDAY;
            default -> DayOfWeek.FRIDAY;   // 9 and 0
        };
    }

    public static boolean isCoding(String plate, LocalDate date) {
        int digit = lastDigit(plate);
        if (digit < 0 || date == null) return false;
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return false;
        if (isHoliday(date)) return false;
        return codingDay(digit) == day;
    }

    /** The message to show when the car is under coding on that date, or null when it is not. */
    public static String warning(String plate, LocalDate date) {
        if (!isCoding(plate, date)) return null;
        DayOfWeek day = codingDay(lastDigit(plate));
        String dayName = day.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        return "This car's plate ends in " + lastDigit(plate) + ", so it is under number coding on "
                + dayName + "s (7:00-10:00 AM and 5:00-8:00 PM in Metro Manila), and "
                + date.format(DAY_FORMAT) + " is one. Choose another car or another rent date.";
    }

    /** Holidays on a fixed date every year, plus National Heroes Day (last Monday of August). */
    private static boolean isHoliday(LocalDate date) {
        Month m = date.getMonth();
        int d = date.getDayOfMonth();
        switch (m) {
            case JANUARY:   if (d == 1) return true; break;
            case APRIL:     if (d == 9) return true; break;
            case MAY:       if (d == 1) return true; break;
            case JUNE:      if (d == 12) return true; break;
            case AUGUST:
                if (d == 21) return true;
                if (date.getDayOfWeek() == DayOfWeek.MONDAY && d + 7 > 31) return true;   // last Monday
                break;
            case NOVEMBER:  if (d == 1 || d == 30) return true; break;
            case DECEMBER:  if (d == 8 || d == 24 || d == 25 || d == 30 || d == 31) return true; break;
            default: break;
        }
        return false;
    }
}
