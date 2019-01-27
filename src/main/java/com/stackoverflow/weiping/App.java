package com.stackoverflow.weiping;

import org.apache.tinkerpop.gremlin.process.traversal.Operator;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Pop;
import org.apache.tinkerpop.gremlin.process.traversal.Scope;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Column;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.stackoverflow.weiping.util.TextUtil.*;
import static com.stackoverflow.weiping.util.TimeUtil.*;

public class App {

    public static void main(final String... args) {

        final GraphTraversalSource g = FlightRouteGraph.createSampleGraph().traversal();

        {
            // Route from HNL to LHR departing 2019-01-24 (Thursday) from 08:00:00 but no
            // later than 12:00:00 with a minimum connect time of 90 minutes
            final LocalDate travelDate = LocalDate.of(2019, 1, 24);
            final String origin = "HNL";
            final String destination = "LHR";
            final Iterator flights = findFlights(g, travelDate, origin, destination, 90,
                    toMinutes(LocalTime.of(8, 0)), toMinutes(LocalTime.of(12, 0)));

            printFlights(travelDate, origin, destination, flights);
        }
        {
            // Route from PDX to CAN departing 2019-03-19 (Tuesday) from 15:00:00 but no
            // later than 20:00 with a minimum connect time of 60 minutes
            final LocalDate travelDate = LocalDate.of(2019, 3, 19);
            final String origin = "PDX";
            final String destination = "CAN";
            final Iterator flights = findFlights(g, travelDate, origin, destination, 60,
                    toMinutes(LocalTime.of(15, 0)), toMinutes(LocalTime.of(20, 0)));

            printFlights(travelDate, origin, destination, flights);
        }
        {
            // Route from ORD to CAN departing 2019-08-20 (Tuesday) from 06:00:00 but no
            // later than 10:00 with a minimum connect time of 60 minutes and allow only stops via 'LAX'
            final LocalDate travelDate = LocalDate.of(2019, 8, 20);
            final String origin = "ORD";
            final String destination = "CAN";
            final Iterator flights = findFlights(g, travelDate, origin, destination, 60,
                    toMinutes(LocalTime.of(6, 0)), toMinutes(LocalTime.of(10, 0)),
                    "LAX");

            printFlights(travelDate, origin, destination, flights);
        }
    }

    private static void printFlights(final LocalDate travelDate, final String origin, final String destination,
                                     final Iterator results) {

        System.out.println(String.format("\n=== Flights from %s to %s on %s ===\n", origin, destination, travelDate));

        if (results.hasNext()) {

            final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("EEEE, yyyy-MM-dd HH:mm").withLocale(Locale.ENGLISH);
            final DateTimeFormatter TF = DateTimeFormatter.ofPattern("HH:mm").withLocale(Locale.ENGLISH);

            for (int i = 1; results.hasNext(); i++) {

                final Map result = (Map) results.next();
                final List routes = (List) result.get("routes");
                final List layovers = (List) result.get("layovers");
                final int numStops = routes.size() - 1;

                System.out.println(String.format("* Option %d (%s, %s)", i,
                        numStops == 0 ? "direct" : String.format("%d %s", numStops, numStops == 1 ? "stop" : "stops"),
                        durationToString((double) result.get("time"))));

                LocalDateTime dt = LocalDate.from(travelDate)
                        .atTime(toLocalTime((int) ((Map) routes.get(0)).get("depTime")));

                for (int j = 0; j <= numStops; j++) {

                    final Map route = (Map) routes.get(j);

                    System.out.println(String.format("  - %s --[%s]-> %s (%s to %s)",
                            route.get("depAirport"),
                            String.format("%s-%s", route.get("carrier"), route.get("num")),
                            route.get("arrAirport"),
                            dt.format(DTF), (dt = dt.plusMinutes((int) route.get("dur")))
                                    .format((boolean) route.get("o") ? DTF : TF)));

                    if (j < numStops) {
                        System.out.println(String.format("    (%s layover)", durationToString((int) layovers.get(j))));
                        dt = dt.plusMinutes((int) layovers.get(j));
                    } else {
                        System.out.println();
                    }
                }
            }
        } else {
            System.out.println ("* No flights found.");
        }
    }

