package com.conveyal.r5.analyst.scenario;

import com.conveyal.r5.transit.TransportNetwork;
import com.conveyal.r5.transit.TripPattern;
import com.conveyal.r5.transit.TripSchedule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static com.conveyal.r5.analyst.scenario.FakeGraph.buildNetwork;
import static com.conveyal.r5.analyst.scenario.FakeGraph.set;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Test adding stops (rerouting)
 */
public class AddStopsTest {
    public TransportNetwork network;
    public long checksum;

    @Before
    public void setUp () {
        network = buildNetwork(FakeGraph.TransitNetwork.SINGLE_LINE);
        checksum = network.checksum();
    }

    /** Test rerouting a route in the middle */
    @Test
    public void testRerouteInMiddle () {
        AddStops as = new AddStops();
        // skip s3, insert s5
        as.fromStop = "SINGLE_LINE:s2";
        as.toStop = "SINGLE_LINE:s4";
        as.stops = Arrays.asList(new StopSpec("SINGLE_LINE:s5"));
        as.dwellTimes = new int[] { 20, 40, 50 };
        as.hopTimes = new int[] { 60, 70 };
        as.routes = set("SINGLE_LINE:route");

        Scenario scenario = new Scenario();
        scenario.modifications = Arrays.asList(as);

        TransportNetwork mod = scenario.applyToTransportNetwork(network);

        assertEquals(1, mod.transitLayer.tripPatterns.size());

        TripPattern pattern = mod.transitLayer.tripPatterns.get(0);

        // make sure the stops are in the right order
        assertEquals(4, pattern.stops.length);
        assertEquals("SINGLE_LINE:s1", mod.transitLayer.stopIdForIndex.get(pattern.stops[0]));
        assertEquals("SINGLE_LINE:s2", mod.transitLayer.stopIdForIndex.get(pattern.stops[1]));
        assertEquals("SINGLE_LINE:s5", mod.transitLayer.stopIdForIndex.get(pattern.stops[2]));
        assertEquals("SINGLE_LINE:s4", mod.transitLayer.stopIdForIndex.get(pattern.stops[3]));

        for (TripSchedule schedule : pattern.tripSchedules) {
            int[] a = IntStream.of(schedule.arrivals).map(time -> time - schedule.arrivals[0]).toArray();
            int[] d = IntStream.of(schedule.departures).map(time -> time - schedule.arrivals[0]).toArray();

            // slightly awkward, but make sure that the trip starts at the same time it did before
            assertEquals("SINGLE_LINE:trip" + schedule.arrivals[0], schedule.tripId);

            // confirm the times are correct. Note that first and last stop of original route don't have a dwell time
            // so this is no dwell at s1, 500 sec travel time (from FakeGraph) to s2, 20 sec dwell time at s2 (overwritten by modification),
            // 60 sec travel time to s5 (which replaces s3), 40 sec dwell at s5, 70 sec travel time to s4 (which is part
            // of the original route), and 50 sec dwell time at s4, replaces 0 from original modification
            assertArrayEquals(new int[] { 0, 500, 580, 690 }, a);
            assertArrayEquals(new int[] { 0, 520, 620, 740 }, d);
        }

        assertEquals(checksum, network.checksum());
    }

