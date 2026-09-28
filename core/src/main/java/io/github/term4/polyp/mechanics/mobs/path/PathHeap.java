package io.github.term4.polyp.mechanics.mobs.path;

/** 1.8 Path: the open set, a binary heap on {@code distanceToTarget}. */
final class PathHeap {

    private PathPoint[] points = new PathPoint[1024];
    private int count;

    PathPoint add(PathPoint point) {
        if (point.index >= 0) throw new IllegalStateException("already queued");
        if (count == points.length) {
            PathPoint[] grown = new PathPoint[count << 1];
            System.arraycopy(points, 0, grown, 0, count);
            points = grown;
        }
        points[count] = point;
        point.index = count;
        sortBack(count++);
        return point;
    }

    void clear() { count = 0; }

    PathPoint dequeue() {
        PathPoint first = points[0];
        points[0] = points[--count];
        points[count] = null;
        if (count > 0) sortForward(0);
        first.index = -1;
        return first;
    }

    void changeDistance(PathPoint point, float distance) {
        float was = point.distanceToTarget;
        point.distanceToTarget = distance;
        if (distance < was) sortBack(point.index);
        else sortForward(point.index);
    }

    private void sortBack(int i) {
        PathPoint point = points[i];
        int parent;
        for (float d = point.distanceToTarget; i > 0; i = parent) {
            parent = i - 1 >> 1;
            PathPoint above = points[parent];
            if (d >= above.distanceToTarget) break;
            points[i] = above;
            above.index = i;
        }
        points[i] = point;
        point.index = i;
    }

    private void sortForward(int i) {
        PathPoint point = points[i];
        float d = point.distanceToTarget;
        while (true) {
            int left = 1 + (i << 1);
            int right = left + 1;
            if (left >= count) break;
            PathPoint l = points[left];
            float dl = l.distanceToTarget;
            PathPoint r;
            float dr;
            if (right >= count) {
                r = null;
                dr = Float.POSITIVE_INFINITY;
            } else {
                r = points[right];
                dr = r.distanceToTarget;
            }
            if (dl < dr) {
                if (dl >= d) break;
                points[i] = l;
                l.index = i;
                i = left;
            } else {
                if (dr >= d) break;
                points[i] = r;
                r.index = i;
                i = right;
            }
        }
        points[i] = point;
        point.index = i;
    }

    boolean isEmpty() { return count == 0; }
}
