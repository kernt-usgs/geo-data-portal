package gov.usgs.cida.gdp.utilities;

import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.nc2.dataset.CoordinateAxis1D;
import ucar.nc2.dataset.CoordinateAxis1DTime;
import ucar.nc2.dt.GridDatatype;
import ucar.nc2.time.CalendarDate;

public class TimeRangeUtil {
    public static Range generateTimeRange(GridDatatype gridDataType, Date timeStart, Date timeEnd) {
        CoordinateAxis1DTime timeAxis = gridDataType.getCoordinateSystem().getTimeAxis1D();
        Range timeRange = null;
        if (timeAxis != null) {
            int timeStartIndex = timeStart != null
                    ? timeAxis.findTimeIndexFromDate(timeStart)
                    : 0;
            int timeEndIndex = timeEnd != null
                    ? timeAxis.findTimeIndexFromDate(timeEnd)
                    : timeAxis.getShape(0) - 1;
            try {
                timeRange = new Range(timeStartIndex, timeEndIndex);
            } catch (InvalidRangeException e) {
                throw new RuntimeException("Unable to generate time range.", e);
            }
        }
        return timeRange;
    }
    
    public static CalendarDate getTimeFromRangeIndex(GridDatatype gridDataType, int index) {
        CoordinateAxis1DTime timeAxis = gridDataType.getCoordinateSystem().getTimeAxis1D();
        
        return timeAxis.getCalendarDate(index);
    }
    
    public static Iterable<Range> decomposeTimeAxis(CoordinateAxis1D axis) {
        Range range = null;
        if (axis != null) {
            List<Range> rangeList = axis.getRanges();
            if (rangeList != null) {
                if (rangeList.size() == 1) {
                    range = rangeList.get(0);
                } else {
                    // decomposing along a single range for axes with more than
                    // one is something we don't want to do.  This should only
                    // happen with X and Y axes (and this method shouldn't be
                    // called for those)
                    throw new IllegalArgumentException(
                            "Axis \"" + axis.getFullName() + "\" has coordinates with more that 1 dimension");
                }
            }
        }
        return decomposeRange(range);
    }
    
    public static Iterable<Range> decomposeRange(Range range) {
        return new SingleElementRangeDecomposer(range);
    }
    
    public static class SingleElementRangeDecomposer implements Iterable<Range> {
        public final Range range;
        public SingleElementRangeDecomposer(Range range) {
            this.range = range;
        }
        @Override
        public Iterator<Range>iterator() {
            return range == null ?
                Arrays.asList((Range)null).iterator() :
                new SingleElementRangeIterator(range);
        }
        public class SingleElementRangeIterator implements Iterator<Range> {
            private final Range range;
            private int next = 0;
            public SingleElementRangeIterator(Range range) {
                this.range = range;
            }
            @Override
            public boolean hasNext() {
                return next < range.length();
            }
            @Override
            public Range next() {
                Range r = null;
                try {
                    int e = range.element(next++);
                    // yes!  upper-bound is inclusive!  Yes I agree it's wierd!
                    r = new Range(e, e, 1);
                } catch (InvalidRangeException ignore) { /* why is this a checked exception? */ }
                return r;
            }
            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }   
        }
    }
}
