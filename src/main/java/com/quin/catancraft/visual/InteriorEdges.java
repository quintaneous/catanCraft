package com.quin.catancraft.visual;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Horizontal perimeter of authored, non-overlapping walkable rectangles on each floor.
 * Subtract adjoining spans so a room never displays internal seams from its rectangle decomposition. */
final class InteriorEdges {
    record Edge(boolean alongX, double fixed, double begin, double end, double floor, double ceiling,
                boolean active) {}
    private record Span(double lo, double hi) {}

    static List<Edge> build(List<OutlineShape> shapes) {
        List<OutlineShape> rooms = shapes.stream().filter(s -> s.kind() == OutlineShape.Kind.ACTIVE_INTERIOR
                || s.kind() == OutlineShape.Kind.INACTIVE_INTERIOR).toList();
        List<Edge> edges = new ArrayList<>();
        for (OutlineShape room : rooms) {
            for (boolean alongX : new boolean[]{true, false}) {
                double begin = alongX ? room.x0() : room.z0(), end = alongX ? room.x1() : room.z1();
                for (boolean lower : new boolean[]{true, false}) {
                    double fixed = face(room, alongX, lower);
                    List<Span> hidden = new ArrayList<>();
                    for (OutlineShape neighbor : rooms) {
                        if (neighbor == room || neighbor.kind() != room.kind() || neighbor.y0() != room.y0()
                                || neighbor.y1() != room.y1() || face(neighbor, alongX, !lower) != fixed) continue;
                        double lo = Math.max(begin, alongX ? neighbor.x0() : neighbor.z0());
                        double hi = Math.min(end, alongX ? neighbor.x1() : neighbor.z1());
                        if (lo < hi) hidden.add(new Span(lo, hi));
                    }
                    hidden.sort(Comparator.comparingDouble(Span::lo));
                    double cursor = begin;
                    for (Span span : hidden) {
                        add(edges, room, alongX, fixed, cursor, span.lo());
                        cursor = Math.max(cursor, span.hi());
                    }
                    add(edges, room, alongX, fixed, cursor, end);
                }
            }
        }
        return List.copyOf(edges);
    }

    private static double face(OutlineShape s, boolean alongX, boolean lower) {
        if (alongX) return lower ? s.z0() : s.z1();
        return lower ? s.x0() : s.x1();
    }

    private static void add(List<Edge> edges, OutlineShape s, boolean alongX, double fixed, double begin, double end) {
        if (begin < end) edges.add(new Edge(alongX, fixed, begin, end, s.y0(), s.y1(),
                s.kind() == OutlineShape.Kind.ACTIVE_INTERIOR));
    }
}