    /**
     * Test extending a route by inserting stops at the beginning, without removing any stops.
     * All stops are references to existing stops by ID, not newly created from coordinates.
     */
    @Test
    public void testExtendRouteAtBeginning () {
        AddStops as = new AddStops();
        // add s5 at beginning
        as.toStop = "SINGLE_LINE:s1";
        as.stops = Arrays.asList(new StopSpec("SINGLE_LINE:s5"));
        as.dwellTimes = new int[] { 40, 50 };
        as.hopTimes = new int[] { 60 };
        as.routes = set("SINGLE_LINE:route");

        Scenario scenario = new Scenario();
        scenario.modifications = Arrays.asList(as);

        TransportNetwork mod = scenario.applyToTransportNetwork(network);

        assertEquals(1, mod.transitLayer.tripPatterns.size());

        TripPattern pattern = mod.transitLayer.tripPatterns.get(0);

        // make sure the stops are in the right order
        assertEquals(5, pattern.stops.length);
        assertEquals("SINGLE_LINE:s5", mod.transitLayer.stopIdForIndex.get(pattern.stops[0]));
        assertEquals("SINGLE_LINE:s1", mod.transitLayer.stopIdForIndex.get(pattern.stops[1]));
        assertEquals("SINGLE_LINE:s2", mod.transitLayer.stopIdForIndex.get(pattern.stops[2]));
        assertEquals("SINGLE_LINE:s3", mod.transitLayer.stopIdForIndex.get(pattern.stops[3]));
        assertEquals("SINGLE_LINE:s4", mod.transitLayer.stopIdForIndex.get(pattern.stops[4]));

        for (TripSchedule schedule : pattern.tripSchedules) {
            // confirm the times are correct
            int[] a = IntStream.of(schedule.arrivals).map(time -> time - schedule.arrivals[0]).toArray();
            int[] d = IntStream.of(schedule.departures).map(time -> time - schedule.arrivals[0]).toArray();

            // slightly awkward, but make sure that the trip passes the original first stop at the same time it did before
            assertEquals("SINGLE_LINE:trip" + schedule.arrivals[1], schedule.tripId);

            // 40 sec dwell time at added stop s5, 60 sec travel time to s1, 50 sec dwell at s1, and back to 500 and 30 sec dwell time
            // to end (with s4 having no dwell, per FakeGraph)
            assertArrayEquals(new int[] { 0, 100, 650, 1180, 1710 }, a);
            assertArrayEquals(new int[] { 40, 150, 680, 1210, 1710 }, d);
        }

        assertEquals(checksum, network.checksum());
    }

    /**
     * Test extending a route, not removing any stops.
     * All stops are references to existing stops by ID, not newly created from coordinates.
     */
    @Test
    public void testExtendRouteAtEnd () {
        AddStops as = new AddStops();
        // add s5 at end
        as.fromStop = "SINGLE_LINE:s4";
        as.stops = Arrays.asList(new StopSpec("SINGLE_LINE:s5"));
        as.dwellTimes = new int[] { 20, 40 };
        as.hopTimes = new int[] { 60 };
        as.routes = set("SINGLE_LINE:route");

        Scenario scenario = new Scenario();
        scenario.modifications = Arrays.asList(as);

        TransportNetwork mod = scenario.applyToTransportNetwork(network);

        assertEquals(1, mod.transitLayer.tripPatterns.size());

        TripPattern pattern = mod.transitLayer.tripPatterns.get(0);

        // make sure the stops are in the right order
        assertEquals(5, pattern.stops.length);
        assertEquals("SINGLE_LINE:s1", mod.transitLayer.stopIdForIndex.get(pattern.stops[0]));
        assertEquals("SINGLE_LINE:s2", mod.transitLayer.stopIdForIndex.get(pattern.stops[1]));
        assertEquals("SINGLE_LINE:s3", mod.transitLayer.stopIdForIndex.get(pattern.stops[2]));
        assertEquals("SINGLE_LINE:s4", mod.transitLayer.stopIdForIndex.get(pattern.stops[3]));
        assertEquals("SINGLE_LINE:s5", mod.transitLayer.stopIdForIndex.get(pattern.stops[4]));

        for (TripSchedule schedule : pattern.tripSchedules) {
            // slightly awkward, but make sure that the trip starts at the same time it did before
            assertEquals("SINGLE_LINE:trip" + schedule.arrivals[0], schedule.tripId);

            int[] a = IntStream.of(schedule.arrivals).map(time -> time - schedule.arrivals[0]).toArray();
            int[] d = IntStream.of(schedule.departures).map(time -> time - schedule.arrivals[0]).toArray();

            // confirm the times are correct. Note that first and last stop of original route don't have a dwell time
            // so 0 sec dwell time at s1, 500 sec travel time from FakeGraph to s2, 30 sec dwell time at s2, 500 sec
            // travel time to s3, 30 sec dwell time at s3, 500 sec travel time to s4, 20 sec dwell time at s4 from modification, and 60 sec travel
            // time and 40 sec dwell time from modification to added stop s5
            assertArrayEquals(new int[] { 0, 500, 1030, 1560, 1640 }, a);
            assertArrayEquals(new int[] { 0, 530, 1060, 1580, 1680}, d);
        }

        assertEquals(checksum, network.checksum());
    }

    @After
    public void tearDown () {
        network = null;
    }
}