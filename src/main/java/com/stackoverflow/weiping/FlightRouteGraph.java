package com.stackoverflow.weiping;

import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static com.stackoverflow.weiping.util.TimeUtil.daysBetween;
import static com.stackoverflow.weiping.util.TimeUtil.toLocalTime;
import static com.stackoverflow.weiping.util.TimeUtil.toMinutes;

class FlightRouteGraph {
    
    private static Flight[] SAMPLE_FLIGHTS = new Flight[]{
            new Flight("HNL", "PDX", "AA", "100", LocalDate.of(2019, 1, 23), LocalDate.of(2019, 3, 20), LocalTime.of(8, 0), LocalTime.of(13, 0), false, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            new Flight("HNL", "PDX", "AA", "201", LocalDate.of(2019, 1, 23), LocalDate.of(2019, 3, 20), LocalTime.of(8, 0), LocalTime.of(13, 0), false, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            new Flight("PDX", "LHR", "BA", "100", LocalDate.of(2019, 1, 31), LocalDate.of(2019, 3, 5), LocalTime.of(13, 30), LocalTime.of(23, 0), false, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            new Flight("PDX", "LHR", "BA", "201", LocalDate.of(2019, 2, 5), LocalDate.of(2019, 3, 17), LocalTime.of(13, 30), LocalTime.of(23, 0), false, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            new Flight("PDX", "LHR", "BA", "202", LocalDate.of(2019, 2, 5), LocalDate.of(2019, 3, 17), LocalTime.of(16, 0), LocalTime.of(2, 0), true, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            new Flight("PDX", "LHR", "BA", "203", LocalDate.of(2019, 2, 5), LocalDate.of(2019, 3, 17), LocalTime.of(16, 0), LocalTime.of(2, 0), true, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            new Flight("ORD", "PDX", "CC", "66", LocalDate.of(2019, 8, 11), LocalDate.of(2019, 12, 11), LocalTime.of(6, 0), LocalTime.of(12, 0), false, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            new Flight("ORD", "LAX", "CC", "76", LocalDate.of(2019, 8, 11), LocalDate.of(2019, 12, 11), LocalTime.of(6, 0), LocalTime.of(12, 0), false, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
            new Flight("LAX", "CAN", "CC", "12", LocalDate.of(2019, 3, 11), LocalDate.of(2019, 12, 24), LocalTime.of(15, 0), LocalTime.of(5, 0), true, DayOfWeek.TUESDAY, DayOfWeek.SATURDAY),
            new Flight("PDX", "CAN", "CC", "22", LocalDate.of(2019, 3, 11), LocalDate.of(2019, 12, 24), LocalTime.of(15, 0), LocalTime.of(6, 0), true, DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)
    };

    private static Stream<Flight> sampleFlights() {
        return Arrays.stream(SAMPLE_FLIGHTS);
    }

    static Graph createSampleGraph() {

        final Graph graph = TinkerGraph.open();

        // create airport vertices
        sampleFlights().
                flatMap(flight -> Stream.of(flight.getFrom(), flight.getTo())).distinct().
                forEach(airport -> graph.addVertex(T.id, airport, T.label, "airport", "name", airport));

        // create flight vertices and edges between airports and flights
        sampleFlights().forEach(flight -> {

            final int departure = toMinutes(flight.getDepartureTime());
            final int arrival = toMinutes(flight.getArrivalTime()) + (flight.isOvernight() ? 1 : 0) * 24 * 60;

            for (int dow = 1; dow <= 7; dow++) {

                if (flight.flyingOnWeekday(DayOfWeek.of(dow))) {

                    final String id = String.join("-", flight.getCarrier(), flight.getFlightNumber(), Integer.toString(dow));
                    final Vertex fv = graph.addVertex(T.id, id, T.label, "flight",
                            "carrier", flight.getCarrier(),
                            "flightNumber", flight.getFlightNumber(),
                            "departure", departure,
                            "duration", arrival - departure,
                            "overnight", flight.isOvernight(),
                            "dayOfWeek", dow,
                            "origin", flight.getFrom(),     // denormalize origin and destination to
                            "destination", flight.getTo()); // improve query/filter performance

                    fv.addEdge("from", graph.vertices(flight.getFrom()).next(),
                            "start", flight.getStartDate().toEpochDay(),
                            "end", flight.getEndDate().toEpochDay(),
                            "dayOfWeek", dow,
                            "departure", departure);
                    fv.addEdge("to", graph.vertices(flight.getTo()).next());
                }
            }
        });

        computeLayovers(graph);

        return graph;
    }

    private static void computeLayovers(final Graph graph) {

        final GraphTraversalSource g = graph.traversal();

        g.V().hasLabel("flight")
                .match(__.as("previousFlight").out("to").in("from").as("nextFlight"))
                .select("previousFlight","nextFlight")
                    .by(__.project("id","dow", "dep", "dur")
                            .by(T.id)
                            .by("dayOfWeek")
                            .by("departure")
                            .by("duration"))
                .by(__.project("id", "dow", "dep", "dst")
                        .by(T.id)
                        .by("dayOfWeek")
                        .by("departure")
                        .by("destination"))
                .forEachRemaining(m -> {

            final Map prev = (Map) m.get("previousFlight");
            final Map next = (Map) m.get("nextFlight");
            final int layoverTime = calculateLayoverTime(
                    DayOfWeek.of((int) prev.get("dow")), (int) prev.get("dep"), (int) prev.get("dur"),
                    DayOfWeek.of((int) next.get("dow")), (int) next.get("dep"));

            graph.vertices(prev.get("id")).next()
                    .addEdge("next", graph.vertices(next.get("id")).next(),
                            "layover", layoverTime,
                            "destination", next.get("dst"));  // denormalize destination to improve filter performance
        });
    }

    private static int calculateLayoverTime(final DayOfWeek dayOfPreviousDeparture,
                                            final int timeOfPreviousDeparture, final int duration,
                                            final DayOfWeek dayOfNextDeparture, final int timeOfNextDeparture) {
        final LocalTime timeOfArrival = toLocalTime(timeOfPreviousDeparture).plusMinutes(duration);
        final DayOfWeek dayOfArrival = dayOfPreviousDeparture.plus(timeOfPreviousDeparture + duration >= 24 * 60 ? 1 : 0);
        final int hours = daysBetween(dayOfArrival, dayOfNextDeparture) * 24 * 60 +
                (timeOfNextDeparture - toMinutes(timeOfArrival));
        return hours <= 0 ? 7 * 24 * 60 + hours : hours;
    }
}