    @SuppressWarnings("unchecked")
    private static GraphTraversal findFlights(final GraphTraversalSource g, final LocalDate travelDate,
                                              final String origin, final String destination,
                                              final int minLayover, final int departureMinTime, final int departureMaxTime,
                                              final String... layoverAirports) {

        final GraphTraversal traversal = g.V(origin)
                .inE("from")
                    .has("start", P.lte(travelDate.toEpochDay()))
                    .has("end", P.gte(travelDate.toEpochDay()))
                    .has("dayOfWeek", travelDate.getDayOfWeek().getValue())
                    .has("departure", P.gte(departureMinTime).and(P.lte(departureMaxTime)))
                .outV().as("flight");

        final GraphTraversal connectionTraversal =
                __.outE("next").has("layover", P.gte(minLayover))
                        .filter(__
                                .project("start", "end", "date")
                                    .by("start")
                                    .by("end")
                                    .by(__.select(Pop.last, "date"))
                                .where("date", P.gte("start").and(P.lte("end"))));

        if (layoverAirports.length > 0) {
            final P layoverFilter = P.eq(destination).or(P.within(layoverAirports));
            traversal.has("destination", layoverFilter);
            connectionTraversal.filter(__.has("destination", layoverFilter));
        }

        connectionTraversal
                .group()
                    .by("flight")
                .unfold().select(Column.values)
                .order(Scope.local)
                    .by("layover")
                .limit(Scope.local, 1);

        return traversal
                .sack(Operator.assign)
                    .by("duration")
                .group("m")
                    .by("destination")
                    .by(__.sack())
                .choose(__.values("overnight"))
                    .option(false, __.constant(travelDate.toEpochDay()))
                    .option(true, __.constant(travelDate.toEpochDay() + 1)).as("date")
                .select("flight")
                .until(__.has("destination", destination))
                    .repeat(__
                            .flatMap(connectionTraversal).as("connection")
                            .sack(Operator.sum)
                                .by("layover")
                            .inV().as("flight")
                            .map(__.union(
                                    __.select(Pop.last, "date"),
                                    __.has("overnight", true).constant(1)).sum()).as("date")
                            .select(Pop.last, "flight")
                            .sack(Operator.sum)
                                .by("duration")
                            // The next filter prevents the traversal from following routes that just hit an airport
                            // that was already reached in a shorter travel time. The version for >= TP 3.4.0 is a lot
                            // more efficient, but the old variant should still be worth it on larger graphs.
                            // Also note, that this is another way to prevent cyclic paths.
                            /* >= TP 3.4.0:
                            .not(__.select("m")
                                    .select(__.select(Pop.last, "flight").by("destination"))
                                    .project("a","b")
                                        .by()
                                        .by(__.sack())
                                    .where("a", P.gte("b")))*/
                            /* < TP 3.4.0: */
                            .not(__.values("destination").as("d")
                                    .select("m").unfold().as("kv")
                                    .select(Column.keys).where(P.eq("d"))
                                    .select("kv")
                                    .project("a","b")
                                        .by(Column.values)
                                        .by(__.sack())
                                    .where("a", P.lt("b")))
                            .group("m")
                                .by("destination")
                                .by(__.sack()))
                .project("routes", "layovers", "time")
                    .by(__.select(Pop.all, "flight")
                            .by(__.unfold()
                                    .project("depAirport", "depTime", "carrier", "num", "dur", "o", "arrAirport")
                                        .by("origin")
                                        .by("departure")
                                        .by("carrier")
                                        .by("flightNumber")
                                        .by("duration")
                                        .by("overnight")
                                        .by("destination")
                                    .fold()))
                    .by(__.coalesce(__.select(Pop.all, "connection")
                                        .by(__.unfold().values("layover").fold()),
                                    __.constant(new ArrayList<>())))
                    .by(__.sack().math("_/60"))
                .order()
                    .by(__.select("time"));
    }
}
