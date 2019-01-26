package com.stackoverflow.weiping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.HashSet;

class Flight {

    private final String from;
    private final String to;
    private final String carrier;
    private final String flightNumber;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final LocalTime departureTime;
    private final LocalTime arrivalTime;
    private final boolean overnight;
    private final HashSet<DayOfWeek> days;

    Flight(final String from, final String to, final String carrier, final String flightNumber,
           final LocalDate startDate, final LocalDate endDate,
           final LocalTime departureTime, final LocalTime arrivalTime,
           final boolean overnight, final DayOfWeek... days) {
        this.from = from;
        this.to = to;
        this.carrier = carrier;
        this.flightNumber = flightNumber;
        this.startDate = startDate;
        this.endDate = endDate;
        this.departureTime = departureTime;
        this.arrivalTime = arrivalTime;
        this.overnight = overnight;
        this.days = new HashSet<>(Arrays.asList(days));
    }

    String getFrom() {
        return from;
    }

    String getTo() {
        return to;
    }

    String getCarrier() {
        return carrier;
    }

    String getFlightNumber() {
        return flightNumber;
    }

    LocalDate getStartDate() {
        return startDate;
    }

    LocalDate getEndDate() {
        return endDate;
    }

    LocalTime getDepartureTime() {
        return departureTime;
    }

    LocalTime getArrivalTime() {
        return arrivalTime;
    }

    boolean isOvernight() {
        return overnight;
    }

    boolean flyingOnWeekday(final DayOfWeek dayOfWeek) {
        return days.contains(dayOfWeek);
    }
}
